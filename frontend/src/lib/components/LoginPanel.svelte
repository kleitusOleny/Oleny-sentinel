<script lang="ts">
	import { onMount } from 'svelte';

	interface Props {
		onlogin: (user: { email: string; name: string; picture: string; token: string }) => void;
		apiBase: string;
	}

	let { onlogin, apiBase }: Props = $props();

	let errorMessage = $state('');
	let isLoading = $state(false);

	import QRCode from 'qrcode';

	let loginMethod = $state<'google' | 'qr'>('google');
	let qrCodeUrl = $state('');
	let qrSessionCode = $state('');
	let isGeneratingQr = $state(false);
	let qrPollingInterval: any = null;

	onMount(() => {
		if (!document.getElementById('google-gsi-client')) {
			const script = document.createElement('script');
			script.id = 'google-gsi-client';
			script.src = 'https://accounts.google.com/gsi/client';
			script.async = true;
			script.defer = true;
			script.onload = initGoogleSignIn;
			document.head.appendChild(script);
		} else {
			initGoogleSignIn();
		}

		return () => {
			if (qrPollingInterval) clearInterval(qrPollingInterval);
		};
	});

	function initGoogleSignIn() {
		try {
			// @ts-ignore
			google.accounts.id.initialize({
				client_id: '524474374334-slg0hc2rbskjf5hnjk0hu04m5kobhpfk.apps.googleusercontent.com',
				callback: handleCredentialResponse
			});
			// @ts-ignore
			google.accounts.id.renderButton(
				document.getElementById('google-signin-btn'),
				{ theme: 'filled_dark', size: 'large', text: 'signin_with', width: 280, shape: 'pill' }
			);
		} catch (err) {
			console.error(err);
		}
	}

	async function generateQrCode() {
		isGeneratingQr = true;
		errorMessage = '';
		try {
			const res = await fetch(`${apiBase}/auth/qr/generate`);
			const data = await res.json();
			if (data.status === 'success') {
				qrSessionCode = data.code;
				// Chuỗi QR chứa định danh hệ thống Sentinel
				const qrPayload = JSON.stringify({
					app: 'server-sentinel',
					action: 'login',
					code: data.code
				});
				qrCodeUrl = await QRCode.toDataURL(qrPayload, {
					width: 240,
					margin: 2,
					color: {
						dark: '#000000',
						light: '#ffffff'
					}
				});

				// Bắt đầu poll kiểm tra khi điện thoại quét
				if (qrPollingInterval) clearInterval(qrPollingInterval);
				qrPollingInterval = setInterval(checkQrStatus, 2000);
			} else {
				throw new Error('Không thể tạo mã QR.');
			}
		} catch (err: any) {
			errorMessage = err.message || 'Lỗi khi tạo mã QR';
		} finally {
			isGeneratingQr = false;
		}
	}

	async function checkQrStatus() {
		if (!qrSessionCode) return;
		try {
			const res = await fetch(`${apiBase}/auth/qr/status?code=${encodeURIComponent(qrSessionCode)}`);
			const data = await res.json();
			if (data.status === 'approved') {
				if (qrPollingInterval) clearInterval(qrPollingInterval);
				onlogin({
					email: data.email,
					name: data.name,
					picture: data.picture,
					token: data.token
				});
			} else if (data.status === 'expired') {
				if (qrPollingInterval) clearInterval(qrPollingInterval);
				qrCodeUrl = '';
				qrSessionCode = '';
				errorMessage = 'Mã QR đã hết hạn. Vui lòng bấm tạo mã mới.';
			}
		} catch (err) {
			console.error('Lỗi kiểm tra QR:', err);
		}
	}

	function switchTab(method: 'google' | 'qr') {
		loginMethod = method;
		errorMessage = '';
		if (method === 'qr' && !qrCodeUrl) {
			generateQrCode();
		}
	}

	async function handleCredentialResponse(response: any) {
		isLoading = true;
		errorMessage = '';
		try {
			const res = await fetch(`${apiBase}/auth/google`, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({ idToken: response.credential })
			});
			const data = await res.json();
			if (data.status === 'success') {
				onlogin({
					email: data.email,
					name: data.name,
					picture: data.picture,
					token: data.token
				});
			} else {
				errorMessage = data.message || 'Lỗi đăng nhập không xác định.';
			}
		} catch (err: any) {
			errorMessage = `Không thể kết nối đến máy chủ xác thực: ${err.message}`;
		} finally {
			isLoading = false;
		}
	}
</script>

