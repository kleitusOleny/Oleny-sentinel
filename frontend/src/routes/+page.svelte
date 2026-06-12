<script lang="ts">
	import { onMount } from 'svelte';

	const API_BASE = 'http://localhost:8080/api';

	// Reactive states (Svelte 5 runes)
	let containers = $state<any[]>([]);
	let systemStats = $state<{ cpuLoad: number; freeMemoryMB: number; totalMemoryMB: number } | null>(null);
	let whitelist = $state<string[]>([]);
	let searchQuery = $state('');
	let isLoading = $state(true);
	let autoRefresh = $state(true);
	let errorMessage = $state('');

	// Action tracking states
	let actionLoading = $state<Record<string, boolean>>({});
	let copySuccess = $state<Record<string, boolean>>({});

	// Derived states (Svelte 5 runes)
	let filteredContainers = $derived(
		containers.filter((c) => {
			const name = getContainerName(c).toLowerCase();
			const image = (c.Image || c.image || '').toLowerCase();
			const query = searchQuery.toLowerCase();
			return name.includes(query) || image.includes(query);
		})
	);

	let usedMemoryMB = $derived(
		systemStats ? systemStats.totalMemoryMB - systemStats.freeMemoryMB : 0
	);
	let memoryUsagePercent = $derived(
		systemStats && systemStats.totalMemoryMB > 0
			? (usedMemoryMB / systemStats.totalMemoryMB) * 100
			: 0
	);

	function getContainerName(container: any): string {
		const names = container.Names || container.names;
		if (names && names.length > 0) {
			return names[0].replace(/^\//, '');
		}
		return 'unknown';
	}

	async function fetchData() {
		try {
			const [containersRes, statsRes, whitelistRes] = await Promise.all([
				fetch(`${API_BASE}/containers`),
				fetch(`${API_BASE}/system/stats`),
				fetch(`${API_BASE}/auto-heal/whitelist`)
			]);

			if (!containersRes.ok || !statsRes.ok || !whitelistRes.ok) {
				throw new Error('Không thể tải dữ liệu từ Backend.');
			}

			containers = await containersRes.json();
			systemStats = await statsRes.json();
			whitelist = await whitelistRes.json();
			errorMessage = '';
		} catch (err: any) {
			console.error(err);
			errorMessage = 'Lỗi kết nối với Backend Sentinel (http://localhost:8080). Vui lòng kiểm tra xem server đã khởi động chưa.';
		} finally {
			isLoading = false;
		}
	}

	async function handleContainerAction(containerId: string, action: 'start' | 'stop' | 'restart') {
		actionLoading[containerId] = true;
		try {
			const res = await fetch(`${API_BASE}/containers/${containerId}/${action}`, {
				method: 'POST'
			});
			const data = await res.json();
			if (data.status === 'error') {
				alert(`Thao tác thất bại: ${data.message}`);
			} else {
				await fetchData();
			}
		} catch (err: any) {
			alert(`Không thể gửi yêu cầu: ${err.message}`);
		} finally {
			actionLoading[containerId] = false;
		}
	}

	async function handleToggleAutoHeal(containerName: string) {
		try {
			const res = await fetch(`${API_BASE}/auto-heal/whitelist/toggle`, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({ name: containerName })
			});
			const data = await res.json();
			if (data.status === 'success') {
				whitelist = data.whitelist;
			} else {
				alert(`Lỗi cấu hình Auto-heal: ${data.message}`);
			}
		} catch (err: any) {
			alert(`Không thể thay đổi Auto-heal: ${err.message}`);
		}
	}

	function copyToClipboard(text: string, id: string) {
		navigator.clipboard.writeText(text);
		copySuccess[id] = true;
		setTimeout(() => {
			copySuccess[id] = false;
		}, 2000);
	}

	// Dynamic Polling Effect (Svelte 5 runes)
	$effect(() => {
		fetchData();

		let interval: any;
		if (autoRefresh) {
			interval = setInterval(fetchData, 5000); // Polling mỗi 5 giây
		}

		return () => {
			if (interval) clearInterval(interval);
		};
	});
</script>

