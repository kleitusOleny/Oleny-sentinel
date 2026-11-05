<script lang="ts">
	import { onMount } from 'svelte';
	import nekoIcon from '$lib/assets/neko-icon.png';
	import nanallyGif from '$lib/assets/nanally1.gif';

	interface HangoutMember {
		id: string;
		name: string;
		availableSlots: string[]; // e.g. "YYYY-MM-DD_morning", "YYYY-MM-DD_afternoon", "YYYY-MM-DD_evening"
		color?: string;
		updatedAt?: number;
		pinHash?: string;
	}

	type SlotType = 'morning' | 'afternoon' | 'evening';

	interface SlotDefinition {
		id: SlotType;
		label: string;
		shortLabel: string;
		icon: string;
		timeRange: string;
		color: string;
		activeClass: string;
	}

	const SLOTS: SlotDefinition[] = [
		{
			id: 'morning',
			label: 'Sáng',
			shortLabel: 'Sáng',
			icon: '🌅',
			timeRange: '08:00 - 12:00',
			color: '#f59e0b',
			activeClass: 'bg-amber-500/20 border-amber-500 text-amber-300'
		},
		{
			id: 'afternoon',
			label: 'Chiều',
			shortLabel: 'Chiều',
			icon: '☀️',
			timeRange: '13:00 - 17:00',
			color: '#3b82f6',
			activeClass: 'bg-sky-500/20 border-sky-500 text-sky-300'
		},
		{
			id: 'evening',
			label: 'Tối',
			shortLabel: 'Tối',
			icon: '🌙',
			timeRange: '18:00 - 22:00+',
			color: '#a855f7',
			activeClass: 'bg-purple-500/20 border-purple-500 text-purple-300'
		}
	];

	const API_BASE = '/api';
	const PINS_STORAGE_KEY = 'hangout_member_pins_v1';

	// States
	let members = $state<HangoutMember[]>([]);
	let isLoading = $state(true);
	let errorMessage = $state('');
	let newMemberName = $state('');
	let newMemberPin = $state('');
	let isAddingMember = $state(false);
	let activeMemberId = $state<string>(''); // Thành viên hiện tại
	let viewMode = $state<'table' | 'mobile-cards'>('table'); // Chế độ xem: Bảng ma trận hay Thẻ theo ngày
	let activeSlotFilter = $state<string>('all'); // 'all', 'morning', 'afternoon', 'evening'

	// PIN Verification Modal
	let isPinModalOpen = $state(false);
	let pinModalTargetMember = $state<HangoutMember | null>(null);
	let pinModalInput = $state('');
	let pinModalError = $state('');
	let isVerifyingPin = $state(false);

	// Quản lý tuần (Thứ 2 của tuần hiện tại)
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

	// Thống kê chi tiết theo từng ngày và từng buổi
	let slotStats = $derived.by(() => {
		const stats: Record<string, {
			morning: HangoutMember[];
			afternoon: HangoutMember[];
			evening: HangoutMember[];
			any: HangoutMember[];
			maxCountInDay: number;
		}> = {};

		for (const day of weekDays) {
			const mKey = `${day.dateStr}_morning`;
			const aKey = `${day.dateStr}_afternoon`;
			const eKey = `${day.dateStr}_evening`;

			const morningMembers = members.filter((m) => m.availableSlots?.includes(mKey));
			const afternoonMembers = members.filter((m) => m.availableSlots?.includes(aKey));
			const eveningMembers = members.filter((m) => m.availableSlots?.includes(eKey));

			const anyMembers = members.filter((m) =>
				m.availableSlots?.some((s) => s.startsWith(day.dateStr))
			);

			const maxCountInDay = Math.max(
				morningMembers.length,
				afternoonMembers.length,
				eveningMembers.length
			);

			stats[day.dateStr] = {
				morning: morningMembers,
				afternoon: afternoonMembers,
				evening: eveningMembers,
				any: anyMembers,
				maxCountInDay
			};
		}
		return stats;
	});

	// Tìm số lượng người đông nhất trong một buổi bất kỳ trong tuần
	let maxSlotCount = $derived.by(() => {
		let max = 0;
		for (const day of weekDays) {
			const s = slotStats[day.dateStr];
			if (!s) continue;
			if (s.morning.length > max) max = s.morning.length;
			if (s.afternoon.length > max) max = s.afternoon.length;
			if (s.evening.length > max) max = s.evening.length;
		}
		return max;
	});

	// Danh sách các buổi "Hot" nhất (có số người rảnh = maxSlotCount)
	let topSlotsList = $derived.by(() => {
		if (maxSlotCount === 0) return [];
		const tops: { day: typeof weekDays[0]; slot: SlotDefinition; count: number; members: HangoutMember[] }[] = [];
		for (const day of weekDays) {
			const s = slotStats[day.dateStr];
			if (!s) continue;
			if (s.morning.length === maxSlotCount) {
				tops.push({ day, slot: SLOTS[0], count: maxSlotCount, members: s.morning });
			}
			if (s.afternoon.length === maxSlotCount) {
				tops.push({ day, slot: SLOTS[1], count: maxSlotCount, members: s.afternoon });
			}
			if (s.evening.length === maxSlotCount) {
				tops.push({ day, slot: SLOTS[2], count: maxSlotCount, members: s.evening });
			}
		}
		return tops;
	});

	// Thành viên đang được chọn (active persona)
	let activeMember = $derived.by(() => {
		return members.find((m) => m.id === activeMemberId) || null;
	});

	// Quản lý lưu trữ mã PIN trong localStorage
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
			console.error('Cannot save pin', err);
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

	function hasPinAccess(memberId: string): boolean {
		return Boolean(getStoredPin(memberId));
	}

	// Fetch API
	async function fetchMembers() {
		isLoading = true;
		try {
			const res = await fetch(`${API_BASE}/hangout/members`);
			if (!res.ok) throw new Error('Không thể tải lịch hẹn');
			const data = await res.json();
			members = data.map((m: any) => ({
				...m,
				availableSlots: m.availableSlots || []
			}));
			errorMessage = '';

			// Khôi phục active persona
			if (typeof window !== 'undefined') {
				const savedId = localStorage.getItem('hangout_active_member_id');
				if (savedId && members.some((m) => m.id === savedId) && hasPinAccess(savedId)) {
					activeMemberId = savedId;
				} else {
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

	// Thêm thành viên mới
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
				const created = {
					...data.member,
					availableSlots: data.member.availableSlots || []
				};
				members = [...members, created];
				saveStoredPin(created.id, pin);
				activeMemberId = created.id;
				if (typeof window !== 'undefined') {
					localStorage.setItem('hangout_active_member_id', created.id);
				}
				newMemberName = '';
				newMemberPin = '';
				alert(`🎉 Đã tạo thành viên "${name}" thành công!\nMã PIN [${pin}] đã được lưu tự động trên máy này. Bấm vào các buổi (Sáng/Chiều/Tối) để chọn lịch rảnh.`);
			} else {
				alert(data.message || 'Lỗi thêm thành viên');
			}
		} catch (err: any) {
			alert('Lỗi kết nối: ' + err.message);
		} finally {
			isAddingMember = false;
		}
	}

	// Mở popup nhập PIN
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

	// Toggle 1 buổi cụ thể (Sáng / Chiều / Tối)
	async function handleToggleSlot(memberId: string, slotStr: string) {
		const targetMember = members.find((m) => m.id === memberId);
		if (!targetMember) return;

		const pin = getStoredPin(memberId);
		if (!pin) {
			openUnlockPinModal(targetMember);
			return;
		}

		const currentSlots = targetMember.availableSlots || [];
		const isCurrentlyAvail = currentSlots.includes(slotStr);
		const newSlots = isCurrentlyAvail
			? currentSlots.filter((s) => s !== slotStr)
			: [...currentSlots, slotStr];

		// Optimistic update
		members = members.map((m) => (m.id === memberId ? { ...m, availableSlots: newSlots } : m));

		try {
			const res = await fetch(`${API_BASE}/hangout/members/${memberId}/toggle-slot`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ slot: slotStr, pin })
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = members.map((m) =>
					m.id === memberId
						? { ...data.member, availableSlots: data.member.availableSlots || [] }
						: m
				);
			} else {
				alert(data.message || 'Lỗi khi cập nhật buổi rảnh');
				removeStoredPin(memberId);
				members = members.map((m) => (m.id === memberId ? targetMember : m));
				openUnlockPinModal(targetMember);
			}
		} catch (err) {
			console.error('Toggle slot failed', err);
			members = members.map((m) => (m.id === memberId ? targetMember : m));
		}
	}

	// Toggle nguyên cả ngày (Sáng + Chiều + Tối)
	async function handleToggleFullDay(memberId: string, dateStr: string) {
		const targetMember = members.find((m) => m.id === memberId);
		if (!targetMember) return;

		const pin = getStoredPin(memberId);
		if (!pin) {
			openUnlockPinModal(targetMember);
			return;
		}

		const mSlot = `${dateStr}_morning`;
		const aSlot = `${dateStr}_afternoon`;
		const eSlot = `${dateStr}_evening`;

		const currentSlots = targetMember.availableSlots || [];
		const hasAll = currentSlots.includes(mSlot) && currentSlots.includes(aSlot) && currentSlots.includes(eSlot);

		let newSlots: string[];
		if (hasAll) {
			newSlots = currentSlots.filter((s) => s !== mSlot && s !== aSlot && s !== eSlot);
		} else {
			newSlots = [...new Set([...currentSlots, mSlot, aSlot, eSlot])];
		}

		// Optimistic update
		members = members.map((m) => (m.id === memberId ? { ...m, availableSlots: newSlots } : m));

		try {
			const res = await fetch(`${API_BASE}/hangout/members/${memberId}/toggle-day`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ date: dateStr, pin })
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = members.map((m) =>
					m.id === memberId
						? { ...data.member, availableSlots: data.member.availableSlots || [] }
						: m
				);
			} else {
				alert(data.message || 'Lỗi khi cập nhật cả ngày');
				removeStoredPin(memberId);
				members = members.map((m) => (m.id === memberId ? targetMember : m));
				openUnlockPinModal(targetMember);
			}
		} catch (err) {
			console.error('Toggle full day failed', err);
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

		if (!confirm(`Bạn có chắc muốn xóa lịch của "${member.name}"?`)) return;

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
			alert('Lỗi kết nối: ' + err.message);
		}
	}

	function selectActiveMember(id: string) {
		activeMemberId = id;
		if (typeof window !== 'undefined') {
			localStorage.setItem('hangout_active_member_id', id);
		}
		const m = members.find((x) => x.id === id);
		if (m && !hasPinAccess(id)) {
			openUnlockPinModal(m);
		}
	}

	// Auto check screen size
	onMount(() => {
		fetchMembers();
		if (typeof window !== 'undefined' && window.innerWidth < 768) {
			viewMode = 'mobile-cards';
		}
	});
