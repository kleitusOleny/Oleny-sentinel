<script lang="ts">
	import { onMount } from 'svelte';
	import nekoIcon from '$lib/assets/neko-icon.png';
	import nanallyGif from '$lib/assets/nanally1.gif';

	interface HangoutMember {
		id: string;
		name: string;
		availableDates: string[];
		color?: string;
		updatedAt?: number;
		pinHash?: string;
	}

	const API_BASE = '/api';

	// Storage key for user's stored PINs per member ID
	const PINS_STORAGE_KEY = 'hangout_member_pins_v1';

	// States
	let members = $state<HangoutMember[]>([]);
	let isLoading = $state(true);
	let errorMessage = $state('');
	let newMemberName = $state('');
	let newMemberPin = $state('');
	let isAddingMember = $state(false);
	let activeMemberId = $state<string>(''); // Thành viên người dùng hiện tại
	let viewMode = $state<'table' | 'mobile-cards'>('table'); // Chế độ xem

	// PIN Verification Modal / Prompt state (khi mở trên thiết bị mới)
	let isPinModalOpen = $state(false);
	let pinModalTargetMember = $state<HangoutMember | null>(null);
	let pinModalInput = $state('');
	let pinModalError = $state('');
	let isVerifyingPin = $state(false);

	// Quản lý tuần (Mặc định là ngày Thứ 2 của tuần hiện tại)
	let currentMonday = $state<Date>(getMonday(new Date()));

	function getMonday(d: Date): Date {
		const date = new Date(d);
		const day = date.getDay();
		const diff = date.getDate() - day + (day === 0 ? -6 : 1);
		date.setDate(diff);
		date.setHours(0, 0, 0, 0);
		return date;
	}

	function formatDate(d: Date): string {
		const year = d.getFullYear();
		const month = String(d.getMonth() + 1).padStart(2, '0');
		const day = String(d.getDate()).padStart(2, '0');
		return `${year}-${month}-${day}`;
	}

	// 7 ngày trong tuần được chọn
	let weekDays = $derived.by(() => {
		const days = [];
		const dayNames = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'CN'];
		const dayFullNames = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];
		const base = new Date(currentMonday);

		for (let i = 0; i < 7; i++) {
			const d = new Date(base);
			d.setDate(base.getDate() + i);
			const dateStr = formatDate(d);
			const isToday = formatDate(new Date()) === dateStr;
			days.push({
				date: d,
				dateStr: dateStr,
				name: dayNames[i],
				fullName: dayFullNames[i],
				dayNum: d.getDate(),
				monthNum: d.getMonth() + 1,
				isWeekend: i >= 5,
				isToday: isToday
			});
		}
		return days;
	});

	function prevWeek() {
		const next = new Date(currentMonday);
		next.setDate(next.getDate() - 7);
		currentMonday = next;
	}

	function nextWeek() {
		const next = new Date(currentMonday);
		next.setDate(next.getDate() + 7);
		currentMonday = next;
	}

	function goToCurrentWeek() {
		currentMonday = getMonday(new Date());
	}

	// Thống kê số người rảnh từng ngày trong tuần
	let dayStats = $derived.by(() => {
		const stats: Record<string, { count: number; members: HangoutMember[] }> = {};
		for (const day of weekDays) {
			const avail = members.filter((m) => m.availableDates?.includes(day.dateStr));
			stats[day.dateStr] = {
				count: avail.length,
				members: avail
			};
		}
		return stats;
	});

	// Tìm ngày có nhiều người rảnh nhất trong tuần
	let maxAvailableCount = $derived.by(() => {
		let max = 0;
		for (const day of weekDays) {
			const cnt = dayStats[day.dateStr]?.count || 0;
			if (cnt > max) max = cnt;
		}
		return max;
	});

	// Thành viên đang được chọn (active persona)
	let activeMember = $derived.by(() => {
		return members.find((m) => m.id === activeMemberId) || null;
	});

	// Lấy mã PIN đã lưu trong localStorage của member
	function getStoredPin(memberId: string): string {
		if (typeof window === 'undefined') return '';
		try {
			const map = JSON.parse(localStorage.getItem(PINS_STORAGE_KEY) || '{}');
			return map[memberId] || '';
		} catch {
			return '';
		}
	}

	function saveStoredPin(memberId: string, pin: string) {
		if (typeof window === 'undefined') return;
		try {
			const map = JSON.parse(localStorage.getItem(PINS_STORAGE_KEY) || '{}');
			map[memberId] = pin;
			localStorage.setItem(PINS_STORAGE_KEY, JSON.stringify(map));
		} catch (err) {
			console.error('Cannot save pin to localStorage', err);
		}
	}

	function removeStoredPin(memberId: string) {
		if (typeof window === 'undefined') return;
		try {
			const map = JSON.parse(localStorage.getItem(PINS_STORAGE_KEY) || '{}');
			delete map[memberId];
			localStorage.setItem(PINS_STORAGE_KEY, JSON.stringify(map));
		} catch {}
	}

	// Kiểm tra xem trình duyệt hiện tại đã mở khóa (có PIN) cho member chưa
	function hasPinAccess(memberId: string): boolean {
		return Boolean(getStoredPin(memberId));
	}

	// Fetch API
	async function fetchMembers() {
		isLoading = true;
		try {
			const res = await fetch(`${API_BASE}/hangout/members`);
			if (!res.ok) throw new Error('Không thể tải lịch hẹn');
			members = await res.json();
			errorMessage = '';

			// Khôi phục activeMemberId từ localStorage
			if (typeof window !== 'undefined') {
				const savedId = localStorage.getItem('hangout_active_member_id');
				if (savedId && members.some((m) => m.id === savedId) && hasPinAccess(savedId)) {
					activeMemberId = savedId;
				} else {
					// Tìm member đầu tiên mà máy này sở hữu PIN
					const myMember = members.find((m) => hasPinAccess(m.id));
					if (myMember) {
						activeMemberId = myMember.id;
					}
				}
			}
		} catch (err: any) {
			console.error(err);
			errorMessage = 'Không thể kết nối đến máy chủ. Vui lòng thử lại sau.';
		} finally {
			isLoading = false;
		}
	}

	// Thêm thành viên mới kèm mã PIN 4 số
	async function handleAddMember(e: SubmitEvent) {
		e.preventDefault();
		const name = newMemberName.trim();
		const pin = newMemberPin.trim();

		if (!name) return;
		if (pin.length !== 4 || !/^\d{4}$/.test(pin)) {
			alert('Mã PIN phải gồm đúng 4 chữ số (ví dụ: 1234, 8888)!');
			return;
		}

		isAddingMember = true;
		try {
			const res = await fetch(`${API_BASE}/hangout/members`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ name, pin })
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = [...members, data.member];
				saveStoredPin(data.member.id, pin);
				activeMemberId = data.member.id;
				if (typeof window !== 'undefined') {
					localStorage.setItem('hangout_active_member_id', data.member.id);
				}
				newMemberName = '';
				newMemberPin = '';
				alert(`🎉 Đã tạo thành viên "${name}" thành công!\nMã PIN [${pin}] đã được lưu trên máy của bạn. Hãy ghi nhớ mã PIN này nếu bạn muốn sửa lịch trên thiết bị khác!`);
			} else {
				alert(data.message || 'Lỗi thêm thành viên');
			}
		} catch (err: any) {
			alert('Lỗi kết nối: ' + err.message);
		} finally {
			isAddingMember = false;
		}
	}

	// Mở popup nhập PIN để mở khóa quyền chỉnh sửa
	function openUnlockPinModal(member: HangoutMember) {
		pinModalTargetMember = member;
		pinModalInput = '';
		pinModalError = '';
		isPinModalOpen = true;
	}

	async function handleVerifyPinSubmit(e: SubmitEvent) {
		e.preventDefault();
		if (!pinModalTargetMember) return;
		const pin = pinModalInput.trim();
		if (pin.length !== 4) {
			pinModalError = 'Vui lòng nhập đủ 4 số PIN!';
			return;
		}

		isVerifyingPin = true;
		pinModalError = '';
		try {
			const res = await fetch(`${API_BASE}/hangout/members/${pinModalTargetMember.id}/verify-pin`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ pin })
			});
			const data = await res.json();
			if (data.status === 'success') {
				saveStoredPin(pinModalTargetMember.id, pin);
				activeMemberId = pinModalTargetMember.id;
				if (typeof window !== 'undefined') {
					localStorage.setItem('hangout_active_member_id', pinModalTargetMember.id);
				}
				isPinModalOpen = false;
			} else {
				pinModalError = data.message || 'Mã PIN không đúng!';
			}
		} catch (err: any) {
			pinModalError = 'Lỗi kết nối: ' + err.message;
		} finally {
			isVerifyingPin = false;
		}
	}

	// Toggle ngày rảnh với mã PIN
	async function handleToggleDate(memberId: string, dateStr: string) {
		const targetMember = members.find((m) => m.id === memberId);
		if (!targetMember) return;

		const pin = getStoredPin(memberId);
		if (!pin) {
			// Chưa có mã PIN trên thiết bị này -> Yêu cầu mở khóa
			openUnlockPinModal(targetMember);
			return;
		}

		const currentDates = targetMember.availableDates || [];
		const isCurrentlyAvailable = currentDates.includes(dateStr);
		const newDates = isCurrentlyAvailable
			? currentDates.filter((d) => d !== dateStr)
			: [...currentDates, dateStr];

		// Optimistic update
		members = members.map((m) => (m.id === memberId ? { ...m, availableDates: newDates } : m));

		try {
			const res = await fetch(`${API_BASE}/hangout/members/${memberId}/toggle-date`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ date: dateStr, pin })
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = members.map((m) => (m.id === memberId ? data.member : m));
			} else {
				alert(data.message || 'Lỗi khi cập nhật');
				// Nếu sai PIN, xóa PIN đã lưu và mở modal lại
				removeStoredPin(memberId);
				members = members.map((m) => (m.id === memberId ? targetMember : m));
				openUnlockPinModal(targetMember);
			}
		} catch (err) {
			console.error('Toggle date failed, rollbacking', err);
			members = members.map((m) => (m.id === memberId ? targetMember : m));
		}
	}

	// Xóa thành viên
	async function handleDeleteMember(member: HangoutMember) {
		const pin = getStoredPin(member.id);
		if (!pin) {
			openUnlockPinModal(member);
			return;
		}

		if (!confirm(`Bạn có chắc muốn xóa vĩnh viễn lịch của "${member.name}"?`)) return;

		try {
			const res = await fetch(`${API_BASE}/hangout/members/${member.id}?pin=${encodeURIComponent(pin)}`, {
				method: 'DELETE'
			});
			const data = await res.json();
			if (data.status === 'success') {
				removeStoredPin(member.id);
				members = members.filter((m) => m.id !== member.id);
				if (activeMemberId === member.id) {
					activeMemberId = '';
				}
			} else {
				alert(data.message || 'Lỗi khi xóa');
			}
		} catch (err: any) {
			alert('Lỗi khi xóa: ' + err.message);
		}
	}

	function selectActiveMember(id: string) {
		if (hasPinAccess(id)) {
			activeMemberId = id;
			if (typeof window !== 'undefined') {
				localStorage.setItem('hangout_active_member_id', id);
			}
		} else {
			const target = members.find((m) => m.id === id);
			if (target) {
				openUnlockPinModal(target);
			}
		}
	}

	onMount(() => {
		fetchMembers();
	});
