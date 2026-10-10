<script lang="ts">
	import { onMount } from 'svelte';

	interface Props {
		apiBase: string;
		currentPath?: string;
		isOpen: boolean;
		onclose: () => void;
		onuploadSuccess?: () => void;
	}

	let {
		apiBase,
		currentPath = '',
		isOpen,
		onclose,
		onuploadSuccess
	}: Props = $props();

	let selectedFiles = $state<File[]>([]);
	let isUploading = $state(false);
	let uploadProgress = $state(0);
	let currentUploadingName = $state('');
	let statusMessage = $state('');
	let errorMessage = $state('');
	let fileInputMobile: HTMLInputElement;

	let isProcessingFiles = $state(false);

	function formatBytes(bytes: number, decimals = 2) {
		if (bytes === 0) return '0 B';
		const k = 1024;
		const dm = decimals < 0 ? 0 : decimals;
		const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
		const i = Math.floor(Math.log(bytes) / Math.log(k));
		return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
	}

	async function handleFileSelect(e: Event) {
		const target = e.target as HTMLInputElement;
		if (!target.files || target.files.length === 0) return;

		isProcessingFiles = true;
		errorMessage = '';
		statusMessage = 'Đang kiểm tra và nạp tệp từ thiết bị...';

		try {
			const rawFiles = Array.from(target.files);
			const validFiles: File[] = [];
			const emptyOrICloudFiles: string[] = [];

			for (const file of rawFiles) {
				// Thử đọc 1 đoạn nhỏ để kiểm tra xem file có sẵn trên máy hay đang bị kẹt trên iCloud
				try {
					const slice = file.slice(0, Math.min(1024, file.size || 1024));
					const buffer = await slice.arrayBuffer();
					// Nếu file báo size > 0 nhưng buffer đọc ra rỗng hoặc file size = 0
					if (file.size === 0 && buffer.byteLength === 0) {
						emptyOrICloudFiles.push(file.name);
					} else {
						validFiles.push(file);
					}
				} catch (readErr) {
					console.warn('Không thể đọc file từ thiết bị (có thể do iCloud):', file.name, readErr);
					emptyOrICloudFiles.push(file.name);
				}
			}

			if (validFiles.length > 0) {
				selectedFiles = [...selectedFiles, ...validFiles];
				statusMessage = `Đã chọn ${selectedFiles.length} tệp tin sẵn sàng tải lên.`;
			}

			if (emptyOrICloudFiles.length > 0) {
				errorMessage = `⚠️ ${emptyOrICloudFiles.length} tệp (${emptyOrICloudFiles.join(', ')}) chưa được tải về máy (đang lưu trên iCloud). Hãy mở ứng dụng Ảnh/Files trên iPhone để tải về trước, hoặc chụp/chọn lại ảnh.`;
			}
		} catch (err: any) {
			console.error('Lỗi khi nạp file:', err);
			errorMessage = 'Không thể nạp tệp tin từ thiết bị: ' + (err.message || 'Lỗi không xác định');
		} finally {
			isProcessingFiles = false;
			// Reset value để người dùng có thể chọn lại cùng 1 file nếu muốn
			target.value = '';
		}
	}

	function removeFile(index: number) {
		selectedFiles = selectedFiles.filter((_, i) => i !== index);
	}

	async function startMobileUpload() {
		if (selectedFiles.length === 0 || isUploading) return;

		isUploading = true;
		errorMessage = '';
		uploadProgress = 0;

		try {
			// Upload tuần tự từng file bằng FormData multipart trực tiếp
			for (let i = 0; i < selectedFiles.length; i++) {
				const file = selectedFiles[i];
				currentUploadingName = file.name;
				statusMessage = `Đang tải lên (${i + 1}/${selectedFiles.length}): ${file.name}`;

				// Nạp dữ liệu thành Blob độc lập để tránh Safari giải phóng con trỏ stream ngầm
				let uploadBlob: Blob;
				try {
					const arrayBuffer = await file.arrayBuffer();
					if (arrayBuffer.byteLength === 0) {
						throw new Error(`Tệp "${file.name}" có dung lượng 0 bytes hoặc chưa tải xong từ iCloud.`);
					}
					uploadBlob = new Blob([arrayBuffer], { type: file.type || 'application/octet-stream' });
				} catch (blobErr: any) {
					throw new Error(blobErr.message || `Lỗi khi đọc dữ liệu tệp "${file.name}".`);
				}

				const formData = new FormData();
				formData.append('path', currentPath);
				// Gửi Blob kèm tên file chính xác
				formData.append('file', uploadBlob, file.name);

				await new Promise<void>((resolve, reject) => {
					const xhr = new XMLHttpRequest();
					xhr.open('POST', `${apiBase}/storage/upload`);
					xhr.timeout = 180000; // 3 phút timeout cho file lớn

					xhr.upload.onprogress = (e) => {
						if (e.lengthComputable) {
							// Tính tổng % trên toàn bộ danh sách
							uploadProgress = Math.round(((i + e.loaded / e.total) / selectedFiles.length) * 100);
						}
					};

					xhr.onload = () => {
						if (xhr.status >= 200 && xhr.status < 300) {
							resolve();
						} else {
							try {
								const res = JSON.parse(xhr.responseText);
								reject(new Error(res.message || `Lỗi máy chủ (${xhr.status})`));
							} catch {
								reject(new Error(`Tải lên thất bại (Mã lỗi HTTP: ${xhr.status} - ${xhr.statusText || 'Unknown'})`));
							}
						}
					};

					xhr.onerror = () => reject(new Error('Lỗi kết nối mạng khi tải tệp lên (Network / CORS error).'));
					xhr.ontimeout = () => reject(new Error('Hết thời gian chờ (Upload timeout).'));

					xhr.send(formData);
				});
			}

			uploadProgress = 100;
			statusMessage = '✅ Đã tải lên thành công toàn bộ tệp tin!';
			selectedFiles = [];
			if (onuploadSuccess) onuploadSuccess();

			// Tự động đóng sau 1.5 giây
			setTimeout(() => {
				if (isOpen) onclose();
			}, 1500);

		} catch (err: any) {
			console.error('[Mobile Upload Error]:', err);
			errorMessage = err.message || 'Lỗi khi tải tệp lên từ thiết bị.';
			statusMessage = '';
		} finally {
			isUploading = false;
			currentUploadingName = '';
		}
	}

	function handleClose() {
		if (isUploading) {
			if (!confirm('Đang tải tệp lên, bạn có chắc chắn muốn thoát?')) return;
		}
		selectedFiles = [];
		errorMessage = '';
		statusMessage = '';
		uploadProgress = 0;
		onclose();
	}
