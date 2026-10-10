<script lang="ts">
	import { transferQueue, formatBytes } from '$lib/stores/transferQueue.svelte';

	interface Props {
		apiBase: string;
	}

	let { apiBase }: Props = $props();

	// SVG circle geometry
	const radius = 22;
	const circumference = 2 * Math.PI * radius;
	let strokeDashoffset = $derived(
		circumference - (transferQueue.overallProgress / 100) * circumference
	);
</script>

<!-- FLOATING ACTION BUTTON (Ẩn khi không có tác vụ nào đang tải lên hoặc tải xuống) -->
{#if transferQueue.activeCount > 0 || transferQueue.isOpen}
<div class="fixed bottom-20 md:bottom-6 right-4 md:right-6 z-50 flex flex-col items-end select-none">
	
	<!-- POPOVER QUEUE LIST -->
	{#if transferQueue.isOpen}
		<div class="mb-3 w-80 sm:w-96 max-h-[70vh] bg-zinc-950/95 backdrop-blur-2xl border border-zinc-800 rounded-3xl shadow-2xl overflow-hidden flex flex-col animate-slideUp">
			<!-- Header -->
			<div class="p-4 border-b border-zinc-800/80 flex items-center justify-between bg-zinc-900/60">
				<div class="flex items-center gap-2.5">
					<div class="w-8 h-8 rounded-xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
						</svg>
					</div>
					<div>
						<h4 class="text-xs font-bold text-white flex items-center gap-2">
							Hàng đợi truyền tải
							{#if transferQueue.activeCount > 0}
								<span class="px-1.5 py-0.2 rounded-full bg-indigo-500/20 text-indigo-300 text-[10px] font-mono">
									{transferQueue.activeCount} đang chạy
								</span>
							{/if}
						</h4>
						<p class="text-[10px] text-zinc-400">
							{transferQueue.tasks.length} tệp trong danh sách
						</p>
					</div>
				</div>

				<div class="flex items-center gap-1">
					{#if transferQueue.activeCount > 0}
						<button
							onclick={() => transferQueue.cancelAll(apiBase)}
							class="text-[11px] font-semibold text-rose-400 hover:text-rose-300 px-2 py-1 rounded-lg hover:bg-rose-950/40 cursor-pointer transition-colors"
							title="Hủy toàn bộ tệp đang truyền"
						>
							Hủy tất cả
						</button>
					{/if}

					<button
						onclick={() => transferQueue.clearFinished()}
						class="text-[11px] font-semibold text-zinc-400 hover:text-zinc-200 px-2 py-1 rounded-lg hover:bg-zinc-800 cursor-pointer transition-colors"
						title="Dọn dẹp các tệp đã hoàn tất"
					>
						Dọn dẹp
					</button>

					<button
						onclick={() => transferQueue.setOpen(false)}
						class="p-1.5 text-zinc-400 hover:text-white rounded-lg hover:bg-zinc-800 cursor-pointer"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
						</svg>
					</button>
				</div>
			</div>

			<!-- Task List -->
			<div class="p-3 overflow-y-auto space-y-2.5 flex-1 divide-y divide-zinc-900">
				{#if transferQueue.tasks.length === 0}
					<div class="py-10 text-center text-zinc-500 text-xs flex flex-col items-center gap-2">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-8 h-8 opacity-40">
							<path stroke-linecap="round" stroke-linejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
						</svg>
						<span>Chưa có tệp nào đang tải lên hoặc tải xuống</span>
					</div>
				{:else}
					{#each transferQueue.tasks as task (task.id)}
						<div class="pt-2 first:pt-0 flex flex-col gap-1.5">
							<div class="flex items-center justify-between gap-2">
								<!-- Icon & File Name -->
								<div class="flex items-center gap-2 min-w-0 flex-1">
									{#if task.type === 'upload'}
										<span class="p-1 rounded-md bg-indigo-500/20 text-indigo-400 shrink-0">
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
												<path stroke-linecap="round" stroke-linejoin="round" d="M4.5 10.5 12 3m0 0 7.5 7.5M12 3v18" />
											</svg>
										</span>
									{:else}
										<span class="p-1 rounded-md bg-cyan-500/20 text-cyan-400 shrink-0">
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
												<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" />
											</svg>
										</span>
									{/if}

									<div class="min-w-0 flex-1">
										<p class="text-xs font-semibold text-zinc-200 truncate" title={task.name}>
											{task.name}
										</p>
										<div class="flex items-center gap-2 text-[10px] text-zinc-400 font-mono">
											<span>{formatBytes(task.loaded)} / {formatBytes(task.size)}</span>
											<span class="text-indigo-400 font-semibold font-mono">({task.progress}%)</span>
											{#if task.totalChunks && task.totalChunks > 1}
												<span>• Chunk { (task.currentChunk || 0) + 1 }/{ task.totalChunks }</span>
											{/if}
										</div>
									</div>
								</div>

								<!-- Status / Cancel Button -->
								<div class="flex items-center gap-2 shrink-0">
									{#if task.status === 'transferring'}
										<div class="flex flex-col items-end">
											<span class="text-[11px] font-mono font-bold text-indigo-400">
												{task.speedText}
											</span>
											<span class="text-[10px] font-mono font-semibold text-cyan-400">
												{task.progress}%
											</span>
										</div>
										<button
											onclick={() => transferQueue.cancelTask(task.id, apiBase)}
											class="p-1 text-zinc-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg cursor-pointer transition-colors"
											title="Hủy tệp này"
										>
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
												<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
											</svg>
										</button>
									{:else if task.status === 'queued'}
										<span class="text-[10px] text-amber-400 bg-amber-950/40 px-1.5 py-0.5 rounded-md font-semibold">
											Đang chờ
										</span>
										<button
											onclick={() => transferQueue.cancelTask(task.id, apiBase)}
											class="p-1 text-zinc-400 hover:text-rose-400 rounded-lg cursor-pointer"
											title="Hủy khỏi hàng đợi"
										>
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
												<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
											</svg>
										</button>
									{:else if task.status === 'completed'}
										<span class="text-[11px] font-bold text-emerald-400 flex items-center gap-1">
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
												<path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
											</svg>
											100% Xong
										</span>
									{:else if task.status === 'cancelled'}
										<span class="text-[10px] text-zinc-500 italic">Đã hủy</span>
									{:else if task.status === 'error'}
										<span class="text-[10px] font-bold text-rose-400 truncate max-w-[100px]" title={task.errorMessage}>
											{task.errorMessage || 'Lỗi'}
										</span>
									{/if}
								</div>
							</div>

							<!-- Mini Progress Bar -->
							{#if task.status === 'transferring' || task.status === 'queued'}
								<div class="w-full bg-zinc-800 rounded-full h-1.5 overflow-hidden">
									<div
										class="h-full bg-gradient-to-r from-indigo-500 via-cyan-400 to-indigo-400 transition-all duration-150"
										style="width: {task.progress}%"
									></div>
								</div>
							{/if}
						</div>
					{/each}
				{/if}
			</div>
		</div>
	{/if}

	<!-- CIRCULAR FLOATING BUTTON -->
	<button
		onclick={() => transferQueue.toggleOpen()}
		class="relative w-14 h-14 rounded-full bg-zinc-900 border-2 border-zinc-700/80 hover:border-indigo-500 text-white shadow-2xl flex items-center justify-center cursor-pointer transition-all hover:scale-105 active:scale-95 group backdrop-blur-xl"
		title="Hàng đợi tải file"
	>
		<!-- SVG Circular Progress Ring -->
		<svg class="absolute inset-0 w-full h-full -rotate-90 pointer-events-none" viewBox="0 0 52 52">
			<!-- Background circle -->
			<circle
				cx="26"
				cy="26"
				r={radius}
				fill="transparent"
				stroke="rgba(255, 255, 255, 0.08)"
				stroke-width="3"
			/>
			<!-- Active Progress arc -->
			{#if transferQueue.activeCount > 0}
				<circle
					cx="26"
					cy="26"
					r={radius}
					fill="transparent"
					stroke="url(#gradient-fab)"
					stroke-width="3.5"
					stroke-linecap="round"
					stroke-dasharray={circumference}
					stroke-dashoffset={strokeDashoffset}
					class="transition-all duration-300"
				/>
				<defs>
					<linearGradient id="gradient-fab" x1="0%" y1="0%" x2="100%" y2="100%">
						<stop offset="0%" stop-color="#818cf8" />
						<stop offset="100%" stop-color="#22d3ee" />
					</linearGradient>
				</defs>
			{/if}
		</svg>

		<!-- Center Icon -->
		<div class="relative z-10 text-zinc-300 group-hover:text-white transition-colors">
			{#if transferQueue.activeCount > 0}
				<svg
					xmlns="http://www.w3.org/2000/svg"
					fill="none"
					viewBox="0 0 24 24"
					stroke-width="2"
					stroke="currentColor"
					class="w-6 h-6 animate-pulse text-indigo-400"
				>
					<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5m-13.5-9L12 3m0 0 4.5 4.5M12 3v13.5" />
				</svg>
			{:else}
				<svg
					xmlns="http://www.w3.org/2000/svg"
					fill="none"
					viewBox="0 0 24 24"
					stroke-width="2"
					stroke="currentColor"
					class="w-6 h-6"
				>
					<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21.75 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
				</svg>
			{/if}
		</div>

		<!-- Badge Count -->
		{#if transferQueue.activeCount > 0}
			<span class="absolute -top-1 -right-1 z-20 bg-indigo-600 text-white font-bold text-[10px] w-5 h-5 rounded-full flex items-center justify-center border-2 border-zinc-950 shadow">
				{transferQueue.activeCount}
			</span>
		{/if}
	</button>
</div>
{/if}

<style>
	@keyframes slideUp {
		from { opacity: 0; transform: translateY(12px) scale(0.95); }
		to { opacity: 1; transform: translateY(0) scale(1); }
	}
	.animate-slideUp {
		animation: slideUp 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
	}
</style>
