<script lang="ts">
	import { onMount } from 'svelte';
	import LoginPanel from '$lib/components/LoginPanel.svelte';
	import Header from '$lib/components/Header.svelte';
	import MetricCards from '$lib/components/MetricCards.svelte';
	import PerformanceChart from '$lib/components/PerformanceChart.svelte';
	import AlertSettingsPanel from '$lib/components/AlertSettingsPanel.svelte';
	import ContainerCard from '$lib/components/ContainerCard.svelte';
	import LogsModal from '$lib/components/LogsModal.svelte';

	const API_BASE = 'http://localhost:8081/api';

	// Session state
	let userSession = $state<{ email: string; name: string; picture: string; token: string } | null>(null);

	// Reactive states (Svelte 5 runes)
	let containers = $state<any[]>([]);
	let systemStats = $state<any | null>(null);
	let whitelist = $state<string[]>([]);
	let searchQuery = $state('');
	let isLoading = $state(true);
	let autoRefresh = $state(true);
	let errorMessage = $state('');

	// Action tracking states
	let actionLoading = $state<Record<string, boolean>>({});
	let copySuccess = $state<Record<string, boolean>>({});

	// Logs Viewer States
	let activeLogContainerId = $state('');
	let activeLogContainerName = $state('');
	let containerLogsText = $state('');
	let isLogsLoading = $state(false);
	let logLinesCount = $state(100);

	// Historical metrics stats
	let statsHistory = $state<any[]>([]);

	// Dynamic Alerts Configuration states (fetched from backend)
	let settingsCpu = $state(90.0);
	let settingsRam = $state(500);
	let settingsDiscordToken = $state('');
	let settingsDiscordChannelId = $state('');

	// Derived states (Svelte 5 runes)
	let filteredContainers = $derived(
		containers.filter((c) => {
			const name = getContainerName(c).toLowerCase();
			const image = (c.Image || c.image || '').toLowerCase();
			const query = searchQuery.toLowerCase();
			return name.includes(query) || image.includes(query);
		})
	);

	function getContainerName(container: any): string {
		const names = container.Names || container.names;
		if (names && names.length > 0) {
			return names[0].replace(/^\//, '');
		}
		return 'unknown';
	}

	async function fetchSettings() {
		try {
			const res = await fetch(`${API_BASE}/settings`);
			if (res.ok) {
				const data = await res.json();
				settingsCpu = data.cpuThreshold ?? 90.0;
				settingsRam = data.ramThresholdMB ?? 500;
				settingsDiscordToken = data.discordBotToken ?? '';
				settingsDiscordChannelId = data.discordChannelId ?? '';
			}
		} catch (err) {
			console.error('Lỗi khi tải cấu hình:', err);
		}
	}

	async function handleSaveSettings(settings: { cpuThreshold: number; ramThresholdMB: number; discordBotToken: string; discordChannelId: string }) {
		const res = await fetch(`${API_BASE}/settings`, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json'
			},
			body: JSON.stringify(settings)
		});
		const data = await res.json();
		if (data.status === 'success') {
			settingsCpu = data.settings.cpuThreshold;
			settingsRam = data.settings.ramThresholdMB;
			settingsDiscordToken = data.settings.discordBotToken;
			settingsDiscordChannelId = data.settings.discordChannelId;
		} else {
			throw new Error(data.message || 'Lưu cấu hình thất bại.');
		}
	}

	async function fetchData() {
		// Chỉ lấy dữ liệu khi người dùng đã đăng nhập thành công
		if (!userSession) return;
		
		try {
			const [containersRes, statsRes, whitelistRes, historyRes] = await Promise.all([
				fetch(`${API_BASE}/containers`),
				fetch(`${API_BASE}/system/stats`),
				fetch(`${API_BASE}/auto-heal/whitelist`),
				fetch(`${API_BASE}/system/history`)
			]);

			if (!containersRes.ok || !statsRes.ok || !whitelistRes.ok || !historyRes.ok) {
				throw new Error('Không thể tải dữ liệu từ Backend.');
			}

			containers = await containersRes.json();
			systemStats = await statsRes.json();
			whitelist = await whitelistRes.json();
			statsHistory = await historyRes.json();
			errorMessage = '';
		} catch (err: any) {
			console.error(err);
			errorMessage = 'Lỗi kết nối với Backend Sentinel (http://localhost:8081). Vui lòng kiểm tra xem server đã khởi động chưa.';
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

	async function fetchLogs(containerId: string) {
		isLogsLoading = true;
		try {
			const res = await fetch(`${API_BASE}/containers/${containerId}/logs?lines=${logLinesCount}`);
			if (!res.ok) throw new Error('Không thể tải nhật ký.');
			const data = await res.json();
			containerLogsText = data.logs || '';
		} catch (err: any) {
			containerLogsText = `Lỗi khi lấy logs: ${err.message}`;
		} finally {
			isLogsLoading = false;
		}
	}

	function openLogsModal(containerId: string, containerName: string) {
		activeLogContainerId = containerId;
		activeLogContainerName = containerName;
		fetchLogs(containerId);
	}

	function closeLogsModal() {
		activeLogContainerId = '';
		activeLogContainerName = '';
		containerLogsText = '';
	}

	function handleLoginSuccess(user: any) {
		userSession = user;
		localStorage.setItem('sentinel_user', JSON.stringify(user));
		fetchSettings();
		fetchData();
	}

	function handleLogout() {
		userSession = null;
		localStorage.removeItem('sentinel_user');
	}

	onMount(() => {
		const stored = localStorage.getItem('sentinel_user');
		if (stored) {
			userSession = JSON.parse(stored);
			fetchSettings();
		}
	});

	// Dynamic Polling Effect (Svelte 5 runes)
	$effect(() => {
		if (userSession) {
			fetchData();
			let interval: any;
			if (autoRefresh) {
				interval = setInterval(fetchData, 5000); // Polling mỗi 5 giây
			}
			return () => {
				if (interval) clearInterval(interval);
			};
		}
	});
</script>

{#if !userSession}
	<!-- LOGIN SCREEN -->
	<LoginPanel onlogin={handleLoginSuccess} apiBase={API_BASE} />
{:else}
	<!-- MAIN DASHBOARD -->
	<div class="min-h-screen bg-zinc-950 text-zinc-50 font-sans p-4 md:p-8 selection:bg-indigo-500 selection:text-white">
		<div class="max-w-7xl mx-auto space-y-8 animate-fadeIn">

			<!-- TOP HEADER -->
			<Header 
				user={userSession} 
				autoRefresh={autoRefresh} 
				onrefresh={fetchData} 
				onlogout={handleLogout}
				updateAutoRefresh={(val) => autoRefresh = val}
			/>

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
			<MetricCards systemStats={systemStats} />

			<!-- CHARTS & SETTINGS SECTION -->
			<section class="grid grid-cols-1 lg:grid-cols-3 gap-6">
				<!-- SVG Line Chart -->
				<div class="lg:col-span-2">
					<PerformanceChart statsHistory={statsHistory} />
				</div>

				<!-- Alert Configuration Panel -->
				<AlertSettingsPanel 
					cpuThreshold={settingsCpu} 
					ramThresholdMB={settingsRam} 
					discordBotToken={settingsDiscordToken} 
					discordChannelId={settingsDiscordChannelId} 
					onsave={handleSaveSettings}
				/>
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
							<ContainerCard 
								container={c} 
								whitelist={whitelist} 
								actionLoading={actionLoading} 
								copySuccess={copySuccess} 
								onaction={handleContainerAction} 
								ontoggleAutoHeal={handleToggleAutoHeal} 
								onopenLogs={openLogsModal} 
								oncopy={copyToClipboard}
							/>
						{/each}
					</div>
				{/if}
			</section>

			<!-- FOOTER -->
			<footer class="text-center py-8 border-t border-zinc-900 text-xs text-zinc-600 select-none">
				<p>© 2026 Server Sentinel. Cấu trúc dạng mô-đun Svelte components với SvelteKit 5 & Tailwind CSS v4.</p>
			</footer>
		</div>

		<!-- LOGS VIEWER MODAL -->
		{#if activeLogContainerId}
			<LogsModal 
				activeLogContainerId={activeLogContainerId} 
				activeLogContainerName={activeLogContainerName} 
				containerLogsText={containerLogsText} 
				isLogsLoading={isLogsLoading} 
				logLinesCount={logLinesCount} 
				onrefresh={() => fetchLogs(activeLogContainerId)} 
				onclose={closeLogsModal}
				updateLines={(lines) => {
					logLinesCount = lines;
					fetchLogs(activeLogContainerId);
				}}
			/>
		{/if}
	</div>
{/if}

<style>
	@keyframes fadeIn {
		from { opacity: 0; transform: translateY(8px); }
		to { opacity: 1; transform: translateY(0); }
	}
	.animate-fadeIn {
		animation: fadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
	}
</style>
