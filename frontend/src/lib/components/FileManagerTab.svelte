<script lang="ts">
	import { onMount } from 'svelte';

	interface FileItem {
		name: string;
		path: string;
		size: number;
		directory: boolean;
		lastModified: number;
		extension: string;
		mimeType: string;
	}

	interface Props {
		apiBase: string;
		onpreview: (item: FileItem) => void;
		ondownload: (item: FileItem) => void;
	}

	let { apiBase, onpreview, ondownload }: Props = $props();

	// State
	let currentPath = $state('');
	let items = $state<FileItem[]>([]);
	let isLoading = $state(false);
	let errorMessage = $state('');
	let viewMode = $state<'table' | 'grid'>('table');
	let searchQuery = $state('');

	// Upload states
	let isUploading = $state(false);
	let uploadProgress = $state(0); // 0 - 100%
	let uploadSpeedText = $state(''); // e.g. 1.2 MB/s
	let uploadSizeText = $state(''); // e.g. 1.2 MB / 2.0 MB
	let uploadStatusText = $state('');
	let fileInputRef: HTMLInputElement;

	// New Folder Dialog
	let showNewFolderModal = $state(false);
	let newFolderName = $state('');
	let isCreatingFolder = $state(false);

	// Delete confirmation
	let deleteTarget = $state<FileItem | null>(null);
	let isDeleting = $state(false);

	// Direct IP Fast Upload Mode (Bypass Cloudflare Tunnel)
	// Server Tailscale IP: 100.94.177.113:8081
	let directUploadMode = $state(true);
	let directBackendUrl = $state('http://100.94.177.113:8081/api');

	// Derived: Breadcrumbs
	let breadcrumbs = $derived.by(() => {
		if (!currentPath) return [];
		const parts = currentPath.split('/').filter(Boolean);
		let acc = '';
		return parts.map((part) => {
			acc = acc ? `${acc}/${part}` : part;
			return { name: part, path: acc };
		});
	});

	// Derived: Filtered items
	let filteredItems = $derived(
		items.filter((item) => item.name.toLowerCase().includes(searchQuery.toLowerCase()))
	);

	function formatBytes(bytes: number, decimals = 1): string {
		if (bytes === 0) return '0 B';
		const k = 1024;
		const dm = decimals < 0 ? 0 : decimals;
		const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
		const i = Math.floor(Math.log(bytes) / Math.log(k));
		return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
	}

	function formatDate(timestamp: number): string {
		if (!timestamp) return '-';
		const d = new Date(timestamp);
		return d.toLocaleString('vi-VN', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit'
		});
	}

	async function loadDirectory(path: string = currentPath) {
		isLoading = true;
		errorMessage = '';
		try {
			const res = await fetch(`${apiBase}/storage/list?path=${encodeURIComponent(path)}`);
			const data = await res.json();
			if (!res.ok || data.status === 'error') {
				throw new Error(data.message || 'Không thể đọc thư mục');
			}
			currentPath = data.currentPath || '';
			items = data.items || [];
		} catch (err: any) {
			console.error(err);
			errorMessage = err.message || 'Lỗi kết nối khi tải danh sách tệp';
		} finally {
			isLoading = false;
		}
	}

	function navigateTo(path: string) {
		loadDirectory(path);
	}

	function navigateUp() {
		if (!currentPath) return;
		const parts = currentPath.split('/').filter(Boolean);
		parts.pop();
		const parentPath = parts.join('/');
		loadDirectory(parentPath);
	}

	async function handleUploadFiles(e: Event) {
		const target = e.target as HTMLInputElement;
		if (!target.files || target.files.length === 0) return;

		const filesToUpload = Array.from(target.files);
		isUploading = true;
		uploadProgress = 0;
		uploadSpeedText = '0 KB/s';
		uploadSizeText = '0 B';
		uploadStatusText = `Chuẩn bị tải lên ${filesToUpload.length} tệp...`;

		const formData = new FormData();
		formData.append('path', currentPath);
		for (const file of filesToUpload) {
			formData.append('file', file);
		}

		let startTime = Date.now();
		let lastLoaded = 0;
		let lastTime = startTime;

		let uploadTargetUrl = directUploadMode && directBackendUrl ? `${directBackendUrl}/storage/upload` : `${apiBase}/storage/upload`;

		try {
			await new Promise<void>((resolve, reject) => {
				const xhr = new XMLHttpRequest();
				xhr.open('POST', uploadTargetUrl);

				xhr.upload.onprogress = (event) => {
					if (event.lengthComputable) {
						const now = Date.now();
						const percent = Math.round((event.loaded / event.total) * 100);
						uploadProgress = percent;
						uploadSizeText = `${formatBytes(event.loaded)} / ${formatBytes(event.total)}`;

						// Tính tốc độ trung bình theo khoảng thời gian
						const timeDiff = (now - lastTime) / 1000;
						if (timeDiff >= 0.3 || event.loaded === event.total) {
							const bytesDiff = event.loaded - lastLoaded;
							const speedBytesPerSec = timeDiff > 0 ? bytesDiff / timeDiff : 0;
							uploadSpeedText = `${formatBytes(speedBytesPerSec)}/s`;
							lastLoaded = event.loaded;
							lastTime = now;
						}

						if (percent < 100) {
							uploadStatusText = `Đang tải lên (${percent}%)...`;
						} else {
							uploadStatusText = 'Đang lưu tệp vào máy chủ...';
						}
					}
				};

				xhr.onload = () => {
					if (xhr.status >= 200 && xhr.status < 300) {
						try {
							const data = JSON.parse(xhr.responseText);
							if (data.status === 'error') {
								reject(new Error(data.message || 'Lỗi xử lý tệp trên máy chủ'));
							} else {
								resolve();
							}
						} catch (jsonErr) {
							resolve();
						}
					} else {
						try {
							const data = JSON.parse(xhr.responseText);
							reject(new Error(data.message || `Lỗi tải lên: mã phản hồi ${xhr.status}`));
						} catch {
							reject(new Error(`Tải lên thất bại: mã phản hồi ${xhr.status}`));
						}
					}
				};

				xhr.onerror = () => {
					reject(new Error('Mất kết nối mạng hoặc server không phản hồi'));
				};

				xhr.send(formData);
			});

			await loadDirectory(currentPath);
		} catch (err: any) {
			alert(`Lỗi upload: ${err.message}`);
		} finally {
			isUploading = false;
			uploadProgress = 0;
			uploadSpeedText = '';
			uploadSizeText = '';
			uploadStatusText = '';
			target.value = '';
		}
	}

	async function handleCreateFolder() {
		if (!newFolderName.trim()) return;
		isCreatingFolder = true;
		try {
			const res = await fetch(`${apiBase}/storage/mkdir`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					path: currentPath,
					folderName: newFolderName.trim()
				})
			});
			const data = await res.json();
			if (!res.ok || data.status === 'error') {
				throw new Error(data.message || 'Không thể tạo thư mục');
			}
			showNewFolderModal = false;
			newFolderName = '';
			await loadDirectory(currentPath);
		} catch (err: any) {
			alert(`Lỗi tạo thư mục: ${err.message}`);
		} finally {
			isCreatingFolder = false;
		}
	}

	async function confirmDelete() {
		if (!deleteTarget) return;
		isDeleting = true;
		try {
			const res = await fetch(`${apiBase}/storage/delete?path=${encodeURIComponent(deleteTarget.path)}`, {
				method: 'DELETE'
			});
			const data = await res.json();
			if (!res.ok || data.status === 'error') {
				throw new Error(data.message || 'Xóa thất bại');
			}
			deleteTarget = null;
			await loadDirectory(currentPath);
		} catch (err: any) {
			alert(`Lỗi khi xóa: ${err.message}`);
		} finally {
			isDeleting = false;
		}
	}

	function isPreviewable(ext: string): boolean {
		const lower = ext.toLowerCase();
		const textExts = ['txt', 'log', 'json', 'yml', 'yaml', 'xml', 'md', 'env', 'properties', 'js', 'ts', 'html', 'css', 'sh', 'sql', 'csv'];
		const mediaExts = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'ico', 'bmp', 'mp4', 'webm', 'ogg', 'mov', 'mkv', 'mp3', 'wav', 'pdf'];
		return textExts.includes(lower) || mediaExts.includes(lower);
	}

	onMount(() => {
		loadDirectory('');
	});
