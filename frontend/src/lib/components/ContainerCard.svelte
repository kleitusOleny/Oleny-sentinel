<script lang="ts">
	interface Props {
		container: any;
		whitelist: string[];
		actionLoading: Record<string, boolean>;
		copySuccess: Record<string, boolean>;
		onaction: (id: string, action: 'start' | 'stop' | 'restart') => void;
		ontoggleAutoHeal: (name: string) => void;
		onopenLogs: (id: string, name: string) => void;
		oncopy: (text: string, id: string) => void;
	}

	let { 
		container, 
		whitelist, 
		actionLoading, 
		copySuccess, 
		onaction, 
		ontoggleAutoHeal, 
		onopenLogs, 
		oncopy 
	}: Props = $props();

	function getContainerName(c: any): string {
		const names = c.Names || c.names;
		if (names && names.length > 0) {
			return names[0].replace(/^\//, '');
		}
		return 'unknown';
	}

	let cId = $derived(container.Id || container.id || '');
	let cName = $derived(getContainerName(container));
	let cState = $derived(container.State || container.state || '');
	let cStatus = $derived(container.Status || container.status || '');
	let cImage = $derived(container.Image || container.image || '');
	
	let isRunning = $derived(cState && cState.toLowerCase() === 'running');
	let isExited = $derived(cState && cState.toLowerCase() === 'exited');
	let inWhitelist = $derived(whitelist.includes(cName));
</script>

<div class="bg-zinc-900/40 backdrop-blur-md border border-zinc-800/80 rounded-2xl p-5 hover:border-zinc-700/80 transition-all shadow-xl flex flex-col justify-between group select-none">
	<div class="space-y-3">
		<!-- Container Name -->
		<div class="flex justify-between items-start gap-2">
			<h3 class="font-extrabold text-zinc-200 truncate group-hover:text-white transition-colors text-base" title={cName}>
				{cName}
			</h3>

			<!-- Status Badge -->
			<span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold select-none
				{isRunning ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : ''}
				{isExited ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20' : ''}
				{!isRunning && !isExited ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' : ''}
			">
				<span class="w-1.5 h-1.5 rounded-full
					{isRunning ? 'bg-emerald-400 animate-pulse' : ''}
					{isExited ? 'bg-rose-400' : ''}
					{!isRunning && !isExited ? 'bg-amber-400' : ''}
				"></span>
				{cState || 'unknown'}
			</span>
		</div>

		<!-- Image & Status -->
		<div class="text-xs text-zinc-500 space-y-1">
			<p class="truncate"><span class="text-zinc-600 font-medium">Image:</span> {cImage}</p>
			<p class="truncate"><span class="text-zinc-600 font-medium">Status:</span> {cStatus}</p>
		</div>

		<!-- ID Copyable -->
		<div class="flex items-center justify-between bg-zinc-950/60 rounded-lg px-2.5 py-1 text-xs border border-zinc-800/50">
			<code class="font-mono text-zinc-500 truncate mr-2">{cId.substring(0, 12)}...</code>
			<div class="flex items-center gap-1">
				<!-- LOG BUTTON -->
				<button 
					onclick={() => onopenLogs(cId, cName)}
					class="text-zinc-500 hover:text-zinc-300 transition-colors p-1 cursor-pointer"
					title="Xem nhật ký logs"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
						<path stroke-linecap="round" stroke-linejoin="round" d="m6.75 7.5 3 2.25-3 2.25m4.5 0h3m-9 8.25h13.5A2.25 2.25 0 0 0 21 18V6a2.25 2.25 0 0 0-2.25-2.25H5.25A2.25 2.25 0 0 0 3 6v12a2.25 2.25 0 0 0 2.25 2.25Z" />
					</svg>
				</button>
				<!-- COPY BUTTON -->
				<button 
					onclick={() => oncopy(cId, cId)}
					class="text-zinc-500 hover:text-zinc-300 transition-colors p-1 cursor-pointer"
					title="Sao chép ID"
				>
					{#if copySuccess[cId]}
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5 text-emerald-400">
							<path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
						</svg>
					{:else}
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 0 1-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H5.25m11.9-3.664A2.251 2.251 0 0 0 15 2.25h-1.5a2.25 2.25 0 0 0-2.25 2.25v1.5a2.25 2.25 0 0 0 2.25 2.25H15a2.25 2.25 0 0 0 2.25-2.25v-1.5a2.25 2.25 0 0 0-.35-1.164Z" />
						</svg>
					{/if}
				</button>
			</div>
		</div>

		<!-- Auto Heal Toggle Slider -->
		<div class="flex items-center justify-between border-t border-zinc-800/60 pt-3.5 select-none">
			<div class="flex items-center gap-1.5">
				<span class="text-xs font-semibold text-zinc-400">Tự phục hồi (Auto-heal)</span>
				<div class="group/info relative inline-block">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5 text-zinc-500 hover:text-zinc-400 cursor-help">
						<path stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 1 1 1.063 1.063L12 13.504L10.875 12.11a.75.75 0 0 1 1.062-1.062M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75s9.75-4.365 9.75-9.75S17.385 2.25 12 2.25Z" />
					</svg>
					<div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-48 hidden group-hover/info:block bg-zinc-950 border border-zinc-800 text-[10px] text-zinc-400 p-2.5 rounded-lg shadow-xl z-20 whitespace-normal">
						Khi bật, nếu container này bị crash (Exited), Server Sentinel sẽ tự khởi động lại nó.
					</div>
				</div>
			</div>

			<button
				onclick={() => ontoggleAutoHeal(cName)}
				class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none
					{inWhitelist ? 'bg-indigo-600' : 'bg-zinc-800'}"
				role="switch"
				aria-checked={inWhitelist}
				aria-label="Kích hoạt tự phục hồi cho {cName}"
			>
				<span
					aria-hidden="true"
					class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out
						{inWhitelist ? 'translate-x-4' : 'translate-x-0'}"
				></span>
			</button>
		</div>
	</div>

	<!-- Action Buttons -->
	<div class="grid grid-cols-3 gap-2 mt-4 pt-3.5 border-t border-zinc-800/60">
		<!-- START BUTTON -->
		<button
			onclick={() => onaction(cId, 'start')}
			disabled={isRunning || actionLoading[cId]}
			class="flex items-center justify-center gap-1 py-1.5 rounded-lg text-xs font-semibold transition-all border cursor-pointer select-none
				{isRunning
					? 'bg-zinc-950 text-zinc-600 border-zinc-900 cursor-not-allowed opacity-50'
					: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20 hover:bg-emerald-600 hover:text-white hover:border-emerald-500'}"
		>
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
				<path stroke-linecap="round" stroke-linejoin="round" d="M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.347a1.125 1.125 0 0 1 0 1.972l-11.54 6.347a1.125 1.125 0 0 1-1.667-.986V5.653Z" />
			</svg>
			Start
		</button>

		<!-- STOP BUTTON -->
		<button
			onclick={() => onaction(cId, 'stop')}
			disabled={!isRunning || actionLoading[cId]}
			class="flex items-center justify-center gap-1 py-1.5 rounded-lg text-xs font-semibold transition-all border cursor-pointer select-none
				{!isRunning
					? 'bg-zinc-950 text-zinc-600 border-zinc-900 cursor-not-allowed opacity-50'
					: 'bg-rose-500/10 text-rose-400 border-rose-500/20 hover:bg-rose-600 hover:text-white hover:border-rose-500'}"
		>
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
				<path stroke-linecap="round" stroke-linejoin="round" d="M5.25 7.5A2.25 2.25 0 0 1 7.5 5.25h9a2.25 2.25 0 0 1 2.25 2.25v9a2.25 2.25 0 0 1-2.25 2.25h-9a2.25 2.25 0 0 1-2.25-2.25v-9Z" />
			</svg>
			Stop
		</button>

		<!-- RESTART BUTTON -->
		<button
			onclick={() => onaction(cId, 'restart')}
			disabled={actionLoading[cId]}
			class="flex items-center justify-center gap-1 py-1.5 rounded-lg text-xs font-semibold bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-zinc-800 hover:border-zinc-700 transition-all cursor-pointer select-none disabled:opacity-50"
		>
			{#if actionLoading[cId]}
				<svg class="animate-spin h-3.5 w-3.5 text-zinc-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
					<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
					<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
				</svg>
			{:else}
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
				</svg>
			{/if}
			Restart
		</button>
	</div>
</div>