</script>

{#if isOpen}
	<!-- Backdrop -->
	<button
		type="button"
		aria-label="Đóng tải lên"
		onclick={handleClose}
		class="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm transition-opacity w-full h-full border-0 p-0 m-0 cursor-default"
	></button>

	<!-- Mobile Upload Modal Dialog -->
	<div class="fixed bottom-0 left-0 right-0 z-50 bg-zinc-950 border-t border-zinc-800 rounded-t-3xl p-5 shadow-2xl max-h-[90vh] overflow-y-auto space-y-4 animate-slideUp safe-area-pb">
		<!-- Header -->
		<div class="flex items-center justify-between pb-3 border-b border-zinc-800/80">
			<div class="flex items-center gap-2.5">
				<div class="w-8 h-8 rounded-xl bg-indigo-600/20 text-indigo-400 border border-indigo-500/30 flex items-center justify-center text-sm">
					📱
				</div>
				<div>
					<h3 class="text-sm font-bold text-white">Gửi Tệp Tin Từ Di Động</h3>
					<p class="text-[10px] text-zinc-400 font-mono">
						Lưu vào: /{currentPath || 'Root'}
					</p>
				</div>
			</div>
			<button
				onclick={handleClose}
				class="p-1.5 rounded-xl text-zinc-400 hover:text-white bg-zinc-900 border border-zinc-800 cursor-pointer"
			>
				✕
			</button>
		</div>

		<!-- File Selector Input (Native Hidden) -->
		<input
			id="mobile-native-file-picker"
			bind:this={fileInputMobile}
			onchange={handleFileSelect}
			type="file"
			multiple
			class="sr-only"
		/>

		<!-- Select Buttons Area -->
		{#if selectedFiles.length === 0}
			<label
				for="mobile-native-file-picker"
				class="w-full py-8 px-4 rounded-2xl border-2 border-dashed border-indigo-500/40 hover:border-indigo-400 bg-indigo-950/20 flex flex-col items-center justify-center gap-2 cursor-pointer transition-all active:scale-98 select-none"
			>
				{#if isProcessingFiles}
					<span class="w-8 h-8 rounded-full border-3 border-indigo-400 border-t-transparent animate-spin"></span>
					<div class="text-xs font-bold text-indigo-300">Đang nạp dữ liệu từ thiết bị...</div>
				{:else}
					<span class="text-3xl">📤</span>
					<div class="text-center">
						<div class="text-xs font-bold text-indigo-300">Nhấn vào đây để chọn tệp từ điện thoại</div>
						<div class="text-[10px] text-zinc-400 mt-0.5">Hỗ trợ Hình ảnh, Video, Tài liệu, Tệp nén...</div>
					</div>
				{/if}
			</label>
		{:else}
			<!-- Selected Files List -->
			<div class="space-y-2">
				<div class="flex items-center justify-between text-xs text-zinc-400">
					<span>Đã chọn ({selectedFiles.length} tệp):</span>
					{#if !isUploading}
						<label
							for="mobile-native-file-picker"
							class="text-indigo-400 hover:underline font-semibold cursor-pointer text-[11px]"
						>
							+ Chọn thêm
						</label>
					{/if}
				</div>

				<div class="max-h-48 overflow-y-auto space-y-1.5 p-1">
					{#each selectedFiles as f, i}
						<div class="p-2.5 rounded-xl bg-zinc-900/80 border border-zinc-800 flex items-center justify-between gap-2 text-xs">
							<div class="truncate flex-1">
								<div class="font-medium text-zinc-200 truncate">{f.name}</div>
								<div class="text-[10px] text-zinc-500 font-mono">{formatBytes(f.size)}</div>
							</div>
							{#if !isUploading}
								<button
									onclick={() => removeFile(i)}
									class="text-rose-400 p-1 hover:bg-rose-950/40 rounded-lg cursor-pointer text-xs"
								>
									✕
								</button>
							{/if}
						</div>
					{/each}
				</div>
			</div>
		{/if}

		<!-- Upload Progress Bar -->
		{#if isUploading}
			<div class="space-y-2 p-3 rounded-2xl bg-zinc-900/90 border border-zinc-800">
				<div class="flex items-center justify-between text-xs">
					<span class="text-zinc-300 font-bold truncate flex items-center gap-2">
						<span class="w-2.5 h-2.5 rounded-full border-2 border-indigo-400 border-t-transparent animate-spin"></span>
						{currentUploadingName || 'Đang truyền tệp...'}
					</span>
					<span class="text-indigo-400 font-mono font-bold">{uploadProgress}%</span>
				</div>
				<div class="w-full h-2 bg-zinc-800 rounded-full overflow-hidden">
					<div
						class="h-full bg-gradient-to-r from-indigo-500 to-cyan-400 rounded-full transition-all duration-200"
						style="width: {uploadProgress}%"
					></div>
				</div>
			</div>
		{/if}

		<!-- Status / Error Messages -->
		{#if statusMessage}
			<div class="text-xs text-emerald-400 bg-emerald-950/30 border border-emerald-800/40 p-2.5 rounded-xl font-medium">
				{statusMessage}
			</div>
		{/if}
		{#if errorMessage}
			<div class="text-xs text-rose-400 bg-rose-950/30 border border-rose-800/40 p-2.5 rounded-xl font-medium">
				{errorMessage}
			</div>
		{/if}

		<!-- Action Footer -->
		<div class="pt-2 flex items-center gap-3">
			<button
				onclick={handleClose}
				disabled={isUploading}
				class="flex-1 py-2.5 rounded-xl bg-zinc-900 text-zinc-300 font-bold text-xs border border-zinc-800 hover:bg-zinc-800 disabled:opacity-50 cursor-pointer"
			>
				Đóng
			</button>
			{#if selectedFiles.length > 0}
				<button
					onclick={startMobileUpload}
					disabled={isUploading}
					class="flex-1 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-900 text-white font-bold text-xs shadow-lg shadow-indigo-950 transition-all cursor-pointer disabled:cursor-not-allowed flex items-center justify-center gap-2"
				>
					{#if isUploading}
						<span class="w-3.5 h-3.5 rounded-full border-2 border-white border-t-transparent animate-spin"></span>
						<span>Đang gửi...</span>
					{:else}
						<span>Tải lên ngay ({selectedFiles.length})</span>
					{/if}
				</button>
			{/if}
		</div>
	</div>
{/if}