<div class="min-h-screen bg-zinc-950 flex flex-col justify-center items-center p-4 relative overflow-hidden select-none">
	<!-- Background abstract shapes -->
	<div class="absolute w-96 h-96 bg-violet-600/10 blur-[120px] rounded-full top-1/4 left-1/4"></div>
	<div class="absolute w-96 h-96 bg-cyan-600/10 blur-[120px] rounded-full bottom-1/4 right-1/4"></div>

	<!-- Login Card -->
	<div class="w-full max-w-md bg-zinc-900/40 backdrop-blur-xl border border-zinc-800/80 rounded-3xl p-8 shadow-2xl relative z-10 text-center space-y-6">
		<div class="space-y-2">
			<div class="inline-flex p-3 rounded-2xl bg-indigo-600/10 text-indigo-400 border border-indigo-500/20 mb-2">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-8 h-8">
					<path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.57-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z" />
				</svg>
			</div>
			<h1 class="text-3xl font-black tracking-tight bg-gradient-to-r from-violet-400 via-indigo-400 to-cyan-400 bg-clip-text text-transparent">
				SERVER SENTINEL
			</h1>
			<p class="text-zinc-400 text-xs leading-relaxed max-w-xs mx-auto">
				Hệ thống giám sát bảo mật của doanh nghiệp. Đăng nhập để tiếp tục quản lý hạ tầng.
			</p>
		</div>

		<!-- Switch Login Method Tabs -->
		<div class="grid grid-cols-2 p-1 bg-zinc-950/80 border border-zinc-800 rounded-2xl">
			<button
				onclick={() => switchTab('google')}
				class="py-2 text-xs font-bold rounded-xl transition-all cursor-pointer {loginMethod === 'google' ? 'bg-zinc-800 text-white shadow' : 'text-zinc-500 hover:text-zinc-300'}"
			>
				Tài khoản Google
			</button>
			<button
				onclick={() => switchTab('qr')}
				class="py-2 text-xs font-bold rounded-xl transition-all cursor-pointer flex items-center justify-center gap-1.5 {loginMethod === 'qr' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-500 hover:text-zinc-300'}"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 3.75 9.375v-4.5ZM3.75 14.625c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5a1.125 1.125 0 0 1-1.125-1.125v-4.5ZM13.5 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 13.5 9.375v-4.5Z" />
					<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 6.75h.008v.008H6.75V6.75ZM6.75 16.5h.008v.008H6.75V16.5ZM16.5 6.75h.008v.008H16.5V6.75ZM13.5 13.5h.008v.008H13.5V13.5ZM13.5 19.5h.008v.008H13.5V19.5ZM19.5 13.5h.008v.008H19.5V13.5ZM16.5 16.5h.008v.008H16.5V16.5ZM19.5 19.5h.008v.008H19.5V19.5Z" />
				</svg>
				Quét mã QR Mobile
			</button>
		</div>

		<!-- Login Method: Google -->
		{#if loginMethod === 'google'}
			<div class="flex flex-col items-center justify-center py-4 relative min-h-[50px]">
				{#if isLoading}
					<div class="flex flex-col items-center gap-2 text-zinc-500 text-xs font-semibold">
						<svg class="animate-spin h-6 w-6 text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
							<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
							<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
						</svg>
						<span>Đang xác thực thông tin...</span>
					</div>
				{/if}

				<div class="w-full flex justify-center {isLoading ? 'hidden' : 'block'}" id="google-signin-btn"></div>
			</div>
		{:else}
			<!-- Login Method: QR CODE -->
			<div class="flex flex-col items-center justify-center py-2 space-y-4">
				{#if isGeneratingQr}
					<div class="w-60 h-60 rounded-2xl bg-zinc-950 flex flex-col items-center justify-center gap-3 border border-zinc-800 text-zinc-500">
						<svg class="animate-spin h-7 w-7 text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
							<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
							<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
						</svg>
						<span class="text-xs">Đang khởi tạo mã QR...</span>
					</div>
				{:else if qrCodeUrl}
					<div class="p-3 bg-white rounded-2xl shadow-xl border-4 border-indigo-500/30">
						<img src={qrCodeUrl} alt="QR Code Login" class="w-56 h-56 rounded-lg" />
					</div>
					<div class="space-y-1">
						<p class="text-xs font-semibold text-zinc-300">
							Dùng điện thoại đã đăng nhập để quét mã này
						</p>
						<p class="text-[11px] text-zinc-500">
							Mã tự động làm mới sau 5 phút • Đang chờ quét...
						</p>
					</div>
					<button
						onclick={generateQrCode}
						class="text-xs text-indigo-400 hover:text-indigo-300 font-semibold cursor-pointer underline underline-offset-4"
					>
						Tạo mã QR mới
					</button>
				{:else}
					<button
						onclick={generateQrCode}
						class="py-2.5 px-5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold transition-all shadow-lg shadow-indigo-950 cursor-pointer"
					>
						Nhấn để hiển thị mã QR
					</button>
				{/if}
			</div>
		{/if}

		<!-- Error Message -->
		{#if errorMessage}
			<div class="bg-rose-950/40 border border-rose-800/80 text-rose-300 text-xs px-4 py-3 rounded-2xl flex items-start gap-2.5">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4 shrink-0 text-rose-400 mt-0.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
				</svg>
				<span class="text-left font-medium">{errorMessage}</span>
			</div>
		{/if}

		<div class="border-t border-zinc-900 pt-4 text-[10px] text-zinc-600">
			Dữ liệu phân quyền được lưu bảo mật trong <code class="font-mono bg-zinc-950 px-1 py-0.5 rounded text-zinc-500">allow_accesss.txt</code>.
		</div>
	</div>
</div>
