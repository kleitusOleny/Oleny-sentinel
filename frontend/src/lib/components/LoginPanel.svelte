<script lang="ts">
	import { onMount } from 'svelte';

	interface Props {
		onlogin: (user: { email: string; name: string; picture: string; token: string }) => void;
		apiBase: string;
	}

	let { onlogin, apiBase }: Props = $props();

	let errorMessage = $state('');
	let isLoading = $state(false);

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
	});

	function initGoogleSignIn() {
		try {
			// @ts-ignore
			google.accounts.id.initialize({
				// Cấu hình Client ID của bạn (Google Cloud Console). Sử dụng ID mặc định/mẫu nếu chưa cấu hình.
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
				Hệ thống giám sát bảo mật của doanh nghiệp. Vui lòng đăng nhập bằng Google Account được phê duyệt để tiếp tục.
			</p>
		</div>

		<!-- Login Buttons & States -->
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
