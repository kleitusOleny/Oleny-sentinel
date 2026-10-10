<script lang="ts">
	interface Props {
		collapsed: boolean;
		activeTab: string;
		user: { email: string; name: string; picture: string; token: string } | null;
		containerCount: number;
		runningCount: number;
		ontoggleCollapse: () => void;
		onselectTab: (tab: string) => void;
		onlogout: () => void;
		onopenQrScan?: () => void;
	}

	let {
		collapsed,
		activeTab,
		user,
		containerCount,
		runningCount,
		ontoggleCollapse,
		onselectTab,
		onlogout,
		onopenQrScan
	}: Props = $props();

	let isMobileDrawerOpen = $state(false);

	let navItems = $derived.by(() => [
		{
			id: 'home',
			label: 'Home',
			icon: `M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25`
		},
		{
			id: 'overview',
			label: 'Tổng quan & Metrics',
			icon: `M3.75 3v11.25A2.25 2.25 0 0 0 6 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0 1 18 16.5h-2.25m-7.5 0h7.5m-7.5 0-1 3m8.5-3 1 3m0 0 .5 1.5m-.5-1.5h-9.5m0 0-.5 1.5M9 11.25v1.5M12 9v3.75m3-6v6`
		},
		{
			id: 'containers',
			label: 'Docker Containers',
			badge: containerCount,
			icon: `M21 7.5l-9-5.25L3 7.5m18 0l-9 5.25m9-5.25v9l-9 5.25M3 7.5l9 5.25M3 7.5v9l9 5.25m0-9v9`
		},
		{
			id: 'terminal',
			label: 'Web Terminal',
			icon: `M6.75 7.5l3 2.25-3 2.25m4.5 0h3m-9 8.25h13.5A2.25 2.25 0 0021 18V6a2.25 2.25 0 00-2.25-2.25H4.5A2.25 2.25 0 002.25 6v12A2.25 2.25 0 004.5 20.25z`
		},
		{
			id: 'files',
			label: 'Quản lý Tệp tin',
			icon: `M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z`
		},
		{
			id: 'settings',
			label: 'Cấu hình Cảnh báo',
			icon: `M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 0 1 0 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 0 1 0-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z`
		}
	]);

	// Danh sách 3 mục chính trên bottom bar mobile
	const mobileMainTabs = ['home', 'containers', 'files'];

	function handleMobileSelect(tabId: string) {
		onselectTab(tabId);
		isMobileDrawerOpen = false;
	}
</script>

<!-- DESKTOP ASIDE SIDEBAR (Hidden on mobile) -->
<aside
	class="hidden md:flex transition-all duration-300 ease-in-out border-r border-zinc-800/80 bg-zinc-950/70 backdrop-blur-xl flex-col justify-between shrink-0 h-screen sticky top-0 z-30 select-none {collapsed ? 'w-20' : 'w-72'}"
