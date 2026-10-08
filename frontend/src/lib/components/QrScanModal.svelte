<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import QrScanner from 'qr-scanner';

	interface Props {
		user: { email: string; name: string; picture: string; token: string };
		apiBase: string;
		onclose: () => void;
		onsuccess: (msg: string) => void;
	}

	let { user, apiBase, onclose, onsuccess }: Props = $props();

	let videoElem: HTMLVideoElement | null = $state(null);
	let qrScanner: QrScanner | null = null;
	let scanStatus = $state<'scanning' | 'processing' | 'success' | 'error'>('scanning');
	let statusMessage = $state('Hướng camera về phía mã QR trên màn hình Desktop');
	let errorMessage = $state('');

	onMount(async () => {
		if (!videoElem) return;

		try {
			const hasCamera = await QrScanner.hasCamera();
			if (!hasCamera) {
				scanStatus = 'error';
				errorMessage = 'Thiết bị không có Camera hoặc chưa cấp quyền truy cập.';
				return;
			}

			qrScanner = new QrScanner(
				videoElem,
				async (result) => {
					if (scanStatus === 'processing' || scanStatus === 'success') return;
					await handleScanResult(result.data);
				},
				{
					highlightScanRegion: true,
					highlightCodeOutline: true,
					preferredCamera: 'environment'
				}
			);

			await qrScanner.start();
		} catch (err: any) {
			console.error('Camera start error:', err);
			scanStatus = 'error';
			errorMessage = err.message || 'Không thể mở Camera. Vui lòng cấp quyền truy cập Camera.';
		}
	});

	onDestroy(() => {
		if (qrScanner) {
			qrScanner.stop();
			qrScanner.destroy();
			qrScanner = null;
		}
	});

	async function handleScanResult(rawValue: string) {
		let qrCode = rawValue.trim();

		try {
			if (qrCode.startsWith('{') && qrCode.endsWith('}')) {
				const parsed = JSON.parse(qrCode);
				if (parsed.code) {
					qrCode = parsed.code;
				}
			}
		} catch {
			// fallback
		}

		scanStatus = 'processing';
		statusMessage = 'Đang xác thực và phê duyệt phiên đăng nhập...';

		try {
			if (qrScanner) {
				qrScanner.stop();
			}

			const res = await fetch(`${apiBase}/auth/qr/approve`, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({
					code: qrCode,
					email: user.email,
					name: user.name,
					picture: user.picture,
					token: user.token
				})
			});

			const data = await res.json();
			if (!res.ok || data.status === 'error') {
				throw new Error(data.message || 'Phê duyệt thất bại hoặc mã QR đã hết hạn.');
			}

			scanStatus = 'success';
			statusMessage = 'Đã đăng nhập thành công trên Desktop!';
			setTimeout(() => {
				onsuccess('Phê duyệt đăng nhập thành công!');
				onclose();
			}, 1500);
		} catch (err: any) {
			scanStatus = 'error';
			errorMessage = err.message || 'Lỗi khi xác thực mã QR.';
		}
	}

	function retryScan() {
		scanStatus = 'scanning';
		statusMessage = 'Hướng camera về phía mã QR trên màn hình Desktop';
		errorMessage = '';
		if (qrScanner) {
			qrScanner.start().catch((err) => {
				scanStatus = 'error';
				errorMessage = err.message;
			});
		}
	}
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
	<div class="relative w-full max-w-sm bg-zinc-950 border border-zinc-800 rounded-3xl shadow-2xl overflow-hidden flex flex-col items-center p-6 text-center">
		<!-- Close Button -->
		<button
			onclick={onclose}
			class="absolute top-4 right-4 p-2 text-zinc-400 hover:text-white bg-zinc-900/80 hover:bg-zinc-800 rounded-full transition-colors cursor-pointer"
			title="Đóng"
		>
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
				<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
			</svg>
		</button>

		<div class="w-12 h-12 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center mb-4">
			<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" class="w-6 h-6">
				<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 3.75 9.375v-4.5ZM3.75 14.625c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5a1.125 1.125 0 0 1-1.125-1.125v-4.5ZM13.5 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 13.5 9.375v-4.5Z" />
				<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 6.75h.75v.75h-.75v-.75ZM6.75 16.5h.75v.75h-.75v-.75ZM16.5 6.75h.75v.75h-.75v-.75ZM13.5 13.5h.75v.75h-.75v-.75ZM13.5 19.5h.75v.75h-.75v-.75ZM19.5 13.5h.75v.75h-.75v-.75ZM19.5 19.5h.75v.75h-.75v-.75ZM16.5 16.5h.75v.75h-.75v-.75Z" />
			</svg>
		</div>

		<h3 class="text-lg font-bold text-white mb-1">Quét mã QR Desktop</h3>
		<p class="text-xs text-zinc-400 mb-5">{statusMessage}</p>

		<!-- Camera Frame -->
		<div class="relative w-64 h-64 rounded-2xl overflow-hidden bg-black border-2 border-indigo-500/40 shadow-inner flex items-center justify-center">
			<video bind:this={videoElem} class="w-full h-full object-cover"></video>

			{#if scanStatus === 'scanning'}
				<!-- Laser Scan Line Effect -->
				<div class="absolute inset-x-0 h-0.5 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_8px_#38bdf8] animate-scan"></div>
			{/if}

			{#if scanStatus === 'processing'}
				<div class="absolute inset-0 bg-black/70 backdrop-blur-sm flex flex-col items-center justify-center gap-2 text-indigo-400">
					<svg class="animate-spin h-8 w-8" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
						<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
						<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
					</svg>
					<span class="text-xs text-zinc-300 font-semibold">Đang phê duyệt...</span>
				</div>
			{:else if scanStatus === 'success'}
				<div class="absolute inset-0 bg-emerald-950/90 backdrop-blur-sm flex flex-col items-center justify-center gap-2 text-emerald-400">
					<svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 animate-bounce" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
					</svg>
					<span class="text-xs font-bold text-white">Đã phê duyệt thành công!</span>
				</div>
			{:else if scanStatus === 'error'}
				<div class="absolute inset-0 bg-rose-950/90 backdrop-blur-sm flex flex-col items-center justify-center p-4 gap-3 text-rose-300">
					<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
						<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
					</svg>
					<p class="text-xs text-center font-medium leading-relaxed">{errorMessage}</p>
					<button
						onclick={retryScan}
						class="py-2 px-4 bg-zinc-800 hover:bg-zinc-700 text-white rounded-xl text-xs font-bold transition-all cursor-pointer"
					>
						Thử lại
					</button>
				</div>
			{/if}
		</div>

		<div class="mt-5 text-[11px] text-zinc-500">
			Đăng nhập với tài khoản: <span class="font-semibold text-zinc-400">{user.email}</span>
		</div>
	</div>
</div>

<style>
	@keyframes scanAnim {
		0% { top: 0%; opacity: 0.3; }
		50% { opacity: 1; }
		100% { top: 98%; opacity: 0.3; }
	}
	.animate-scan {
		animation: scanAnim 2s ease-in-out infinite alternate;
	}
</style>