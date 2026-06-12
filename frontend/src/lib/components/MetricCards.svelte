<script lang="ts">
	interface Stats {
		cpuLoad: number;
		freeMemoryMB: number;
		totalMemoryMB: number;
		diskTotalGB: number;
		diskUsedGB: number;
		diskUsagePercent: number;
		rxSpeedKBps: number;
		txSpeedKBps: number;
		gpuAvailable: boolean;
		gpuName: string;
		gpuLoad: number;
		gpuMemoryTotalMB: number;
		gpuMemoryUsedMB: number;
		gpuMemoryUsagePercent: number;
	}

	interface Props {
		systemStats: Stats | null;
	}

	let { systemStats }: Props = $props();

	// Derived values
	let usedMemoryMB = $derived(
		systemStats ? systemStats.totalMemoryMB - systemStats.freeMemoryMB : 0
	);
	let memoryUsagePercent = $derived(
		systemStats && systemStats.totalMemoryMB > 0
			? (usedMemoryMB / systemStats.totalMemoryMB) * 100
			: 0
	);

	function formatNetSpeed(kbps: number): string {
		if (kbps >= 1024) {
			return `${(kbps / 1024).toFixed(1)} MB/s`;
		}
		return `${kbps.toFixed(1)} KB/s`;
	}
</script>