>
	<!-- TOP BRAND & COLLAPSE TOGGLE -->
	<div class="p-4 border-b border-zinc-800/60">
		<div class="flex items-center {collapsed ? 'justify-center' : 'justify-between'} gap-3">
			{#if !collapsed}
				<div class="flex items-center gap-3 overflow-hidden">
					<div class="relative flex h-3.5 w-3.5 shrink-0">
						<span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
						<span class="relative inline-flex rounded-full h-3.5 w-3.5 bg-indigo-500"></span>
					</div>
					<div class="truncate">
						<span class="font-extrabold tracking-wider bg-gradient-to-r from-violet-400 via-indigo-400 to-cyan-400 bg-clip-text text-transparent text-base">
							SENTINEL
						</span>
						<span class="block text-[10px] text-zinc-500 uppercase tracking-widest font-semibold">Monitor Hub</span>
					</div>
				</div>
			{:else}
				<div class="relative flex h-3.5 w-3.5 shrink-0">
					<span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
					<span class="relative inline-flex rounded-full h-3.5 w-3.5 bg-indigo-500"></span>
				</div>
			{/if}

			<!-- Collapse Button -->
			<button
				onclick={ontoggleCollapse}
				class="p-2 text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800/80 rounded-xl transition-all cursor-pointer shrink-0"
				title={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu nhỏ thanh điều hướng'}
			>
				<svg
					xmlns="http://www.w3.org/2000/svg"
					fill="none"
					viewBox="0 0 24 24"
					stroke-width="2"
					stroke="currentColor"
					class="w-5 h-5 transition-transform duration-300 {collapsed ? 'rotate-180' : ''}"
				>
					<path stroke-linecap="round" stroke-linejoin="round" d="M18.75 19.5l-7.5-7.5 7.5-7.5m-6 15L5.25 12l7.5-7.5" />
				</svg>
			</button>
		</div>

		{#if !collapsed}
			<!-- Quick Status Pill -->
			<div class="mt-4 px-3 py-2 rounded-xl bg-zinc-900/60 border border-zinc-800/60 flex items-center justify-between text-xs">
				<span class="text-zinc-400 flex items-center gap-1.5">
					<span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
					Trạng thái
				</span>
				<span class="text-emerald-400 font-medium font-mono text-[11px]">
					{runningCount}/{containerCount} Online
				</span>
			</div>
		{/if}
	</div>

	<!-- NAVIGATION MENU -->
	<div class="p-3 space-y-1.5 flex-1 overflow-y-auto">
		{#if !collapsed}
			<div class="px-3 py-2 text-[10px] uppercase font-bold text-zinc-500 tracking-wider">
				Danh mục
			</div>
		{/if}

		{#each navItems as item}
			{@const isActive = activeTab === item.id}
			<button
				onclick={() => onselectTab(item.id)}
				class="w-full flex items-center gap-3.5 px-3.5 py-3 rounded-xl text-sm font-semibold transition-all cursor-pointer relative group {isActive ? 'bg-gradient-to-r from-indigo-600/20 to-violet-600/10 text-indigo-300 border border-indigo-500/30 shadow-lg shadow-indigo-950/40' : 'text-zinc-400 hover:text-zinc-100 hover:bg-zinc-900/60 border border-transparent'} {collapsed ? 'justify-center px-0' : ''}"
				title={collapsed ? item.label : undefined}
			>
				{#if item.id === 'containers'}
					<!-- Official Docker Whale Logo -->
					<svg
						viewBox="0 0 24 24"
						fill="currentColor"
						class="w-5 h-5 shrink-0 transition-transform group-hover:scale-110 {isActive ? 'text-[#0db7ed]' : 'text-zinc-400 group-hover:text-[#0db7ed]'}"
					>
						<path d="M13.983 11.078h2.119a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.119a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 00.186-.186V3.574a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 00.186-.186V6.29a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.887c0 .102.082.186.185.186m-2.93 0h2.12a.186.186 0 00.184-.186V6.29a.185.185 0 00-.185-.185H8.1a.185.185 0 00-.185.185v1.887c0 .102.083.186.185.186m-2.964 0h2.119a.186.186 0 00.185-.186V6.29a.185.185 0 00-.185-.185H5.136a.186.186 0 00-.186.185v1.887c0 .102.084.186.186.186m5.893 2.715h2.119a.186.186 0 00.186-.186V9.007a.186.186 0 00-.186-.186h-2.119a.186.186 0 00-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 00.184-.186V9.007a.185.185 0 00-.184-.186H8.1a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H5.136a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m-2.928 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H2.208a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m21.71 1.834c-.313-.234-.963-.48-1.803-.443-.139-.834-.693-1.503-1.393-1.782l-.468-.186-.29.417c-.506.726-.84 1.543-.996 2.378-.42.062-.84.135-1.258.219H1.47a.465.465 0 00-.46.52c.21 1.254.72 2.392 1.488 3.328 1.482 1.806 3.642 2.766 6.307 2.766 5.088 0 9.208-2.616 11.233-7.575.823-.07 2.05-.333 2.656-1.508l.192-.37-.478-.164z"/>
					</svg>
				{:else}
					<svg
						xmlns="http://www.w3.org/2000/svg"
						fill="none"
						viewBox="0 0 24 24"
						stroke-width="1.8"
						stroke="currentColor"
						class="w-5 h-5 shrink-0 transition-transform group-hover:scale-110 {isActive ? 'text-indigo-400' : 'text-zinc-400'}"
					>
						<path stroke-linecap="round" stroke-linejoin="round" d={item.icon} />
					</svg>
				{/if}

				{#if !collapsed}
					<span class="truncate flex-1 text-left">{item.label}</span>
					{#if item.badge !== undefined && item.badge > 0}
						<span
							class="text-[11px] font-mono font-semibold px-2 py-0.5 rounded-full {isActive ? 'bg-indigo-500/30 text-indigo-200' : 'bg-zinc-800 text-zinc-400'}"
						>
							{item.badge}
						</span>
					{/if}
				{/if}

				{#if isActive}
					<div class="absolute left-0 top-2 bottom-2 w-1 bg-indigo-500 rounded-r-full"></div>
				{/if}
			</button>
		{/each}
	</div>

	<!-- BOTTOM USER PROFILE & LOGOUT -->
	<div class="p-3 border-t border-zinc-800/60 bg-zinc-900/30">
		{#if user}
			<div class="flex items-center {collapsed ? 'justify-center' : 'justify-between'} gap-2.5">
				<div class="flex items-center gap-2.5 overflow-hidden">
					{#if user.picture}
						<img
							src={user.picture}
							alt={user.name}
							class="w-9 h-9 rounded-xl border border-zinc-700/80 shadow object-cover shrink-0"
							referrerpolicy="no-referrer"
						/>
					{:else}
						<div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 text-white font-extrabold text-sm flex items-center justify-center border border-indigo-400/30 shrink-0">
							{user.name.charAt(0).toUpperCase()}
						</div>
					{/if}

					{#if !collapsed}
						<div class="text-left leading-tight truncate">
							<h4 class="text-xs font-bold text-zinc-200 truncate">{user.name}</h4>
							<p class="text-[11px] text-zinc-500 truncate">{user.email}</p>
						</div>
					{/if}
				</div>

				<button
					onclick={onlogout}
					class="p-2 text-zinc-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-xl transition-all cursor-pointer shrink-0"
					title="Đăng xuất"
				>
					<svg
						xmlns="http://www.w3.org/2000/svg"
						fill="none"
						viewBox="0 0 24 24"
						stroke-width="2"
						stroke="currentColor"
						class="w-4 h-4"
					>
						<path
							stroke-linecap="round"
							stroke-linejoin="round"
							d="M15.75 9V5.25A2.25 2.25 0 0 0 13.5 3h-6a2.25 2.25 0 0 0-2.25 2.25v13.5A2.25 2.25 0 0 0 7.5 21h6a2.25 2.25 0 0 0 2.25-2.25V15m3 0 3-3m0 0-3-3m3 3H9"
						/>
					</svg>
				</button>
			</div>
		{/if}
	</div>
</aside>

<!-- MOBILE STREAMLINED BOTTOM NAVIGATION BAR (Gọn gàng với 4 nút chuẩn) -->
<nav
	class="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-zinc-950/95 backdrop-blur-xl border-t border-zinc-800/80 px-3 py-1.5 flex items-center justify-between select-none safe-area-pb shadow-2xl"
>
	<!-- 1. Home -->
	<button
		onclick={() => onselectTab('home')}
		class="flex flex-col items-center justify-center flex-1 py-1 px-1 rounded-xl transition-all cursor-pointer {activeTab === 'home' ? 'text-indigo-400 font-bold' : 'text-zinc-500 hover:text-zinc-300'}"
	>
		<div class="relative">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" class="w-5 h-5 transition-transform {activeTab === 'home' ? 'scale-110 text-indigo-400' : ''}">
				<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
			</svg>
		</div>
		<span class="text-[10px] mt-1">Home</span>
	</button>

	<!-- 2. Containers -->
	<button
		onclick={() => onselectTab('containers')}
		class="flex flex-col items-center justify-center flex-1 py-1 px-1 rounded-xl transition-all cursor-pointer {activeTab === 'containers' ? 'text-indigo-400 font-bold' : 'text-zinc-500 hover:text-zinc-300'}"
	>
		<div class="relative">
			<svg viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 transition-transform {activeTab === 'containers' ? 'scale-110 text-[#0db7ed]' : 'text-zinc-500'}">
				<path d="M13.983 11.078h2.119a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.119a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 00.186-.186V3.574a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 00.186-.186V6.29a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.887c0 .102.082.186.185.186m-2.93 0h2.12a.186.186 0 00.184-.186V6.29a.185.185 0 00-.185-.185H8.1a.185.185 0 00-.185.185v1.887c0 .102.083.186.185.186m-2.964 0h2.119a.186.186 0 00.185-.186V6.29a.185.185 0 00-.185-.185H5.136a.186.186 0 00-.186.185v1.887c0 .102.084.186.186.186m5.893 2.715h2.119a.186.186 0 00.186-.186V9.007a.186.186 0 00-.186-.186h-2.119a.186.186 0 00-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 00.184-.186V9.007a.185.185 0 00-.184-.186H8.1a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H5.136a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m-2.928 0h2.119a.185.185 0 00.185-.186V9.007a.185.185 0 00-.185-.186H2.208a.186.186 0 00-.186.185v1.888c0 .102.084.185.186.185m21.71 1.834c-.313-.234-.963-.48-1.803-.443-.139-.834-.693-1.503-1.393-1.782l-.468-.186-.29.417c-.506.726-.84 1.543-.996 2.378-.42.062-.84.135-1.258.219H1.47a.465.465 0 00-.46.52c.21 1.254.72 2.392 1.488 3.328 1.482 1.806 3.642 2.766 6.307 2.766 5.088 0 9.208-2.616 11.233-7.575.823-.07 2.05-.333 2.656-1.508l.192-.37-.478-.164z"/>
			</svg>
			{#if containerCount > 0}
				<span class="absolute -top-1 -right-2 bg-indigo-600 text-white text-[9px] font-bold px-1 rounded-full">
					{containerCount}
				</span>
			{/if}
		</div>
		<span class="text-[10px] mt-1">Docker</span>
	</button>

	<!-- 3. Files -->
	<button
		onclick={() => onselectTab('files')}
		class="flex flex-col items-center justify-center flex-1 py-1 px-1 rounded-xl transition-all cursor-pointer {activeTab === 'files' ? 'text-indigo-400 font-bold' : 'text-zinc-500 hover:text-zinc-300'}"
	>
		<div class="relative">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" class="w-5 h-5 transition-transform {activeTab === 'files' ? 'scale-110 text-indigo-400' : ''}">
				<path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z" />
			</svg>
		</div>
		<span class="text-[10px] mt-1">Files</span>
	</button>

	<!-- 4. MORE MENU BUTTON (Mở Bottom Sheet) -->
	<button
		onclick={() => (isMobileDrawerOpen = true)}
		class="flex flex-col items-center justify-center flex-1 py-1 px-1 rounded-xl transition-all cursor-pointer {['overview', 'terminal', 'settings'].includes(activeTab) || isMobileDrawerOpen ? 'text-indigo-400 font-bold' : 'text-zinc-500 hover:text-zinc-300'}"
	>
		<div class="relative">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
				<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
			</svg>
			{#if ['overview', 'terminal', 'settings'].includes(activeTab)}
				<span class="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-indigo-500 animate-pulse"></span>
			{/if}
		</div>
		<span class="text-[10px] mt-1">Thêm</span>
	</button>
</nav>

<!-- MOBILE MORE DRAWER / BOTTOM SHEET MODAL -->
{#if isMobileDrawerOpen}
	<!-- Backdrop -->
	<button
		type="button"
		aria-label="Đóng menu"
		onclick={() => (isMobileDrawerOpen = false)}
		class="md:hidden fixed inset-0 z-50 bg-black/60 backdrop-blur-sm transition-opacity w-full h-full border-0 p-0 m-0 cursor-default"
	></button>

	<!-- Bottom Drawer Panel -->
	<div
		class="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-zinc-950 border-t border-zinc-800 rounded-t-3xl p-5 shadow-2xl space-y-4 max-h-[85vh] overflow-y-auto animate-slideUp safe-area-pb"
	>
		<!-- Drag handle & Header -->
		<div class="flex items-center justify-between pb-2 border-b border-zinc-800/80">
			<div class="flex items-center gap-2.5">
				<div class="w-3 h-3 rounded-full bg-indigo-500"></div>
				<h3 class="font-bold text-sm text-zinc-200">Menu & Công Cụ Hệ Thống</h3>
			</div>
			<button
				onclick={() => (isMobileDrawerOpen = false)}
				class="p-1.5 rounded-lg text-zinc-400 hover:text-white bg-zinc-900 border border-zinc-800"
			>
				✕
			</button>
		</div>

		<!-- List Action Navigation -->
		<div class="grid grid-cols-2 gap-2.5">
			<!-- Overview -->
			<button
				onclick={() => handleMobileSelect('overview')}
				class="flex items-center gap-3 p-3 rounded-2xl border transition-all text-left {activeTab === 'overview' ? 'bg-indigo-600/20 border-indigo-500/50 text-indigo-300' : 'bg-zinc-900/60 border-zinc-800 text-zinc-300 hover:bg-zinc-800'}"
			>
				<span class="text-xl">📊</span>
				<div>
					<div class="text-xs font-bold">Tổng quan</div>
					<div class="text-[10px] text-zinc-500">CPU, RAM, Charts</div>
				</div>
			</button>

			<!-- Terminal -->
			<button
				onclick={() => handleMobileSelect('terminal')}
				class="flex items-center gap-3 p-3 rounded-2xl border transition-all text-left {activeTab === 'terminal' ? 'bg-indigo-600/20 border-indigo-500/50 text-indigo-300' : 'bg-zinc-900/60 border-zinc-800 text-zinc-300 hover:bg-zinc-800'}"
			>
				<span class="text-xl">💻</span>
				<div>
					<div class="text-xs font-bold">Web Terminal</div>
					<div class="text-[10px] text-zinc-500">Shell & Docker exec</div>
				</div>
			</button>

			<!-- Settings -->
			<button
				onclick={() => handleMobileSelect('settings')}
				class="flex items-center gap-3 p-3 rounded-2xl border transition-all text-left {activeTab === 'settings' ? 'bg-indigo-600/20 border-indigo-500/50 text-indigo-300' : 'bg-zinc-900/60 border-zinc-800 text-zinc-300 hover:bg-zinc-800'}"
			>
				<span class="text-xl">⚙️</span>
				<div>
					<div class="text-xs font-bold">Cài đặt</div>
					<div class="text-[10px] text-zinc-500">Cảnh báo Discord</div>
				</div>
			</button>

			<!-- QR Scanner -->
			{#if onopenQrScan}
				<button
					onclick={() => {
						isMobileDrawerOpen = false;
						onopenQrScan();
					}}
					class="flex items-center gap-3 p-3 rounded-2xl border border-zinc-800 bg-zinc-900/60 text-zinc-300 hover:bg-zinc-800 transition-all text-left"
				>
					<span class="text-xl">📱</span>
					<div>
						<div class="text-xs font-bold">Quét QR</div>
						<div class="text-[10px] text-zinc-500">Login desktop</div>
					</div>
				</button>
			{/if}
		</div>

		<!-- User Profile Card & Logout -->
		{#if user}
			<div class="pt-3 border-t border-zinc-800/80 flex items-center justify-between">
				<div class="flex items-center gap-2.5 overflow-hidden">
					{#if user.picture}
						<img src={user.picture} alt={user.name} class="w-8 h-8 rounded-xl object-cover border border-zinc-700" referrerpolicy="no-referrer" />
					{:else}
						<div class="w-8 h-8 rounded-xl bg-indigo-600 text-xs font-bold text-white flex items-center justify-center">
							{user.name.charAt(0) || 'U'}
						</div>
					{/if}
					<div class="truncate text-left">
						<div class="text-xs font-bold text-zinc-200 truncate">{user.name}</div>
						<div class="text-[10px] text-zinc-500 truncate">{user.email}</div>
					</div>
				</div>

				<button
					onclick={() => {
						isMobileDrawerOpen = false;
						onlogout();
					}}
					class="px-3 py-1.5 rounded-xl bg-rose-600/10 text-rose-400 hover:bg-rose-600 hover:text-white border border-rose-500/20 text-xs font-semibold transition-all cursor-pointer"
				>
					Đăng xuất
				</button>
			</div>
		{/if}
	</div>
{/if}
