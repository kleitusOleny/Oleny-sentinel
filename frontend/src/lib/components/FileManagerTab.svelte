<script lang="ts">
	import { onMount } from 'svelte';

	interface FileItem {
		filename: string;
		size: number;
		contentType: string;
		lastModified: string;
		streamUrl: string;
		presignedUrl: string;
	}

	let files = $state<FileItem[]>([]);
	let isLoading = $state(false);
	let isUploading = $state(false);
	let uploadError = $state('');
	let copySuccess = $state<Record<string, boolean>>({});

	// Preview modal state
	let previewFile = $state<FileItem | null>(null);

	let fileInputRef: HTMLInputElement | null = null;
	let isDragging = $state(false);

	async function fetchFiles() {
		isLoading = true;
		try {
			const res = await fetch('/api/files');
			if (res.ok) {
				files = await res.json();
			}
		} catch (err: any) {
			console.error('Lỗi khi tải danh sách files:', err);
		} finally {
			isLoading = false;
		}
	}

	async function handleUpload(fileList: FileList | null) {
		if (!fileList || fileList.length === 0) return;
		isUploading = true;
		uploadError = '';

		try {
			for (let i = 0; i < fileList.length; i++) {
				const file = fileList[i];
				const formData = new FormData();
				formData.append('file', file);

				const res = await fetch('/api/files/upload', {
					method: 'POST',
					body: formData
				});

				if (!res.ok) {
					const data = await res.json().catch(() => ({}));
					throw new Error(data.error || `Tải file ${file.name} thất bại.`);
				}
			}
			await fetchFiles();
		} catch (err: any) {
			uploadError = err.message || 'Có lỗi xảy ra khi upload.';
		} finally {
			isUploading = false;
			if (fileInputRef) fileInputRef.value = '';
		}
	}

	async function handleDelete(filename: string) {
		if (!confirm(`Bạn có chắc chắn muốn xóa file "${filename}"?`)) return;

		try {
			const res = await fetch(`/api/files/${encodeURIComponent(filename)}`, {
				method: 'DELETE'
			});
			if (res.ok) {
				files = files.filter((f) => f.filename !== filename);
				if (previewFile?.filename === filename) {
					previewFile = null;
				}
			} else {
				alert('Xóa file không thành công.');
			}
		} catch (err) {
			alert('Lỗi kết nối khi xóa file.');
		}
	}

	function copyToClipboard(text: string, id: string) {
		navigator.clipboard.writeText(text);
		copySuccess[id] = true;
		setTimeout(() => {
			copySuccess[id] = false;
		}, 2000);
	}

	function formatBytes(bytes: number): string {
		if (bytes === 0) return '0 B';
		const k = 1024;
		const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
		const i = Math.floor(Math.log(bytes) / Math.log(k));
		return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
	}

	function formatDate(dateStr: string): string {
		if (!dateStr) return 'N/A';
		try {
			const d = new Date(dateStr);
			return d.toLocaleString('vi-VN');
		} catch {
			return dateStr;
		}
	}

	function isVideo(contentType: string, name: string): boolean {
		return contentType.startsWith('video/') || /\.(mp4|mkv|webm|mov|avi)$/i.test(name);
	}

	function isAudio(contentType: string, name: string): boolean {
		return contentType.startsWith('audio/') || /\.(mp3|wav|ogg|flac|aac|m4a)$/i.test(name);
	}

	function isImage(contentType: string, name: string): boolean {
		return contentType.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(name);
	}

	function isPdf(contentType: string, name: string): boolean {
		return contentType === 'application/pdf' || /\.pdf$/i.test(name);
	}

	onMount(() => {
		fetchFiles();
	});
</script>

