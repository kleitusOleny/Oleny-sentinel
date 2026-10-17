<script lang="ts">
	import { onMount } from 'svelte';

	export interface CalendarEvent {
		id: string;
		title: string;
		description: string;
		startDate: string; // YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss
		endDate: string;
		allDay: boolean;
		color: string;
		category: string;
		reminderMinutes: number;
		recurrence: string;
		createdAt?: number;
	}

	interface Props {
		apiBase?: string;
	}

	let { apiBase = '/api' }: Props = $props();

	// State
	let events = $state<CalendarEvent[]>([]);
	let isLoading = $state(false);
	let errorMessage = $state('');

	// View mode: 'month' | 'week' | 'day' | 'agenda'
	let currentView = $state<'month' | 'week' | 'day' | 'agenda'>('month');

	// Current reference date (default to today)
	let currentDate = $state(new Date());

	// Filter & Search
	let searchQuery = $state('');
	let selectedCategoryFilter = $state('All');

	// Modal: Create / Edit
	let showEventModal = $state(false);
	let isEditing = $state(false);
	let modalId = $state('');
	let modalTitle = $state('');
	let modalDescription = $state('');
	let modalStartDate = $state('');
	let modalStartTime = $state('09:00');
	let modalEndDate = $state('');
	let modalEndTime = $state('10:00');
	let modalAllDay = $state(false);
	let modalColor = $state('indigo');
	let modalCategory = $state('Công việc');
	let modalReminder = $state(15);
	let modalRecurrence = $state('none');
	let isSaving = $state(false);

	// Modal: Delete confirmation
	let showDeleteConfirm = $state(false);
	let eventToDelete = $state<CalendarEvent | null>(null);

	const COLOR_OPTIONS = [
		{ id: 'indigo', label: 'Xanh Chàm', bg: 'bg-indigo-600', border: 'border-indigo-500', text: 'text-indigo-400', badge: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30' },
		{ id: 'blue', label: 'Xanh Dương', bg: 'bg-blue-600', border: 'border-blue-500', text: 'text-blue-400', badge: 'bg-blue-500/15 text-blue-300 border-blue-500/30' },
		{ id: 'emerald', label: 'Xanh Lá', bg: 'bg-emerald-600', border: 'border-emerald-500', text: 'text-emerald-400', badge: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30' },
		{ id: 'amber', label: 'Vàng Cam', bg: 'bg-amber-600', border: 'border-amber-500', text: 'text-amber-400', badge: 'bg-amber-500/15 text-amber-300 border-amber-500/30' },
		{ id: 'rose', label: 'Đỏ Hồng', bg: 'bg-rose-600', border: 'border-rose-500', text: 'text-rose-400', badge: 'bg-rose-500/15 text-rose-300 border-rose-500/30' },
		{ id: 'purple', label: 'Tím', bg: 'bg-purple-600', border: 'border-purple-500', text: 'text-purple-400', badge: 'bg-purple-500/15 text-purple-300 border-purple-500/30' },
		{ id: 'cyan', label: 'Xanh Ngọc', bg: 'bg-cyan-600', border: 'border-cyan-500', text: 'text-cyan-400', badge: 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30' }
	];

	const CATEGORIES = ['Công việc', 'Server', 'Họp', 'Cá nhân', 'Deadline', 'Bảo trì'];

	function getColorConfig(colorId: string) {
		return COLOR_OPTIONS.find((c) => c.id === colorId) || COLOR_OPTIONS[0];
	}

	function formatDateToYMD(d: Date): string {
		const year = d.getFullYear();
		const month = String(d.getMonth() + 1).padStart(2, '0');
		const day = String(d.getDate()).padStart(2, '0');
		return `${year}-${month}-${day}`;
	}

	async function fetchEvents() {
		isLoading = true;
		errorMessage = '';
		try {
			const res = await fetch(`${apiBase}/calendar/events`);
			if (!res.ok) throw new Error('Không thể tải danh sách sự kiện từ server.');
			events = await res.json();
		} catch (err: any) {
			errorMessage = err.message || 'Lỗi khi tải dữ liệu lịch.';
		} finally {
			isLoading = false;
		}
	}

	onMount(() => {
		fetchEvents();
	});

	// --- Navigation Helpers ---
	function goToToday() {
		currentDate = new Date();
	}

	function prevPeriod() {
		const d = new Date(currentDate);
		if (currentView === 'month') {
			d.setMonth(d.getMonth() - 1);
		} else if (currentView === 'week') {
			d.setDate(d.getDate() - 7);
		} else if (currentView === 'day') {
			d.setDate(d.getDate() - 1);
		}
		currentDate = d;
	}

	function nextPeriod() {
		const d = new Date(currentDate);
		if (currentView === 'month') {
			d.setMonth(d.getMonth() + 1);
		} else if (currentView === 'week') {
			d.setDate(d.getDate() + 7);
		} else if (currentView === 'day') {
			d.setDate(d.getDate() + 1);
		}
		currentDate = d;
	}

	let headerTitle = $derived.by(() => {
		const monthNames = [
			'Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6',
			'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'
		];
		const m = monthNames[currentDate.getMonth()];
		const y = currentDate.getFullYear();
		if (currentView === 'day') {
			return `${currentDate.getDate()} ${m}, ${y}`;
		}
		return `${m} năm ${y}`;
	});

	// Filtered Events
	let filteredEvents = $derived.by(() => {
		return events.filter((ev) => {
			if (selectedCategoryFilter !== 'All' && ev.category !== selectedCategoryFilter) {
				return false;
			}
			if (searchQuery.trim()) {
				const q = searchQuery.toLowerCase();
				const matchTitle = (ev.title || '').toLowerCase().includes(q);
				const matchDesc = (ev.description || '').toLowerCase().includes(q);
				const matchCat = (ev.category || '').toLowerCase().includes(q);
				if (!matchTitle && !matchDesc && !matchCat) return false;
			}
			return true;
		});
	});

	// --- Month View Grid Calculation ---
	interface CalendarDay {
		date: Date;
		dateStr: string;
		isCurrentMonth: boolean;
		isToday: boolean;
		events: CalendarEvent[];
	}

	let monthDays = $derived.by(() => {
		const days: CalendarDay[] = [];
		const year = currentDate.getFullYear();
		const month = currentDate.getMonth();

		const firstDayOfMonth = new Date(year, month, 1);
		const lastDayOfMonth = new Date(year, month + 1, 0);

		// T2 là ngày bắt đầu tuần (1), CN là (0) -> đổi thành T2=0, CN=6
		let startingDay = firstDayOfMonth.getDay() - 1;
		if (startingDay < 0) startingDay = 6;

		// Ngày của tháng trước để lấp đầy hàng đầu
		const prevMonthLastDay = new Date(year, month, 0).getDate();
		for (let i = startingDay - 1; i >= 0; i--) {
			const d = new Date(year, month - 1, prevMonthLastDay - i);
			const dateStr = formatDateToYMD(d);
			days.push({
				date: d,
				dateStr,
				isCurrentMonth: false,
				isToday: checkIfToday(d),
				events: getEventsForDate(dateStr)
			});
		}

		// Các ngày trong tháng hiện tại
		for (let day = 1; day <= lastDayOfMonth.getDate(); day++) {
			const d = new Date(year, month, day);
			const dateStr = formatDateToYMD(d);
			days.push({
				date: d,
				dateStr,
				isCurrentMonth: true,
				isToday: checkIfToday(d),
				events: getEventsForDate(dateStr)
			});
		}

		// Lấp đầy các ngày của tháng sau cho đủ bội số 7 (42 ô / 6 tuần)
		const remaining = 42 - days.length;
		for (let day = 1; day <= remaining; day++) {
			const d = new Date(year, month + 1, day);
			const dateStr = formatDateToYMD(d);
			days.push({
				date: d,
				dateStr,
				isCurrentMonth: false,
				isToday: checkIfToday(d),
				events: getEventsForDate(dateStr)
			});
		}

		return days;
	});

	function checkIfToday(d: Date): boolean {
		const today = new Date();
		return d.getDate() === today.getDate() &&
			d.getMonth() === today.getMonth() &&
			d.getFullYear() === today.getFullYear();
	}

	function getEventsForDate(dateStr: string): CalendarEvent[] {
		return filteredEvents.filter((ev) => {
			if (!ev.startDate) return false;
			const evStart = ev.startDate.substring(0, 10);
			const evEnd = ev.endDate ? ev.endDate.substring(0, 10) : evStart;
			return dateStr >= evStart && dateStr <= evEnd;
		});
	}

	// --- Modal Open Helpers ---
	function openCreateModal(dateStr?: string) {
		isEditing = false;
		modalId = '';
		modalTitle = '';
		modalDescription = '';
		const targetDate = dateStr || formatDateToYMD(new Date());
		modalStartDate = targetDate;
		modalEndDate = targetDate;
		modalStartTime = '09:00';
		modalEndTime = '10:00';
		modalAllDay = false;
		modalColor = 'indigo';
		modalCategory = 'Công việc';
		modalReminder = 15;
		modalRecurrence = 'none';
		showEventModal = true;
	}

	function openEditModal(ev: CalendarEvent, e?: MouseEvent) {
		if (e) {
			e.stopPropagation();
		}
		isEditing = true;
		modalId = ev.id;
		modalTitle = ev.title;
		modalDescription = ev.description || '';
		modalAllDay = ev.allDay;

		if (ev.startDate.includes('T')) {
			const parts = ev.startDate.split('T');
			modalStartDate = parts[0];
			modalStartTime = parts[1].substring(0, 5);
		} else {
			modalStartDate = ev.startDate;
			modalStartTime = '09:00';
		}

		if (ev.endDate && ev.endDate.includes('T')) {
			const parts = ev.endDate.split('T');
			modalEndDate = parts[0];
			modalEndTime = parts[1].substring(0, 5);
		} else {
			modalEndDate = ev.endDate || modalStartDate;
			modalEndTime = '10:00';
		}

		modalColor = ev.color || 'indigo';
		modalCategory = ev.category || 'Công việc';
		modalReminder = ev.reminderMinutes ?? 15;
		modalRecurrence = ev.recurrence || 'none';
		showEventModal = true;
	}

	async function handleSaveEvent(e: SubmitEvent) {
		e.preventDefault();
		if (!modalTitle.trim() || !modalStartDate) return;

		isSaving = true;

		let startFormatted = modalStartDate;
		let endFormatted = modalEndDate || modalStartDate;

		if (!modalAllDay) {
			startFormatted = `${modalStartDate}T${modalStartTime || '00:00'}:00`;
			endFormatted = `${modalEndDate || modalStartDate}T${modalEndTime || '23:59'}:00`;
		}

		const payload: Partial<CalendarEvent> = {
			title: modalTitle.trim(),
			description: modalDescription.trim(),
			startDate: startFormatted,
			endDate: endFormatted,
			allDay: modalAllDay,
			color: modalColor,
			category: modalCategory,
			reminderMinutes: Number(modalReminder),
			recurrence: modalRecurrence
		};

		try {
			if (isEditing && modalId) {
				const res = await fetch(`${apiBase}/calendar/events/${modalId}`, {
					method: 'PUT',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify(payload)
				});
				if (!res.ok) throw new Error('Không thể cập nhật sự kiện.');
			} else {
				const res = await fetch(`${apiBase}/calendar/events`, {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify(payload)
				});
				if (!res.ok) throw new Error('Không thể tạo sự kiện.');
			}

			showEventModal = false;
			await fetchEvents();
		} catch (err: any) {
			alert(`Lỗi: ${err.message}`);
		} finally {
			isSaving = false;
		}
	}

	async function confirmDeleteEvent() {
		if (!eventToDelete) return;
		try {
			const res = await fetch(`${apiBase}/calendar/events/${eventToDelete.id}`, {
				method: 'DELETE'
			});
			if (!res.ok) throw new Error('Không thể xóa sự kiện.');
			showDeleteConfirm = false;
			eventToDelete = null;
			showEventModal = false;
			await fetchEvents();
		} catch (err: any) {
			alert(`Lỗi khi xóa: ${err.message}`);
		}
	}

	function formatDisplayTime(dateStr: string): string {
		if (!dateStr || !dateStr.includes('T')) return 'Cả ngày';
		return dateStr.substring(11, 16);
	}
</script>

<div class="space-y-6">
	<!-- TOP TOOLBAR -->
	<div class="bg-zinc-900/60 backdrop-blur-xl border border-zinc-800/80 rounded-3xl p-5 shadow-2xl flex flex-col md:flex-row md:items-center justify-between gap-4">
		<!-- Left: Navigation & Current Month -->
		<div class="flex items-center gap-3">
			<div class="p-2.5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
					<path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25m-18 0A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75m-18 0v-7.5A2.25 2.25 0 0 1 5.25 9h13.5A2.25 2.25 0 0 1 21 9v7.5" />
				</svg>
			</div>

			<div>
				<h2 class="text-xl font-black text-white tracking-tight flex items-center gap-2">
					{headerTitle}
				</h2>
				<p class="text-xs text-zinc-400">Lập lịch & Quản lý sự kiện lưu trữ trực tiếp trên Server Sentinel</p>
			</div>

			<!-- Date Jump Buttons -->
			<div class="flex items-center gap-1 ml-2 bg-zinc-950/60 border border-zinc-800 rounded-xl p-1">
				<button
					onclick={prevPeriod}
					class="p-1.5 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-lg transition-colors cursor-pointer"
					title="Trước"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" />
					</svg>
				</button>
				<button
					onclick={goToToday}
					class="px-2.5 py-1 text-xs font-bold text-zinc-300 hover:text-white hover:bg-zinc-800 rounded-lg transition-colors cursor-pointer"
				>
					Hôm nay
				</button>
				<button
					onclick={nextPeriod}
					class="p-1.5 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-lg transition-colors cursor-pointer"
					title="Tiếp"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
					</svg>
				</button>
			</div>
		</div>

		<!-- Right: View switch, Search & Add button -->
		<div class="flex items-center gap-3 flex-wrap">
			<!-- Search Bar -->
			<div class="relative">
				<input
					type="text"
					bind:value={searchQuery}
					placeholder="Tìm sự kiện..."
					class="bg-zinc-950/80 border border-zinc-800 rounded-xl pl-8 pr-3 py-1.5 text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 w-36 sm:w-44"
				/>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5 text-zinc-500 absolute left-2.5 top-2.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.637 10.636Z" />
				</svg>
			</div>

			<!-- Category Filter -->
			<select
				bind:value={selectedCategoryFilter}
				class="bg-zinc-950/80 border border-zinc-800 rounded-xl px-2.5 py-1.5 text-xs text-zinc-300 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer"
			>
				<option value="All">Tất cả nhãn</option>
				{#each CATEGORIES as cat}
					<option value={cat}>{cat}</option>
				{/each}
			</select>

			<!-- View Mode Switch Tabs -->
			<div class="flex items-center p-1 bg-zinc-950/80 border border-zinc-800 rounded-xl">
				<button
					onclick={() => (currentView = 'month')}
					class="px-2.5 py-1 text-xs font-bold rounded-lg transition-all cursor-pointer {currentView === 'month' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-400 hover:text-white'}"
				>
					Tháng
				</button>
				<button
					onclick={() => (currentView = 'agenda')}
					class="px-2.5 py-1 text-xs font-bold rounded-lg transition-all cursor-pointer {currentView === 'agenda' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-400 hover:text-white'}"
				>
					Lịch trình
				</button>
			</div>

			<!-- Create Event Button -->
			<button
				onclick={() => openCreateModal()}
				class="px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-indigo-950/50 cursor-pointer transition-all hover:scale-105 active:scale-95"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
					<path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
				</svg>
				Tạo sự kiện
			</button>
		</div>
	</div>

	<!-- MAIN CALENDAR CONTAINER -->
	{#if isLoading && events.length === 0}
		<div class="h-96 rounded-3xl bg-zinc-900/40 border border-zinc-800/80 flex flex-col items-center justify-center gap-3 text-zinc-400">
			<span class="w-8 h-8 rounded-full border-3 border-indigo-400 border-t-transparent animate-spin"></span>
			<p class="text-xs font-medium">Đang tải dữ liệu lịch trình từ server...</p>
		</div>
	{:else if errorMessage}
		<div class="p-4 rounded-2xl bg-rose-950/30 border border-rose-800/60 text-rose-300 text-xs flex items-center gap-2">
			<span>⚠️</span>
			<span>{errorMessage}</span>
		</div>
	{:else if currentView === 'month'}
		<!-- MONTH VIEW: Google Calendar Style Grid -->
		<div class="bg-zinc-900/40 border border-zinc-800/80 rounded-3xl overflow-hidden shadow-2xl backdrop-blur-xl">
			<!-- Weekday Headers -->
			<div class="grid grid-cols-7 border-b border-zinc-800/80 text-center py-2.5 bg-zinc-950/50 text-[11px] font-bold text-zinc-400 uppercase tracking-wider">
				<div>T2</div>
				<div>T3</div>
				<div>T4</div>
				<div>T5</div>
				<div>T6</div>
				<div class="text-amber-400/90">T7</div>
				<div class="text-rose-400/90">CN</div>
			</div>

			<!-- Days Grid (6 rows x 7 columns = 42 cells) -->
			<div class="grid grid-cols-7 grid-rows-6 divide-x divide-y divide-zinc-800/50 min-h-[680px]">
				{#each monthDays as day}
					<div
						role="button"
						tabindex="0"
						onclick={() => openCreateModal(day.dateStr)}
						onkeydown={(e) => { if (e.key === 'Enter') openCreateModal(day.dateStr); }}
						class="p-2 min-h-[110px] flex flex-col justify-between transition-colors cursor-pointer group hover:bg-zinc-800/20 {day.isCurrentMonth ? 'bg-zinc-900/20' : 'bg-zinc-950/40 opacity-40'}"
					>
						<!-- Day Number Header -->
						<div class="flex items-center justify-between">
							<span
								class="text-xs font-bold w-6 h-6 flex items-center justify-center rounded-full transition-transform group-hover:scale-110 {day.isToday ? 'bg-indigo-600 text-white font-extrabold shadow-md shadow-indigo-900' : day.isCurrentMonth ? 'text-zinc-300' : 'text-zinc-600'}"
							>
								{day.date.getDate()}
							</span>
							{#if day.events.length > 0}
								<span class="text-[10px] font-mono text-zinc-500 font-semibold">
									{day.events.length}
								</span>
							{/if}
						</div>

						<!-- Events In This Day -->
						<div class="space-y-1 mt-1.5 flex-1 overflow-y-auto max-h-[75px] scrollbar-none">
							{#each day.events.slice(0, 3) as ev (ev.id)}
								{@const cfg = getColorConfig(ev.color)}
								<button
									type="button"
									onclick={(e) => openEditModal(ev, e)}
									class="w-full text-left px-2 py-0.5 rounded-md text-[10px] font-semibold truncate border transition-all hover:scale-[1.02] shadow-sm flex items-center gap-1.5 {cfg.badge}"
									title={`${ev.title} (${ev.allDay ? 'Cả ngày' : formatDisplayTime(ev.startDate)})`}
								>
									<span class="w-1.5 h-1.5 rounded-full {cfg.bg} shrink-0"></span>
									<span class="truncate">{ev.title}</span>
								</button>
							{/each}
							{#if day.events.length > 3}
								<div class="text-[9px] text-zinc-500 font-bold px-1">
									+{day.events.length - 3} sự kiện nữa...
								</div>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		</div>
	{:else}
		<!-- AGENDA / SCHEDULE VIEW -->
		<div class="bg-zinc-900/40 border border-zinc-800/80 rounded-3xl p-6 shadow-2xl backdrop-blur-xl space-y-4">
			<div class="flex items-center justify-between pb-3 border-b border-zinc-800/60">
				<h3 class="text-sm font-bold text-zinc-300 flex items-center gap-2">
					<span>Danh sách sự kiện sắp tới ({filteredEvents.length})</span>
				</h3>
			</div>

			{#if filteredEvents.length === 0}
				<div class="p-12 text-center text-zinc-500 space-y-2">
					<div class="text-3xl">📅</div>
					<p class="text-xs">Chưa có sự kiện nào trong danh sách hoặc không khớp với bộ lọc.</p>
					<button
						onclick={() => openCreateModal()}
						class="text-xs text-indigo-400 hover:text-indigo-300 font-bold hover:underline cursor-pointer"
					>
						+ Thêm sự kiện đầu tiên
					</button>
				</div>
			{:else}
				<div class="divide-y divide-zinc-800/60">
					{#each filteredEvents as ev (ev.id)}
						{@const cfg = getColorConfig(ev.color)}
						<div class="py-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-zinc-800/20 px-3 rounded-2xl transition-colors group">
							<div class="flex items-start gap-3">
								<div class="w-3 h-3 rounded-full {cfg.bg} mt-1 shrink-0 shadow-sm"></div>
								<div class="space-y-1">
									<div class="flex items-center gap-2 flex-wrap">
										<h4 class="text-sm font-bold text-zinc-100 group-hover:text-indigo-300 transition-colors">
											{ev.title}
										</h4>
										<span class="px-2 py-0.5 rounded-lg text-[10px] font-semibold border {cfg.badge}">
											{ev.category}
										</span>
										{#if ev.reminderMinutes > 0}
											<span class="text-[10px] text-zinc-400 flex items-center gap-1 font-mono">
												🔔 Nhắc trước {ev.reminderMinutes}p
											</span>
										{/if}
									</div>
									{#if ev.description}
										<p class="text-xs text-zinc-400 line-clamp-2">{ev.description}</p>
									{/if}
									<div class="text-[11px] text-zinc-500 font-mono flex items-center gap-2">
										<span>📅 {ev.startDate.substring(0, 10)}</span>
										{#if !ev.allDay && ev.startDate.includes('T')}
											<span>⏰ {formatDisplayTime(ev.startDate)} - {formatDisplayTime(ev.endDate)}</span>
										{:else}
											<span class="text-indigo-400">Cả ngày</span>
										{/if}
									</div>
								</div>
							</div>

							<!-- Action Buttons -->
							<div class="flex items-center gap-2 self-end sm:self-center shrink-0">
								<button
									onclick={() => openEditModal(ev)}
									class="px-3 py-1.5 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-300 hover:text-white text-xs font-semibold cursor-pointer transition-all"
								>
									Sửa
								</button>
								<button
									onclick={() => {
										eventToDelete = ev;
										showDeleteConfirm = true;
									}}
									class="p-1.5 text-zinc-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-xl transition-all cursor-pointer"
									title="Xóa sự kiện"
								>
									<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
										<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
									</svg>
								</button>
							</div>
						</div>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</div>

<!-- MODAL: TẠO / SỬA SỰ KIỆN -->
{#if showEventModal}
	<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fadeIn">
		<div class="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 w-full max-w-lg space-y-4 shadow-2xl max-h-[90vh] overflow-y-auto">
			<div class="flex items-center justify-between pb-3 border-b border-zinc-800/80">
				<h3 class="text-base font-extrabold text-white flex items-center gap-2">
					<span class="w-3 h-3 rounded-full {getColorConfig(modalColor).bg}"></span>
					{isEditing ? 'Chỉnh sửa sự kiện' : 'Thêm sự kiện mới'}
				</h3>
				<button
					onclick={() => (showEventModal = false)}
					class="p-1 text-zinc-500 hover:text-white rounded-lg hover:bg-zinc-800 cursor-pointer"
				>
					✕
				</button>
			</div>

			<form onsubmit={handleSaveEvent} class="space-y-4 text-xs">
				<!-- Title -->
				<div>
					<label for="event-title-input" class="block text-zinc-400 font-semibold mb-1">Tiêu đề sự kiện *</label>
					<input
						id="event-title-input"
						type="text"
						bind:value={modalTitle}
						placeholder="Ví dụ: Họp dự án, Backup database, Bảo trì server..."
						required
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3.5 py-2.5 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
					/>
				</div>

				<!-- All Day Toggle -->
				<div class="flex items-center justify-between p-3 rounded-xl bg-zinc-950/60 border border-zinc-800/80">
					<div>
						<span class="font-bold text-zinc-200">Sự kiện cả ngày</span>
						<p class="text-[11px] text-zinc-500">Không cần chỉ định giờ phút chi tiết</p>
					</div>
					<input
						type="checkbox"
						bind:checked={modalAllDay}
						class="w-4 h-4 rounded text-indigo-600 focus:ring-indigo-500 bg-zinc-900 border-zinc-700 cursor-pointer"
					/>
				</div>

				<!-- Date & Time Range -->
				<div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
					<div>
						<label for="event-start-date" class="block text-zinc-400 font-semibold mb-1">Ngày bắt đầu</label>
						<input
							id="event-start-date"
							type="date"
							bind:value={modalStartDate}
							required
							class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
						/>
						{#if !modalAllDay}
							<input
								type="time"
								bind:value={modalStartTime}
								class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500 mt-2"
							/>
						{/if}
					</div>

					<div>
						<label for="event-end-date" class="block text-zinc-400 font-semibold mb-1">Ngày kết thúc</label>
						<input
							id="event-end-date"
							type="date"
							bind:value={modalEndDate}
							class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
						/>
						{#if !modalAllDay}
							<input
								type="time"
								bind:value={modalEndTime}
								class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500 mt-2"
							/>
						{/if}
					</div>
				</div>

				<!-- Category & Color -->
				<div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
					<div>
						<label for="event-category-select" class="block text-zinc-400 font-semibold mb-1">Nhãn / Phân loại</label>
						<select
							id="event-category-select"
							bind:value={modalCategory}
							class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer"
						>
							{#each CATEGORIES as cat}
								<option value={cat}>{cat}</option>
							{/each}
						</select>
					</div>

					<div>
						<span class="block text-zinc-400 font-semibold mb-1">Màu sắc sự kiện</span>
						<div class="flex items-center gap-2 pt-1">
							{#each COLOR_OPTIONS as c}
								<button
									type="button"
									onclick={() => (modalColor = c.id)}
									class="w-6 h-6 rounded-full {c.bg} transition-transform cursor-pointer {modalColor === c.id ? 'scale-125 ring-2 ring-white' : 'opacity-70 hover:opacity-100'}"
									title={c.label}
								></button>
							{/each}
						</div>
					</div>
				</div>

				<!-- Reminder Minutes (Discord Bot Alert) -->
				<div class="p-3 rounded-xl bg-zinc-950/60 border border-zinc-800/80 space-y-1">
					<div class="flex items-center justify-between">
						<label for="event-reminder-select" class="font-bold text-zinc-300 flex items-center gap-1.5">
							<span>🔔 Nhắc nhở qua Discord Bot</span>
						</label>
						<select
							id="event-reminder-select"
							bind:value={modalReminder}
							class="bg-zinc-900 border border-zinc-700 rounded-lg px-2 py-1 text-zinc-200 text-xs focus:outline-none"
						>
							<option value={0}>Không nhắc nhở</option>
							<option value={5}>Trước 5 phút</option>
							<option value={10}>Trước 10 phút</option>
							<option value={15}>Trước 15 phút</option>
							<option value={30}>Trước 30 phút</option>
							<option value={60}>Trước 1 tiếng</option>
							<option value={1440}>Trước 1 ngày</option>
						</select>
					</div>
					<p class="text-[10px] text-zinc-500">Sentinel sẽ tự động gửi thông báo qua Discord trước khi sự kiện bắt đầu.</p>
				</div>

				<!-- Description -->
				<div>
					<label for="event-description-input" class="block text-zinc-400 font-semibold mb-1">Ghi chú chi tiết</label>
					<textarea
						id="event-description-input"
						bind:value={modalDescription}
						rows="3"
						placeholder="Thêm mô tả, link cuộc họp, lệnh server hoặc danh sách công việc cần làm..."
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3.5 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none"
					></textarea>
				</div>

				<!-- Action Buttons -->
				<div class="flex items-center justify-between pt-3 border-t border-zinc-800/80">
					{#if isEditing}
						<button
							type="button"
							onclick={() => {
								eventToDelete = events.find((e) => e.id === modalId) || null;
								showDeleteConfirm = true;
							}}
							class="px-3.5 py-2 rounded-xl text-xs font-bold text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
						>
							Xóa sự kiện
						</button>
					{:else}
						<div></div>
					{/if}

					<div class="flex items-center gap-2">
						<button
							type="button"
							onclick={() => (showEventModal = false)}
							class="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors cursor-pointer"
						>
							Hủy
						</button>
						<button
							type="submit"
							disabled={isSaving || !modalTitle.trim()}
							class="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all shadow-lg shadow-indigo-950 cursor-pointer disabled:opacity-50"
						>
							{isSaving ? 'Đang lưu...' : isEditing ? 'Cập nhật' : 'Tạo mới'}
						</button>
					</div>
				</div>
			</form>
		</div>
	</div>
{/if}

<!-- MODAL: CONFIRM DELETE -->
{#if showDeleteConfirm && eventToDelete}
	<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fadeIn">
		<div class="bg-zinc-900 border border-rose-900/60 rounded-3xl p-6 w-full max-w-sm space-y-4 shadow-2xl">
			<div class="flex items-center gap-3">
				<div class="p-2.5 rounded-xl bg-rose-500/10 text-rose-400">
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
						<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
					</svg>
				</div>
				<div>
					<h3 class="text-sm font-bold text-white">Xác nhận xóa</h3>
					<p class="text-xs text-zinc-400">Hành động này không thể hoàn tác.</p>
				</div>
			</div>
			<p class="text-xs text-zinc-300 bg-zinc-950 p-3 rounded-xl border border-zinc-800">
				Bạn có chắc chắn muốn xóa sự kiện: <b class="text-rose-400">{eventToDelete.title}</b>?
			</p>
			<div class="flex items-center justify-end gap-2 pt-2">
				<button
					onclick={() => (showDeleteConfirm = false)}
					class="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors cursor-pointer"
				>
					Hủy
				</button>
				<button
					onclick={confirmDeleteEvent}
					class="px-4 py-2 rounded-xl text-xs font-bold bg-rose-600 hover:bg-rose-500 text-white transition-all cursor-pointer shadow-lg shadow-rose-950"
				>
					Xóa vĩnh viễn
				</button>
			</div>
		</div>
	</div>
{/if}
