<script lang="ts">
	import { onMount, onDestroy } from 'svelte';

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
		icon?: string;
		color?: string;
	}

	interface WidgetModule {
		id: string;
		title: string;
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
			// Sử dụng toạ độ mặc định (TP.HCM: 10.8231, 106.6297)
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
				} catch {
					// Fallback mặc định
				}
			}

			const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true`);
			if (res.ok) {
				const data = await res.json();
				if (data.current_weather) {
					weatherTemp = Math.round(data.current_weather.temperature);
					weatherCode = data.current_weather.weathercode;
				}
			}
		} catch {
			// Bỏ qua lỗi mạng thời tiết
		} finally {
			weatherLoading = false;
		}
	}

	// --- 3. SMART OMNI SEARCH BAR ---
	type SearchEngine = 'bing' | 'google' | 'containers' | 'files';
	let searchEngine = $state<SearchEngine>('bing');
	let searchQuery = $state('');
	let suggestions = $state<string[]>([]);
	let selectedSuggestionIndex = $state(-1);
	let isInputFocused = $state(false);
	let suggestDebounce: any;

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

	// Lọc container trực tiếp khi chọn chế độ tìm Containers
	let matchedContainers = $derived.by(() => {
		if (searchEngine !== 'containers' || !searchQuery.trim()) return [];
		const q = searchQuery.toLowerCase();
		return containers.filter((c) => {
			const name = (c.Names?.[0] || c.name || '').toLowerCase();
			const image = (c.Image || c.image || '').toLowerCase();
			return name.includes(q) || image.includes(q);
		});
	});

	// --- 4. SHORTCUTS / SERVICES MANAGEMENT ---
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
	let newShortcutTitle = $state('');
	let newShortcutUrl = $state('');
	let failedFaviconSet = $state<Set<string>>(new Set());

	// Hàm lấy URL favicon (.ico) từ Google Favicon Service hoặc trực tiếp từ domain
	function getFaviconUrl(rawUrl: string): string {
		try {
			const parsed = new URL(rawUrl);
			// Dùng dịch vụ favicon chuẩn của Google để lấy icon .ico/png 64px mượt mà
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

	function addShortcut() {
		if (!newShortcutTitle.trim() || !newShortcutUrl.trim()) return;
		let formattedUrl = newShortcutUrl.trim();
		if (!/^https?:\/\//i.test(formattedUrl)) {
			formattedUrl = 'https://' + formattedUrl;
		}

		shortcuts = [
			...shortcuts,
			{
				id: Date.now().toString(),
				title: newShortcutTitle.trim(),
				url: formattedUrl,
				color: 'from-indigo-600 to-violet-600'
			}
		];
		saveShortcuts();
		showAddShortcutModal = false;
		newShortcutTitle = '';
		newShortcutUrl = '';
	}

	function removeShortcut(id: string, e: MouseEvent) {
		e.stopPropagation();
		shortcuts = shortcuts.filter((s) => s.id !== id);
		saveShortcuts();
	}

	// --- 5. DRAGGABLE MODULAR WIDGETS ---
	const DEFAULT_MODULES: WidgetModule[] = [
		{ id: 'system_metrics', title: 'Chỉ số Máy chủ (System Specs)', visible: true, order: 0 },
		{ id: 'container_glance', title: 'Tình trạng Containers', visible: true, order: 1 },
		{ id: 'quick_scratchpad', title: 'Ghi chú Nhanh (Scratchpad)', visible: true, order: 2 }
	];

	let modules = $state<WidgetModule[]>([]);
	let draggedModuleId = $state<string | null>(null);
	let scratchpadText = $state('');

	function loadModules() {
		try {
			const saved = localStorage.getItem('sentinel_home_modules');
			if (saved) {
				modules = JSON.parse(saved);
			} else {
				modules = DEFAULT_MODULES;
			}
			const savedNotes = localStorage.getItem('sentinel_home_scratchpad');
			if (savedNotes) scratchpadText = savedNotes;
		} catch {
			modules = DEFAULT_MODULES;
		}
	}

	function saveModules() {
		try {
			localStorage.setItem('sentinel_home_modules', JSON.stringify(modules));
		} catch {}
	}

	function saveScratchpad() {
		try {
			localStorage.setItem('sentinel_home_scratchpad', scratchpadText);
		} catch {}
	}

	// Drag & Drop Handlers
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
			// Cập nhật lại số thứ tự
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
	<!-- 1. HEADER HERO: CLOCK & WEATHER WIDGET -->
	<div class="flex flex-col md:flex-row items-center justify-between gap-6 bg-gradient-to-br from-zinc-900/60 via-zinc-950/40 to-indigo-950/20 backdrop-blur-xl border border-zinc-800/80 rounded-3xl p-6 md:p-8 shadow-2xl relative overflow-hidden">
		<!-- Decorative Background Glow -->
		<div class="absolute -top-24 -left-24 w-72 h-72 bg-indigo-600/10 rounded-full blur-3xl pointer-events-none"></div>
		<div class="absolute -bottom-24 -right-24 w-72 h-72 bg-cyan-600/10 rounded-full blur-3xl pointer-events-none"></div>

		<!-- Time & Greeting -->
		<div class="text-center md:text-left z-10 space-y-1">
			<div class="font-mono font-black text-4xl sm:text-5xl md:text-6xl tracking-tight bg-gradient-to-r from-zinc-100 via-indigo-200 to-cyan-300 bg-clip-text text-transparent drop-shadow-sm">
				{currentTime || '00:00:00'}
			</div>
			<div class="text-xs sm:text-sm font-semibold text-zinc-400 capitalize flex items-center justify-center md:justify-start gap-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-indigo-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25m-18 0A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75m-18 0v-7.5A2.25 2.25 0 0 1 5.25 9h13.5A2.25 2.25 0 0 1 21 9v7.5" />
				</svg>
				{currentDate || 'Đang tải ngày...'}
			</div>
		</div>

		<!-- Live Weather Card -->
		<div class="z-10 flex items-center gap-4 bg-zinc-900/80 border border-zinc-800 px-5 py-3.5 rounded-2xl shadow-lg">
			{#if weatherLoading}
				<div class="flex items-center gap-2 text-zinc-400 text-xs font-mono animate-pulse">
					<span class="w-3 h-3 rounded-full border-2 border-indigo-400 border-t-transparent animate-spin"></span>
					Đang cập nhật thời tiết...
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
				<!-- Search Engine Dropdown / Toggle -->
				<div class="relative shrink-0">
					<select
						bind:value={searchEngine}
						class="bg-zinc-800 hover:bg-zinc-750 text-xs font-bold text-zinc-200 py-2.5 px-3 rounded-2xl border border-zinc-700/80 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer appearance-none pr-8 transition-colors"
					>
						<option value="bing">🌐 Bing</option>
						<option value="google">🔍 Google</option>
						<option value="containers">🐳 Containers</option>
						<option value="files">📁 Tệp tin</option>
					</select>
					<div class="absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none text-zinc-400">
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
							<path stroke-linecap="round" stroke-linejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
						</svg>
					</div>
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
								? 'Tìm kiếm trên Bing hoặc nhập địa chỉ web...'
								: searchEngine === 'google'
									? 'Tìm kiếm trên Google...'
									: searchEngine === 'containers'
										? 'Lọc nhanh Docker Container trong server...'
										: 'Tìm kiếm tệp tin lưu trữ...'
						}
						class="w-full bg-transparent text-sm sm:text-base text-zinc-100 placeholder-zinc-500 px-3 py-2 focus:outline-none"
					/>
				</div>

				<!-- Action Submit Button -->
				<button
					onclick={() => executeSearch()}
					class="shrink-0 p-3 bg-gradient-to-r from-indigo-600 to-cyan-600 hover:from-indigo-500 hover:to-cyan-500 text-white rounded-2xl font-bold cursor-pointer transition-all shadow-md shadow-indigo-950/50 hover:scale-105 active:scale-95"
					title="Tìm kiếm"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-5 h-5">
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

		<!-- Live Inline Matched Containers (khi chọn engine là containers) -->
		{#if searchEngine === 'containers' && searchQuery.trim()}
			<div class="bg-zinc-900/60 border border-zinc-800 rounded-2xl p-4 space-y-2">
				<div class="text-xs font-bold text-zinc-400">Kết quả tìm kiếm Container ({matchedContainers.length}):</div>
				{#if matchedContainers.length === 0}
					<div class="text-xs text-zinc-500 italic">Không tìm thấy container nào khớp với từ khoá.</div>
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

	<!-- 3. PERSONAL SHORTCUTS GRID -->
	<div class="space-y-3">
		<div class="flex items-center justify-between">
			<h2 class="text-xs font-bold text-zinc-400 uppercase tracking-wider flex items-center gap-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-indigo-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 0 1 1.242 7.244l-4.5 4.5a4.5 4.5 0 0 1-6.364-6.364l1.757-1.757m13.35-.622 1.757-1.757a4.5 4.5 0 0 0-6.364-6.364l-4.5 4.5a4.5 4.5 0 0 0 1.242 7.244" />
				</svg>
				Lối tắt & Dịch vụ Nhanh
			</h2>

			<button
				onclick={() => (showAddShortcutModal = true)}
				class="text-[11px] font-bold text-indigo-400 hover:text-indigo-300 flex items-center gap-1 cursor-pointer hover:underline"
			>
				+ Thêm lối tắt
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
					<!-- Delete button on hover -->
					<button
						onclick={(e) => removeShortcut(s.id, e)}
						class="absolute top-1.5 right-1.5 opacity-0 group-hover:opacity-100 p-1 text-zinc-500 hover:text-rose-400 rounded-md hover:bg-rose-500/10 transition-all cursor-pointer"
						title="Xóa lối tắt này"
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
			<div>
				<h2 class="text-xs font-bold text-zinc-400 uppercase tracking-wider flex items-center gap-2">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4 text-cyan-400">
						<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25a2.25 2.25 0 0 1-1.25-2.25V18v-2.25Z" />
					</svg>
					Module Tiện Ích Tùy Biến (Kéo Thả Sắp Xếp)
				</h2>
				<p class="text-[11px] text-zinc-500 mt-0.5">Giữ chuột vào phần tiêu đề để kéo thả đổi vị trí giữa các module</p>
			</div>

			<!-- Module Visibility Toggles -->
			<div class="flex items-center gap-1.5">
				{#each modules as m}
					<button
						onclick={() => toggleModuleVisibility(m.id)}
						class="px-2.5 py-1 rounded-lg text-[10px] font-bold border transition-all cursor-pointer {m.visible ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/40' : 'bg-zinc-900 text-zinc-500 border-zinc-800'}"
						title={m.visible ? `Ẩn ${m.title}` : `Hiện ${m.title}`}
					>
						{m.visible ? '✓' : '+'} {m.title.split(' ')[0]}
					</button>
				{/each}
			</div>
		</div>

		<!-- Drag-and-Drop Container Grid -->
		<div class="grid grid-cols-1 md:grid-cols-3 gap-6">
			{#each modules.filter((m) => m.visible) as m (m.id)}
				<div
					draggable="true"
					ondragstart={(e) => onDragStart(e, m.id)}
					ondragover={onDragOver}
					ondrop={(e) => onDrop(e, m.id)}
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
							title="Ẩn module này"
						>
							✕
						</button>
					</div>

					<!-- Module Body Rendering -->
					<div class="pt-4 flex-1">
						{#if m.id === 'system_metrics'}
							<!-- MODULE 1: SYSTEM METRICS GLANCE -->
							<div class="space-y-3">
								<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
									<span class="text-xs text-zinc-400">CPU Usage</span>
									<span class="font-mono text-sm font-bold {systemStats?.cpuUsage > 80 ? 'text-rose-400' : 'text-emerald-400'}">
										{systemStats?.cpuUsage !== undefined ? `${systemStats.cpuUsage.toFixed(1)}%` : '--'}
									</span>
								</div>

								<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
									<span class="text-xs text-zinc-400">Bộ nhớ RAM</span>
									<span class="font-mono text-sm font-bold text-cyan-400">
										{systemStats?.usedMemoryMB !== undefined ? `${systemStats.usedMemoryMB} MB` : '--'}
									</span>
								</div>

								<button
									onclick={() => onnavigateTab('overview')}
									class="w-full mt-2 py-2 rounded-xl bg-zinc-800/60 hover:bg-indigo-600/20 text-zinc-300 hover:text-indigo-300 text-xs font-bold transition-all border border-zinc-700/50 cursor-pointer"
								>
									Xem biểu đồ chi tiết →
								</button>
							</div>
						{:else if m.id === 'container_glance'}
							<!-- MODULE 2: CONTAINER HEALTH GLANCE -->
							<div class="space-y-3">
								<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
									<span class="text-xs text-zinc-400">Đang hoạt động</span>
									<span class="font-mono text-sm font-bold text-emerald-400">
										{containers.filter((c) => (c.State || c.state) === 'running').length} / {containers.length}
									</span>
								</div>

								<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/50 border border-zinc-800/60">
									<span class="text-xs text-zinc-400">Đã dừng / Crash</span>
									<span class="font-mono text-sm font-bold text-rose-400">
										{containers.filter((c) => (c.State || c.state) !== 'running').length}
									</span>
								</div>

								<button
									onclick={() => onnavigateTab('containers')}
									class="w-full mt-2 py-2 rounded-xl bg-zinc-800/60 hover:bg-indigo-600/20 text-zinc-300 hover:text-indigo-300 text-xs font-bold transition-all border border-zinc-700/50 cursor-pointer"
								>
									Quản lý Containers →
								</button>
							</div>
						{:else if m.id === 'quick_scratchpad'}
							<!-- MODULE 3: QUICK SCRATCHPAD NOTES -->
							<div class="space-y-2">
								<textarea
									bind:value={scratchpadText}
									oninput={saveScratchpad}
									placeholder="Ghi chú nhanh việc cần làm, lệnh terminal, IP..."
									class="w-full h-32 bg-zinc-950/60 border border-zinc-800 rounded-xl p-2.5 text-xs font-mono text-zinc-200 placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none"
								></textarea>
								<div class="text-[10px] text-zinc-500 text-right">Tự động lưu vào trình duyệt</div>
							</div>
						{/if}
					</div>
				</div>
			{/each}
		</div>
	</div>
</div>

<!-- MODAL: THÊM LỐI TẮT MỚI -->
{#if showAddShortcutModal}
	<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
		<div class="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 w-full max-w-sm space-y-4 shadow-2xl">
			<h3 class="text-base font-extrabold text-white">Thêm lối tắt dịch vụ mới</h3>

			<div class="space-y-3 text-xs">
				<div>
					<label class="block text-zinc-400 mb-1 font-semibold">Tên dịch vụ</label>
					<input
						type="text"
						bind:value={newShortcutTitle}
						placeholder="Ví dụ: Portainer, Grafana..."
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
					/>
				</div>

				<div>
					<label class="block text-zinc-400 mb-1 font-semibold">Địa chỉ URL</label>
					<input
						type="text"
						bind:value={newShortcutUrl}
						placeholder="http://100.94.177.113:9000 hoặc https://..."
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
					/>
				</div>

				<div class="text-[11px] text-zinc-500 italic">
					* Biểu tượng .ico sẽ được tự động trích xuất từ trang web. Nếu trang web không có, biểu tượng 🌐 sẽ được dùng làm mặc định.
				</div>
			</div>

			<div class="flex items-center justify-end gap-2 pt-2">
				<button
					onclick={() => (showAddShortcutModal = false)}
					class="px-3.5 py-1.5 rounded-xl bg-zinc-800 text-zinc-400 hover:text-white text-xs font-semibold cursor-pointer"
				>
					Hủy
				</button>
				<button
					onclick={addShortcut}
					class="px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold cursor-pointer shadow-lg shadow-indigo-950"
				>
					Thêm
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
