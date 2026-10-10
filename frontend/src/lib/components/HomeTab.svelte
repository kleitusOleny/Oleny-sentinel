<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import SystemMetricsModule from './module/SystemMetricsModule.svelte';
	import ContainerGlanceModule from './module/ContainerGlanceModule.svelte';
	import ScratchpadModule from './module/ScratchpadModule.svelte';

	interface ContainerItem {
		Id?: string;
		id?: string;
		Names?: string[];
		name?: string;
		Image?: string;
		image?: string;
		State?: string;
		state?: string;
		Status?: string;
		status?: string;
	}

	interface ShortcutItem {
		id: string;
		title: string;
		url: string;
		color?: string;
	}

	interface WidgetModule {
		id: string;
		title: string;
		description?: string;
		category?: string;
		icon?: string;
		visible: boolean;
		order: number;
	}

	interface Props {
		containers: ContainerItem[];
		systemStats: any | null;
		onnavigateTab: (tab: string) => void;
	}

	let { containers = [], systemStats = null, onnavigateTab }: Props = $props();

	// --- 1. CLOCK & DATE STATES ---
	let currentTime = $state('');
	let currentDate = $state('');
	let clockInterval: any;

	function updateClock() {
		const now = new Date();
		currentTime = now.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
		currentDate = now.toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' });
	}

	// --- 2. WEATHER WIDGET (Open-Meteo API) ---
	let weatherTemp = $state<number | null>(null);
	let weatherCode = $state<number | null>(null);
	let weatherCity = $state('TP. Hồ Chí Minh');
	let weatherLoading = $state(true);

	function getWeatherDesc(code: number | null): { text: string; icon: string } {
		if (code === null) return { text: 'Đang cập nhật', icon: '🌤️' };
		if (code === 0) return { text: 'Trời quang đãng', icon: '☀️' };
		if (code >= 1 && code <= 3) return { text: 'Ít mây / Mây rải rác', icon: '⛅' };
		if (code >= 45 && code <= 48) return { text: 'Có sương mù', icon: '🌫️' };
		if (code >= 51 && code <= 55) return { text: 'Mưa phùn nhẹ', icon: '🌦️' };
		if (code >= 61 && code <= 65) return { text: 'Mưa rào', icon: '🌧️' };
		if (code >= 71 && code <= 77) return { text: 'Có tuyết / mưa đá', icon: '🌨️' };
		if (code >= 80 && code <= 82) return { text: 'Mưa rào nặng hạt', icon: '🌧️' };
		if (code >= 95) return { text: 'Có dông sét', icon: '⛈️' };
		return { text: 'Thời tiết ổn định', icon: '🌤️' };
	}

	async function fetchWeather() {
		weatherLoading = true;
		try {
			let lat = 10.8231;
			let lon = 106.6297;

			if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
				try {
					const pos: any = await new Promise((res, rej) => {
						navigator.geolocation.getCurrentPosition(res, rej, { timeout: 3000 });
					});
					lat = pos.coords.latitude;
					lon = pos.coords.longitude;
					weatherCity = 'Vị trí hiện tại';
				} catch {}
			}

			const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true`);
			if (res.ok) {
				const data = await res.json();
				if (data.current_weather) {
					weatherTemp = Math.round(data.current_weather.temperature);
					weatherCode = data.current_weather.weathercode;
				}
			}
		} catch {} finally {
			weatherLoading = false;
		}
	}

	// --- 3. SMART OMNI SEARCH BAR ---
	type SearchEngine = 'bing' | 'google' | 'containers' | 'files';
	let searchEngine = $state<SearchEngine>('bing');
	let isEngineMenuOpen = $state(false);
	let searchQuery = $state('');
	let suggestions = $state<string[]>([]);
	let selectedSuggestionIndex = $state(-1);
	let isInputFocused = $state(false);
	let suggestDebounce: any;

	const searchEngines: { id: SearchEngine; label: string }[] = [
		{ id: 'bing', label: 'Bing' },
		{ id: 'google', label: 'Google' },
		{ id: 'containers', label: 'Docker Containers' },
		{ id: 'files', label: 'Files' }
	];

	function handleSearchInput() {
		selectedSuggestionIndex = -1;
		clearTimeout(suggestDebounce);

		if (!searchQuery.trim() || searchEngine === 'containers' || searchEngine === 'files') {
			suggestions = [];
			return;
		}

		suggestDebounce = setTimeout(async () => {
			try {
				const res = await fetch(`/api/search-suggest?q=${encodeURIComponent(searchQuery.trim())}`);
				if (res.ok) {
					suggestions = await res.json();
				}
			} catch {
				suggestions = [];
			}
		}, 200);
	}

	function executeSearch(queryToSearch?: string) {
		const q = (queryToSearch !== undefined ? queryToSearch : searchQuery).trim();
		if (!q) return;

		suggestions = [];

		if (searchEngine === 'bing') {
			window.open(`https://www.bing.com/search?q=${encodeURIComponent(q)}`, '_blank');
		} else if (searchEngine === 'google') {
			window.open(`https://www.google.com/search?q=${encodeURIComponent(q)}`, '_blank');
		} else if (searchEngine === 'containers') {
			onnavigateTab('containers');
		} else if (searchEngine === 'files') {
			onnavigateTab('files');
		}
	}

	function handleKeyDown(e: KeyboardEvent) {
		if (e.key === 'ArrowDown') {
			if (suggestions.length > 0) {
				e.preventDefault();
				selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.length;
			}
		} else if (e.key === 'ArrowUp') {
			if (suggestions.length > 0) {
				e.preventDefault();
				selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestions.length) % suggestions.length;
			}
		} else if (e.key === 'Enter') {
			e.preventDefault();
			if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < suggestions.length) {
				searchQuery = suggestions[selectedSuggestionIndex];
				executeSearch(suggestions[selectedSuggestionIndex]);
			} else {
				executeSearch();
			}
		} else if (e.key === 'Escape') {
			suggestions = [];
		}
	}

	let matchedContainers = $derived.by(() => {
		if (searchEngine !== 'containers' || !searchQuery.trim()) return [];
		const q = searchQuery.toLowerCase();
		return containers.filter((c) => {
			const name = (c.Names?.[0] || c.name || '').toLowerCase();
			const image = (c.Image || c.image || '').toLowerCase();
			return name.includes(q) || image.includes(q);
		});
	});

	// --- 4. SHORTCUTS MANAGEMENT ---
	const DEFAULT_SHORTCUTS: ShortcutItem[] = [
		{ id: '1', title: 'Portainer', url: 'http://100.94.177.113:9000', color: 'from-blue-600 to-cyan-500' },
		{ id: '2', title: 'GitHub', url: 'https://github.com', color: 'from-zinc-800 to-zinc-700' },
		{ id: '3', title: 'YouTube', url: 'https://youtube.com', color: 'from-red-600 to-rose-600' },
		{ id: '4', title: 'Cloudflare', url: 'https://dash.cloudflare.com', color: 'from-amber-500 to-orange-600' },
		{ id: '5', title: 'Plex / Media', url: 'http://100.94.177.113:32400', color: 'from-yellow-500 to-amber-600' },
		{ id: '6', title: 'Discord Web', url: 'https://discord.com/app', color: 'from-indigo-600 to-violet-600' }
	];

	let shortcuts = $state<ShortcutItem[]>([]);
	let showAddShortcutModal = $state(false);
	let editingShortcut = $state<ShortcutItem | null>(null);
	let newShortcutTitle = $state('');
	let newShortcutUrl = $state('');
	let failedFaviconSet = $state<Set<string>>(new Set());

	function getFaviconUrl(rawUrl: string): string {
		try {
			const parsed = new URL(rawUrl);
			return `https://www.google.com/s2/favicons?domain=${parsed.hostname}&sz=64`;
		} catch {
			return '';
		}
	}

	function handleFaviconError(id: string) {
		const updated = new Set(failedFaviconSet);
		updated.add(id);
		failedFaviconSet = updated;
	}

	function loadShortcuts() {
		try {
			const saved = localStorage.getItem('sentinel_home_shortcuts');
			if (saved) {
				shortcuts = JSON.parse(saved);
				return;
			}
		} catch {}
		shortcuts = DEFAULT_SHORTCUTS;
	}

	function saveShortcuts() {
		try {
			localStorage.setItem('sentinel_home_shortcuts', JSON.stringify(shortcuts));
		} catch {}
	}

	function saveOrUpdateShortcut() {
		if (!newShortcutTitle.trim() || !newShortcutUrl.trim()) return;
		let formattedUrl = newShortcutUrl.trim();
		if (!/^https?:\/\//i.test(formattedUrl)) {
			formattedUrl = 'https://' + formattedUrl;
		}

		if (editingShortcut) {
			shortcuts = shortcuts.map((s) =>
				s.id === editingShortcut!.id
					? { ...s, title: newShortcutTitle.trim(), url: formattedUrl }
					: s
			);
			editingShortcut = null;
		} else {
			shortcuts = [
				...shortcuts,
				{
					id: Date.now().toString(),
					title: newShortcutTitle.trim(),
					url: formattedUrl,
					color: 'from-indigo-600 to-violet-600'
				}
			];
		}

		saveShortcuts();
		showAddShortcutModal = false;
		newShortcutTitle = '';
		newShortcutUrl = '';
	}

	function openEditShortcut(s: ShortcutItem, e: MouseEvent) {
		e.preventDefault();
		e.stopPropagation();
		editingShortcut = s;
		newShortcutTitle = s.title;
		newShortcutUrl = s.url;
		showAddShortcutModal = true;
	}

	function removeShortcut(id: string, e: MouseEvent) {
		e.preventDefault();
		e.stopPropagation();
		shortcuts = shortcuts.filter((s) => s.id !== id);
		saveShortcuts();
	}

	// --- 5. DRAGGABLE MODULAR WIDGETS ---
	const DEFAULT_MODULES: WidgetModule[] = [
		{
			id: 'system_metrics',
			title: 'System Specs',
			description: 'Real-time CPU, RAM, Disk usage, Platform info and Server Uptime metrics.',
			category: 'Monitoring',
			icon: 'metrics',
			visible: true,
			order: 0
		},
		{
			id: 'container_glance',
			title: 'Docker Containers',
			description: 'Live overview of active Docker containers, health status, and quick inspect links.',
			category: 'Docker',
			icon: 'docker',
			visible: true,
			order: 1
		},
		{
			id: 'quick_scratchpad',
			title: 'Scratchpad',
			description: 'Instant local memo pad for server notes, IPs, commands or temporary ideas.',
			category: 'Utilities',
			icon: 'scratchpad',
			visible: true,
			order: 2
		}
	];

	let modules = $state<WidgetModule[]>([]);
	let draggedModuleId = $state<string | null>(null);
	let showModuleModal = $state(false);

	function loadModules() {
		try {
			const saved = localStorage.getItem('sentinel_home_modules');
			if (saved) {
				const parsed: WidgetModule[] = JSON.parse(saved);
				// Merge with DEFAULT_MODULES to guarantee updated titles, icons, descriptions
				modules = DEFAULT_MODULES.map((def, idx) => {
					const existing = parsed.find((p) => p.id === def.id);
					return existing
						? { ...def, visible: existing.visible, order: existing.order ?? idx }
						: def;
				}).sort((a, b) => a.order - b.order);
			} else {
				modules = DEFAULT_MODULES;
			}
		} catch {
			modules = DEFAULT_MODULES;
		}
	}

	function saveModules() {
		try {
			localStorage.setItem('sentinel_home_modules', JSON.stringify(modules));
		} catch {}
	}

	function onDragStart(e: DragEvent, id: string) {
		draggedModuleId = id;
		if (e.dataTransfer) {
			e.dataTransfer.effectAllowed = 'move';
			e.dataTransfer.setData('text/plain', id);
		}
	}

	function onDragOver(e: DragEvent) {
		e.preventDefault();
		if (e.dataTransfer) {
			e.dataTransfer.dropEffect = 'move';
		}
	}

	function onDrop(e: DragEvent, targetId: string) {
		e.preventDefault();
		if (!draggedModuleId || draggedModuleId === targetId) return;

		const currentIdx = modules.findIndex((m) => m.id === draggedModuleId);
		const targetIdx = modules.findIndex((m) => m.id === targetId);

		if (currentIdx >= 0 && targetIdx >= 0) {
			const updated = [...modules];
			const [moved] = updated.splice(currentIdx, 1);
			updated.splice(targetIdx, 0, moved);
			modules = updated.map((m, idx) => ({ ...m, order: idx }));
			saveModules();
		}
		draggedModuleId = null;
	}

	function toggleModuleVisibility(id: string) {
		modules = modules.map((m) => (m.id === id ? { ...m, visible: !m.visible } : m));
		saveModules();
	}

	onMount(() => {
		updateClock();
		clockInterval = setInterval(updateClock, 1000);
		fetchWeather();
		loadShortcuts();
		loadModules();
	});

	onDestroy(() => {
		clearInterval(clockInterval);
		clearTimeout(suggestDebounce);
	});