</script>

<svelte:head>
	<title>Lịch Hẹn Đi Chơi Theo Buổi | Sentinel Hangout</title>
</svelte:head>

<div class="min-h-screen bg-gradient-to-b from-zinc-950 via-zinc-900 to-black text-zinc-100 flex flex-col font-sans selection:bg-pink-500 selection:text-white">
	<!-- TOP NAVIGATION / BRAND BAR -->
	<header class="border-b border-zinc-800/80 bg-zinc-950/70 backdrop-blur-xl sticky top-0 z-30 transition-all">
		<div class="max-w-7xl mx-auto px-3 sm:px-6 py-2.5 sm:py-3 flex items-center justify-between gap-3">
			<div class="flex items-center gap-2 sm:gap-3">
				<a href="/" class="flex items-center gap-2 group cursor-pointer" title="Về trang chủ Sentinel">
					<div class="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-pink-500/10 border border-pink-500/30 flex items-center justify-center p-1 group-hover:scale-105 transition-transform">
						<img src={nekoIcon} alt="Neko" class="w-full h-full object-contain drop-shadow" />
					</div>
					<div>
						<div class="text-[10px] sm:text-xs font-mono font-bold tracking-widest text-pink-400 uppercase">
							SENTINEL
						</div>
						<div class="text-xs sm:text-sm font-extrabold text-white flex items-center gap-1.5 leading-none">
							<span>Lịch Đi Chơi</span>
							<span class="text-[10px] font-normal px-1.5 py-0.5 rounded-full bg-pink-500/20 text-pink-300 border border-pink-500/30">
								Theo Buổi 🕒
							</span>
						</div>
					</div>
				</a>
			</div>

			<!-- Center Nanally Gif Mascot -->
			<div class="hidden md:flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900/60 border border-zinc-800 text-xs text-zinc-400">
				<img src={nanallyGif} alt="Nanally" class="w-6 h-6 object-contain" />
				<span class="font-medium">Chọn buổi Sáng / Chiều / Tối cùng bạn bè</span>
			</div>

			<!-- Right: Quick Status -->
			<div class="flex items-center gap-2">
				<button
					onclick={fetchMembers}
					class="p-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-white hover:border-zinc-700 transition-all cursor-pointer text-xs flex items-center gap-1"
					title="Làm mới dữ liệu"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5 {isLoading ? 'animate-spin text-pink-400' : ''}">
						<path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
					</svg>
					<span class="hidden sm:inline">Làm mới</span>
				</button>
			</div>
		</div>
	</header>

	<!-- MAIN CONTENT -->
	<main class="flex-1 max-w-7xl w-full mx-auto p-3 sm:p-6 space-y-4 sm:space-y-6">
		<!-- HERO / QUICK ADD MEMBER -->
		<div class="bg-gradient-to-br from-pink-950/20 via-zinc-900/50 to-zinc-950 border border-pink-500/20 rounded-2xl sm:rounded-3xl p-4 sm:p-6 shadow-2xl backdrop-blur-md">
			<div class="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
				<div>
					<div class="flex items-center gap-2">
						<span class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-pink-500/20 border border-pink-500/40 text-pink-300">
							Lên kèo tự do • Không cần login
						</span>
						<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-300 border border-amber-500/30">
							🔒 Khóa bằng mã PIN 4 số
						</span>
					</div>
					<h1 class="text-lg sm:text-2xl font-black text-white mt-1">
						Kèo Này Đi Mấy Giờ? 🎯
					</h1>
					<p class="text-xs sm:text-sm text-zinc-400 mt-0.5">
						Mỗi ngày được chia thành 3 ca: <strong class="text-amber-400">🌅 Sáng (8h-12h)</strong>, <strong class="text-sky-400">☀️ Chiều (13h-17h)</strong>, <strong class="text-purple-400">🌙 Tối (18h-22h+)</strong>. Bấm vào buổi để tick rảnh!
					</p>
				</div>

				<!-- Quick Add Member Form -->
				<form onsubmit={handleAddMember} class="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 bg-zinc-950/80 p-2 rounded-2xl border border-zinc-800 shadow-inner">
					<div class="flex items-center gap-2">
						<input
							type="text"
							bind:value={newMemberName}
							placeholder="Nhập tên bạn (vd: Oleny)..."
							maxlength="20"
							disabled={isAddingMember}
							class="w-full sm:w-44 bg-zinc-900 border border-zinc-700/80 rounded-xl px-3 py-2 text-xs sm:text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-pink-500"
							required
						/>
						<input
							type="password"
							inputmode="numeric"
							pattern="[0-9]{4}"
							maxlength="4"
							bind:value={newMemberPin}
							placeholder="PIN 4 số"
							disabled={isAddingMember}
							title="Mã PIN 4 số để bạn sửa/xóa lịch của chính mình"
							class="w-24 bg-zinc-900 border border-zinc-700/80 rounded-xl px-2.5 py-2 text-xs sm:text-sm text-center font-mono tracking-widest text-pink-300 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-pink-500"
							required
						/>
					</div>
					<button
						type="submit"
						disabled={isAddingMember}
						class="px-4 py-2 rounded-xl bg-pink-600 hover:bg-pink-500 active:scale-95 text-white font-bold text-xs sm:text-sm transition-all shadow-lg shadow-pink-600/30 disabled:opacity-50 cursor-pointer flex items-center justify-center gap-1.5 whitespace-nowrap"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
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

		<!-- CONTROLS: WEEK NAVIGATOR, PERSONA SELECTOR & SLOT FILTER -->
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

				<!-- View Switcher & Slot Quick Filter -->
				<div class="flex items-center gap-2 flex-wrap">
					<!-- Slot Quick Filter (Chỉ áp dụng khi xem theo ngày hoặc lọc cột) -->
					<div class="flex items-center bg-zinc-950 p-1 rounded-xl border border-zinc-800 text-[11px] font-semibold">
						<button
							type="button"
							onclick={() => (activeSlotFilter = 'all')}
							class="px-2 py-1 rounded-lg transition-all cursor-pointer {activeSlotFilter === 'all' ? 'bg-zinc-800 text-white font-bold' : 'text-zinc-500 hover:text-zinc-300'}"
						>
							Tất cả ca
						</button>
						<button
							type="button"
							onclick={() => (activeSlotFilter = 'morning')}
							class="px-2 py-1 rounded-lg transition-all cursor-pointer {activeSlotFilter === 'morning' ? 'bg-amber-500/20 text-amber-300 font-bold border border-amber-500/30' : 'text-zinc-500 hover:text-zinc-300'}"
							title="08:00 - 12:00"
						>
							🌅 Sáng
						</button>
						<button
							type="button"
							onclick={() => (activeSlotFilter = 'afternoon')}
							class="px-2 py-1 rounded-lg transition-all cursor-pointer {activeSlotFilter === 'afternoon' ? 'bg-sky-500/20 text-sky-300 font-bold border border-sky-500/30' : 'text-zinc-500 hover:text-zinc-300'}"
							title="13:00 - 17:00"
						>
							☀️ Chiều
						</button>
						<button
							type="button"
							onclick={() => (activeSlotFilter = 'evening')}
							class="px-2 py-1 rounded-lg transition-all cursor-pointer {activeSlotFilter === 'evening' ? 'bg-purple-500/20 text-purple-300 font-bold border border-purple-500/30' : 'text-zinc-500 hover:text-zinc-300'}"
							title="18:00 - 22:00+"
						>
							🌙 Tối
						</button>
					</div>

					<!-- View Mode Switcher -->
					<div class="flex items-center bg-zinc-950 p-1 rounded-xl border border-zinc-800 text-[11px] font-semibold">
						<button
							type="button"
							onclick={() => (viewMode = 'table')}
							class="px-2.5 py-1 rounded-lg transition-all cursor-pointer {viewMode === 'table' ? 'bg-pink-500/20 text-pink-300 font-bold border border-pink-500/30' : 'text-zinc-400'}"
						>
							Bảng ca chi tiết
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
			</div>

			<!-- User Persona Bar -->
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

		<!-- MOBILE CARDS VIEW: CHI TIẾT THEO NGÀY VÀ THEO BUỔI -->
		{#if viewMode === 'mobile-cards'}
			<div class="space-y-3">
				<div class="text-xs text-zinc-400 px-1 font-semibold flex items-center justify-between">
					<span>Lịch rảnh từng buổi trong 7 ngày:</span>
					{#if activeMember}
						<span class="text-pink-300">Đang chọn: <strong>{activeMember.name}</strong></span>
					{:else}
						<span class="text-amber-400">Hãy bấm vào tên của bạn ở trên để tick lịch</span>
					{/if}
				</div>

				<div class="grid grid-cols-1 md:grid-cols-2 gap-3.5">
					{#each weekDays as day (day.dateStr)}
						{@const s = slotStats[day.dateStr]}
						{@const isTopDay = maxSlotCount > 0 && s?.maxCountInDay === maxSlotCount}

						<div class="p-4 rounded-2xl border transition-all relative overflow-hidden backdrop-blur-md {isTopDay ? 'bg-gradient-to-br from-amber-950/20 via-zinc-900/60 to-zinc-900/90 border-amber-500/40 shadow-lg' : 'bg-zinc-900/40 border-zinc-800/80'}">
							<!-- Header of Day Card -->
							<div class="flex items-start justify-between gap-2 mb-3">
								<div class="flex items-center gap-2.5">
									<div class="w-12 h-12 rounded-xl bg-zinc-950 border border-zinc-800 flex flex-col items-center justify-center font-mono shrink-0 {day.isToday ? 'border-pink-500 text-pink-400' : ''}">
										<span class="text-[10px] font-bold text-zinc-400">{day.name}</span>
										<span class="text-lg font-black leading-none">{day.dayNum}</span>
									</div>
									<div>
										<div class="text-xs font-bold text-zinc-200 flex items-center gap-1.5">
											<span>{day.fullName}</span>
											{#if day.isToday}
												<span class="px-1.5 py-0.2 rounded-full bg-pink-500 text-white text-[9px] font-black uppercase">
													Hôm nay
												</span>
											{/if}
										</div>
										<div class="text-[11px] text-zinc-500 font-mono">
											{day.dateStr}
										</div>
									</div>
								</div>

								<!-- Nút chọn nhanh cả ngày cho Persona đang active -->
								{#if activeMember}
									{@const mSlot = `${day.dateStr}_morning`}
									{@const aSlot = `${day.dateStr}_afternoon`}
									{@const eSlot = `${day.dateStr}_evening`}
									{@const hasAll = activeMember.availableSlots?.includes(mSlot) && activeMember.availableSlots?.includes(aSlot) && activeMember.availableSlots?.includes(eSlot)}
									<button
										type="button"
										onclick={() => handleToggleFullDay(activeMember.id, day.dateStr)}
										class="px-2 py-1 rounded-lg text-[10px] font-bold border transition-all cursor-pointer {hasAll ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40' : 'bg-zinc-950 text-zinc-400 border-zinc-800 hover:text-white'}"
										title="Bấm để chọn hoặc bỏ chọn toàn bộ 3 ca trong ngày"
									>
										{hasAll ? '✓ Rảnh cả ngày' : '+ Chọn cả ngày'}
									</button>
								{/if}
							</div>

							<!-- 3 BUỔI CỦA NGÀY (Sáng, Chiều, Tối) -->
							<div class="space-y-2">
								{#each SLOTS as slot (slot.id)}
									{#if activeSlotFilter === 'all' || activeSlotFilter === slot.id}
										{@const slotKey = `${day.dateStr}_${slot.id}`}
										{@const slotMembers = slot.id === 'morning' ? s?.morning : slot.id === 'afternoon' ? s?.afternoon : s?.evening}
										{@const count = slotMembers?.length || 0}
										{@const isMaxSlot = maxSlotCount > 0 && count === maxSlotCount}
										{@const isMySlotActive = activeMember?.availableSlots?.includes(slotKey)}

										<div class="p-2.5 rounded-xl border transition-all {isMaxSlot && count > 0 ? 'bg-amber-500/10 border-amber-500/40' : 'bg-zinc-950/70 border-zinc-800/80'}">
											<div class="flex items-center justify-between gap-2">
												<div class="flex items-center gap-2">
													<span class="text-base">{slot.icon}</span>
													<div>
														<span class="text-xs font-bold text-zinc-200">{slot.label}</span>
														<span class="text-[10px] text-zinc-500 font-mono ml-1">({slot.timeRange})</span>
													</div>
												</div>

												<!-- Slot Count Pill & Action Button -->
												<div class="flex items-center gap-2">
													<div class="flex items-center gap-1 font-mono text-xs font-bold {isMaxSlot && count > 0 ? 'text-amber-400' : count > 0 ? 'text-emerald-400' : 'text-zinc-600'}">
														<span>{count}/{members.length}</span>
														{#if isMaxSlot && count > 0}
															<span class="text-[10px]">👑</span>
														{/if}
													</div>

													{#if activeMember}
														<button
															type="button"
															onclick={() => handleToggleSlot(activeMember.id, slotKey)}
															class="px-2.5 py-1 rounded-lg text-xs font-bold border transition-all cursor-pointer active:scale-95 {isMySlotActive ? 'bg-emerald-500/25 border-emerald-500 text-emerald-300 shadow-[0_0_8px_rgba(16,185,129,0.3)]' : 'bg-zinc-900 border-zinc-800 text-zinc-500 hover:text-zinc-300 hover:border-zinc-700'}"
														>
															{isMySlotActive ? '✓ Rảnh' : '+ Báo'}
														</button>
													{/if}
												</div>
											</div>

											<!-- List Members Available in this Slot -->
											{#if slotMembers && slotMembers.length > 0}
												<div class="flex items-center gap-1.5 flex-wrap mt-2 pt-2 border-t border-zinc-800/50">
													{#each slotMembers as sm (sm.id)}
														<span
															class="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-zinc-900 border border-zinc-700/70 text-zinc-200"
														>
															<span class="w-1.5 h-1.5 rounded-full" style="background-color: {sm.color || '#f43f86'}"></span>
															<span>{sm.name}</span>
														</span>
													{/each}
												</div>
											{/if}
										</div>
									{/if}
								{/each}
							</div>
						</div>
					{/each}
				</div>
			</div>
		{/if}

		<!-- TABLE MATRIX VIEW (BẢNG MA TRẬN 7 NGÀY x 3 BUỔI) -->
		{#if viewMode === 'table'}
			<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-2xl sm:rounded-3xl shadow-2xl backdrop-blur-xl overflow-hidden">
				{#if isLoading}
					<div class="p-16 text-center text-zinc-400 flex flex-col items-center justify-center gap-3">
						<div class="w-8 h-8 rounded-full border-2 border-pink-500 border-t-transparent animate-spin"></div>
						<span class="text-xs font-mono font-semibold">Đang tải lịch rảnh theo ca...</span>
					</div>
				{:else}
					<div class="overflow-x-auto touch-pan-x">
						<table class="w-full text-left border-collapse select-none">
							<!-- THEAD: Tên & 7 Cột Ngày, mỗi ngày có 3 sub-cột (Sáng/Chiều/Tối) -->
							<thead>
								<!-- Hàng 1: Thứ Ngày -->
								<tr class="bg-zinc-950 border-b border-zinc-800 text-xs">
									<th
										rowspan="2"
										class="py-3 px-3 sm:px-5 w-36 sm:w-52 font-bold text-zinc-300 uppercase tracking-wider sticky left-0 bg-zinc-950 z-20 border-r border-zinc-800 shadow-[2px_0_10px_rgba(0,0,0,0.6)] align-middle"
									>
										<div class="flex items-center justify-between">
											<span>Tên ({members.length})</span>
										</div>
									</th>

									{#each weekDays as day (day.dateStr)}
										<th
											colspan="3"
											class="py-2 px-2 text-center border-r border-zinc-800/80 last:border-r-0 {day.isToday ? 'bg-pink-500/10' : ''}"
										>
											<div class="flex flex-col items-center justify-center">
												<div class="flex items-center gap-1">
													<span class="text-[11px] font-bold {day.isWeekend ? 'text-amber-400' : 'text-zinc-400'}">
														{day.name}
													</span>
													<span class="font-mono text-sm font-black {day.isToday ? 'text-pink-400 underline underline-offset-2' : 'text-zinc-100'}">
														{day.dayNum}
													</span>
													{#if day.isToday}
														<span class="text-[8px] px-1 rounded-full bg-pink-500 text-white font-black uppercase">
															Nay
														</span>
													{/if}
												</div>
											</div>
										</th>
									{/each}

									<th rowspan="2" class="py-2.5 px-2 text-center w-16 sm:w-20 font-bold text-zinc-400 text-[10px] sm:text-[11px] uppercase tracking-wider align-middle">
										Tổng ca
									</th>
								</tr>

								<!-- Hàng 2: Phân ca Sáng / Chiều / Tối -->
								<tr class="bg-zinc-950/90 border-b border-zinc-800 text-[10px] font-bold text-zinc-400">
									{#each weekDays as day (day.dateStr)}
										{#each SLOTS as slot, idx (slot.id)}
											<th
												class="py-1 px-1 text-center w-11 sm:w-14 border-r {idx === 2 ? 'border-zinc-800/80' : 'border-zinc-800/40'} {day.isToday ? 'bg-pink-500/5' : ''}"
												title="{slot.label}: {slot.timeRange}"
											>
												<span class="{slot.id === 'morning' ? 'text-amber-400' : slot.id === 'afternoon' ? 'text-sky-400' : 'text-purple-400'}">
													{slot.shortLabel}
												</span>
											</th>
										{/each}
									{/each}
								</tr>
							</thead>

							<!-- TBODY: Danh sách thành viên và các nút ca -->
							<tbody class="divide-y divide-zinc-800/60 text-sm font-medium">
								{#if members.length === 0}
									<tr>
										<td colspan="23" class="py-12 text-center text-zinc-500 text-xs italic">
											Chưa có ai trong danh sách. Hãy nhập tên và mã PIN 4 số ở phía trên để bắt đầu! 🚀
										</td>
									</tr>
								{/if}

								{#each members as member (member.id)}
									{@const countThisWeek = weekDays.reduce((acc, d) => {
										return acc + (SLOTS.filter((s) => member.availableSlots?.includes(`${d.dateStr}_${s.id}`)).length);
									}, 0)}
									{@const isCurrentSelected = activeMemberId === member.id}
									{@const isUnlocked = hasPinAccess(member.id)}

									<tr class="hover:bg-zinc-800/30 transition-colors {isCurrentSelected ? 'bg-pink-500/5' : ''}">
										<!-- Member Column (Sticky) -->
										<td class="py-2.5 px-3 sm:px-5 sticky left-0 bg-zinc-950/95 z-10 border-r border-zinc-800 shadow-[2px_0_10px_rgba(0,0,0,0.6)]">
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
																<span class="shrink-0 text-[9px] px-1 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-semibold" title="Đã mở khóa trên máy này">
																	🔓 Tôi
																</span>
															{:else}
																<span class="shrink-0 text-[9px] px-1 rounded bg-zinc-800 text-zinc-400 border border-zinc-700" title="Chưa nhập PIN">
																	🔒
																</span>
															{/if}
														</div>
													</div>
												</div>

												<!-- Action buttons -->
												<div class="flex items-center gap-0.5 shrink-0">
													{#if !isUnlocked}
														<button
															type="button"
															onclick={() => openUnlockPinModal(member)}
															class="text-[10px] text-pink-400 hover:text-pink-300 p-1 hover:bg-pink-500/10 rounded font-semibold"
															title="Nhập PIN để sửa"
														>
															Mở
														</button>
													{/if}
													<button
														type="button"
														onclick={() => handleDeleteMember(member)}
														class="text-zinc-600 hover:text-rose-400 p-1 hover:bg-rose-500/10 rounded transition-all"
														title="Xóa thành viên (Cần PIN)"
													>
														<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
															<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
														</svg>
													</button>
												</div>
											</div>
										</td>

										<!-- 7 Ngày x 3 Ca = 21 Ô bấm -->
										{#each weekDays as day (day.dateStr)}
											{#each SLOTS as slot, idx (slot.id)}
												{@const slotKey = `${day.dateStr}_${slot.id}`}
												{@const isAvailable = member.availableSlots?.includes(slotKey)}
												<td class="p-1 text-center border-r {idx === 2 ? 'border-zinc-800/80' : 'border-zinc-800/30'} {day.isToday ? 'bg-pink-500/5' : ''}">
													<button
														type="button"
														onclick={() => handleToggleSlot(member.id, slotKey)}
														class="w-full h-8 sm:h-9 rounded-lg transition-all flex items-center justify-center cursor-pointer active:scale-90 {isAvailable
															? `${slot.activeClass} border shadow-sm`
															: 'bg-zinc-950/60 border border-zinc-800/60 text-zinc-700 hover:border-zinc-700 hover:text-zinc-400'}"
														title="{member.name} {isAvailable ? 'RẢNH' : 'BẬN'} {slot.label} ({slot.timeRange}) - {day.name} {day.dateStr} {!isUnlocked ? '(Cần PIN để sửa)' : ''}"
													>
														{#if isAvailable}
															<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3.5 h-3.5">
																<path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clip-rule="evenodd" />
															</svg>
														{:else}
															<span class="text-[10px] font-mono opacity-20">•</span>
														{/if}
													</button>
												</td>
											{/each}
										{/each}

										<!-- Tổng số ca rảnh trong tuần -->
										<td class="py-2 px-1 text-center">
											<span class="font-mono text-[11px] sm:text-xs font-bold px-2 py-0.5 rounded-full {countThisWeek > 0 ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40' : 'text-zinc-600 bg-zinc-950'}">
												{countThisWeek}
											</span>
										</td>
									</tr>
								{/each}
							</tbody>

							<!-- TFOOT: Tổng số người rảnh từng ca -->
							<tfoot>
								<tr class="bg-zinc-950 border-t-2 border-zinc-800 text-xs font-bold">
									<td class="py-3 px-3 sm:px-5 sticky left-0 bg-zinc-950 z-10 border-r border-zinc-800 text-zinc-300">
										<div class="flex items-center gap-1.5">
											<span>🔥</span>
											<span class="text-[11px]">Tổng rảnh:</span>
										</div>
									</td>

									{#each weekDays as day (day.dateStr)}
										{#each SLOTS as slot, idx (slot.id)}
											{@const slotKey = `${day.dateStr}_${slot.id}`}
											{@const count = members.filter((m) => m.availableSlots?.includes(slotKey)).length}
											{@const isMax = maxSlotCount > 0 && count === maxSlotCount}

											<td class="py-2 px-0.5 text-center border-r {idx === 2 ? 'border-zinc-800/80' : 'border-zinc-800/30'} {isMax && count > 0 ? 'bg-amber-500/10' : ''}">
												<div class="flex flex-col items-center justify-center">
													<span class="font-mono text-xs sm:text-sm font-black {isMax && count > 0 ? 'text-amber-400 drop-shadow-[0_0_8px_rgba(251,191,36,0.5)]' : count > 0 ? 'text-zinc-200' : 'text-zinc-600'}">
														{count}
													</span>
													{#if isMax && count > 0}
														<span class="text-[8px] text-amber-300 font-extrabold leading-none mt-0.5">Top</span>
													{/if}
												</div>
											</td>
										{/each}
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

		<!-- SUMMARY CARD: CÁC BUỔI ĐI CHƠI ĐÔNG NHẤT -->
		{#if members.length > 0 && topSlotsList.length > 0}
			<div class="bg-gradient-to-r from-amber-950/20 via-zinc-900/60 to-zinc-900/40 border border-amber-500/30 rounded-2xl sm:rounded-3xl p-4 sm:p-6 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
				<div class="flex items-center gap-3">
					<div class="w-10 h-10 sm:w-12 sm:h-12 rounded-2xl bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-xl sm:text-2xl shrink-0 shadow-[0_0_15px_rgba(251,191,36,0.2)]">
						🎯
					</div>
					<div>
						<h3 class="font-bold text-sm sm:text-base text-zinc-100 flex items-center gap-2">
							Buổi đi chơi lý tưởng nhất tuần:
						</h3>
						<p class="text-xs text-zinc-400 mt-0.5">
							Có <strong class="text-amber-400 font-mono text-sm">{maxSlotCount}/{members.length}</strong> bạn cùng rảnh vào:
							{#each topSlotsList as top, idx}
								<strong class="text-pink-300 font-semibold">{top.day.name} ({top.day.dayNum}/{top.day.monthNum}) - {top.slot.icon} {top.slot.label}</strong>{idx < topSlotsList.length - 1 ? ', ' : ''}
							{/each}
						</p>
					</div>
				</div>

				<div class="flex items-center gap-2 self-stretch md:self-auto justify-end">
					<span class="text-xs text-zinc-400 bg-zinc-950 px-3 py-1.5 rounded-xl border border-zinc-800">
						Chốt kèo ngay thôi! 🍻
					</span>
				</div>
			</div>
		{/if}
	</main>

	<!-- PIN VERIFICATION MODAL -->
	{#if isPinModalOpen && pinModalTargetMember}
		<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
			<div class="bg-zinc-950 border border-zinc-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl relative space-y-4">
				<button
					type="button"
					onclick={() => (isPinModalOpen = false)}
					class="absolute top-4 right-4 text-zinc-500 hover:text-white p-1 rounded-lg hover:bg-zinc-900 cursor-pointer"
					title="Đóng"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
						<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
					</svg>
				</button>

				<div class="flex items-center gap-3">
					<div
						class="w-10 h-10 rounded-2xl flex items-center justify-center text-white font-bold text-sm shadow"
						style="background-color: {pinModalTargetMember.color || '#f43f86'}"
					>
						{pinModalTargetMember.name.charAt(0).toUpperCase()}
					</div>
					<div>
						<h3 class="font-extrabold text-base text-white">Xác thực danh tính</h3>
						<p class="text-xs text-zinc-400">Thành viên: <strong class="text-pink-400">{pinModalTargetMember.name}</strong></p>
					</div>
				</div>

				<p class="text-xs text-zinc-400">
					Nhập <strong class="text-white">mã PIN 4 số</strong> bạn đã đặt khi tạo tên để mở khóa quyền chọn buổi rảnh trên thiết bị này:
				</p>

				{#if pinModalError}
					<div class="p-2.5 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-xs text-center font-medium">
						{pinModalError}
					</div>
				{/if}

				<form onsubmit={handleVerifyPinSubmit} class="space-y-4">
					<div>
						<input
							type="password"
							inputmode="numeric"
							pattern="[0-9]{4}"
							maxlength="4"
							bind:value={pinModalInput}
							placeholder="• • • •"
							class="w-full bg-zinc-900 border border-zinc-700 text-center font-mono text-2xl tracking-[0.5em] py-2.5 rounded-2xl text-white focus:outline-none focus:ring-2 focus:ring-pink-500"
							required
						/>
					</div>

					<div class="flex items-center justify-end gap-2">
						<button
							type="button"
							onclick={() => (isPinModalOpen = false)}
							class="px-4 py-2 rounded-xl text-xs font-bold text-zinc-400 hover:text-white hover:bg-zinc-900 transition-all cursor-pointer"
						>
							Hủy
						</button>
						<button
							type="submit"
							disabled={isVerifyingPin}
							class="px-5 py-2 rounded-xl bg-pink-600 hover:bg-pink-500 active:scale-95 text-white font-bold text-xs transition-all shadow-lg shadow-pink-600/30 disabled:opacity-50 cursor-pointer flex items-center gap-1.5"
						>
							{#if isVerifyingPin}
								<div class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
							{/if}
							Mở khóa
						</button>
					</div>
				</form>
			</div>
		</div>
	{/if}
</div>

<style>
	/* Hide scrollbar for tabs navigation */
	.no-scrollbar::-webkit-scrollbar {
		display: none;
	}
	.no-scrollbar {
		-ms-overflow-style: none;
		scrollbar-width: none;
	}
</style>
