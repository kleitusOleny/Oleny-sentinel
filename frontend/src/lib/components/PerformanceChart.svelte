<script lang="ts">
	interface Props {
		statsHistory: any[];
	}

	let { statsHistory }: Props = $props();

	// Derived paths calculated locally for encapsulation
	let cpuPath = $derived.by(() => {
		if (statsHistory.length < 2) return '';
		return statsHistory.map((d, i) => {
			const x = (i / (statsHistory.length - 1)) * 1000;
			const y = 190 - (d.cpuLoad / 100) * 180;
			return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
		}).join(' ');
	});

	let cpuAreaPath = $derived.by(() => {
		if (statsHistory.length < 2) return '';
		return `${cpuPath} L 1000 195 L 0 195 Z`;
	});

	let ramPath = $derived.by(() => {
		if (statsHistory.length < 2) return '';
		return statsHistory.map((d, i) => {
			const x = (i / (statsHistory.length - 1)) * 1000;
			const y = 190 - (d.ramUsagePercent / 100) * 180;
			return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
		}).join(' ');
	});

	let ramAreaPath = $derived.by(() => {
		if (statsHistory.length < 2) return '';
		return `${ramPath} L 1000 195 L 0 195 Z`;
	});
</script>

<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-6 shadow-xl relative overflow-hidden flex flex-col justify-between">
	<div>
		<h3 class="font-bold text-zinc-200 text-lg flex items-center gap-2 mb-2">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 text-indigo-400">
				<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 18 9 11.25l4.306 4.306a11.95 11.95 0 0 1 5.814-5.518l2.74-1.22m0 0-5.94-2.281m5.94 2.28-2.28 5.941" />
			</svg>
			Lịch sử hiệu năng (30 phút gần nhất)
		</h3>
		<p class="text-zinc-500 text-xs mb-4">Chu kỳ lấy mẫu 30 giây/lần. Tự động cập nhật.</p>
	</div>

	<!-- Chart Container -->
	<div class="relative w-full h-48 bg-zinc-950/40 rounded-xl border border-zinc-800/40 p-2 flex items-center justify-center">
		{#if statsHistory.length < 2}
			<div class="text-zinc-600 text-xs italic flex items-center gap-2">
				<svg class="animate-pulse h-4 w-4 text-zinc-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
					<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
					<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
				</svg>
				Đang thu thập dữ liệu lịch sử hiệu năng...
			</div>
		{:else}
			<svg viewBox="0 0 1000 200" class="w-full h-full overflow-visible" preserveAspectRatio="none">
				<defs>
					<!-- CPU Gradient -->
					<linearGradient id="cpuGrad" x1="0" y1="0" x2="0" y2="1">
						<stop offset="0%" stop-color="#8b5cf6" stop-opacity="0.3"/>
						<stop offset="100%" stop-color="#8b5cf6" stop-opacity="0"/>
					</linearGradient>
					<!-- RAM Gradient -->
					<linearGradient id="ramGrad" x1="0" y1="0" x2="0" y2="1">
						<stop offset="0%" stop-color="#06b6d4" stop-opacity="0.3"/>
						<stop offset="100%" stop-color="#06b6d4" stop-opacity="0"/>
					</linearGradient>
				</defs>

				<!-- Grid Lines -->
				<line x1="0" y1="10" x2="1000" y2="10" stroke="#27272a" stroke-width="1" stroke-dasharray="4"/>
				<line x1="0" y1="100" x2="1000" y2="100" stroke="#27272a" stroke-width="1" stroke-dasharray="4"/>
				<line x1="0" y1="190" x2="1000" y2="190" stroke="#27272a" stroke-width="1"/>

				<!-- Area charts -->
				<path d={cpuAreaPath} fill="url(#cpuGrad)" />
				<path d={ramAreaPath} fill="url(#ramGrad)" />

				<!-- Line charts -->
				<path d={cpuPath} fill="none" stroke="#a78bfa" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
				<path d={ramPath} fill="none" stroke="#22d3ee" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
			</svg>

			<!-- Floating legends on chart -->
			<div class="absolute top-2 right-4 flex gap-4 text-[10px] font-semibold select-none">
				<span class="flex items-center gap-1.5 text-violet-400">
					<span class="w-2 h-2 rounded-full bg-violet-400"></span> CPU
				</span>
				<span class="flex items-center gap-1.5 text-cyan-400">
					<span class="w-2 h-2 rounded-full bg-cyan-400"></span> RAM
				</span>
			</div>
		{/if}
	</div>
</div>
