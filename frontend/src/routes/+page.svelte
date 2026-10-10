<script lang="ts">
	import { onMount } from 'svelte';
	import LoginPanel from '$lib/components/LoginPanel.svelte';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import Header from '$lib/components/Header.svelte';
	import MetricCards from '$lib/components/MetricCards.svelte';
	import PerformanceChart from '$lib/components/PerformanceChart.svelte';
	import AlertSettingsPanel from '$lib/components/AlertSettingsPanel.svelte';
	import ContainersTab from '$lib/components/ContainersTab.svelte';
	import ContainerCard from '$lib/components/ContainerCard.svelte';
	import LogsModal from '$lib/components/LogsModal.svelte';
	import FileManagerTab from '$lib/components/FileManagerTab.svelte';
	import FilePreviewModal from '$lib/components/FilePreviewModal.svelte';
	import QrScanModal from '$lib/components/QrScanModal.svelte';
	import TransferQueueFloatingButton from '$lib/components/TransferQueueFloatingButton.svelte';
	import HomeTab from '$lib/components/HomeTab.svelte';
	import { transferQueue } from '$lib/stores/transferQueue.svelte';

	const API_BASE = '/api';

	// Session state
	let userSession = $state<{ email: string; name: string; picture: string; token: string } | null>(null);

	// Navigation & Layout states
	let sidebarCollapsed = $state(false);
	let activeTab = $state('home'); // 'home' | 'overview' | 'containers' | 'settings' | 'files'
	let isQrScanModalOpen = $state(false);
	let notificationToast = $state('');

	// Reactive states (Svelte 5 runes)
	let containers = $state<any[]>([]);
	let systemStats = $state<any | null>(null);
	let whitelist = $state<string[]>([]);
	let searchQuery = $state('');
	let statusFilter = $state('all'); // 'all' | 'running' | 'exited'
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

	// File Preview States
	let activePreviewItem = $state<{ name: string; path: string; size: number } | null>(null);
	let activePreviewContent = $state('');
	let activePreviewUrl = $state('');
	let isFilePreviewLoading = $state(false);
	let filesInitialSearchQuery = $state('');
	let filesInitialPath = $state('');

	async function handleOpenFilePreview(item: { name: string; path: string; size: number }) {
		activePreviewItem = item;
		activePreviewContent = '';
		activePreviewUrl = `${API_BASE}/storage/raw?path=${encodeURIComponent(item.path)}&inline=true`;
		
		const ext = item.name.split('.').pop()?.toLowerCase() || '';
		const isMedia = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'ico', 'mp4', 'webm', 'ogg', 'mov', 'mkv', 'mp3', 'wav', 'pdf'].includes(ext);

		if (isMedia) {
			// Đối với media và PDF, render trực tiếp thẻ img, video, audio hoặc iframe
			isFilePreviewLoading = false;
			return;
		}

		// Đối với text/code, tải nội dung dạng text
		isFilePreviewLoading = true;
		try {
			const res = await fetch(`${API_BASE}/storage/preview?path=${encodeURIComponent(item.path)}`);
			const data = await res.json();
			if (!res.ok || data.status === 'error') {
				throw new Error(data.message || 'Không thể xem tệp này.');
			}
			activePreviewContent = data.content;
		} catch (err: any) {
			activePreviewContent = `Không thể xem trước tệp tin: ${err.message}`;
		} finally {
			isFilePreviewLoading = false;
		}
	}

	function handleDownloadFile(item: { name: string; path: string; size?: number }) {
		transferQueue.addDownload(item.name, item.path, item.size || 0, API_BASE);
		transferQueue.setOpen(true);
	}

	// Historical metrics stats
	let statsHistory = $state<any[]>([]);

	// Dynamic Alerts Configuration states (fetched from backend)
	let settingsCpu = $state(90.0);
	let settingsRam = $state(500);
	let settingsDiscordToken = $state('');
	let settingsDiscordChannelId = $state('');

	// Derived metrics
	let runningContainersCount = $derived(
		containers.filter((c) => {
			const state = (c.State || c.state || '').toLowerCase();
			return state === 'running';
		}).length
	);

	// Filtered containers
	let filteredContainers = $derived(
		containers.filter((c) => {
			const name = getContainerName(c).toLowerCase();
			const image = (c.Image || c.image || '').toLowerCase();
			const query = searchQuery.toLowerCase();
			const matchesSearch = name.includes(query) || image.includes(query);

			if (!matchesSearch) return false;

			const state = (c.State || c.state || '').toLowerCase();
			if (statusFilter === 'running') return state === 'running';
			if (statusFilter === 'exited') return state === 'exited';
			return true;
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
			errorMessage = `Lỗi kết nối với Backend Sentinel (${API_BASE}). Vui lòng kiểm tra server.`;
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
		// Tự động thu gọn trên màn hình nhỏ
		if (typeof window !== 'undefined' && window.innerWidth < 1024) {
			sidebarCollapsed = true;
		}
	});

	// Dynamic Polling Effect
	$effect(() => {
		if (userSession) {
			fetchData();
			let interval: any;
			if (autoRefresh) {
				interval = setInterval(fetchData, 5000);
			}
			return () => {
				if (interval) clearInterval(interval);
			};
		}
	});

	// Dynamic page title / subtitle
	let tabTitle = $derived.by(() => {
		if (activeTab === 'home') return 'Trang chủ & Trung tâm Tìm kiếm';
		if (activeTab === 'containers') return 'Quản lý Docker Containers';
		if (activeTab === 'files') return 'Quản lý Tệp tin & Thư mục Hệ thống';
		if (activeTab === 'settings') return 'Cấu hình Cảnh báo & Discord Bot';
		return 'Bảng điều khiển & Giám sát Hệ thống';
	});

	let tabSubtitle = $derived.by(() => {
		return '';
	});
</script>

{#if !userSession}
	<!-- LOGIN SCREEN -->
	<LoginPanel onlogin={handleLoginSuccess} apiBase={API_BASE} />
{:else}
	<!-- MAIN DASHBOARD WITH COLLAPSIBLE SIDEBAR LAYOUT -->
	<div class="flex h-screen bg-zinc-950 text-zinc-50 font-sans selection:bg-indigo-500 selection:text-white overflow-hidden">

		<!-- COLLAPSIBLE SIDEBAR & MOBILE BOTTOM BAR -->
		<Sidebar
			collapsed={sidebarCollapsed}
			activeTab={activeTab}
			user={userSession}
			containerCount={containers.length}
			runningCount={runningContainersCount}
			ontoggleCollapse={() => (sidebarCollapsed = !sidebarCollapsed)}
			onselectTab={(tab) => (activeTab = tab)}
			onlogout={handleLogout}
			onopenQrScan={() => (isQrScanModalOpen = true)}
		/>

		<!-- MAIN CONTENT WRAPPER -->
		<main class="flex-1 flex flex-col h-screen overflow-y-auto min-w-0 bg-gradient-to-b from-zinc-950 via-zinc-950 to-zinc-900/40 pb-20 md:pb-8">
			<div class="p-4 md:p-8 space-y-6 max-w-7xl w-full mx-auto animate-fadeIn">

				<!-- TOP DYNAMIC HEADER (Ẩn khi ở tab Home) -->
				{#if activeTab !== 'home'}
					<Header
						title={tabTitle}
						subtitle={tabSubtitle}
						isLoading={isLoading}
						onrefresh={fetchData}
					/>
				{/if}

				<!-- ERROR ALERT BANNER -->
				{#if errorMessage}
					<div class="bg-rose-950/40 border border-rose-800/80 text-rose-300 px-6 py-4 rounded-2xl flex items-start gap-3 shadow-xl backdrop-blur-md">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6 shrink-0 text-rose-400 mt-0.5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
						</svg>
						<div>
							<h4 class="font-bold text-rose-200">Lỗi Kết Nối Hệ Thống</h4>
							<p class="text-sm mt-0.5">{errorMessage}</p>
						</div>
					</div>
				{/if}

				<!-- TAB CONTENT: HOME (SEARCH & DASHBOARD) -->
				{#if activeTab === 'home'}
					<HomeTab 
						containers={containers} 
						systemStats={systemStats} 
						whitelist={whitelist}
						actionLoading={actionLoading}
						copySuccess={copySuccess}
						apiBase={API_BASE}
						onnavigateTab={(tab) => (activeTab = tab)}
						onaction={handleContainerAction}
						ontoggleAutoHeal={handleToggleAutoHeal}
						onopenLogs={openLogsModal}
						oncopy={copyToClipboard}
						onopenFilePreview={handleOpenFilePreview}
						ondownloadFile={handleDownloadFile}
						onnavigateFiles={(query, path) => {
							filesInitialSearchQuery = query || '';
							filesInitialPath = path || '';
							activeTab = 'files';
						}}
					/>
				{/if}

				<!-- TAB CONTENT: OVERVIEW -->
				{#if activeTab === 'overview'}
					<div class="space-y-6">
						<!-- SYSTEM METRICS SECTION -->
						<MetricCards systemStats={systemStats} />

						<!-- PERFORMANCE CHART & QUICK METRICS SUMMARY -->
						<section class="grid grid-cols-1 lg:grid-cols-3 gap-6">
							<div class="lg:col-span-2">
								<PerformanceChart statsHistory={statsHistory} />
							</div>

							<!-- Quick Container Health Card -->
							<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-6 shadow-xl flex flex-col justify-between">
								<div>
									<h3 class="font-bold text-zinc-200 text-lg flex items-center gap-2 mb-4">
										<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 text-indigo-400">
											<path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z" />
										</svg>
										Sức khỏe Container
									</h3>

									<div class="space-y-3.5">
										<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
											<div class="flex items-center gap-2.5">
												<span class="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
												<span class="text-xs font-semibold text-zinc-300">Đang hoạt động (Running)</span>
											</div>
											<span class="font-mono text-sm font-bold text-emerald-400">{runningContainersCount}</span>
										</div>

										<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
											<div class="flex items-center gap-2.5">
												<span class="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
												<span class="text-xs font-semibold text-zinc-300">Đã dừng / Crash (Exited)</span>
											</div>
											<span class="font-mono text-sm font-bold text-rose-400">{containers.length - runningContainersCount}</span>
										</div>

										<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
											<div class="flex items-center gap-2.5">
												<span class="w-2.5 h-2.5 rounded-full bg-indigo-400"></span>
												<span class="text-xs font-semibold text-zinc-300">Auto-Heal Bảo vệ</span>
											</div>
											<span class="font-mono text-sm font-bold text-indigo-400">{whitelist.length} Container</span>
										</div>
									</div>
								</div>

								<button
									onclick={() => (activeTab = 'containers')}
									class="mt-6 w-full py-2.5 px-4 rounded-xl bg-indigo-600/10 hover:bg-indigo-600 text-indigo-300 hover:text-white border border-indigo-500/20 text-xs font-bold transition-all flex items-center justify-center gap-2 cursor-pointer shadow-lg"
								>
									Xem toàn bộ containers
									<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
										<path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
									</svg>
								</button>
							</div>
						</section>
					</div>
				{/if}

				<!-- TAB CONTENT: CONTAINERS -->
				{#if activeTab === 'containers'}
					<section class="space-y-6">
						<ContainersTab 
							containers={containers}
							whitelist={whitelist}
							actionLoading={actionLoading}
							copySuccess={copySuccess}
							isLoading={isLoading}
							bind:searchQuery={searchQuery}
							bind:statusFilter={statusFilter}
							onaction={handleContainerAction}
							ontoggleAutoHeal={handleToggleAutoHeal}
							onopenLogs={openLogsModal}
							oncopy={copyToClipboard}
						/>
					</section>
				{/if}

				<!-- TAB CONTENT: FILES MANAGER -->
				{#if activeTab === 'files'}
					<section class="space-y-6">
						<FileManagerTab 
							apiBase={API_BASE} 
							initialSearchQuery={filesInitialSearchQuery}
							initialPath={filesInitialPath}
							onpreview={handleOpenFilePreview}
							ondownload={handleDownloadFile}
						/>
					</section>
				{/if}

				<!-- TAB CONTENT: SETTINGS -->
				{#if activeTab === 'settings'}
					<section class="max-w-2xl mx-auto py-2">
						<AlertSettingsPanel 
							cpuThreshold={settingsCpu} 
							ramThresholdMB={settingsRam} 
							discordBotToken={settingsDiscordToken} 
							discordChannelId={settingsDiscordChannelId} 
							onsave={handleSaveSettings}
						/>
					</section>
				{/if}

				<!-- FOOTER -->
				<footer class="text-center py-8 border-t border-zinc-900/80 text-xs text-zinc-600 select-none">
					<p>© 2026 Server Sentinel. Thiết kế trực quan & tối ưu hiệu năng với SvelteKit 5.</p>
				</footer>
			</div>
		</main>

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

		<!-- FILE PREVIEW MODAL (TOP-LEVEL) -->
		{#if activePreviewItem}
			<FilePreviewModal
				previewItem={activePreviewItem}
				previewContent={activePreviewContent}
				previewUrl={activePreviewUrl}
				isPreviewLoading={isFilePreviewLoading}
				onclose={() => (activePreviewItem = null)}
				ondownload={() => activePreviewItem && handleDownloadFile(activePreviewItem)}
			/>
		{/if}

		<!-- QR SCANNER MODAL (MOBILE) -->
		{#if isQrScanModalOpen && userSession}
			<QrScanModal
				user={userSession}
				apiBase={API_BASE}
				onclose={() => (isQrScanModalOpen = false)}
				onsuccess={(msg: string) => {
					notificationToast = msg;
					setTimeout(() => (notificationToast = ''), 4000);
				}}
			/>
		{/if}

		<!-- TOAST NOTIFICATION -->
		{#if notificationToast}
			<div class="fixed top-6 left-1/2 -translate-x-1/2 z-50 bg-emerald-500/90 text-white font-bold text-xs px-5 py-3 rounded-2xl shadow-2xl backdrop-blur-md border border-emerald-400 flex items-center gap-2 animate-bounce">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
				</svg>
				<span>{notificationToast}</span>
			</div>
		{/if}

		<!-- TRANSFER QUEUE FLOATING BUTTON -->
		<TransferQueueFloatingButton apiBase={API_BASE} />
	</div>
{/if}

<style>
	@keyframes fadeIn {
		from { opacity: 0; transform: translateY(8px); }
		to { opacity: 1; transform: translateY(0); }
	}
	.animate-fadeIn {
		animation: fadeIn 0.35s cubic-bezier(0.16, 1, 0.3, 1) forwards;
	}
</style>
