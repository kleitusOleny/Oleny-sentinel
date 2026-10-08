<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import Hls from 'hls.js';

	interface Props {
		previewItem: { name: string; path: string; size: number } | null;
		previewContent: string;
		previewUrl: string;
		isPreviewLoading: boolean;
		onclose: () => void;
		ondownload: () => void;
	}

	let { previewItem, previewContent, previewUrl, isPreviewLoading, onclose, ondownload }: Props = $props();

	let videoRef: HTMLVideoElement | null = $state(null);
	let hlsInstance: Hls | null = null;
	let useHls = $state(true);

	function formatBytes(bytes: number, decimals = 1): string {
		if (bytes === 0) return '0 B';
		const k = 1024;
		const dm = decimals < 0 ? 0 : decimals;
		const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
		const i = Math.floor(Math.log(bytes) / Math.log(k));
		return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
	}

	function getExtension(name: string): string {
		const idx = name.lastIndexOf('.');
		return idx >= 0 ? name.substring(idx + 1).toLowerCase() : '';
	}

	let fileExt = $derived(previewItem ? getExtension(previewItem.name) : '');
	let isImage = $derived(['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'ico', 'bmp'].includes(fileExt));
	let isVideo = $derived(['mp4', 'webm', 'ogg', 'mov', 'mkv'].includes(fileExt));
	let isAudio = $derived(['mp3', 'wav', 'ogg', 'm4a', 'flac'].includes(fileExt));
	let isPdf = $derived(fileExt === 'pdf');

	$effect(() => {
		if (isVideo && videoRef && previewItem) {
			const hlsPlaylistUrl = `/api/storage/hls/playlist?path=${encodeURIComponent(previewItem.path)}`;

			if (hlsInstance) {
				hlsInstance.destroy();
				hlsInstance = null;
			}

			if (useHls && Hls.isSupported()) {
				hlsInstance = new Hls({
					enableWorker: true,
					lowLatencyMode: true
				});
				hlsInstance.loadSource(hlsPlaylistUrl);
				hlsInstance.attachMedia(videoRef);
				hlsInstance.on(Hls.Events.ERROR, (_, data) => {
					if (data.fatal) {
						console.warn('HLS stream không phản hồi, tự động chuyển về MP4 trực tiếp:', data);
						useHls = false;
					}
				});
			} else if (useHls && videoRef.canPlayType('application/vnd.apple.mpegurl')) {
				videoRef.src = hlsPlaylistUrl;
			} else {
				videoRef.src = previewUrl;
			}
		}

		return () => {
			if (hlsInstance) {
				hlsInstance.destroy();
				hlsInstance = null;
			}
		};
	});
</script>

{#if previewItem}
	<div 
		class="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-4 md:p-8 bg-black/85 backdrop-blur-md animate-fadeIn"
		role="dialog"
		aria-modal="true"
	>
		<div class="bg-zinc-900 border border-zinc-700/80 rounded-2xl w-full max-w-5xl h-[88vh] flex flex-col shadow-2xl overflow-hidden relative">
			<!-- Header -->
			<div class="p-4 border-b border-zinc-800 flex items-center justify-between bg-zinc-950/80 shrink-0">
				<div class="flex items-center gap-3 truncate mr-4">
					<div class="p-2 rounded-xl {isImage ? 'bg-emerald-500/10 text-emerald-400' : isVideo ? 'bg-rose-500/10 text-rose-400' : isAudio ? 'bg-amber-500/10 text-amber-400' : 'bg-indigo-500/10 text-indigo-400'} shrink-0">
						{#if isImage}
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
								<path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
							</svg>
						{:else if isVideo}
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
								<path stroke-linecap="round" stroke-linejoin="round" d="m15.75 10.5 4.72-4.72a.75.75 0 0 1 1.28.53v11.38a.75.75 0 0 1-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25h-9A2.25 2.25 0 0 0 2.25 7.5v9a2.25 2.25 0 0 0 2.25 2.25Z" />
							</svg>
						{:else}
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
								<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
							</svg>
						{/if}
					</div>
					<div class="truncate">
						<h3 class="text-sm md:text-base font-bold text-white truncate">{previewItem.name}</h3>
						<span class="text-[11px] text-zinc-400 font-mono">{formatBytes(previewItem.size)} • {previewItem.path}</span>
					</div>
				</div>

				<div class="flex items-center gap-2 shrink-0">
					{#if isVideo}
						<button
							onclick={() => (useHls = !useHls)}
							class="px-2.5 py-1.5 rounded-xl text-xs font-semibold border transition-all cursor-pointer flex items-center gap-1.5 {useHls ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/40' : 'bg-zinc-800 text-zinc-400 border-zinc-700'}"
							title={useHls ? 'Đang dùng luồng HLS phân đoạn siêu nhẹ (kiểu YouTube). Bấm để đổi sang MP4 trực tiếp' : 'Đang dùng phát MP4 trực tiếp. Bấm để bật HLS'}
						>
							<span class="w-2 h-2 rounded-full {useHls ? 'bg-indigo-400 animate-pulse' : 'bg-zinc-500'}"></span>
							<span>{useHls ? 'HLS Stream' : 'MP4 Gốc'}</span>
						</button>
					{/if}

					<button
						onclick={ondownload}
						class="px-3 py-1.5 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer border border-zinc-700/60"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
						</svg>
						Tải về
					</button>
					<button
						onclick={onclose}
						class="p-2 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-xl transition-all cursor-pointer"
						aria-label="Đóng cửa sổ xem trước"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
						</svg>
					</button>
				</div>
			</div>

			<!-- Body Content -->
			<div class="flex-1 bg-zinc-950/90 flex items-center justify-center overflow-auto p-4 md:p-6 relative select-text">
				{#if isPreviewLoading}
					<div class="flex flex-col items-center justify-center gap-3 text-zinc-500">
						<svg class="animate-spin h-7 w-7 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
							<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
							<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
						</svg>
						<span class="text-xs">Đang nạp tệp đa phương tiện...</span>
					</div>
				{:else if isImage}
					<!-- Image Viewer -->
					<div class="w-full h-full flex items-center justify-center p-2">
						<img 
							src={previewUrl} 
							alt={previewItem.name} 
							class="max-w-full max-h-[75vh] object-contain rounded-xl shadow-2xl border border-zinc-800 bg-zinc-900/50" 
						/>
					</div>
				{:else if isVideo}
					<!-- Video Player (HLS Streaming YouTube-Style) -->
					<div class="w-full h-full flex items-center justify-center p-2">
						<!-- svelte-ignore a11y_media_has_caption -->
						<video 
							bind:this={videoRef}
							controls 
							autoplay 
							playsinline
							class="max-w-full max-h-[75vh] rounded-2xl shadow-2xl border border-zinc-800 bg-black outline-none"
						></video>
					</div>
				{:else if isAudio}
					<!-- Audio Player -->
					<div class="w-full max-w-md p-6 bg-zinc-900 border border-zinc-800 rounded-2xl text-center space-y-4 shadow-xl">
						<div class="w-16 h-16 mx-auto rounded-full bg-amber-500/10 flex items-center justify-center text-amber-400">
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-8 h-8">
								<path stroke-linecap="round" stroke-linejoin="round" d="M9 9l10.5-3m0 6.553v3.75a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 1 1-.99-3.467l2.31-.66v-3.8m0-4.913a.75.75 0 0 0-.53-.72L8.97 3.32a.75.75 0 0 0-.97.72v9.513a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 1 1-.99-3.467l2.31-.66V3.75" />
							</svg>
						</div>
						<div class="text-sm font-semibold text-zinc-200 truncate">{previewItem.name}</div>
						<!-- svelte-ignore a11y_media_has_caption -->
						<audio src={previewUrl} controls class="w-full outline-none"></audio>
					</div>
				{:else if isPdf}
					<!-- PDF Viewer -->
					<iframe 
						src={previewUrl} 
						title={previewItem.name} 
						class="w-full h-full rounded-xl border border-zinc-800 bg-zinc-900"
					></iframe>
				{:else}
					<!-- Text / Code Viewer -->
					<div class="w-full h-full overflow-auto font-mono text-xs text-zinc-200 whitespace-pre leading-relaxed select-text p-2">
						{previewContent}
					</div>
				{/if}
			</div>
		</div>
	</div>
{/if}