</script>
<div class="space-y-5 animate-fadeIn">
	<!-- TOP TOOLBAR & ACTION HEADER -->
	<div class="bg-zinc-900/40 backdrop-blur-md border border-zinc-800/80 rounded-2xl p-4 flex flex-col md:flex-row justify-between items-stretch md:items-center gap-4 shadow-xl">
		<!-- Breadcrumbs Navigation -->
		<div class="flex items-center gap-1.5 overflow-x-auto text-sm py-1">
			<button
				onclick={() => navigateTo('')}
				class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl hover:bg-zinc-800 text-zinc-300 hover:text-white transition-all cursor-pointer font-bold text-xs"
				title="Thư mục gốc"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-indigo-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="m2.25 12 8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
				</svg>
				Root
			</button>

			{#if currentPath}
				<span class="text-zinc-600">/</span>
				{#each breadcrumbs as crumb, i}
					<button
						onclick={() => navigateTo(crumb.path)}
						class="px-2 py-1 rounded-lg hover:bg-zinc-800 transition-all cursor-pointer text-xs {i === breadcrumbs.length - 1 ? 'text-indigo-400 font-bold bg-indigo-500/10' : 'text-zinc-400 hover:text-zinc-200'}"
					>
						{crumb.name}
					</button>
					{#if i < breadcrumbs.length - 1}
						<span class="text-zinc-600">/</span>
					{/if}
				{/each}
			{/if}
		</div>

		<!-- Action Buttons & Search -->
		<div class="flex flex-wrap items-center gap-2.5 justify-end">
			<!-- Search Bar -->
			<div class="relative w-full sm:w-56">
				<input
					type="text"
					placeholder="Tìm tệp tin..."
					bind:value={searchQuery}
					class="w-full bg-zinc-950/70 border border-zinc-800 text-zinc-200 placeholder-zinc-500 rounded-xl pl-9 pr-3 py-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
				/>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5 text-zinc-500 absolute left-3 top-2.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
				</svg>
			</div>

			<!-- Toggle View Mode -->
			<div class="flex bg-zinc-950/80 border border-zinc-800/80 rounded-xl p-0.5">
				<button
					onclick={() => (viewMode = 'table')}
					class="p-1.5 rounded-lg transition-all cursor-pointer {viewMode === 'table' ? 'bg-zinc-800 text-indigo-400 shadow' : 'text-zinc-500 hover:text-zinc-300'}"
					title="Dạng bảng"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
					</svg>
				</button>
				<button
					onclick={() => (viewMode = 'grid')}
					class="p-1.5 rounded-lg transition-all cursor-pointer {viewMode === 'grid' ? 'bg-zinc-800 text-indigo-400 shadow' : 'text-zinc-500 hover:text-zinc-300'}"
					title="Dạng lưới"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25A2.25 2.25 0 0 1 13.5 18v-2.25Z" />
					</svg>
				</button>
			</div>

			<!-- Back Button -->
			{#if currentPath}
				<button
					onclick={navigateUp}
					class="px-3 py-1.5 rounded-xl bg-zinc-800/80 hover:bg-zinc-700 text-zinc-300 text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer"
					title="Lên thư mục cha"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M9 15 3 9m0 0 6-6M3 9h12a6 6 0 0 1 0 12h-3" />
					</svg>
					Quay lại
				</button>
			{/if}

			<!-- Direct IP Fast Upload Toggle -->
			<button
				onclick={() => (directUploadMode = !directUploadMode)}
				class="px-2.5 py-1.5 rounded-xl border text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer {directUploadMode ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300 shadow-sm shadow-emerald-950' : 'bg-zinc-800/60 border-zinc-700/50 text-zinc-400 hover:text-zinc-200'}"
				title={directUploadMode ? 'Đang gửi trực tiếp đến IP 100.94.177.113:8081 (Tốc độ LAN/Tailscale tối đa)' : 'Đang gửi qua Cloudflare Tunnel'}
			>
				<span class="w-2 h-2 rounded-full {directUploadMode ? 'bg-emerald-400 animate-pulse' : 'bg-zinc-500'}"></span>
				<span>{directUploadMode ? 'Gửi qua IP trực tiếp' : 'Gửi qua Tunnel'}</span>
			</button>

			<!-- New Folder Button -->
			<button
				onclick={() => (showNewFolderModal = true)}
				class="px-3 py-1.5 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer border border-zinc-700/60 shadow"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5 text-amber-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M12 10.5v6m3-3H9m4.06-7.19-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
				</svg>
				Thư mục mới
			</button>

			<!-- Upload Button -->
			<input
				type="file"
				multiple
				bind:this={fileInputRef}
				onchange={handleUploadFiles}
				class="hidden"
			/>
			<button
				onclick={() => fileInputRef.click()}
				disabled={isUploading}
				class="px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-lg shadow-indigo-950/50 disabled:opacity-50"
			>
				{#if isUploading}
					<svg class="animate-spin -ml-0.5 mr-1 h-3.5 w-3.5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
						<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
						<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
					</svg>
					Đang tải lên...
				{:else}
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5m-13.5-9L12 3m0 0 4.5 4.5M12 3v13.5" />
					</svg>
					Tải lên
				{/if}
			</button>
		</div>
	</div>

	<!-- UPLOAD PROGRESS CARD (WITH SPEED & PERCENTAGE) -->
	{#if isUploading}
		<div class="bg-zinc-900/80 border border-indigo-500/30 rounded-2xl p-4 shadow-xl backdrop-blur-md animate-fadeIn">
			<div class="flex items-center justify-between mb-2">
				<div class="flex items-center gap-2">
					<div class="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-400">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 animate-bounce">
							<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5m-13.5-9L12 3m0 0 4.5 4.5M12 3v13.5" />
						</svg>
					</div>
					<div>
						<div class="text-xs font-bold text-zinc-200">{uploadStatusText}</div>
						<div class="text-[11px] text-zinc-400 font-mono mt-0.5">{uploadSizeText}</div>
					</div>
				</div>
				<div class="text-right">
					<div class="text-xs font-bold text-indigo-400 font-mono">{uploadProgress}%</div>
					{#if uploadSpeedText}
						<div class="text-[11px] text-emerald-400 font-mono font-medium flex items-center justify-end gap-1 mt-0.5">
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3 h-3">
								<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
							</svg>
							{uploadSpeedText}
						</div>
					{/if}
				</div>
			</div>

			<!-- Progress bar track -->
			<div class="w-full bg-zinc-950 rounded-full h-2 overflow-hidden border border-zinc-800">
				<div
					class="bg-gradient-to-r from-indigo-500 to-indigo-400 h-2 rounded-full transition-all duration-150 ease-out shadow-sm shadow-indigo-500/50"
					style="width: {uploadProgress}%"
				></div>
			</div>
		</div>
	{/if}

	<!-- ERROR BANNER -->
	{#if errorMessage}
		<div class="bg-rose-950/40 border border-rose-800/80 text-rose-300 p-4 rounded-xl flex items-center gap-3 text-xs">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 shrink-0 text-rose-400">
				<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
			</svg>
			<span>{errorMessage}</span>
		</div>
	{/if}

	<!-- CONTENT VIEW -->
	{#if isLoading}
		<div class="p-16 flex flex-col items-center justify-center space-y-3">
			<svg class="animate-spin h-7 w-7 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
				<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
				<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
			</svg>
			<span class="text-zinc-500 text-xs font-semibold">Đang nạp tệp tin từ máy chủ...</span>
		</div>
	{:else if filteredItems.length === 0}
		<div class="text-center py-20 bg-zinc-900/10 border border-dashed border-zinc-800/80 rounded-2xl space-y-3">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-12 h-12 text-zinc-600 mx-auto">
				<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
			</svg>
			<h4 class="text-zinc-400 font-bold text-sm">Thư mục hiện tại đang trống</h4>
			<p class="text-xs text-zinc-500 max-w-sm mx-auto">Chưa có tệp tin hoặc thư mục nào tại đường dẫn này. Bấm nút <b>Tải lên</b> hoặc <b>Thư mục mới</b> để bắt đầu.</p>
		</div>
	{:else if viewMode === 'table'}
		<!-- TABLE VIEW -->
		<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl overflow-hidden shadow-xl">
			<div class="overflow-x-auto">
				<table class="w-full text-left text-xs text-zinc-300">
					<thead class="bg-zinc-950/70 text-zinc-500 font-semibold uppercase tracking-wider border-b border-zinc-800/60">
						<tr>
							<th class="py-3 px-4">Tên tệp</th>
							<th class="py-3 px-4 w-32">Kích thước</th>
							<th class="py-3 px-4 w-44">Thời gian sửa đổi</th>
							<th class="py-3 px-4 w-36 text-right">Thao tác</th>
						</tr>
					</thead>
					<tbody class="divide-y divide-zinc-800/40">
						{#each filteredItems as item}
							<tr class="hover:bg-zinc-800/30 transition-colors group">
								<td class="py-3 px-4 font-medium flex items-center gap-3">
									{#if item.directory}
										<div class="p-2 rounded-xl bg-amber-500/10 text-amber-400 shrink-0">
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
												<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
											</svg>
										</div>
										<button
											onclick={() => navigateTo(item.path)}
											class="text-zinc-200 hover:text-amber-300 font-semibold text-left truncate cursor-pointer transition-colors max-w-md"
										>
											{item.name}
										</button>
									{:else}
										<div class="p-2 rounded-xl bg-indigo-500/10 text-indigo-400 shrink-0">
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
												<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
											</svg>
										</div>
										<span class="text-zinc-300 font-normal truncate max-w-md">{item.name}</span>
									{/if}
								</td>
								<td class="py-3 px-4 font-mono text-zinc-400">
									{item.directory ? '-' : formatBytes(item.size)}
								</td>
								<td class="py-3 px-4 text-zinc-400 font-mono text-[11px]">
									{formatDate(item.lastModified)}
								</td>
								<td class="py-3 px-4 text-right">
									<div class="flex items-center justify-end gap-1.5 opacity-80 group-hover:opacity-100 transition-opacity">
										{#if !item.directory}
											{#if isPreviewable(item.extension)}
												<button
													onclick={() => onpreview(item)}
													class="p-1.5 text-zinc-400 hover:text-indigo-300 hover:bg-indigo-600/10 rounded-lg transition-colors cursor-pointer"
													title="Xem trước logs/text"
												>
													<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
														<path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 0 1 0-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.964-7.178Z" />
														<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
													</svg>
												</button>
											{/if}
											<button
												onclick={() => ondownload(item)}
												class="p-1.5 text-zinc-400 hover:text-cyan-300 hover:bg-cyan-600/10 rounded-lg transition-colors cursor-pointer"
												title="Tải xuống tệp tin"
											>
												<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
													<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
												</svg>
											</button>
										{/if}

										<button
											onclick={() => (deleteTarget = item)}
											class="p-1.5 text-zinc-500 hover:text-rose-400 hover:bg-rose-600/10 rounded-lg transition-colors cursor-pointer"
											title="Xóa"
										>
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
												<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
											</svg>
										</button>
									</div>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</div>
	{:else}
		<!-- GRID VIEW -->
		<div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
			{#each filteredItems as item}
				<div class="bg-zinc-900/40 border border-zinc-800/80 rounded-2xl p-4 flex flex-col justify-between hover:border-zinc-700 transition-all group relative">
					{#if item.directory}
						<button
							onclick={() => navigateTo(item.path)}
							class="flex flex-col items-center text-center space-y-2 cursor-pointer w-full"
						>
							<div class="p-3 rounded-2xl bg-amber-500/10 text-amber-400 group-hover:scale-110 transition-transform">
								<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-8 h-8">
									<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
								</svg>
							</div>
							<span class="text-xs font-semibold text-zinc-200 truncate w-full group-hover:text-amber-300 transition-colors">
								{item.name}
							</span>
							<span class="text-[10px] text-zinc-500">Thư mục</span>
						</button>
					{:else}
						<div class="flex flex-col items-center text-center space-y-2 w-full">
							<div class="p-3 rounded-2xl bg-indigo-500/10 text-indigo-400 group-hover:scale-110 transition-transform">
								<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-8 h-8">
									<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
								</svg>
							</div>
							<span class="text-xs font-medium text-zinc-200 truncate w-full" title={item.name}>
								{item.name}
							</span>
							<span class="text-[10px] text-zinc-500 font-mono">{formatBytes(item.size)}</span>
						</div>
					{/if}

					<!-- Quick Action Hover Menu -->
					<div class="mt-3 pt-2 border-t border-zinc-800/60 flex items-center justify-center gap-2">
						{#if !item.directory}
							{#if isPreviewable(item.extension)}
								<button
									onclick={() => onpreview(item)}
									class="p-1 text-zinc-400 hover:text-indigo-300 rounded cursor-pointer"
									title="Xem trước"
								>
									<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
										<path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 0 1 0-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.964-7.178Z" />
										<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
									</svg>
								</button>
							{/if}
							<button
								onclick={() => ondownload(item)}
								class="p-1 text-zinc-400 hover:text-cyan-300 rounded cursor-pointer"
								title="Tải xuống"
							>
								<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
									<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
								</svg>
							</button>
						{/if}
						<button
							onclick={() => (deleteTarget = item)}
							class="p-1 text-zinc-500 hover:text-rose-400 rounded cursor-pointer"
							title="Xóa"
						>
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
								<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
							</svg>
						</button>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>

<!-- MODAL: TẠO THƯ MỤC MỚI -->
{#if showNewFolderModal}
	<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
		<div class="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 max-w-sm w-full space-y-4 shadow-2xl">
			<h3 class="text-base font-bold text-white flex items-center gap-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 text-amber-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M12 10.5v6m3-3H9m4.06-7.19-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
				</svg>
				Tạo thư mục mới
			</h3>
			<p class="text-xs text-zinc-400">Nhập tên thư mục muốn tạo bên trong <code class="bg-zinc-950 px-1 py-0.5 rounded text-indigo-400">/{currentPath}</code></p>
			<input
				type="text"
				placeholder="Ví dụ: backups, logs, temp..."
				bind:value={newFolderName}
				class="w-full bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
			/>
			<div class="flex items-center justify-end gap-2 pt-2">
				<button
					onclick={() => (showNewFolderModal = false)}
					class="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white hover:bg-zinc-800 transition-all cursor-pointer"
				>
					Hủy
				</button>
				<button
					onclick={handleCreateFolder}
					disabled={isCreatingFolder || !newFolderName.trim()}
					class="px-4 py-2 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white transition-all cursor-pointer disabled:opacity-50"
				>
					{isCreatingFolder ? 'Đang tạo...' : 'Tạo mới'}
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- MODAL: XÁC NHẬN XÓA -->
{#if deleteTarget}
	<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
		<div class="bg-zinc-900 border border-rose-900/60 rounded-2xl p-6 max-w-sm w-full space-y-4 shadow-2xl">
			<div class="flex items-center gap-3">
				<div class="p-2.5 rounded-xl bg-rose-500/10 text-rose-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
						<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
					</svg>
				</div>
				<div>
					<h3 class="text-sm font-bold text-white">Xác nhận xóa</h3>
					<p class="text-xs text-zinc-400 mt-0.5">Hành động này không thể hoàn tác.</p>
				</div>
			</div>
			<p class="text-xs text-zinc-300 bg-zinc-950 p-3 rounded-xl border border-zinc-800 break-all">
				Bạn có chắc chắn muốn xóa {deleteTarget.directory ? 'thư mục' : 'tệp tin'}: <b class="text-rose-400">{deleteTarget.name}</b>?
			</p>
			<div class="flex items-center justify-end gap-2 pt-2">
				<button
					onclick={() => (deleteTarget = null)}
					class="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white hover:bg-zinc-800 transition-all cursor-pointer"
				>
					Hủy bỏ
				</button>
				<button
					onclick={confirmDelete}
					disabled={isDeleting}
					class="px-4 py-2 rounded-xl text-xs font-bold bg-rose-600 hover:bg-rose-500 text-white transition-all cursor-pointer disabled:opacity-50"
				>
					{isDeleting ? 'Đang xóa...' : 'Xóa vĩnh viễn'}
				</button>
			</div>
		</div>
	</div>
{/if}