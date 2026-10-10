<script lang="ts">
	interface Props {
		containers: any[];
		onnavigate?: (tab: string) => void;
	}

	let { containers = [], onnavigate }: Props = $props();

	let runningCount = $derived(
		containers.filter((c) => (c.State || c.state) === 'running').length
	);
	let stoppedCount = $derived(
		containers.filter((c) => (c.State || c.state) !== 'running').length
	);
</script>

<div class="space-y-3">
	<!-- Running Containers -->
	<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
		<div class="flex items-center gap-2">
			<span class="w-2 h-2 rounded-full bg-emerald-400"></span>
			<span class="text-xs text-zinc-400">Running</span>
		</div>
		<span class="font-mono text-sm font-bold text-emerald-400">
			{runningCount} <span class="text-xs text-zinc-500 font-normal">/ {containers.length}</span>
		</span>
	</div>

	<!-- Stopped / Crashed Containers -->
	<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
		<div class="flex items-center gap-2">
			<span class="w-2 h-2 rounded-full {stoppedCount > 0 ? 'bg-rose-400' : 'bg-zinc-600'}"></span>
			<span class="text-xs text-zinc-400">Stopped</span>
		</div>
		<span class="font-mono text-sm font-bold {stoppedCount > 0 ? 'text-rose-400' : 'text-zinc-500'}">
			{stoppedCount}
		</span>
	</div>

	{#if onnavigate}
		<button
			onclick={() => onnavigate('containers')}
			class="w-full mt-1 py-2 rounded-xl bg-zinc-800/60 hover:bg-indigo-600/20 text-zinc-300 hover:text-indigo-300 text-xs font-bold transition-all border border-zinc-700/50 cursor-pointer"
		>
			Manage Containers →
		</button>
	{/if}
</div>
