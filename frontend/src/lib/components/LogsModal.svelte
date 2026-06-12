<script lang="ts">
	interface Props {
		activeLogContainerId: string;
		activeLogContainerName: string;
		containerLogsText: string;
		isLogsLoading: boolean;
		logLinesCount: number;
		onrefresh: () => void;
		onclose: () => void;
		updateLines: (val: number) => void;
	}

	let { 
		activeLogContainerId, 
		activeLogContainerName, 
		containerLogsText, 
		isLogsLoading, 
		logLinesCount, 
		onrefresh, 
		onclose,
		updateLines
	}: Props = $props();
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm select-none">
	<div class="bg-zinc-900 border border-zinc-800 rounded-2xl w-full max-w-4xl shadow-2xl overflow-hidden flex flex-col h-[80vh]">
		<!-- Modal Header -->
		<div class="p-4 border-b border-zinc-800 flex justify-between items-center bg-zinc-900">
			<div>
				<h3 class="font-extrabold text-zinc-200 text-lg flex items-center gap-2">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 text-emerald-400">
						<path stroke-linecap="round" stroke-linejoin="round" d="m6.75 7.5 3 2.25-3 2.25m4.5 0h3m-9 8.25h13.5A2.25 2.25 0 0 0 21 18V6a2.25 2.25 0 0 0-2.25-2.25H5.25A2.25 2.25 0 0 0 3 6v12a2.25 2.25 0 0 0 2.25 2.25Z" />
					</svg>
					Logs: {activeLogContainerName}
				</h3>
				<p class="text-zinc-500 text-xs truncate mt-0.5 font-mono">ID: {activeLogContainerId}</p>
			</div>
			<div class="flex items-center gap-3">
				<!-- Select Lines count -->
				<select 
					value={logLinesCount} 
					onchange={(e) => updateLines(Number(e.currentTarget.value))} 
					class="bg-zinc-950 border border-zinc-800 text-zinc-300 rounded-lg px-2.5 py-1 text-xs focus:ring-1 focus:ring-indigo-500 focus:outline-none"
				>
					<option value={50}>50 dòng</option>
					<option value={100}>100 dòng</option>
					<option value={200}>200 dòng</option>
					<option value={500}>500 dòng</option>
				</select>

				<!-- Refresh Button -->
				<button 
					onclick={onrefresh}
					class="p-1.5 rounded-lg bg-zinc-800 border border-zinc-700 text-zinc-300 hover:bg-zinc-700 transition-colors cursor-pointer"
					title="Tải lại logs"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
					</svg>
				</button>

				<!-- Close Button -->
				<button 
					onclick={onclose}
					class="p-1.5 rounded-lg bg-zinc-800 border border-zinc-700 text-zinc-400 hover:text-white hover:bg-rose-600 hover:border-rose-500 transition-colors cursor-pointer"
					aria-label="Đóng cửa sổ logs"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4.5 h-4.5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
					</svg>
				</button>
			</div>
		</div>
		
		<!-- Logs Body -->
		<div class="flex-1 overflow-auto bg-zinc-950 p-4 font-mono text-xs text-emerald-400/90 leading-relaxed selection:bg-emerald-800 selection:text-white">
			{#if isLogsLoading}
				<div class="flex flex-col items-center justify-center h-full gap-2 text-zinc-500">
					<svg class="animate-spin h-6 w-6 text-zinc-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
						<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
						<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
					</svg>
					<span>Đang tải nhật ký...</span>
				</div>
			{:else}
				<pre class="whitespace-pre-wrap">{containerLogsText || 'Không có logs hoặc container không xuất ra console.'}</pre>
			{/if}
		</div>
	</div>
</div>