<div class="min-h-screen bg-zinc-950 text-zinc-50 font-sans p-4 md:p-8 selection:bg-indigo-500 selection:text-white">
	<div class="max-w-7xl mx-auto space-y-8">

		<!-- TOP HEADER -->
		<header class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-zinc-900/40 backdrop-blur-md border border-zinc-800/80 rounded-2xl p-6 shadow-2xl">
			<div>
				<div class="flex items-center gap-3">
					<span class="flex h-3.5 w-3.5 relative">
						<span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
						<span class="relative inline-flex rounded-full h-3.5 w-3.5 bg-indigo-500"></span>
					</span>
					<h1 class="text-2xl md:text-3xl font-extrabold tracking-tight bg-gradient-to-r from-violet-400 via-indigo-400 to-cyan-400 bg-clip-text text-transparent">
						SERVER SENTINEL
					</h1>
				</div>
				<p class="text-zinc-400 text-sm mt-1">Hệ thống giám sát hiệu năng máy chủ và tự khôi phục Docker Container</p>
			</div>

			<div class="flex items-center gap-3 w-full md:w-auto">
				<!-- Toggle Auto Refresh -->
				<label class="flex items-center gap-2 text-sm text-zinc-400 cursor-pointer bg-zinc-900 border border-zinc-800 px-4 py-2 rounded-xl hover:border-zinc-700 select-none">
					<input type="checkbox" bind:checked={autoRefresh} class="rounded border-zinc-800 text-indigo-600 focus:ring-indigo-500 bg-zinc-950 w-4 h-4 cursor-pointer" />
					<span>Tự động tải lại (5s)</span>
				</label>

				<!-- Force Reload -->
				<button
					onclick={fetchData}
					class="p-2.5 rounded-xl bg-indigo-600/10 text-indigo-400 border border-indigo-500/20 hover:bg-indigo-600 hover:text-white transition-all cursor-pointer"
					title="Tải lại ngay"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
					</svg>
				</button>
			</div>
		</header>

		<!-- ERROR ALERT BANNER -->
		{#if errorMessage}
			<div class="bg-rose-950/40 border border-rose-800/80 text-rose-300 px-6 py-4 rounded-2xl flex items-start gap-3 shadow-xl">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6 shrink-0 text-rose-400 mt-0.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
				</svg>
				<div>
					<h4 class="font-bold text-rose-200">Lỗi Kết Nối Hệ Thống</h4>
					<p class="text-sm mt-0.5">{errorMessage}</p>
				</div>
			</div>
		{/if}

		<!-- SYSTEM METRICS SECTION -->
		<section class="grid grid-cols-1 md:grid-cols-2 gap-6">
			<!-- CPU Card -->
			<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-6 shadow-xl relative overflow-hidden">
				<div class="flex justify-between items-center mb-4">
					<div class="flex items-center gap-2">
						<div class="p-2 rounded-lg bg-violet-500/10 text-violet-400">
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
								<path stroke-linecap="round" stroke-linejoin="round" d="M8.25 3v1.5M4.5 8.25H3m10.5-5.25v1.5M3 12h1.5m15 0h-1.5M13.5 19.5v1.5M12 14.25a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5ZM19.5 8.25H21m-1.5 5.25H21m-9 6v1.5m-7.5-6H3m16.5-6H18m0 7.5H16.5m-9-7.5H9m0 7.5H7.5" />
							</svg>
						</div>
						<h3 class="font-bold text-zinc-200 text-lg">Tải CPU (CPU Load)</h3>
					</div>
					<span class="text-2xl font-black font-mono text-violet-400">
						{systemStats ? systemStats.cpuLoad.toFixed(1) : '0.0'}%
					</span>
				</div>

				<div class="space-y-2">
					<div class="w-full bg-zinc-800/80 h-3 rounded-full overflow-hidden">
						<div
							class="h-full bg-gradient-to-r from-violet-500 to-purple-500 rounded-full transition-all duration-500 ease-out"
							style="width: {systemStats ? Math.min(systemStats.cpuLoad, 100) : 0}%"
						></div>
					</div>
					<div class="flex justify-between text-xs text-zinc-500">
						<span>0%</span>
						<span>Cảnh báo</span>
						<span>100% Tải tối đa</span>
					</div>
				</div>
			</div>

			<!-- RAM Card -->
			<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-6 shadow-xl relative overflow-hidden">
				<div class="flex justify-between items-center mb-4">
					<div class="flex items-center gap-2">
						<div class="p-2 rounded-lg bg-cyan-500/10 text-cyan-400">
							<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
								<path stroke-linecap="round" stroke-linejoin="round" d="M6 20.25h12A2.25 2.25 0 0 0 20.25 18V6A2.25 2.25 0 0 0 18 3.75H6A2.25 2.25 0 0 0 3.75 6v12A2.25 2.25 0 0 0 6 20.25Z" />
								<path stroke-linecap="round" stroke-linejoin="round" d="M9 3.75v16.5M15 3.75v16.5M3.75 9h16.5M3.75 15h16.5" />
							</svg>
						</div>
						<h3 class="font-bold text-zinc-200 text-lg">Sử dụng RAM (Memory)</h3>
					</div>
					<span class="text-2xl font-black font-mono text-cyan-400">
						{memoryUsagePercent.toFixed(1)}%
					</span>
				</div>

				<div class="space-y-2">
					<div class="w-full bg-zinc-800/80 h-3 rounded-full overflow-hidden">
						<div
							class="h-full bg-gradient-to-r from-cyan-500 to-blue-500 rounded-full transition-all duration-500 ease-out"
							style="width: {memoryUsagePercent}%"
						></div>
					</div>
					<div class="flex justify-between text-xs text-zinc-400">
						<span class="text-zinc-500">Đã dùng: <strong class="text-zinc-300 font-mono">{usedMemoryMB.toFixed(0)} MB</strong></span>
						<span class="text-zinc-500">Còn trống: <strong class="text-zinc-300 font-mono">{systemStats ? systemStats.freeMemoryMB : 0} MB</strong></span>
						<span class="text-zinc-500">Tổng: <strong class="text-zinc-300 font-mono">{systemStats ? systemStats.totalMemoryMB : 0} MB</strong></span>
					</div>
				</div>
			</div>
		</section>

		<!-- CONTAINERS CONTROLLER SECTION -->
		<section class="space-y-6">
			<!-- Search & Filter Bar -->
			<div class="flex flex-col md:flex-row justify-between items-stretch md:items-center gap-4 bg-zinc-900/20 p-4 border border-zinc-800/60 rounded-2xl">
				<h2 class="text-lg font-bold text-zinc-200 flex items-center gap-2">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 text-indigo-400">
						<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
					</svg>
					Danh sách Docker Container ({filteredContainers.length})
				</h2>

				<div class="relative w-full md:w-96">
					<input
						type="text"
						placeholder="Tìm kiếm container theo tên, image..."
						bind:value={searchQuery}
						class="w-full bg-zinc-900 border border-zinc-800 text-zinc-100 placeholder-zinc-500 rounded-xl pl-10 pr-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
					/>
					<div class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-zinc-500">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
						</svg>
					</div>
				</div>
			</div>

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
					<p class="text-xs text-zinc-500">Hãy thử đổi từ khoá tìm kiếm hoặc kiểm tra xem Docker đã có container nào chưa.</p>
				</div>
			{:else}
				<!-- GRID LIST CONTAINERS -->
				<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
					{#each filteredContainers as c (c.Id || c.id)}
						{@const cId = c.Id || c.id || ''}
						{@const cName = getContainerName(c)}
						{@const cState = c.State || c.state || ''}
						{@const cStatus = c.Status || c.status || ''}
						{@const cImage = c.Image || c.image || ''}
						{@const isRunning = cState && cState.toLowerCase() === 'running'}
						{@const isExited = cState && cState.toLowerCase() === 'exited'}
						{@const inWhitelist = whitelist.includes(cName)}

						<div class="bg-zinc-900/40 backdrop-blur-md border border-zinc-800/80 rounded-2xl p-5 hover:border-zinc-700/80 transition-all shadow-xl flex flex-col justify-between group">
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

								<!-- Image -->
								<div class="text-xs text-zinc-500 space-y-1">
									<p class="truncate"><span class="text-zinc-600 font-medium">Image:</span> {cImage}</p>
									<p class="truncate"><span class="text-zinc-600 font-medium">Status:</span> {cStatus}</p>
								</div>

								<!-- ID Copyable -->
								<div class="flex items-center justify-between bg-zinc-950/60 rounded-lg px-2.5 py-1 text-xs border border-zinc-800/50">
									<code class="font-mono text-zinc-500 truncate mr-2">{cId.substring(0, 12)}...</code>
									<button
										onclick={() => copyToClipboard(cId, cId)}
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

								<!-- Auto Heal Toggle Slider -->
								<div class="flex items-center justify-between border-t border-zinc-800/60 pt-3.5 select-none">
									<div class="flex items-center gap-1.5">
										<span class="text-xs font-semibold text-zinc-400">Tự phục hồi (Auto-heal)</span>
										<div class="group/info relative inline-block">
											<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5 text-zinc-500 hover:text-zinc-400 cursor-help">
												<path stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 1 1 1.063 1.063L12 13.504L10.875 12.11a.75.75 0 0 1 1.062-1.062M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75s9.75-4.365 9.75-9.75S17.385 2.25 12 2.25Z" />
											</svg>
											<div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-48 hidden group-hover/info:block bg-zinc-950 border border-zinc-800 text-[10px] text-zinc-400 p-2.5 rounded-lg shadow-xl z-20">
												Khi bật, nếu container này bị crash (Exited), Server Sentinel sẽ tự khởi động lại nó.
											</div>
										</div>
									</div>

									<button
										onclick={() => handleToggleAutoHeal(cName)}
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
									onclick={() => handleContainerAction(cId, 'start')}
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
									onclick={() => handleContainerAction(cId, 'stop')}
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
									onclick={() => handleContainerAction(cId, 'restart')}
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
					{/each}
				</div>
			{/if}
		</section>

		<!-- FOOTER -->
		<footer class="text-center py-8 border-t border-zinc-900 text-xs text-zinc-600">
			<p>© 2026 Server Sentinel. Thiết kế sang trọng với SvelteKit 5 & Tailwind CSS v4.</p>
		</footer>
	</div>
</div>