<div class="space-y-6">
	<!-- Upload Box / Dropzone -->
	<div
		class="relative border-2 border-dashed rounded-2xl p-8 text-center transition-all duration-200 {isDragging
			? 'border-indigo-500 bg-indigo-500/10'
			: 'border-zinc-800 bg-zinc-900/40 hover:border-zinc-700'}"
		ondragover={(e) => {
			e.preventDefault();
			isDragging = true;
		}}
		ondragleave={() => (isDragging = false)}
		ondrop={(e) => {
			e.preventDefault();
			isDragging = false;
			handleUpload(e.dataTransfer?.files || null);
		}}
		role="region"
		aria-label="File upload dropzone"
	>
		<input
			type="file"
			multiple
			class="hidden"
			bind:this={fileInputRef}
			onchange={(e: any) => handleUpload(e.target.files)}
		/>

		<div class="flex flex-col items-center justify-center space-y-3">
			<div class="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
				<svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
				</svg>
			</div>

			<div>
				<p class="text-sm font-medium text-zinc-200">
					Kéo & thả bất kỳ file nào vào đây hoặc
					<button
						type="button"
						class="text-indigo-400 hover:text-indigo-300 font-semibold underline underline-offset-2 ml-1"
						onclick={() => fileInputRef?.click()}
					>
						chọn tệp từ máy
					</button>
				</p>
				<p class="text-xs text-zinc-400 mt-1">Hỗ trợ Video (MP4, MKV), Nhạc (MP3), Ảnh, PDF, ZIP, v.v. (tối đa 2GB/file)</p>
			</div>

			{#if isUploading}
				<div class="flex items-center space-x-2 text-indigo-400 text-sm font-medium animate-pulse mt-2">
					<svg class="animate-spin h-4 w-4" viewBox="0 0 24 24">
						<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
						<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
					</svg>
					<span>Đang tải file lên MinIO...</span>
				</div>
			{/if}

			{#if uploadError}
				<p class="text-xs text-rose-400 bg-rose-500/10 border border-rose-500/20 px-3 py-1.5 rounded-lg mt-2">
					{uploadError}
				</p>
			{/if}
		</div>
	</div>

	<!-- File List Header & Refresh -->
	<div class="flex items-center justify-between">
		<div class="flex items-center space-x-3">
			<h2 class="text-lg font-bold text-zinc-100 flex items-center gap-2">
				<span>Danh sách tệp lưu trữ</span>
				<span class="text-xs px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400 border border-zinc-700">
					{files.length}
				</span>
			</h2>
		</div>

		<button
			onclick={fetchFiles}
			disabled={isLoading}
			class="flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-zinc-800/80 hover:bg-zinc-700 text-zinc-300 transition-colors border border-zinc-700/60"
		>
			<svg class="w-3.5 h-3.5 {isLoading ? 'animate-spin' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
			</svg>
			<span>Làm mới</span>
		</button>
	</div>

	<!-- File Table / Cards -->
	{#if isLoading && files.length === 0}
		<div class="py-12 text-center text-zinc-400 text-sm animate-pulse">
			Đang tải dữ liệu từ MinIO...
		</div>
	{:else if files.length === 0}
		<div class="py-12 text-center border border-zinc-800/60 rounded-xl bg-zinc-900/20">
			<p class="text-zinc-400 text-sm">Chưa có file nào được tải lên. Hãy kéo thả file vào hộp bên trên!</p>
		</div>
	{:else}
		<div class="overflow-x-auto rounded-xl border border-zinc-800 bg-zinc-900/30">
			<table class="w-full text-left text-sm text-zinc-300">
				<thead class="bg-zinc-900/70 text-xs uppercase text-zinc-400 border-b border-zinc-800">
					<tr>
						<th class="px-4 py-3">Tên file</th>
						<th class="px-4 py-3">Loại file</th>
						<th class="px-4 py-3">Kích thước</th>
						<th class="px-4 py-3">Ngày tải lên</th>
						<th class="px-4 py-3 text-right">Thao tác & Link</th>
					</tr>
				</thead>
				<tbody class="divide-y divide-zinc-800/50">
					{#each files as file}
						<tr class="hover:bg-zinc-800/30 transition-colors">
							<td class="px-4 py-3 font-medium text-zinc-100 flex items-center gap-2 max-w-xs truncate" title={file.filename}>
								{#if isVideo(file.contentType, file.filename)}
									<span class="text-indigo-400">🎬</span>
								{:else if isAudio(file.contentType, file.filename)}
									<span class="text-amber-400">🎵</span>
								{:else if isImage(file.contentType, file.filename)}
									<span class="text-emerald-400">🖼️</span>
								{:else if isPdf(file.contentType, file.filename)}
									<span class="text-rose-400">📄</span>
								{:else}
									<span class="text-zinc-400">📁</span>
								{/if}
								<span class="truncate">{file.filename}</span>
							</td>

							<td class="px-4 py-3 text-xs text-zinc-400">
								<span class="px-2 py-0.5 rounded bg-zinc-800 border border-zinc-700/60">
									{file.contentType}
								</span>
							</td>

							<td class="px-4 py-3 text-xs text-zinc-300 font-mono">
								{formatBytes(file.size)}
							</td>

							<td class="px-4 py-3 text-xs text-zinc-400">
								{formatDate(file.lastModified)}
							</td>

							<td class="px-4 py-3 text-right space-x-2 whitespace-nowrap">
								<!-- Nút Preview / Stream -->
								<button
									onclick={() => (previewFile = file)}
									class="px-2.5 py-1 text-xs font-semibold rounded bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 border border-indigo-500/20 transition-colors"
									title="Xem / Phát trực tiếp"
								>
									▶ Phát / Xem
								</button>

								<!-- Nút Copy Link -->
								<button
									onclick={() => copyToClipboard(file.presignedUrl || window.location.origin + file.streamUrl, file.filename)}
									class="px-2.5 py-1 text-xs font-semibold rounded bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/20 transition-colors"
									title="Sao chép link truy cập"
								>
									{copySuccess[file.filename] ? '✓ Đã sao chép' : '🔗 Copy Link'}
								</button>

								<!-- Nút Tải về -->
								<a
									href={file.streamUrl}
									download={file.filename}
									class="inline-block px-2.5 py-1 text-xs font-semibold rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-300 border border-zinc-700 transition-colors"
									title="Tải về máy"
								>
									⬇ Tải
								</a>

								<!-- Nút Xóa -->
								<button
									onclick={() => handleDelete(file.filename)}
									class="px-2.5 py-1 text-xs font-semibold rounded bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/20 transition-colors"
									title="Xóa tệp"
								>
									🗑 Xóa
								</button>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}

	<!-- Modal Preview & Streaming Player -->
	{#if previewFile}
		<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fadeIn">
			<div class="bg-zinc-900 border border-zinc-800 rounded-2xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
				<!-- Header Modal -->
				<div class="flex items-center justify-between px-6 py-4 border-b border-zinc-800 bg-zinc-900/60">
					<div class="flex items-center space-x-2 truncate pr-4">
						<span class="font-bold text-zinc-100 truncate">{previewFile.filename}</span>
						<span class="text-xs text-zinc-400 font-mono">({formatBytes(previewFile.size)})</span>
					</div>
					<button
						onclick={() => (previewFile = null)}
						class="text-zinc-400 hover:text-zinc-100 p-1.5 rounded-lg hover:bg-zinc-800 transition-colors"
					>
						✕
					</button>
				</div>

				<!-- Content / Media Player -->
				<div class="p-6 flex-1 overflow-auto flex items-center justify-center bg-black/40">
					{#if isVideo(previewFile.contentType, previewFile.filename)}
						<!-- Video Streaming Player -->
						<video
							controls
							autoplay
							class="max-h-[60vh] w-full rounded-xl border border-zinc-800 bg-black shadow-lg"
							src={previewFile.streamUrl}
						>
							<track kind="captions" />
							Trình duyệt của bạn không hỗ trợ thẻ video.
						</video>
					{:else if isAudio(previewFile.contentType, previewFile.filename)}
						<!-- Audio Streaming Player -->
						<div class="w-full max-w-lg p-8 bg-zinc-950/80 rounded-2xl border border-zinc-800 flex flex-col items-center space-y-4">
							<div class="w-16 h-16 rounded-full bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-2xl text-amber-400">
								🎵
							</div>
							<p class="text-zinc-200 font-medium text-center">{previewFile.filename}</p>
							<audio controls autoplay class="w-full" src={previewFile.streamUrl}>
								Trình duyệt của bạn không hỗ trợ thẻ audio.
							</audio>
						</div>
					{:else if isImage(previewFile.contentType, previewFile.filename)}
						<!-- Image View -->
						<img
							src={previewFile.streamUrl}
							alt={previewFile.filename}
							class="max-h-[65vh] max-w-full rounded-xl object-contain border border-zinc-800"
						/>
					{:else if isPdf(previewFile.contentType, previewFile.filename)}
						<!-- PDF Viewer -->
						<iframe
							src={previewFile.streamUrl}
							title={previewFile.filename}
							class="w-full h-[65vh] rounded-xl border border-zinc-800 bg-white"
						></iframe>
					{:else}
						<!-- Generic File -->
						<div class="text-center space-y-3 py-10">
							<p class="text-zinc-400 text-sm">Không thể xem trực tiếp định dạng này.</p>
							<a
								href={previewFile.streamUrl}
								download={previewFile.filename}
								class="px-4 py-2 text-sm font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white transition-colors"
							>
								Tải xuống tệp
							</a>
						</div>
					{/if}
				</div>

				<!-- Footer URL & Copy -->
				<div class="px-6 py-3 border-t border-zinc-800 bg-zinc-950/60 flex items-center justify-between text-xs text-zinc-400">
					<div class="truncate max-w-lg font-mono text-[11px] text-zinc-500">
						Link: {previewFile.presignedUrl || previewFile.streamUrl}
					</div>
					<button
						onclick={() => copyToClipboard(previewFile?.presignedUrl || window.location.origin + previewFile?.streamUrl, 'modal')}
						class="text-indigo-400 hover:text-indigo-300 font-semibold"
					>
						{copySuccess['modal'] ? '✓ Đã sao chép link' : 'Sao chép link truy cập'}
					</button>
				</div>
			</div>
		</div>
	{/if}
</div>
