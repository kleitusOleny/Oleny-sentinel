<script lang="ts">
	interface Props {
		user: { email: string; name: string; picture: string; token: string } | null;
		autoRefresh: boolean;
		onrefresh: () => void;
		onlogout: () => void;
		updateAutoRefresh: (val: boolean) => void;
	}

	let { user, autoRefresh, onrefresh, onlogout, updateAutoRefresh }: Props = $props();
</script>

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

	<div class="flex flex-wrap items-center gap-4 w-full md:w-auto">
		<!-- User Profile -->
		{#if user}
			<div class="flex items-center gap-2.5 bg-zinc-950/60 border border-zinc-800/80 px-3 py-1.5 rounded-2xl">
				{#if user.picture}
					<img src={user.picture} alt={user.name} class="w-7 h-7 rounded-full border border-zinc-800 shadow" referrerpolicy="no-referrer" />
				{:else}
					<div class="w-7 h-7 rounded-full bg-indigo-600 text-white font-bold text-xs flex items-center justify-center border border-zinc-700">
						{user.name.charAt(0).toUpperCase()}
					</div>
				{/if}
				<div class="text-left leading-tight">
					<h4 class="text-xs font-extrabold text-zinc-200 max-w-[120px] truncate">{user.name}</h4>
					<p class="text-[10px] text-zinc-500 max-w-[120px] truncate">{user.email}</p>
				</div>
				<!-- Logout Button -->
				<button 
					onclick={onlogout}
					class="p-1 text-zinc-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition-colors cursor-pointer ml-1.5"
					title="Đăng xuất"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0 0 13.5 3h-6a2.25 2.25 0 0 0-2.25 2.25v13.5A2.25 2.25 0 0 0 7.5 21h6a2.25 2.25 0 0 0 2.25-2.25V15m3 0 3-3m0 0-3-3m3 3H9" />
					</svg>
				</button>
			</div>
		{/if}

		<div class="flex items-center gap-3">
			<!-- Toggle Auto Refresh -->
			<label class="flex items-center gap-2 text-sm text-zinc-400 cursor-pointer bg-zinc-900 border border-zinc-800 px-4 py-2 rounded-xl hover:border-zinc-700 select-none">
				<input 
					type="checkbox" 
					checked={autoRefresh} 
					onchange={(e) => updateAutoRefresh(e.currentTarget.checked)}
					class="rounded border-zinc-800 text-indigo-600 focus:ring-indigo-500 bg-zinc-950 w-4 h-4 cursor-pointer" 
				/>
				<span>Tự động tải (5s)</span>
			</label>

			<!-- Force Reload -->
			<button
				onclick={onrefresh}
				class="p-2.5 rounded-xl bg-indigo-600/10 text-indigo-400 border border-indigo-500/20 hover:bg-indigo-600 hover:text-white transition-all cursor-pointer"
				title="Tải lại ngay"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
					<path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
				</svg>
			</button>
		</div>
	</div>
</header>
