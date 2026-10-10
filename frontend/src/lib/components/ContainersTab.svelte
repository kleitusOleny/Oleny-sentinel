<script lang="ts">
	import ContainerCard from './ContainerCard.svelte';

	interface Props {
		containers: any[];
		whitelist: string[];
		actionLoading?: Record<string, boolean>;
		copySuccess?: Record<string, boolean>;
		isLoading?: boolean;
		searchQuery?: string;
		statusFilter?: string; // 'all' | 'running' | 'exited'
		showFilterBar?: boolean;
		onaction: (id: string, action: 'start' | 'stop' | 'restart') => void;
		ontoggleAutoHeal: (name: string) => void;
		onopenLogs: (id: string, name: string) => void;
		oncopy: (text: string, id: string) => void;
	}

	let {
		containers = [],
		whitelist = [],
		actionLoading = {},
		copySuccess = {},
		isLoading = false,
		searchQuery = $bindable(''),
		statusFilter = $bindable('all'),
		showFilterBar = true,
		onaction,
		ontoggleAutoHeal,
		onopenLogs,
		oncopy
	}: Props = $props();

	let runningCount = $derived(
		containers.filter((c) => {
			const state = (c.State || c.state || '').toLowerCase();
			return state === 'running';
		}).length
	);

	let filteredContainers = $derived(
		containers.filter((c) => {
			const name = (c.Names?.[0] || c.name || '').toLowerCase();
			const image = (c.Image || c.image || '').toLowerCase();
			const state = (c.State || c.state || '').toLowerCase();
			const query = searchQuery.toLowerCase();

			const matchesSearch = name.includes(query) || image.includes(query);
			if (!matchesSearch) return false;

			if (statusFilter === 'running') return state === 'running';
			if (statusFilter === 'exited') return state !== 'running';
			return true;
		})
	);
</script>

<div class="space-y-6">
	<!-- Search & Status Filter Bar -->
	{#if showFilterBar}
		<div class="flex flex-col md:flex-row justify-between items-stretch md:items-center gap-4 bg-zinc-900/30 p-4 border border-zinc-800/80 rounded-2xl backdrop-blur-md">
			<!-- Filter Badges -->
			<div class="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
				<button
					onclick={() => (statusFilter = 'all')}
					class="px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer border {statusFilter === 'all' ? 'bg-indigo-600 text-white border-indigo-500 shadow-md shadow-indigo-900/40' : 'bg-zinc-900 text-zinc-400 border-zinc-800 hover:text-zinc-200'}"
				>
					Tất cả ({containers.length})
				</button>
				<button
					onclick={() => (statusFilter = 'running')}
					class="px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer border {statusFilter === 'running' ? 'bg-emerald-600/30 text-emerald-300 border-emerald-500/50 shadow-md shadow-emerald-950/40' : 'bg-zinc-900 text-zinc-400 border-zinc-800 hover:text-zinc-200'}"
				>
					Đang chạy ({runningCount})
				</button>
				<button
					onclick={() => (statusFilter = 'exited')}
					class="px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer border {statusFilter === 'exited' ? 'bg-rose-600/30 text-rose-300 border-rose-500/50 shadow-md shadow-rose-950/40' : 'bg-zinc-900 text-zinc-400 border-zinc-800 hover:text-zinc-200'}"
				>
					Đã dừng ({containers.length - runningCount})
				</button>
			</div>

			<!-- Search Field -->
			<div class="relative w-full md:w-80">
				<input
					type="text"
					placeholder="Tìm kiếm theo tên container, image..."
					bind:value={searchQuery}
					class="w-full bg-zinc-900/90 border border-zinc-800 text-zinc-100 placeholder-zinc-500 rounded-xl pl-10 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
				/>
				<div class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-zinc-500">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
					</svg>
				</div>
			</div>
		</div>
	{/if}

	<!-- Loading Skeleton -->
	{#if isLoading && containers.length === 0}
		<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
			{#each Array(6) as _}
				<div class="bg-zinc-900/20 border border-zinc-800/80 rounded-2xl p-5 space-y-4 animate-pulse">
					<div class="h-6 bg-zinc-800 rounded w-2/3"></div>
					<div class="h-4 bg-zinc-800 rounded w-1/2"></div>
					<div class="h-4 bg-zinc-800 rounded w-1/3"></div>
					<div class="flex justify-between items-center pt-2">
						<div class="h-8 bg-zinc-800 rounded w-1/3"></div>
						<div class="h-8 bg-zinc-800 rounded w-1/3"></div>
					</div>
				</div>
			{/each}
		</div>
	{:else if filteredContainers.length === 0}
		<!-- Empty state -->
		<div class="text-center py-16 bg-zinc-900/10 border border-dashed border-zinc-800 rounded-2xl space-y-3">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-12 h-12 text-zinc-600 mx-auto">
				<path stroke-linecap="round" stroke-linejoin="round" d="m9.75 9.75 4.5 4.5m0-4.5-4.5 4.5M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
			</svg>
			<h4 class="text-zinc-400 font-bold">Không tìm thấy Container nào</h4>
			<p class="text-xs text-zinc-500">Hãy thử đổi từ khoá tìm kiếm hoặc kiểm tra bộ lọc trạng thái.</p>
		</div>
	{:else}
		<!-- GRID LIST CONTAINERS -->
		<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
			{#each filteredContainers as c (c.Id || c.id)}
				<ContainerCard 
					container={c} 
					whitelist={whitelist} 
					actionLoading={actionLoading} 
					copySuccess={copySuccess} 
					onaction={onaction} 
					ontoggleAutoHeal={ontoggleAutoHeal} 
					onopenLogs={onopenLogs} 
					oncopy={oncopy}
				/>
			{/each}
		</div>
	{/if}
</div>
