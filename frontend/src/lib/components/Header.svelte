<script lang="ts">
	interface Props {
		title: string;
		subtitle: string;
		autoRefresh: boolean;
		isLoading: boolean;
		onrefresh: () => void;
		updateAutoRefresh: (val: boolean) => void;
	}

	let { title, subtitle, autoRefresh, isLoading, onrefresh, updateAutoRefresh }: Props = $props();
</script>

<header class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-zinc-900/40 backdrop-blur-md border border-zinc-800/80 rounded-2xl p-5 shadow-xl">
	<div>
		<h1 class="text-xl sm:text-2xl font-extrabold tracking-tight text-white flex items-center gap-2.5">
			{title}
		</h1>
		<p class="text-zinc-400 text-xs sm:text-sm mt-0.5">{subtitle}</p>
	</div>

	<div class="flex items-center gap-3 w-full sm:w-auto justify-end">
		<!-- Auto Refresh Checkbox -->
		<label class="flex items-center gap-2 text-xs sm:text-sm text-zinc-400 cursor-pointer bg-zinc-900 border border-zinc-800 px-3.5 py-2 rounded-xl hover:border-zinc-700 select-none">
			<input 
				type="checkbox" 
				checked={autoRefresh} 
				onchange={(e) => updateAutoRefresh(e.currentTarget.checked)}
				class="rounded border-zinc-800 text-indigo-600 focus:ring-indigo-500 bg-zinc-950 w-4 h-4 cursor-pointer" 
			/>
			<span>Tự động cập nhật (5s)</span>
		</label>

		<!-- Force Refresh Button -->
		<button
			onclick={onrefresh}
			disabled={isLoading}
			class="p-2.5 rounded-xl bg-indigo-600/10 text-indigo-400 border border-indigo-500/20 hover:bg-indigo-600 hover:text-white transition-all cursor-pointer disabled:opacity-50"
			title="Làm mới ngay"
		>
			<svg
				xmlns="http://www.w3.org/2000/svg"
				fill="none"
				viewBox="0 0 24 24"
				stroke-width="2"
				stroke="currentColor"
				class="w-4 h-4 sm:w-5 sm:h-5 {isLoading ? 'animate-spin' : ''}"
			>
				<path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
			</svg>
		</button>
	</div>
</header>