</script>

<div class="space-y-8 max-w-6xl mx-auto py-2 px-1 animate-fadeIn">
	<!-- 1. CLOCK & WEATHER WIDGET -->
	<div class="flex flex-col md:flex-row items-center justify-between gap-6 bg-gradient-to-br from-zinc-900/60 via-zinc-950/40 to-indigo-950/20 backdrop-blur-xl border border-zinc-800/80 rounded-3xl p-6 md:p-8 shadow-2xl relative overflow-hidden">
		<div class="absolute -top-24 -left-24 w-72 h-72 bg-indigo-600/10 rounded-full blur-3xl pointer-events-none"></div>
		<div class="absolute -bottom-24 -right-24 w-72 h-72 bg-cyan-600/10 rounded-full blur-3xl pointer-events-none"></div>

		<!-- Time & Date -->
		<div class="text-center md:text-left z-10 space-y-1">
			<div class="font-mono font-black text-4xl sm:text-5xl md:text-6xl tracking-tight bg-gradient-to-r from-zinc-100 via-indigo-200 to-cyan-300 bg-clip-text text-transparent drop-shadow-sm">
				{currentTime || '00:00:00'}
			</div>
			<div class="text-xs sm:text-sm font-semibold text-zinc-400 capitalize flex items-center justify-center md:justify-start gap-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-indigo-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25m-18 0A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75m-18 0v-7.5A2.25 2.25 0 0 1 5.25 9h13.5A2.25 2.25 0 0 1 21 9v7.5" />
				</svg>
				{currentDate || 'Loading...'}
			</div>
		</div>

		<!-- Live Weather Card -->
		<div class="z-10 flex items-center gap-4 bg-zinc-900/80 border border-zinc-800 px-5 py-3.5 rounded-2xl shadow-lg">
			{#if weatherLoading}
				<div class="flex items-center gap-2 text-zinc-400 text-xs font-mono animate-pulse">
					<span class="w-3 h-3 rounded-full border-2 border-indigo-400 border-t-transparent animate-spin"></span>
					Loading weather...
				</div>
			{:else}
				<div class="text-3xl select-none">
					{getWeatherDesc(weatherCode).icon}
				</div>
				<div>
					<div class="flex items-baseline gap-1.5">
						<span class="text-2xl font-black font-mono text-zinc-100">{weatherTemp !== null ? `${weatherTemp}°C` : '--'}</span>
						<span class="text-[11px] font-bold text-cyan-400">{weatherCity}</span>
					</div>
					<div class="text-[11px] text-zinc-400 font-medium">
						{getWeatherDesc(weatherCode).text}
					</div>
				</div>
			{/if}
		</div>
	</div>

	<!-- 2. SMART OMNI SEARCH SECTION -->
	<div class="space-y-3 relative z-20">
		<div class="relative bg-zinc-900/70 backdrop-blur-2xl border-2 border-zinc-700/80 focus-within:border-indigo-500 rounded-3xl p-2 shadow-2xl transition-all duration-300">
			<div class="flex items-center gap-2">
				<!-- Search Engine Dropdown / Toggle with Real SVG Logos -->
				<div class="relative shrink-0">
					<button
						type="button"
						onclick={() => (isEngineMenuOpen = !isEngineMenuOpen)}
						class="bg-zinc-800 hover:bg-zinc-750 text-xs font-bold text-zinc-200 py-2.5 px-3 rounded-2xl border border-zinc-700/80 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer flex items-center gap-2 transition-colors select-none shadow-sm"
						title="Choose search engine"
					>
						{#if searchEngine === 'bing'}
							<!-- Microsoft Bing Official Logo -->
							<svg class="w-4 h-4 shrink-0" viewBox="0 0 32 32">
								<defs>
									<linearGradient id="bingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
										<stop offset="0%" stop-color="#008AD7"/>
										<stop offset="100%" stop-color="#005B9E"/>
									</linearGradient>
								</defs>
								<path fill="url(#bingGrad)" d="M5.5 3v24.2l7.7 4.5 12.3-7.2-5.4-3.2-6.9 3.2V8.9l-7.7-5.9z"/>
								<path fill="#0083D0" d="M13.2 8.9v18.3l6.9-3.2 5.4 3.2V16.8L13.2 8.9z"/>
							</svg>
							<span>Bing</span>
						{:else if searchEngine === 'google'}
							<!-- Google Official Color Logo -->
							<svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24">
								<path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.8-2.4 3.65v3h3.88c2.28-2.1 3.665-5.2 3.665-9.09z"/>
								<path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.26v3.1C3.25 21.36 7.33 24 12 24z"/>
								<path fill="#FBBC05" d="M5.28 14.32c-.25-.72-.38-1.49-.38-2.32s.14-1.6.38-2.32V6.58H1.26C.46 8.17 0 9.97 0 12s.46 3.83 1.26 5.42l4.02-3.1z"/>
								<path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.25 2.64 1.26 6.58l4.02 3.1c.95-2.83 3.6-4.93 6.72-4.93z"/>
							</svg>
							<span>Google</span>
						{:else if searchEngine === 'containers'}
							<!-- Docker Official Whale Logo -->
							<svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="#0db7ed">
								<path d="M13.983 11.078h2.119a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.119a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 00.186-.186V3.574a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 00.186-.186V6.29a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.887c0 .102.082.186.185.186m-2.93 0h2.12a.186.186 0 00.184-.186V6.29a.185.185 0 00-.185-.185H8.1a.185.185 0 00-.185.185v1.887c0 .102.083.186.185.186m-2.964 0h2.119a.186.186 0 00.185-.186V6.29a.185.185 0 00-.185-.185H5.136a.186.186 0 00-.186.185v1.887c0 .102.084.186.186.186m5.893 2.715h2.119a.186.186 0 00.186-.186V9.007a.186.186 0 00-.186-.186h-2.119a.186.186 0 00-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 00.184-.186V9.007a.185.185 0 00-.184-.186H8.1a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H5.136a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m-2.928 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H2.208a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m21.71 1.834c-.313-.234-.963-.48-1.803-.443-.139-.834-.693-1.503-1.393-1.782l-.468-.186-.29.417c-.506.726-.84 1.543-.996 2.378-.42.062-.84.135-1.258.219H1.47a.465.465 0 00-.46.52c.21 1.254.72 2.392 1.488 3.328 1.482 1.806 3.642 2.766 6.307 2.766 5.088 0 9.208-2.616 11.233-7.575.823-.07 2.05-.333 2.656-1.508l.192-.37-.478-.164z"/>
							</svg>
							<span>Containers</span>
						{:else}
							<svg class="w-4 h-4 shrink-0 text-amber-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
								<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
							</svg>
							<span>Files</span>
						{/if}

						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3 h-3 text-zinc-400 transition-transform {isEngineMenuOpen ? 'rotate-180' : ''}">
							<path stroke-linecap="round" stroke-linejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
						</svg>
					</button>

					<!-- Custom Dropdown Menu with Authentic Logos -->
					{#if isEngineMenuOpen}
						<div class="absolute left-0 top-full mt-2 w-48 bg-zinc-900 border border-zinc-700/80 rounded-2xl shadow-2xl p-1.5 z-50 space-y-1 animate-fadeIn">
							<!-- Bing Option -->
							<button
								type="button"
								onclick={() => {
									searchEngine = 'bing';
									isEngineMenuOpen = false;
								}}
								class="w-full text-left px-3 py-2 rounded-xl text-xs flex items-center gap-2.5 transition-colors cursor-pointer {searchEngine === 'bing' ? 'bg-indigo-600/25 text-indigo-300 font-bold' : 'text-zinc-300 hover:bg-zinc-800 hover:text-white'}"
							>
								<svg class="w-4 h-4 shrink-0" viewBox="0 0 32 32">
									<defs>
										<linearGradient id="bingGradOpt" x1="0%" y1="0%" x2="100%" y2="100%">
											<stop offset="0%" stop-color="#008AD7"/>
											<stop offset="100%" stop-color="#005B9E"/>
										</linearGradient>
									</defs>
									<path fill="url(#bingGradOpt)" d="M5.5 3v24.2l7.7 4.5 12.3-7.2-5.4-3.2-6.9 3.2V8.9l-7.7-5.9z"/>
									<path fill="#0083D0" d="M13.2 8.9v18.3l6.9-3.2 5.4 3.2V16.8L13.2 8.9z"/>
								</svg>
								<span>Bing</span>
							</button>

							<!-- Google Option -->
							<button
								type="button"
								onclick={() => {
									searchEngine = 'google';
									isEngineMenuOpen = false;
								}}
								class="w-full text-left px-3 py-2 rounded-xl text-xs flex items-center gap-2.5 transition-colors cursor-pointer {searchEngine === 'google' ? 'bg-indigo-600/25 text-indigo-300 font-bold' : 'text-zinc-300 hover:bg-zinc-800 hover:text-white'}"
							>
								<svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24">
									<path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.8-2.4 3.65v3h3.88c2.28-2.1 3.665-5.2 3.665-9.09z"/>
									<path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.26v3.1C3.25 21.36 7.33 24 12 24z"/>
									<path fill="#FBBC05" d="M5.28 14.32c-.25-.72-.38-1.49-.38-2.32s.14-1.6.38-2.32V6.58H1.26C.46 8.17 0 9.97 0 12s.46 3.83 1.26 5.42l4.02-3.1z"/>
									<path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.25 2.64 1.26 6.58l4.02 3.1c.95-2.83 3.6-4.93 6.72-4.93z"/>
								</svg>
								<span>Google</span>
							</button>

							<!-- Docker Containers Option -->
							<button
								type="button"
								onclick={() => {
									searchEngine = 'containers';
									isEngineMenuOpen = false;
								}}
								class="w-full text-left px-3 py-2 rounded-xl text-xs flex items-center gap-2.5 transition-colors cursor-pointer {searchEngine === 'containers' ? 'bg-indigo-600/25 text-indigo-300 font-bold' : 'text-zinc-300 hover:bg-zinc-800 hover:text-white'}"
							>
								<svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="#0db7ed">
									<path d="M13.983 11.078h2.119a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.119a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 00.186-.186V3.574a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 00.186-.186V6.29a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.887c0 .102.082.186.185.186m-2.93 0h2.12a.186.186 0 00.184-.186V6.29a.185.185 0 00-.185-.185H8.1a.185.185 0 00-.185.185v1.887c0 .102.083.186.185.186m-2.964 0h2.119a.186.186 0 00.185-.186V6.29a.185.185 0 00-.185-.185H5.136a.186.186 0 00-.186.185v1.887c0 .102.084.186.186.186m5.893 2.715h2.119a.186.186 0 00.186-.186V9.007a.186.186 0 00-.186-.186h-2.119a.186.186 0 00-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 00.184-.186V9.007a.185.185 0 00-.184-.186H8.1a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H5.136a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m-2.928 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H2.208a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m21.71 1.834c-.313-.234-.963-.48-1.803-.443-.139-.834-.693-1.503-1.393-1.782l-.468-.186-.29.417c-.506.726-.84 1.543-.996 2.378-.42.062-.84.135-1.258.219H1.47a.465.465 0 00-.46.52c.21 1.254.72 2.392 1.488 3.328 1.482 1.806 3.642 2.766 6.307 2.766 5.088 0 9.208-2.616 11.233-7.575.823-.07 2.05-.333 2.656-1.508l.192-.37-.478-.164z"/>
								</svg>
								<span>Docker Containers</span>
							</button>

							<!-- Files Option -->
							<button
								type="button"
								onclick={() => {
									searchEngine = 'files';
									isEngineMenuOpen = false;
								}}
								class="w-full text-left px-3 py-2 rounded-xl text-xs flex items-center gap-2.5 transition-colors cursor-pointer {searchEngine === 'files' ? 'bg-indigo-600/25 text-indigo-300 font-bold' : 'text-zinc-300 hover:bg-zinc-800 hover:text-white'}"
							>
								<svg class="w-4 h-4 shrink-0 text-amber-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
								</svg>
								<span>Files</span>
							</button>
						</div>
					{/if}
				</div>

				<!-- Main Input Field -->
				<div class="relative flex-1">
					<input
						type="text"
						bind:value={searchQuery}
						oninput={handleSearchInput}
						onkeydown={handleKeyDown}
						onfocus={() => (isInputFocused = true)}
						placeholder={
							searchEngine === 'bing'
								? 'Search on Bing or enter URL...'
								: searchEngine === 'google'
									? 'Search on Google...'
									: searchEngine === 'containers'
										? 'Filter Docker Containers...'
										: 'Search storage files...'
						}
						class="w-full bg-transparent text-sm sm:text-base text-zinc-100 placeholder-zinc-500 px-3 py-2 focus:outline-none"
					/>
				</div>

				<!-- Action Submit Button (Same background as search bar) -->
				<button
					onclick={() => executeSearch()}
					class="shrink-0 p-3 bg-transparent hover:bg-zinc-800 text-zinc-400 hover:text-white rounded-2xl cursor-pointer transition-colors"
					title="Search"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
					</svg>
				</button>
			</div>

			<!-- Autocomplete Suggestions Dropdown -->
			{#if suggestions.length > 0 && isInputFocused}
				<div class="absolute left-0 right-0 top-full mt-2 bg-zinc-900/95 backdrop-blur-2xl border border-zinc-700/80 rounded-2xl shadow-2xl overflow-hidden py-1.5 z-50">
					{#each suggestions as item, idx}
						<button
							onclick={() => {
								searchQuery = item;
								executeSearch(item);
							}}
							class="w-full text-left px-4 py-2.5 text-xs sm:text-sm text-zinc-300 hover:text-white flex items-center justify-between transition-colors cursor-pointer {selectedSuggestionIndex === idx ? 'bg-indigo-600/30 text-indigo-300 font-semibold' : 'hover:bg-zinc-800/80'}"
						>
							<div class="flex items-center gap-3 truncate">
								<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-zinc-500">
									<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
								</svg>
								<span class="truncate">{item}</span>
							</div>
							<span class="text-[10px] text-zinc-500 font-mono">Bing</span>
						</button>
					{/each}
				</div>
			{/if}
		</div>

		<!-- Live Inline Matched Containers -->
		{#if searchEngine === 'containers' && searchQuery.trim()}
			<div class="bg-zinc-900/60 border border-zinc-800 rounded-2xl p-4 space-y-2">
				<div class="text-xs font-bold text-zinc-400">Containers ({matchedContainers.length}):</div>
				{#if matchedContainers.length === 0}
					<div class="text-xs text-zinc-500 italic">No matching containers found.</div>
				{:else}
					<div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
						{#each matchedContainers as c}
							<button
								onclick={() => onnavigateTab('containers')}
								class="text-left p-3 rounded-xl bg-zinc-950/60 border border-zinc-800 hover:border-indigo-500/60 transition-all cursor-pointer group"
							>
								<div class="text-xs font-bold text-zinc-200 group-hover:text-indigo-400 truncate">
									{(c.Names?.[0] || c.name || '').replace(/^\//, '')}
								</div>
								<div class="text-[10px] font-mono text-zinc-500 truncate mt-1">
									{c.Image || c.image || ''}
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}
	</div>

	<!-- 3. SHORTCUTS GRID -->
	<div class="space-y-3">
		<div class="flex items-center justify-between">
			<h2 class="text-xs font-bold text-zinc-400 uppercase tracking-wider flex items-center gap-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-indigo-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 0 1 1.242 7.244l-4.5 4.5a4.5 4.5 0 0 1-6.364-6.364l1.757-1.757m13.35-.622 1.757-1.757a4.5 4.5 0 0 0-6.364-6.364l-4.5 4.5a4.5 4.5 0 0 0 1.242 7.244" />
				</svg>
				Shortcut
			</h2>

			<button
				onclick={() => {
					editingShortcut = null;
					newShortcutTitle = '';
					newShortcutUrl = '';
					showAddShortcutModal = true;
				}}
				class="text-[11px] font-bold text-indigo-400 hover:text-indigo-300 flex items-center gap-1 cursor-pointer hover:underline"
			>
				+ Add Shortcut
			</button>
		</div>

		<div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3.5">
			{#each shortcuts as s (s.id)}
				<a
					href={s.url}
					target="_blank"
					rel="noreferrer"
					class="group relative flex flex-col items-center justify-center p-4 rounded-2xl bg-zinc-900/40 hover:bg-zinc-800/60 border border-zinc-800/80 hover:border-indigo-500/50 transition-all duration-200 hover:-translate-y-1 shadow-md"
				>
					<!-- 3-Dots Edit Button on top-left -->
					<button
						onclick={(e) => openEditShortcut(s, e)}
						class="absolute top-1.5 left-1.5 opacity-0 group-hover:opacity-100 p-1 text-zinc-400 hover:text-white rounded-md hover:bg-zinc-700/80 transition-all cursor-pointer"
						title="Edit Shortcut"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 12a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0ZM12.75 12a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0ZM18.75 12a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
						</svg>
					</button>

					<!-- Delete button on top-right -->
					<button
						onclick={(e) => removeShortcut(s.id, e)}
						class="absolute top-1.5 right-1.5 opacity-0 group-hover:opacity-100 p-1 text-zinc-500 hover:text-rose-400 rounded-md hover:bg-rose-500/10 transition-all cursor-pointer"
						title="Delete Shortcut"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
						</svg>
					</button>

					<div class="w-12 h-12 rounded-2xl flex items-center justify-center text-2xl bg-zinc-800/80 border border-zinc-700/60 shadow-lg shadow-black/40 group-hover:scale-110 transition-transform overflow-hidden p-2">
						{#if !failedFaviconSet.has(s.id) && getFaviconUrl(s.url)}
							<img
								src={getFaviconUrl(s.url)}
								alt={s.title}
								onerror={() => handleFaviconError(s.id)}
								class="w-7 h-7 object-contain rounded"
								loading="lazy"
							/>
						{:else}
							<span class="select-none text-2xl">🌐</span>
						{/if}
					</div>
					<span class="mt-2.5 text-xs font-bold text-zinc-300 group-hover:text-white truncate max-w-full text-center">
						{s.title}
					</span>
				</a>
			{/each}
		</div>
	</div>

	<!-- 4. MODULAR DRAGGABLE WIDGETS -->
	<div class="space-y-4">
		<div class="flex items-center justify-between border-t border-zinc-800/80 pt-6">
			<h2 class="text-xs font-bold text-zinc-400 uppercase tracking-wider flex items-center gap-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-cyan-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25a2.25 2.25 0 0 1-1.25-2.25V18v-2.25Z" />
				</svg>
				Module
			</h2>

			<!-- Add Module Popup Modal Button -->
			<button
				onclick={() => (showModuleModal = true)}
				class="px-3 py-2 rounded-xl bg-zinc-800/80 hover:bg-zinc-700 text-zinc-200 hover:text-white border border-zinc-700/60 text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer shadow-sm hover:shadow-cyan-950/40"
				title="Add / Configure Modules"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4 text-cyan-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
				</svg>
				<span>Add Module</span>
			</button>
		</div>

		<!-- Drag-and-Drop Container Grid -->
		<div class="grid grid-cols-1 md:grid-cols-3 gap-6">
			{#each modules.filter((m) => m.visible) as m (m.id)}
				<div
					draggable="true"
					ondragstart={(e) => onDragStart(e, m.id)}
					ondragover={onDragOver}
					ondrop={(e) => onDrop(e, m.id)}
					role="region"
					aria-label={m.title}
					class="bg-zinc-900/40 backdrop-blur-xl border border-zinc-800/80 hover:border-zinc-700 rounded-3xl p-5 shadow-xl transition-all duration-200 flex flex-col justify-between {draggedModuleId === m.id ? 'opacity-40 border-dashed border-indigo-400' : ''}"
				>
					<!-- Module Header (Drag Handle) -->
					<div class="flex items-center justify-between pb-3 border-b border-zinc-800/60 cursor-grab active:cursor-grabbing select-none">
						<div class="flex items-center gap-2">
							<span class="text-zinc-600 hover:text-zinc-400">⠿</span>
							<h3 class="text-xs font-bold text-zinc-200">{m.title}</h3>
						</div>
						<button
							onclick={() => toggleModuleVisibility(m.id)}
							class="text-zinc-500 hover:text-zinc-300 text-xs cursor-pointer p-1"
							title="Hide Module"
						>
							✕
						</button>
					</div>

					<!-- Module Body (Tách riêng component trong thư mục module) -->
					<div class="pt-4 flex-1">
						{#if m.id === 'system_metrics'}
							<SystemMetricsModule {systemStats} onnavigate={onnavigateTab} />
						{:else if m.id === 'container_glance'}
							<ContainerGlanceModule {containers} onnavigate={onnavigateTab} />
						{:else if m.id === 'quick_scratchpad'}
							<ScratchpadModule />
						{/if}
					</div>
				</div>
			{/each}
		</div>
	</div>
</div>

<!-- MODAL: ADD / EDIT SHORTCUT -->
{#if showAddShortcutModal}
	<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
		<div class="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 w-full max-w-sm space-y-4 shadow-2xl">
			<h3 class="text-base font-extrabold text-white">
				{editingShortcut ? 'Edit Shortcut' : 'Add Shortcut'}
			</h3>

			<div class="space-y-3 text-xs">
				<div>
					<label for="shortcut-name-input" class="block text-zinc-400 mb-1 font-semibold">Name</label>
					<input
						id="shortcut-name-input"
						type="text"
						bind:value={newShortcutTitle}
						placeholder="Example: Portainer, GitHub..."
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
					/>
				</div>

				<div>
					<label for="shortcut-url-input" class="block text-zinc-400 mb-1 font-semibold">URL</label>
					<input
						id="shortcut-url-input"
						type="text"
						bind:value={newShortcutUrl}
						placeholder="http://100.94.177.113:9000 or https://..."
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
					/>
				</div>

				<div class="text-[11px] text-zinc-500 italic">
					* Favicon (.ico) is automatically fetched from the URL. Falls back to 🌐 if not found.
				</div>
			</div>

			<div class="flex items-center justify-end gap-2 pt-2">
				<button
					onclick={() => {
						showAddShortcutModal = false;
						editingShortcut = null;
					}}
					class="px-3.5 py-1.5 rounded-xl bg-zinc-800 text-zinc-400 hover:text-white text-xs font-semibold cursor-pointer"
				>
					Cancel
				</button>
				<button
					onclick={saveOrUpdateShortcut}
					class="px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold cursor-pointer shadow-lg shadow-indigo-950"
				>
					{editingShortcut ? 'Save' : 'Add'}
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- MODULE SELECTION & MANAGEMENT MODAL -->
{#if showModuleModal}
	<div class="fixed inset-0 z-50 bg-black/75 backdrop-blur-md flex items-center justify-center p-4">
		<div class="bg-zinc-900 border border-zinc-800 rounded-3xl w-full max-w-xl p-6 shadow-2xl relative space-y-5 animate-fadeIn">
			<!-- Modal Header -->
			<div class="flex items-center justify-between pb-4 border-b border-zinc-800/80">
				<div class="flex items-center gap-3">
					<div class="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25a2.25 2.25 0 0 1-1.25-2.25V18v-2.25Z" />
						</svg>
					</div>
					<div>
						<h3 class="text-base font-extrabold text-zinc-100">Module Hub</h3>
						<p class="text-xs text-zinc-400 mt-0.5">Select modules to display on your dashboard</p>
					</div>
				</div>

				<button
					onclick={() => (showModuleModal = false)}
					class="p-2 text-zinc-400 hover:text-white rounded-xl hover:bg-zinc-800 transition-colors cursor-pointer"
					title="Close"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
					</svg>
				</button>
			</div>

			<!-- Modules List -->
			<div class="space-y-3 max-h-[60vh] overflow-y-auto pr-1">
				{#each modules as m (m.id)}
					<div class="p-4 rounded-2xl border transition-all duration-200 flex items-center justify-between gap-4 {m.visible ? 'bg-indigo-600/10 border-indigo-500/40' : 'bg-zinc-950/60 border-zinc-800/80 hover:border-zinc-700'}">
						<div class="flex items-start gap-3.5">
							<div class="w-10 h-10 rounded-xl bg-zinc-800/80 border border-zinc-700/50 flex items-center justify-center shrink-0 p-2">
								{#if m.id === 'container_glance'}
									<!-- Real Docker Logo -->
									<svg class="w-6 h-6" viewBox="0 0 24 24" fill="#0db7ed">
										<path d="M13.983 11.078h2.119a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.119a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 00.186-.186V3.574a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 00.186-.186V6.29a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.887c0 .102.082.186.185.186m-2.93 0h2.12a.186.186 0 00.184-.186V6.29a.185.185 0 00-.185-.185H8.1a.185.185 0 00-.185.185v1.887c0 .102.083.186.185.186m-2.964 0h2.119a.186.186 0 00.185-.186V6.29a.185.185 0 00-.185-.185H5.136a.186.186 0 00-.186.185v1.887c0 .102.084.186.186.186m5.893 2.715h2.119a.186.186 0 00.186-.186V9.007a.186.186 0 00-.186-.186h-2.119a.186.186 0 00-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 00.184-.186V9.007a.185.185 0 00-.184-.186H8.1a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H5.136a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m-2.928 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H2.208a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m21.71 1.834c-.313-.234-.963-.48-1.803-.443-.139-.834-.693-1.503-1.393-1.782l-.468-.186-.29.417c-.506.726-.84 1.543-.996 2.378-.42.062-.84.135-1.258.219H1.47a.465.465 0 00-.46.52c.21 1.254.72 2.392 1.488 3.328 1.482 1.806 3.642 2.766 6.307 2.766 5.088 0 9.208-2.616 11.233-7.575.823-.07 2.05-.333 2.656-1.508l.192-.37-.478-.164z"/>
									</svg>
								{:else if m.id === 'system_metrics'}
									<!-- Real System Metrics SVG -->
									<svg class="w-5 h-5 text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75ZM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V8.625ZM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V4.125Z" />
									</svg>
								{:else}
									<!-- Real Scratchpad SVG -->
									<svg class="w-5 h-5 text-amber-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L10.582 16.07a4.5 4.5 0 0 1-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0 1 15.75 21H5.25A2.25 2.25 0 0 1 3 18.75V8.25A2.25 2.25 0 0 1 5.25 6H10" />
									</svg>
								{/if}
							</div>
							<div>
								<div class="flex items-center gap-2">
									<h4 class="text-sm font-bold text-zinc-100">{m.title}</h4>
									{#if m.category}
										<span class="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-zinc-800 text-zinc-400 border border-zinc-700/50">
											{m.category}
										</span>
									{/if}
									{#if m.visible}
										<span class="px-2 py-0.5 text-[10px] font-bold rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
											Active
										</span>
									{/if}
								</div>
								<p class="text-xs text-zinc-400 mt-1 leading-relaxed">
									{m.description || 'Custom widget module for your home dashboard.'}
								</p>
							</div>
						</div>

						<!-- Toggle Action Button -->
						<button
							onclick={() => toggleModuleVisibility(m.id)}
							class="shrink-0 px-3.5 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 {m.visible ? 'bg-rose-500/15 hover:bg-rose-500/25 text-rose-300 border border-rose-500/30' : 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-950'}"
						>
							{#if m.visible}
								<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
									<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
								</svg>
								<span>Remove</span>
							{:else}
								<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
									<path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
								</svg>
								<span>Enable</span>
							{/if}
						</button>
					</div>
				{/each}
			</div>

			<!-- Modal Footer -->
			<div class="flex items-center justify-between pt-3 border-t border-zinc-800/80 text-xs">
				<span class="text-zinc-500">
					{modules.filter((m) => m.visible).length} of {modules.length} modules enabled
				</span>
				<button
					onclick={() => (showModuleModal = false)}
					class="px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-100 text-xs font-bold cursor-pointer transition-colors"
				>
					Done
				</button>
			</div>
		</div>
	</div>
{/if}

<style>
	@keyframes fadeIn {
		from { opacity: 0; transform: translateY(6px); }
		to { opacity: 1; transform: translateY(0); }
	}
	.animate-fadeIn {
		animation: fadeIn 0.25s ease-out forwards;
	}
</style>