</script>

<svelte:head>
	<title>Hangout Scheduler - Lên Lịch Đi Chơi</title>
	<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover" />
	<meta name="description" content="Lên lịch rảnh đi chơi theo tuần cùng nhóm bạn, bảo vệ bằng mã PIN 4 số cá nhân" />
</svelte:head>

<div class="min-h-screen bg-[#09090b] text-zinc-100 font-sans selection:bg-pink-500 selection:text-white pb-24 md:pb-16">
	<!-- TOP NAVIGATION / BRAND HEADER -->
	<header class="border-b border-zinc-800/80 bg-zinc-950/85 backdrop-blur-xl sticky top-0 z-30 shadow-lg">
		<div class="max-w-7xl mx-auto px-3.5 sm:px-6 h-14 sm:h-16 flex items-center justify-between gap-3">
			<!-- Logo / Title -->
			<div class="flex items-center gap-2.5 overflow-hidden">
				<a href="/" class="flex items-center gap-2 hover:opacity-85 transition-opacity shrink-0" title="Về trang chủ Sentinel">
					<img src={nekoIcon} alt="Sentinel Neko Icon" class="h-8 w-8 sm:h-9 sm:w-9 object-contain drop-shadow-[0_2px_8px_rgba(244,63,134,0.4)]" />
					<div class="leading-tight">
						<div class="flex items-center gap-1.5">
							<span class="font-extrabold tracking-wider bg-gradient-to-r from-pink-400 via-rose-400 to-amber-300 bg-clip-text text-transparent text-base sm:text-lg">
								HANGOUT
							</span>
							<span class="px-1.5 py-0.2 rounded-full bg-pink-500/15 border border-pink-500/30 text-pink-300 font-bold text-[9px] sm:text-[10px]">
								Bảo vệ bằng PIN
							</span>
						</div>
						<span class="hidden sm:block text-[10px] text-zinc-400 font-semibold tracking-wide">Mỗi người tự quản lý lịch bằng mã PIN 4 số</span>
					</div>
				</a>
			</div>

			<!-- Quick Info / Status -->
			<div class="flex items-center gap-2 shrink-0">
				<img src={nanallyGif} alt="Nanally Mascot" class="h-8 w-8 sm:h-10 sm:w-10 object-contain drop-shadow" />
				<a
					href="/"
					class="px-2.5 py-1.5 rounded-xl border border-zinc-800 hover:border-zinc-700 bg-zinc-900/60 hover:bg-zinc-800 text-xs font-semibold text-zinc-300 hover:text-white transition-all flex items-center gap-1"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
					</svg>
					<span class="hidden xs:inline">Trang chủ</span>
				</a>
			</div>
		</div>
	</header>

	<main class="max-w-7xl mx-auto px-3 sm:px-6 pt-4 sm:pt-6 space-y-4 sm:space-y-6">
		<!-- HERO & ADD FORM WITH 4-DIGIT PIN -->
		<div class="relative bg-gradient-to-r from-zinc-900/80 via-zinc-900/50 to-pink-950/20 border border-zinc-800/80 rounded-2xl sm:rounded-3xl p-4 sm:p-6 shadow-xl backdrop-blur-md overflow-hidden">
			<div class="absolute -right-10 -bottom-10 w-40 h-40 bg-pink-500/10 rounded-full blur-3xl pointer-events-none"></div>

			<div class="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3.5">
				<div class="space-y-1">
					<h1 class="text-base sm:text-xl md:text-2xl font-black text-white flex items-center gap-2">
						<span>🔒</span>
						Lịch Rảnh Đi Chơi - Khóa Riêng Bằng Mã PIN 4 Số
					</h1>
					<p class="text-zinc-400 text-xs sm:text-sm max-w-2xl leading-relaxed">
						Không cần tài khoản Google. Đặt <strong class="text-pink-300">mã PIN 4 số</strong> của riêng bạn khi tạo tên. Chỉ có bạn mới có quyền sửa lịch của mình!
					</p>
				</div>

				<!-- Quick Add Form with PIN -->
				<form onsubmit={handleAddMember} class="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 w-full md:w-auto shrink-0">
					<input
						type="text"
						placeholder="Tên bạn..."
						bind:value={newMemberName}
						class="bg-zinc-950 border border-zinc-700/80 focus:border-pink-500 text-xs sm:text-sm text-zinc-100 rounded-xl px-3 py-2 w-full sm:w-40 focus:outline-none focus:ring-1 focus:ring-pink-500 font-medium placeholder-zinc-500"
						required
					/>
					<input
						type="password"
						inputmode="numeric"
						maxlength="4"
						placeholder="PIN 4 số"
						bind:value={newMemberPin}
						class="bg-zinc-950 border border-zinc-700/80 focus:border-pink-500 text-xs sm:text-sm text-zinc-100 rounded-xl px-3 py-2 w-full sm:w-28 text-center font-mono tracking-widest focus:outline-none focus:ring-1 focus:ring-pink-500 font-bold placeholder-zinc-500"
						required
					/>
					<button
						type="submit"
						disabled={isAddingMember}
						class="px-4 py-2 bg-gradient-to-r from-pink-500 to-rose-500 hover:from-pink-600 hover:to-rose-600 text-white font-bold text-xs rounded-xl shadow-lg shadow-pink-500/20 transition-all cursor-pointer shrink-0 disabled:opacity-50 flex items-center justify-center gap-1.5"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5">
							<path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
						</svg>
						Tạo tên
					</button>
				</form>
			</div>
		</div>

		<!-- ERROR ALERT -->
		{#if errorMessage}
			<div class="p-3.5 rounded-2xl bg-rose-950/40 border border-rose-800/80 text-rose-300 text-xs sm:text-sm flex items-center gap-2">
				<span>⚠️</span>
				<span>{errorMessage}</span>
			</div>
		{/if}

		<!-- MOBILE QUICK CONTROLS: WEEK & PERSONA SELECTOR -->
		<div class="bg-zinc-900/50 border border-zinc-800/80 rounded-2xl p-3 sm:p-4 shadow-xl backdrop-blur-md space-y-3">
			<div class="flex items-center justify-between gap-2 flex-wrap">
				<!-- Week Navigator Buttons -->
				<div class="flex items-center gap-1.5 sm:gap-2">
					<button
						onclick={prevWeek}
						class="p-2 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-zinc-300 hover:text-white transition-all cursor-pointer"
						title="Tuần trước"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" />
						</svg>
					</button>

					<button
						onclick={goToCurrentWeek}
						class="px-2.5 sm:px-3 py-1.5 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-[11px] sm:text-xs font-bold text-zinc-300 hover:text-white transition-all cursor-pointer"
					>
						Hôm nay
					</button>

					<button
						onclick={nextWeek}
						class="p-2 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-zinc-300 hover:text-white transition-all cursor-pointer"
						title="Tuần sau"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
						</svg>
					</button>

					<!-- Range Text -->
					<div class="font-mono text-xs sm:text-sm font-bold text-zinc-200 ml-1">
						<span class="text-pink-400">{weekDays[0].dayNum}/{weekDays[0].monthNum}</span> &rarr;
						<span class="text-pink-400">{weekDays[6].dayNum}/{weekDays[6].monthNum}</span>
					</div>
				</div>

				<!-- Mobile View Switcher Pill -->
				<div class="flex items-center bg-zinc-950 p-1 rounded-xl border border-zinc-800 text-[11px] font-semibold">
					<button
						type="button"
						onclick={() => (viewMode = 'table')}
						class="px-2.5 py-1 rounded-lg transition-all cursor-pointer {viewMode === 'table' ? 'bg-pink-500/20 text-pink-300 font-bold border border-pink-500/30' : 'text-zinc-400'}"
					>
						Bảng ma trận
					</button>
					<button
						type="button"
						onclick={() => (viewMode = 'mobile-cards')}
						class="px-2.5 py-1 rounded-lg transition-all cursor-pointer {viewMode === 'mobile-cards' ? 'bg-pink-500/20 text-pink-300 font-bold border border-pink-500/30' : 'text-zinc-400'}"
					>
						Xem theo ngày
					</button>
				</div>
			</div>

			<!-- User Persona Bar: Chọn thành viên để mở khóa/chỉnh sửa -->
			<div class="pt-2 border-t border-zinc-800/60 flex items-center justify-between gap-2 overflow-x-auto no-scrollbar">
				<div class="flex items-center gap-1.5 shrink-0 text-xs text-zinc-400">
					<span class="text-sm">🔑</span>
					<span class="font-medium text-[11px]">Bạn là ai?</span>
				</div>

				<div class="flex items-center gap-1.5 overflow-x-auto py-0.5">
					{#each members as m (m.id)}
						{@const isSelected = activeMemberId === m.id}
						{@const isUnlocked = hasPinAccess(m.id)}
						<button
							type="button"
							onclick={() => selectActiveMember(m.id)}
							class="px-2.5 py-1 rounded-xl text-xs font-bold transition-all shrink-0 flex items-center gap-1.5 border {isSelected
								? 'bg-pink-500/25 border-pink-500 text-pink-200 shadow-[0_0_10px_rgba(244,63,134,0.3)]'
								: isUnlocked
									? 'bg-zinc-900 border-emerald-500/40 text-emerald-300'
									: 'bg-zinc-950 border-zinc-800 text-zinc-400 hover:text-zinc-200'}"
							title={isUnlocked ? 'Đã mở khóa trên máy này' : 'Chưa nhập mã PIN'}
						>
							<span class="w-2 h-2 rounded-full" style="background-color: {m.color || '#f43f86'}"></span>
							<span>{m.name}</span>
							{#if isUnlocked}
								<span class="text-[10px]">🔓</span>
							{:else}
								<span class="text-[10px] text-zinc-500">🔒</span>
							{/if}
						</button>
					{/each}
				</div>
			</div>
		</div>

		<!-- MOBILE CARDS VIEW -->
		{#if viewMode === 'mobile-cards'}
			<div class="space-y-3">
				<div class="text-xs text-zinc-400 px-1 font-semibold flex items-center justify-between">
					<span>Danh sách 7 ngày trong tuần:</span>
					{#if activeMember}
						<span class="text-pink-300">Đang chọn: <strong>{activeMember.name}</strong></span>
					{:else}
						<span class="text-amber-400">Hãy chọn hoặc nhập tên của bạn ở trên để tick lịch</span>
					{/if}
				</div>

				<div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
					{#each weekDays as day (day.dateStr)}
						{@const dayStat = dayStats[day.dateStr]}
						{@const count = dayStat?.count || 0}
						{@const isTop = maxAvailableCount > 0 && count === maxAvailableCount}
						{@const isUserAvailable = activeMember?.availableDates?.includes(day.dateStr)}

						<div class="p-4 rounded-2xl border transition-all relative overflow-hidden backdrop-blur-md {isTop && count > 0 ? 'bg-gradient-to-br from-amber-950/20 to-zinc-900/80 border-amber-500/40 shadow-lg' : 'bg-zinc-900/40 border-zinc-800/80'}">
							<div class="flex items-start justify-between gap-3 mb-3">
								<div class="flex items-center gap-2.5">
									<div class="w-12 h-12 rounded-xl bg-zinc-950 border border-zinc-800 flex flex-col items-center justify-center font-mono">
										<span class="text-[10px] font-bold uppercase {day.isWeekend ? 'text-amber-400' : 'text-zinc-400'}">{day.name}</span>
										<span class="text-base font-black {day.isToday ? 'text-pink-400' : 'text-white'}">{day.dayNum}</span>
									</div>
									<div>
										<div class="font-bold text-sm text-zinc-100 flex items-center gap-1.5">
											<span>{day.fullName}</span>
											{#if day.isToday}
												<span class="text-[9px] px-1.5 py-0.2 rounded-full bg-pink-500 text-white font-extrabold uppercase">Hôm nay</span>
											{/if}
										</div>
										<span class="text-[11px] text-zinc-400">Ngày {day.dateStr}</span>
									</div>
								</div>

								<!-- Total Available Pill -->
								<div class="text-right shrink-0">
									<div class="inline-flex items-center gap-1 px-2.5 py-1 rounded-xl {isTop && count > 0 ? 'bg-amber-500/20 text-amber-300 border border-amber-400/40 font-bold' : 'bg-zinc-950 text-zinc-300 border border-zinc-800'} text-xs font-mono">
										<span>🔥</span>
										<span class="font-extrabold text-sm">{count}</span>
										<span class="text-zinc-500 text-[10px]">/{members.length}</span>
									</div>
									{#if isTop && count > 0}
										<div class="text-[9px] text-amber-400 font-bold tracking-wide mt-0.5">Tối ưu nhất ✨</div>
									{/if}
								</div>
							</div>

							<!-- Avatars of who is available -->
							<div class="space-y-2 pt-2 border-t border-zinc-800/60">
								<div class="text-[11px] text-zinc-400 font-medium flex items-center justify-between">
									<span>Người rảnh ({count}):</span>
									{#if count > 0}
										<span class="text-[10px] text-emerald-400 font-semibold truncate max-w-[160px]">
											{dayStat.members.map((m) => m.name).join(', ')}
										</span>
									{:else}
										<span class="text-[10px] text-zinc-500 italic">Chưa có ai rảnh</span>
									{/if}
								</div>

								<!-- Quick Action: Tick for currently active persona -->
								{#if activeMember}
									<button
										type="button"
										onclick={() => handleToggleDate(activeMember.id, day.dateStr)}
										class="w-full py-2.5 px-3 rounded-xl font-bold text-xs transition-all flex items-center justify-center gap-2 cursor-pointer border {isUserAvailable
											? 'bg-emerald-500/20 border-emerald-500 text-emerald-300 shadow-[0_0_12px_rgba(16,185,129,0.25)]'
											: 'bg-zinc-950 border-zinc-700/80 text-zinc-300 hover:border-pink-500/60'}"
									>
										{#if isUserAvailable}
											<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4 text-emerald-400">
												<path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clip-rule="evenodd" />
											</svg>
											<span>{activeMember.name}: Tôi RẢNH ngày này</span>
										{:else}
											<span class="text-zinc-400">+ Bấm để {activeMember.name} báo RẢNH</span>
										{/if}
									</button>
								{/if}
							</div>
						</div>
					{/each}
				</div>
			</div>
		{/if}

		<!-- TABLE MATRIX VIEW (DESKTOP & HORIZONTAL SCROLL) -->
		{#if viewMode === 'table'}
			<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl sm:rounded-3xl shadow-2xl backdrop-blur-xl overflow-hidden">
				{#if isLoading}
					<div class="p-16 text-center text-zinc-400 flex flex-col items-center justify-center gap-3">
						<div class="w-8 h-8 rounded-full border-2 border-pink-500 border-t-transparent animate-spin"></div>
						<span class="text-xs font-mono font-semibold">Đang tải danh sách lịch rảnh...</span>
					</div>
				{:else}
					<div class="overflow-x-auto touch-pan-x">
						<table class="w-full text-left border-collapse select-none">
							<!-- TABLE HEADER -->
							<thead>
								<tr class="bg-zinc-950/85 border-b border-zinc-800 text-xs">
									<th class="py-3 sm:py-4 px-3 sm:px-5 w-36 sm:w-56 font-bold text-zinc-300 uppercase tracking-wider sticky left-0 bg-zinc-950 z-20 border-r border-zinc-800/80 shadow-[2px_0_10px_rgba(0,0,0,0.6)]">
										<div class="flex items-center justify-between">
											<span>Tên ({members.length})</span>
										</div>
									</th>

									{#each weekDays as day (day.dateStr)}
										<th class="py-2.5 sm:py-3 px-1.5 sm:px-3 text-center min-w-[78px] sm:min-w-[100px] border-r border-zinc-800/50 last:border-r-0 {day.isToday ? 'bg-pink-500/10' : ''}">
											<div class="flex flex-col items-center justify-center">
												<span class="text-[10px] sm:text-[11px] font-bold {day.isWeekend ? 'text-amber-400' : 'text-zinc-400'}">
													{day.name}
												</span>
												<span class="font-mono text-sm sm:text-base font-extrabold {day.isToday ? 'text-pink-400 underline underline-offset-4' : 'text-zinc-100'}">
													{day.dayNum}
												</span>
												{#if day.isToday}
													<span class="text-[8px] sm:text-[9px] px-1 rounded-full bg-pink-500 text-white font-bold uppercase tracking-wider mt-0.5">
														Nay
													</span>
												{/if}
											</div>
										</th>
									{/each}

									<th class="py-2.5 sm:py-3 px-2 text-center w-16 sm:w-20 font-bold text-zinc-400 text-[10px] sm:text-[11px] uppercase tracking-wider">
										Tổng
									</th>
								</tr>
							</thead>

							<!-- TABLE BODY -->
							<tbody class="divide-y divide-zinc-800/60 text-sm font-medium">
								{#if members.length === 0}
									<tr>
										<td colspan="9" class="py-12 text-center text-zinc-500 text-xs italic">
											Chưa có ai trong danh sách. Hãy nhập tên và mã PIN 4 số ở phía trên để bắt đầu! 🚀
										</td>
									</tr>
								{/if}

								{#each members as member (member.id)}
									{@const isCurrentSelected = activeMemberId === member.id}
									{@const isUnlocked = hasPinAccess(member.id)}
									{@const countThisWeek = weekDays.filter((d) => member.availableDates?.includes(d.dateStr)).length}
									<tr class="hover:bg-zinc-800/30 transition-colors {isCurrentSelected ? 'bg-pink-500/5' : ''}">
										<!-- Member Name Column (Sticky) -->
										<td class="py-3 px-3 sm:px-5 sticky left-0 bg-zinc-950/95 z-10 border-r border-zinc-800/80 shadow-[2px_0_10px_rgba(0,0,0,0.6)]">
											<div class="flex items-center justify-between gap-1.5">
												<div class="flex items-center gap-2 overflow-hidden">
													<div
														class="w-6 h-6 sm:w-7 sm:h-7 rounded-lg text-white font-black text-[11px] sm:text-xs flex items-center justify-center shrink-0 shadow"
														style="background-color: {member.color || '#f43f86'}"
													>
														{member.name.charAt(0).toUpperCase()}
													</div>
													<div class="truncate">
														<div class="font-bold text-xs sm:text-sm text-zinc-100 flex items-center gap-1 truncate">
															<span class="truncate">{member.name}</span>
															{#if isUnlocked}
																<span class="shrink-0 text-[9px] px-1 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-semibold" title="Đã xác thực PIN trên máy này">
																	🔓 Tôi
																</span>
															{:else}
																<span class="shrink-0 text-[9px] px-1 rounded bg-zinc-800 text-zinc-400 border border-zinc-700" title="Chưa nhập PIN">
																	🔒 Khóa
																</span>
															{/if}
														</div>
													</div>
												</div>

												<!-- Action buttons -->
												<div class="flex items-center gap-1 shrink-0">
													{#if !isUnlocked}
														<button
															type="button"
															onclick={() => openUnlockPinModal(member)}
															class="text-[10px] text-pink-400 hover:text-pink-300 p-1 hover:bg-pink-500/10 rounded transition-all font-semibold"
															title="Nhập PIN để mở khóa chỉnh sửa"
														>
															Mở
														</button>
													{/if}
													<button
														type="button"
														onclick={() => handleDeleteMember(member)}
														class="text-zinc-600 hover:text-rose-400 p-1 hover:bg-rose-500/10 rounded transition-all shrink-0"
														title="Xóa thành viên (Cần PIN)"
													>
														<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
															<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
														</svg>
													</button>
												</div>
											</div>
										</td>

										<!-- 7 Checkable Availability Cells -->
										{#each weekDays as day (day.dateStr)}
											{@const isAvailable = member.availableDates?.includes(day.dateStr)}
											<td class="p-1.5 sm:p-2 text-center border-r border-zinc-800/40 last:border-r-0 {day.isToday ? 'bg-pink-500/5' : ''}">
												<button
													type="button"
													onclick={() => handleToggleDate(member.id, day.dateStr)}
													class="w-full h-10 sm:h-11 rounded-xl transition-all flex items-center justify-center cursor-pointer active:scale-95 {isAvailable
														? 'bg-emerald-500/20 border-2 border-emerald-500 text-emerald-300 shadow-[0_0_10px_rgba(16,185,129,0.3)] hover:bg-emerald-500/30'
														: 'bg-zinc-950/60 border border-zinc-800/80 text-zinc-600 hover:border-zinc-700 hover:text-zinc-400'}"
													title="{member.name} {isAvailable ? 'RẢNH' : 'BẬN'} vào {day.name} ({day.dateStr}) {!isUnlocked ? '(Cần PIN để sửa)' : ''}"
												>
													{#if isAvailable}
														<span class="flex items-center gap-1 font-bold text-[11px] sm:text-xs">
															<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3.5 h-3.5 sm:w-4 sm:h-4 text-emerald-400">
																<path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clip-rule="evenodd" />
															</svg>
															<span class="hidden xs:inline">Rảnh</span>
														</span>
													{:else}
														<span class="text-xs font-mono text-zinc-600 opacity-40">-</span>
													{/if}
												</button>
											</td>
										{/each}

										<td class="py-2 px-1 text-center">
											<span class="font-mono text-[11px] sm:text-xs font-bold px-2 py-0.5 rounded-full {countThisWeek > 0 ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40' : 'text-zinc-600 bg-zinc-950'}">
												{countThisWeek}
											</span>
										</td>
									</tr>
								{/each}
							</tbody>

							<!-- TABLE FOOTER -->
							<tfoot>
								<tr class="bg-zinc-950 border-t-2 border-zinc-800 text-xs font-bold">
									<td class="py-3 px-3 sm:px-5 sticky left-0 bg-zinc-950 z-10 border-r border-zinc-800 text-zinc-300">
										<div class="flex items-center gap-1.5">
											<span>🔥</span>
											<span class="text-[11px] sm:text-xs">Tổng rảnh:</span>
										</div>
									</td>

									{#each weekDays as day (day.dateStr)}
										{@const count = dayStats[day.dateStr]?.count || 0}
										{@const isTopDay = maxAvailableCount > 0 && count === maxAvailableCount}
										<td class="py-2.5 px-1 sm:px-2 text-center border-r border-zinc-800/40 last:border-r-0 {isTopDay ? 'bg-amber-500/10' : ''}">
											<div class="flex flex-col items-center justify-center">
												<div class="flex items-baseline gap-0.5">
													<span class="font-mono text-sm sm:text-base font-black {isTopDay ? 'text-amber-400 scale-110 drop-shadow-[0_0_8px_rgba(251,191,36,0.5)]' : count > 0 ? 'text-emerald-400' : 'text-zinc-600'}">
														{count}
													</span>
													<span class="text-[9px] text-zinc-500 font-mono">/{members.length}</span>
												</div>

												{#if isTopDay && count > 0}
													<span class="px-1 py-0.2 rounded-full bg-amber-500/20 border border-amber-400/50 text-amber-300 text-[8px] sm:text-[9px] font-bold uppercase tracking-wider mt-0.5 animate-pulse whitespace-nowrap">
														Top 🌟
													</span>
												{/if}
											</div>
										</td>
									{/each}

									<td class="text-center text-zinc-500 text-[10px] font-mono">
										-
									</td>
								</tr>
							</tfoot>
						</table>
					</div>
				{/if}
			</div>
		{/if}

		<!-- SUMMARY CARD: SUGGESTED DAYS -->
		{#if members.length > 0 && maxAvailableCount > 0}
			{@const bestDays = weekDays.filter((d) => (dayStats[d.dateStr]?.count || 0) === maxAvailableCount)}
			<div class="bg-gradient-to-r from-amber-950/20 via-zinc-900/60 to-zinc-900/40 border border-amber-500/30 rounded-2xl sm:rounded-3xl p-4 sm:p-6 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
				<div class="flex items-center gap-3">
					<div class="w-10 h-10 sm:w-12 sm:h-12 rounded-2xl bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-xl sm:text-2xl shrink-0 shadow-[0_0_15px_rgba(251,191,36,0.2)]">
						🎯
					</div>
					<div>
						<h3 class="font-bold text-sm sm:text-base text-zinc-100 flex items-center gap-2">
							Ngày đi chơi lý tưởng nhất tuần này:
						</h3>
						<p class="text-xs text-zinc-400 mt-0.5">
							Có <strong class="text-amber-400 font-mono text-sm">{maxAvailableCount}/{members.length}</strong> bạn cùng rảnh vào:
							{#each bestDays as bd, idx}
								<strong class="text-pink-300 font-semibold">{bd.name} ({bd.dayNum}/{bd.monthNum})</strong>{idx < bestDays.length - 1 ? ', ' : ''}
							{/each}
						</p>
					</div>
				</div>

				<div class="flex items-center gap-2 w-full md:w-auto justify-end">
					<button
						onclick={() => {
							const text = `🎉 Lịch đi chơi tuần này:\n` + bestDays.map((d) => `👉 ${d.name} (${d.dayNum}/${d.monthNum}): ${maxAvailableCount}/${members.length} người rảnh (${dayStats[d.dateStr].members.map((m) => m.name).join(', ')})`).join('\n') + `\nXem và tick lịch tại: ${window.location.href}`;
							navigator.clipboard.writeText(text);
							alert('Đã sao chép nội dung gợi ý vào Clipboard!');
						}}
						class="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 hover:text-white rounded-xl text-xs font-bold transition-all border border-zinc-700 cursor-pointer flex items-center gap-1.5 w-full md:w-auto justify-center shadow-lg"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 0 1-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 0 1 1.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 0 0-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 0 1-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 0 0-3.375-3.375h-1.5a1.125 1.125 0 0 1-1.125-1.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H9.75" />
						</svg>
						Copy kết quả gửi nhóm
					</button>
				</div>
			</div>
		{/if}
	</main>

	<!-- PIN VERIFICATION MODAL (Khi người dùng đổi thiết bị hoặc mở khóa) -->
	{#if isPinModalOpen && pinModalTargetMember}
		<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fadeIn">
			<div class="bg-zinc-950 border border-zinc-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
				<div class="text-center space-y-1">
					<div class="w-12 h-12 rounded-2xl bg-pink-500/20 border border-pink-500/40 text-pink-300 text-2xl flex items-center justify-center mx-auto mb-2">
						🔒
					</div>
					<h3 class="font-black text-lg text-white">Xác Nhận Mã PIN 4 Số</h3>
					<p class="text-xs text-zinc-400">
						Nhập mã PIN 4 số của <strong class="text-pink-300 font-bold">{pinModalTargetMember.name}</strong> để mở quyền chỉnh sửa trên máy này.
					</p>
				</div>

				<form onsubmit={handleVerifyPinSubmit} class="space-y-3">
					<div>
						<input
							type="password"
							inputmode="numeric"
							maxlength="4"
							placeholder="• • • •"
							bind:value={pinModalInput}
							class="w-full bg-zinc-900 border border-zinc-700 text-center font-mono text-2xl tracking-[0.5em] py-2.5 rounded-2xl text-white focus:outline-none focus:ring-2 focus:ring-pink-500"
							autofocus
							required
						/>
					</div>

					{#if pinModalError}
						<p class="text-xs text-rose-400 font-semibold text-center">{pinModalError}</p>
					{/if}

					<div class="flex items-center gap-2 pt-2">
						<button
							type="button"
							onclick={() => (isPinModalOpen = false)}
							class="flex-1 py-2 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-zinc-300 text-xs font-bold border border-zinc-800 transition-all cursor-pointer"
						>
							Hủy
						</button>
						<button
							type="submit"
							disabled={isVerifyingPin}
							class="flex-1 py-2 rounded-xl bg-gradient-to-r from-pink-500 to-rose-500 hover:from-pink-600 hover:to-rose-600 text-white text-xs font-bold shadow-lg shadow-pink-500/25 transition-all cursor-pointer disabled:opacity-50"
						>
							{isVerifyingPin ? 'Đang kiểm tra...' : 'Mở khóa'}
						</button>
					</div>
				</form>
			</div>
		</div>
	{/if}
</div>

<style>
	.no-scrollbar::-webkit-scrollbar {
		display: none;
	}
	.no-scrollbar {
		-ms-overflow-style: none;
		scrollbar-width: none;
	}
</style>
