<script lang="ts">
	import { themeStore, type ThemeMode } from '$lib/stores/themeStore';

	interface Props {
		cpuThreshold: number;
		ramThresholdMB: number;
		discordBotToken: string;
		discordChannelId: string;
		onsave: (settings: { cpuThreshold: number; ramThresholdMB: number; discordBotToken: string; discordChannelId: string }) => Promise<void>;
	}

	let { cpuThreshold, ramThresholdMB, discordBotToken, discordChannelId, onsave }: Props = $props();

	let settingsCpu = $state(cpuThreshold);
	let settingsRam = $state(ramThresholdMB);
	let settingsDiscordToken = $state(discordBotToken);
	let settingsDiscordChannelId = $state(discordChannelId);
	let isSavingSettings = $state(false);
	let settingsMessage = $state('');
	let settingsMessageType = $state<'success' | 'error' | ''>('');

	// Current Theme state
	let currentTheme = $state<ThemeMode>('default');

	$effect(() => {
		const unsub = themeStore.subscribe((val) => {
			currentTheme = val;
		});
		return unsub;
	});

	function handleSelectTheme(theme: ThemeMode) {
		themeStore.setTheme(theme);
	}

	// Update local state if parent updates values
	$effect(() => {
		settingsCpu = cpuThreshold;
	});
	$effect(() => {
		settingsRam = ramThresholdMB;
	});
	$effect(() => {
		settingsDiscordToken = discordBotToken;
	});
	$effect(() => {
		settingsDiscordChannelId = discordChannelId;
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
				discordBotToken: settingsDiscordToken,
				discordChannelId: settingsDiscordChannelId
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

<div class="space-y-6">
	<!-- THEME SELECTION PANEL -->
	<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-6 shadow-xl flex flex-col justify-between select-none">
		<div>
			<h3 class="font-bold text-zinc-200 text-lg flex items-center gap-2 mb-2">
				<span class="text-xl">🎨</span>
				Giao diện & Theme
			</h3>
			<p class="text-zinc-500 text-xs mb-5">Tùy chỉnh tông màu và phong cách hiển thị của Server Sentinel.</p>
		</div>

		<div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
			<!-- Option 1: Default Sentinel Dark -->
			<button
				type="button"
				onclick={() => handleSelectTheme('default')}
				class="text-left p-4 rounded-xl border transition-all cursor-pointer relative overflow-hidden flex flex-col justify-between gap-3 {currentTheme === 'default' ? 'border-indigo-500 bg-indigo-500/10 ring-2 ring-indigo-500/30' : 'border-zinc-800 bg-zinc-950/60 hover:border-zinc-700'}"
			>
				<div class="flex items-center justify-between">
					<div class="flex items-center gap-2.5">
						<div class="w-4 h-4 rounded-full bg-indigo-500 ring-2 ring-indigo-400/40"></div>
						<span class="font-bold text-sm text-zinc-200">Mặc định (Dark Indigo)</span>
					</div>
					{#if currentTheme === 'default'}
						<span class="text-xs font-semibold px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">
							Đang dùng
						</span>
					{/if}
				</div>
				<p class="text-xs text-zinc-400 leading-relaxed">
					Tông màu tối chuẩn công nghệ, nền zinc-950 với điểm nhấn màu chàm Indigo chuyên nghiệp.
				</p>
				<div class="flex items-center gap-1.5 pt-1">
					<span class="w-5 h-5 rounded-md bg-[#09090b] border border-zinc-700 block" title="Background"></span>
					<span class="w-5 h-5 rounded-md bg-[#18181b] border border-zinc-700 block" title="Card Surface"></span>
					<span class="w-5 h-5 rounded-md bg-[#6366f1] block" title="Primary Accent"></span>
				</div>
			</button>

			<!-- Option 2: Anime Neko Pink Theme (Dựa trên nhân vật anime) -->
			<button
				type="button"
				onclick={() => handleSelectTheme('anime-pink')}
				class="text-left p-4 rounded-xl border transition-all cursor-pointer relative overflow-hidden flex flex-col justify-between gap-3 {currentTheme === 'anime-pink' ? 'border-pink-500 bg-pink-500/15 ring-2 ring-pink-500/30 shadow-[0_0_20px_rgba(244,63,134,0.2)]' : 'border-zinc-800 bg-zinc-950/60 hover:border-pink-500/40'}"
			>
				<div class="flex items-center justify-between">
					<div class="flex items-center gap-2.5">
						<span class="text-base">🐱</span>
						<span class="font-bold text-sm text-pink-200">Idol Neko (Star Pink)</span>
					</div>
					{#if currentTheme === 'anime-pink'}
						<span class="text-xs font-semibold px-2 py-0.5 rounded-full bg-pink-500/20 text-pink-300 border border-pink-500/30 animate-pulse">
							Đang dùng ✨
						</span>
					{/if}
				</div>
				<p class="text-xs text-zinc-300 leading-relaxed">
					Lấy cảm hứng từ nhân vật anime tóc hồng tai mèo: nơ sao vàng, tóc hồng neon rực rỡ và tông nền tím than thủy thủ.
				</p>
				<div class="flex items-center gap-1.5 pt-1">
					<span class="w-5 h-5 rounded-md bg-[#0d0910] border border-pink-900 block" title="Background đen tím thủy thủ"></span>
					<span class="w-5 h-5 rounded-md bg-[#211427] border border-pink-900 block" title="Card tím huyền ảo"></span>
					<span class="w-5 h-5 rounded-md bg-[#f43f86] block shadow-[0_0_8px_#f43f86]" title="Hồng rực rỡ tóc anime"></span>
					<span class="w-5 h-5 rounded-md bg-[#fbbf24] block shadow-[0_0_8px_#fbbf24]" title="Vàng ngôi sao cổ áo"></span>
				</div>
			</button>
		</div>
	</div>

	<!-- ALERT SETTINGS CONFIGURATION -->
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
				<label class="block text-xs font-semibold text-zinc-400 mb-1.5" for="discord_bot_token_input">Discord Bot Token</label>
				<input
					type="password"
					id="discord_bot_token_input"
					placeholder="MTUxN..."
					bind:value={settingsDiscordToken}
					class="w-full bg-zinc-950 border border-zinc-800 text-zinc-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500 font-mono"
					required
				/>
			</div>

			<div>
				<label class="block text-xs font-semibold text-zinc-400 mb-1.5" for="discord_channel_id_input">Discord Channel ID</label>
				<input
					type="text"
					id="discord_channel_id_input"
					placeholder="Ví dụ: 1515016594974183579"
					bind:value={settingsDiscordChannelId}
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

		<div class="mt-5 pt-4 border-t border-zinc-850 text-[11px] text-zinc-500 space-y-2 select-text">
			<div class="font-bold text-zinc-400 flex items-center gap-1.5">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5 text-indigo-400">
					<path stroke-linecap="round" stroke-linejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 111.063 1.06l-.041.02a.75.75 0 01-1.063-1.06zm-9.661 12.77a1.5 1.5 0 01-2.293-1.094l.808-5.314a3.75 3.75 0 01.759-1.952l9.864-9.864a2.441 2.441 0 113.452 3.452l-9.864 9.864a3.75 3.75 0 01-1.952.759l-5.314.808zm18.59-19.59a2.441 2.441 0 113.452 3.452l-2.235 2.235-3.452-3.452 2.235-2.235zM12 3v.75m0 16.5V21m9-9h-.75M4.25 12H3m15.222-6.222l-.53.53m-10.384 10.384l-.53.53m10.914 0l-.53-.53M6.868 6.868l-.53-.53" />
				</svg>
				Hướng dẫn cài đặt Guild (Server):
			</div>
			<ul class="list-decimal pl-4 space-y-1">
				<li>Vào <a href="https://discord.com/developers/applications" target="_blank" rel="noreferrer" class="text-indigo-400 hover:text-indigo-300 font-semibold hover:underline">Discord Dev Portal</a>, chọn App của bạn.</li>
				<li>Mở **OAuth2** > **URL Generator**, tích chọn: <code class="text-zinc-300 bg-zinc-950 px-1 py-0.5 rounded font-mono">bot</code> &amp; <code class="text-zinc-300 bg-zinc-950 px-1 py-0.5 rounded font-mono">applications.commands</code>.</li>
				<li>Tại **Bot Permissions**, tích chọn: <code class="text-zinc-400">Send Messages</code>, <code class="text-zinc-400">Embed Links</code>.</li>
				<li>Sử dụng liên kết được tạo ra ở dưới để cài đặt bot vào Server của bạn.</li>
				<li>Bật Chế độ nhà phát triển trên Discord, click chuột phải vào kênh nhận tin nhắn chọn **Copy Channel ID** rồi điền vào ô trên.</li>
			</ul>
		</div>
	</div>
</div>