<section class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-6">
	<!-- CPU Card -->
	<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-5 shadow-xl relative overflow-hidden flex flex-col justify-between min-h-[140px]">
		<div class="flex justify-between items-center">
			<div class="flex items-center gap-2">
				<div class="p-2 rounded-lg bg-violet-500/10 text-violet-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M8.25 3v1.5M4.5 8.25H3m10.5-5.25v1.5M3 12h1.5m15 0h-1.5M13.5 19.5v1.5M12 14.25a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5ZM19.5 8.25H21m-1.5 5.25H21m-9 6v1.5m-7.5-6H3m16.5-6H18m0 7.5H16.5m-9-7.5H9m0 7.5H7.5" />
					</svg>
				</div>
				<h3 class="font-bold text-zinc-400 text-xs tracking-wider uppercase">Tải CPU</h3>
			</div>
			<span class="text-xl font-black font-mono text-violet-400 shrink-0">
				{systemStats ? systemStats.cpuLoad.toFixed(1) : '0.0'}%
			</span>
		</div>

		<div class="space-y-2 mt-4">
			<div class="w-full bg-zinc-800/80 h-2 rounded-full overflow-hidden">
				<div
					class="h-full bg-gradient-to-r from-violet-500 to-purple-500 rounded-full transition-all duration-500 ease-out"
					style="width: {systemStats ? Math.min(systemStats.cpuLoad, 100) : 0}%"
				></div>
			</div>
			<div class="flex justify-between text-[10px] text-zinc-500">
				<span>0%</span>
				<span>100% Load</span>
			</div>
		</div>
	</div>

	<!-- RAM Card -->
	<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-5 shadow-xl relative overflow-hidden flex flex-col justify-between min-h-[140px]">
		<div class="flex justify-between items-center">
			<div class="flex items-center gap-2">
				<div class="p-2 rounded-lg bg-cyan-500/10 text-cyan-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M6 20.25h12A2.25 2.25 0 0 0 20.25 18V6A2.25 2.25 0 0 0 18 3.75H6A2.25 2.25 0 0 0 3.75 6v12A2.25 2.25 0 0 0 6 20.25Z" />
						<path stroke-linecap="round" stroke-linejoin="round" d="M9 3.75v16.5M15 3.75v16.5M3.75 9h16.5M3.75 15h16.5" />
					</svg>
				</div>
				<h3 class="font-bold text-zinc-400 text-xs tracking-wider uppercase">Bộ nhớ RAM</h3>
			</div>
			<span class="text-xl font-black font-mono text-cyan-400 shrink-0">
				{memoryUsagePercent.toFixed(1)}%
			</span>
		</div>

		<div class="space-y-2 mt-4">
			<div class="w-full bg-zinc-800/80 h-2 rounded-full overflow-hidden">
				<div
					class="h-full bg-gradient-to-r from-cyan-500 to-blue-500 rounded-full transition-all duration-500 ease-out"
					style="width: {memoryUsagePercent}%"
				></div>
			</div>
			<div class="flex justify-between text-[9px] text-zinc-500">
				<span>Dùng: {usedMemoryMB.toFixed(0)} MB</span>
				<span>Tổng: {systemStats ? systemStats.totalMemoryMB : 0} MB</span>
			</div>
		</div>
	</div>

	<!-- Disk Usage Card -->
	<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-5 shadow-xl relative overflow-hidden flex flex-col justify-between min-h-[140px]">
		<div class="flex justify-between items-center">
			<div class="flex items-center gap-2">
				<div class="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m8.25 3v6.75m0 0l-3-3m3 3l3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
					</svg>
				</div>
				<h3 class="font-bold text-zinc-400 text-xs tracking-wider uppercase">Dung lượng đĩa</h3>
			</div>
			<span class="text-xl font-black font-mono text-emerald-400 shrink-0">
				{systemStats ? systemStats.diskUsagePercent.toFixed(1) : '0.0'}%
			</span>
		</div>

		<div class="space-y-2 mt-4">
			<div class="w-full bg-zinc-800/80 h-2 rounded-full overflow-hidden">
				<div
					class="h-full bg-gradient-to-r from-emerald-500 to-teal-500 rounded-full transition-all duration-500 ease-out"
					style="width: {systemStats ? systemStats.diskUsagePercent : 0}%"
				></div>
			</div>
			<div class="flex justify-between text-[9px] text-zinc-500">
				<span>Dùng: {systemStats ? systemStats.diskUsedGB.toFixed(1) : '0.0'} GB</span>
				<span>Tổng: {systemStats ? systemStats.diskTotalGB.toFixed(1) : '0.0'} GB</span>
			</div>
		</div>
	</div>

	<!-- Network Card -->
	<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-5 shadow-xl relative overflow-hidden flex flex-col justify-between min-h-[140px] select-none">
		<div class="flex justify-between items-center mb-1">
			<div class="flex items-center gap-2">
				<div class="p-2 rounded-lg bg-amber-500/10 text-amber-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
					</svg>
				</div>
				<h3 class="font-bold text-zinc-400 text-xs tracking-wider uppercase">Lưu lượng Mạng</h3>
			</div>
		</div>

		<div class="space-y-2.5 mt-2 font-mono text-xs">
			<div class="flex justify-between items-center">
				<span class="text-zinc-500 flex items-center gap-1">
					<span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> Nhận (Rx):
				</span>
				<span class="text-emerald-400 font-bold">
					{systemStats ? formatNetSpeed(systemStats.rxSpeedKBps) : '0.0 KB/s'}
				</span>
			</div>
			<div class="flex justify-between items-center">
				<span class="text-zinc-500 flex items-center gap-1">
					<span class="w-1.5 h-1.5 rounded-full bg-orange-400"></span> Gửi (Tx):
				</span>
				<span class="text-orange-400 font-bold">
					{systemStats ? formatNetSpeed(systemStats.txSpeedKBps) : '0.0 KB/s'}
				</span>
			</div>
		</div>
	</div>

	<!-- GPU Card -->
	<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-5 shadow-xl relative overflow-hidden flex flex-col justify-between min-h-[140px]">
		<div class="flex justify-between items-center">
			<div class="flex items-center gap-2">
				<div class="p-2 rounded-lg bg-rose-500/10 text-rose-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
					</svg>
				</div>
				<h3 class="font-bold text-zinc-400 text-xs tracking-wider uppercase">Đồ họa GPU</h3>
			</div>
			{#if systemStats && systemStats.gpuAvailable}
				<span class="text-xl font-black font-mono text-rose-400 shrink-0">
					{systemStats.gpuLoad.toFixed(0)}%
				</span>
			{/if}
		</div>

		{#if systemStats && systemStats.gpuAvailable}
			<div class="space-y-1 mt-2 text-left">
				<p class="text-[9px] text-zinc-500 truncate" title={systemStats.gpuName}>
					Name: <span class="text-zinc-300 font-semibold">{systemStats.gpuName}</span>
				</p>
				<div class="w-full bg-zinc-800/80 h-1.5 rounded-full overflow-hidden mt-1.5">
					<div
						class="h-full bg-gradient-to-r from-rose-500 to-pink-500 rounded-full transition-all duration-500 ease-out"
						style="width: {systemStats.gpuMemoryUsagePercent}%"
					></div>
				</div>
				<p class="text-[9px] text-zinc-500 flex justify-between font-mono pt-0.5">
					<span>VRAM: {systemStats.gpuMemoryUsedMB} MB</span>
					<span>Max: {systemStats.gpuMemoryTotalMB} MB</span>
				</p>
			</div>
		{:else}
			<div class="text-center py-4 text-[10px] text-zinc-600 italic mt-3 border border-dashed border-zinc-800/60 rounded-xl">
				Không phát hiện GPU rời
			</div>
		{/if}
	</div>
</section>
