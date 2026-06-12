<script lang="ts">
	interface Props {
		cpuThreshold: number;
		ramThresholdMB: number;
		discordWebhookUrl: string;
		onsave: (settings: { cpuThreshold: number; ramThresholdMB: number; discordWebhookUrl: string }) => Promise<void>;
	}

	let { cpuThreshold, ramThresholdMB, discordWebhookUrl, onsave }: Props = $props();

	let settingsCpu = $state(cpuThreshold);
	let settingsRam = $state(ramThresholdMB);
	let settingsDiscordUrl = $state(discordWebhookUrl);
	let isSavingSettings = $state(false);
	let settingsMessage = $state('');
	let settingsMessageType = $state<'success' | 'error' | ''>('');

	// Update local state if parent updates values
	$effect(() => {
		settingsCpu = cpuThreshold;
	});
	$effect(() => {
		settingsRam = ramThresholdMB;
	});
	$effect(() => {
		settingsDiscordUrl = discordWebhookUrl;
	});

	async function handleSubmit(e: SubmitEvent) {
		e.preventDefault();
		isSavingSettings = true;
		settingsMessage = '';
		settingsMessageType = '';
		try {
			await onsave({
				cpuThreshold: settingsCpu,
				ramThresholdMB: settingsRam,
				discordWebhookUrl: settingsDiscordUrl
			});
			settingsMessageType = 'success';
			settingsMessage = 'Lưu cấu hình thành công!';
			setTimeout(() => {
				if (settingsMessage === 'Lưu cấu hình thành công!') {
					settingsMessage = '';
				}
			}, 3000);
		} catch (err: any) {
			settingsMessageType = 'error';
			settingsMessage = err.message || 'Lỗi khi gửi yêu cầu';
		} finally {
			isSavingSettings = false;
		}
	}
</script>

<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-6 shadow-xl flex flex-col justify-between select-none">
	<div>
		<h3 class="font-bold text-zinc-200 text-lg flex items-center gap-2 mb-2">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5 text-indigo-400">
				<path stroke-linecap="round" stroke-linejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.43l-1.003.828c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.43l1.004-.827c.292-.24.437-.613.43-.991a6.936 6.936 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z" />
				<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
			</svg>
			Cấu hình cảnh báo
		</h3>
		<p class="text-zinc-500 text-xs mb-4">Các tham số giám sát hệ thống & kênh liên lạc.</p>
	</div>

	<form onsubmit={handleSubmit} class="space-y-4">
		<div>
			<label class="block text-xs font-semibold text-zinc-400 mb-1.5" for="cpu_threshold_input">Ngưỡng cảnh báo CPU (%)</label>
			<input
				type="number"
				id="cpu_threshold_input"
				min="10"
				max="100"
				step="1"
				bind:value={settingsCpu}
				class="w-full bg-zinc-950 border border-zinc-800 text-zinc-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono"
				required
			/>
		</div>

		<div>
			<label class="block text-xs font-semibold text-zinc-400 mb-1.5" for="ram_threshold_input">RAM trống tối thiểu (MB)</label>
			<input
				type="number"
				id="ram_threshold_input"
				min="50"
				max="32000"
				step="50"
				bind:value={settingsRam}
				class="w-full bg-zinc-950 border border-zinc-800 text-zinc-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono"
				required
			/>
		</div>

		<div>
			<label class="block text-xs font-semibold text-zinc-400 mb-1.5" for="discord_webhook_input">Discord Webhook URL</label>
			<input
				type="password"
				id="discord_webhook_input"
				placeholder="https://discord.com/api/webhooks/..."
				bind:value={settingsDiscordUrl}
				class="w-full bg-zinc-950 border border-zinc-800 text-zinc-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono"
			/>
		</div>

		{#if settingsMessage}
			<p class="text-xs font-semibold
				{settingsMessageType === 'success' ? 'text-emerald-400' : 'text-rose-400'}"
			>
				{settingsMessage}
			</p>
		{/if}

		<button
			type="submit"
			disabled={isSavingSettings}
			class="w-full py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold transition-all cursor-pointer flex justify-center items-center gap-1.5 disabled:opacity-50"
		>
			{#if isSavingSettings}
				<svg class="animate-spin h-3.5 w-3.5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
					<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
					<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
				</svg>
				Đang lưu...
			{:else}
				Lưu cấu hình
			{/if}
		</button>
	</form>
</div>
