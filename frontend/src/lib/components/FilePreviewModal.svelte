<script lang="ts">
	interface Props {
		previewItem: { name: string; path: string; size: number } | null;
		previewContent: string;
		isPreviewLoading: boolean;
		onclose: () => void;
		ondownload: () => void;
	}

	let { previewItem, previewContent, isPreviewLoading, onclose, ondownload }: Props = $props();

	function formatBytes(bytes: number, decimals = 1): string {
		if (bytes === 0) return '0 B';
		const k = 1024;
		const dm = decimals < 0 ? 0 : decimals;
		const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
		const i = Math.floor(Math.log(bytes) / Math.log(k));
		return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
	}
</script>

{#if previewItem}
	<div 
		class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-black/80 backdrop-blur-md animate-fadeIn"
		role="dialog"
		aria-modal="true"
	>
		<div class="bg-zinc-900 border border-zinc-700/80 rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-2xl overflow-hidden relative">
			<!-- Header -->
			<div class="p-4 border-b border-zinc-800 flex items-center justify-between bg-zinc-950/80 shrink-0">
				<div class="flex items-center gap-3 truncate mr-4">
					<div class="p-2 rounded-xl bg-indigo-500/10 text-indigo-400 shrink-0">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
						</svg>
					</div>
					<div class="truncate">
						<h3 class="text-sm md:text-base font-bold text-white truncate">{previewItem.name}</h3>
						<span class="text-[11px] text-zinc-400 font-mono">{formatBytes(previewItem.size)} • {previewItem.path}</span>
					</div>
				</div>

				<div class="flex items-center gap-2 shrink-0">
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
			<div class="flex-1 bg-zinc-950 p-4 md:p-6 overflow-auto font-mono text-xs text-zinc-200 whitespace-pre leading-relaxed select-text">
				{#if isPreviewLoading}
					<div class="h-full flex flex-col items-center justify-center gap-3 text-zinc-500">
						<svg class="animate-spin h-7 w-7 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
							<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
							<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
						</svg>
						<span>Đang tải nội dung tệp...</span>
					</div>
				{:else}
					{previewContent}
				{/if}
			</div>
		</div>
	</div>
{/if}