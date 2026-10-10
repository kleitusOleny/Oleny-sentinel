<script lang="ts">
	interface Props {
		systemStats: any | null;
		onnavigate?: (tab: string) => void;
	}

	let { systemStats, onnavigate }: Props = $props();

	function formatBytes(mb: number): string {
		if (!mb) return '--';
		if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB';
		return mb + ' MB';
	}
</script>

<div class="space-y-3">
	<!-- CPU Usage -->
	<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
		<div class="flex items-center gap-2">
			<span class="w-2 h-2 rounded-full {systemStats?.cpuUsage > 80 ? 'bg-rose-400' : 'bg-emerald-400'}"></span>
			<span class="text-xs text-zinc-400">CPU Usage</span>
		</div>
		<span class="font-mono text-sm font-bold {systemStats?.cpuUsage > 80 ? 'text-rose-400' : 'text-emerald-400'}">
			{systemStats?.cpuUsage !== undefined ? `${systemStats.cpuUsage.toFixed(1)}%` : '--'}
		</span>
	</div>

	<!-- RAM Memory -->
	<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
		<div class="flex items-center gap-2">
			<span class="w-2 h-2 rounded-full bg-cyan-400"></span>
			<span class="text-xs text-zinc-400">RAM Memory</span>
		</div>
		<span class="font-mono text-sm font-bold text-cyan-400">
			{systemStats?.usedMemoryMB !== undefined ? formatBytes(systemStats.usedMemoryMB) : '--'}
			{#if systemStats?.totalMemoryMB}
				<span class="text-xs text-zinc-500 font-normal">/ {formatBytes(systemStats.totalMemoryMB)}</span>
			{/if}
		</span>
	</div>

	<!-- Disk or Uptime if available -->
	{#if systemStats?.diskUsagePercent !== undefined}
		<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
			<div class="flex items-center gap-2">
				<span class="w-2 h-2 rounded-full bg-indigo-400"></span>
				<span class="text-xs text-zinc-400">Disk Storage</span>
			</div>
			<span class="font-mono text-sm font-bold text-indigo-400">
				{systemStats.diskUsagePercent.toFixed(1)}%
			</span>
		</div>
	{/if}

	{#if onnavigate}
		<button
			onclick={() => onnavigate('overview')}
			class="w-full mt-1 py-2 rounded-xl bg-zinc-800/60 hover:bg-indigo-600/20 text-zinc-300 hover:text-indigo-300 text-xs font-bold transition-all border border-zinc-700/50 cursor-pointer"
		>
			View Metrics →
		</button>
	{/if}
</div>
