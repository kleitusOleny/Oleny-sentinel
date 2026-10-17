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
		location?: string; // Địa điểm hoặc Phòng học (nếu là Lịch học)
		reminderMinutes: number;
		recurrence: string; // "none", "daily", "weekly", "monthly"
		repeatWeeks?: number; // Số tuần lặp lại nếu recurrence là weekly
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
	let modalCategory = $state('Lịch học');
	let modalLocation = $state('');
	let modalReminder = $state(15);
	let modalRecurrence = $state('none');
	let modalRepeatWeeks = $state(1);
	let isSaving = $state(false);

	// Modal: Delete confirmation
	let showDeleteConfirm = $state(false);
	let eventToDelete = $state<CalendarEvent | null>(null);

	// Drag to resize / adjust duration in Day & Week views
	let isResizing = $state(false);
	let resizingEvent = $state<CalendarEvent | null>(null);
	let resizeStartY = $state(0);
	let resizeOriginalEndMin = $state(0);
	let resizeCurrentEndMin = $state(0);

	const COLOR_OPTIONS = [
		{ id: 'indigo', label: 'Xanh Chàm', bg: 'bg-indigo-600', border: 'border-indigo-500', text: 'text-indigo-400', badge: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30' },
		{ id: 'blue', label: 'Xanh Dương', bg: 'bg-blue-600', border: 'border-blue-500', text: 'text-blue-400', badge: 'bg-blue-500/15 text-blue-300 border-blue-500/30' },
		{ id: 'emerald', label: 'Xanh Lá', bg: 'bg-emerald-600', border: 'border-emerald-500', text: 'text-emerald-400', badge: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30' },
		{ id: 'amber', label: 'Vàng Cam', bg: 'bg-amber-600', border: 'border-amber-500', text: 'text-amber-400', badge: 'bg-amber-500/15 text-amber-300 border-amber-500/30' },
		{ id: 'rose', label: 'Đỏ Hồng', bg: 'bg-rose-600', border: 'border-rose-500', text: 'text-rose-400', badge: 'bg-rose-500/15 text-rose-300 border-rose-500/30' },
		{ id: 'purple', label: 'Tím', bg: 'bg-purple-600', border: 'border-purple-500', text: 'text-purple-400', badge: 'bg-purple-500/15 text-purple-300 border-purple-500/30' },
		{ id: 'cyan', label: 'Xanh Ngọc', bg: 'bg-cyan-600', border: 'border-cyan-500', text: 'text-cyan-400', badge: 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30' }
	];

	const CATEGORIES = ['Lịch học', 'Công việc', 'Họp', 'Server', 'Cá nhân', 'Deadline', 'Bảo trì'];

	const HOURS_OF_DAY = Array.from({ length: 24 }, (_, i) => i);

	function getColorConfig(colorId: string) {
		return COLOR_OPTIONS.find((c) => c.id === colorId) || COLOR_OPTIONS[0];
	}

	function formatDateToYMD(d: Date): string {
		const year = d.getFullYear();
		const month = String(d.getMonth() + 1).padStart(2, '0');
		const day = String(d.getDate()).padStart(2, '0');
		return `${year}-${month}-${day}`;
	}

	function parseMinutesFromTimeString(timeStr: string): number {
		const [h, m] = timeStr.split(':').map(Number);
		return (h || 0) * 60 + (m || 0);
	}

	function formatMinutesToTimeString(totalMinutes: number): string {
		const clamped = Math.max(0, Math.min(23 * 60 + 59, Math.round(totalMinutes)));
		const h = Math.floor(clamped / 60);
		const m = clamped % 60;
		return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
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

		const handleMouseMove = (e: MouseEvent) => {
			if (!isResizing || !resizingEvent) return;
			const deltaY = e.clientY - resizeStartY;
			// 64px = 60 phút => snap 15 phút
			const deltaMinutes = Math.round(((deltaY / 64) * 60) / 15) * 15;
			const startMin = parseMinutesFromTimeString(formatDisplayTime(resizingEvent.startDate));
			const newEndMin = Math.max(startMin + 15, Math.min(23 * 60 + 59, resizeOriginalEndMin + deltaMinutes));
			resizeCurrentEndMin = newEndMin;
		};

		const handleMouseUp = async () => {
			if (!isResizing || !resizingEvent) return;
			const targetEvent = resizingEvent;
			const finalEndMin = resizeCurrentEndMin;
			isResizing = false;
			resizingEvent = null;

			const newEndTimeStr = formatMinutesToTimeString(finalEndMin);
			const baseDate = targetEvent.endDate ? targetEvent.endDate.substring(0, 10) : targetEvent.startDate.substring(0, 10);
			const updatedEndDate = `${baseDate}T${newEndTimeStr}:00`;

			// Optimistic update
			events = events.map((ev) => (ev.id === targetEvent.id ? { ...ev, endDate: updatedEndDate } : ev));

			try {
				const res = await fetch(`${apiBase}/calendar/events/${targetEvent.id}`, {
					method: 'PUT',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({
						...targetEvent,
						endDate: updatedEndDate
					})
				});
				if (!res.ok) throw new Error('Không thể lưu thay đổi độ dài sự kiện.');
				await fetchEvents();
			} catch (err: any) {
				alert(`Lỗi: ${err.message}`);
				await fetchEvents();
			}
		};

		window.addEventListener('mousemove', handleMouseMove);
		window.addEventListener('mouseup', handleMouseUp);

		return () => {
			window.removeEventListener('mousemove', handleMouseMove);
			window.removeEventListener('mouseup', handleMouseUp);
		};
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
			const dayNames = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
			return `${dayNames[currentDate.getDay()]}, ${currentDate.getDate()} ${m}, ${y}`;
		}
		if (currentView === 'week') {
			const weekDaysList = weekDays;
			if (weekDaysList.length > 0) {
				const start = weekDaysList[0].date;
				const end = weekDaysList[6].date;
				return `${start.getDate()} ${monthNames[start.getMonth()]} - ${end.getDate()} ${monthNames[end.getMonth()]}, ${y}`;
			}
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
				const matchLoc = (ev.location || '').toLowerCase().includes(q);
				if (!matchTitle && !matchDesc && !matchCat && !matchLoc) return false;
			}
			return true;
		});
	});

	function checkIfToday(d: Date): boolean {
		const today = new Date();
		return d.getDate() === today.getDate() &&
			d.getMonth() === today.getMonth() &&
			d.getFullYear() === today.getFullYear();
	}

	// Kiểm tra sự kiện có diễn ra vào dateStr hay không (hỗ trợ cả lặp lại tuần số tuần repeatWeeks)
	function isEventOnDate(ev: CalendarEvent, dateStr: string): boolean {
		if (!ev.startDate) return false;
		const evStart = ev.startDate.substring(0, 10);
		const evEnd = ev.endDate ? ev.endDate.substring(0, 10) : evStart;

		// 1. Kiểm tra dải ngày gốc
		if (dateStr >= evStart && dateStr <= evEnd) {
			return true;
		}

		// 2. Kiểm tra nếu có lặp lại hàng tuần
		if (ev.recurrence === 'weekly' && dateStr > evStart) {
			const startDateObj = new Date(evStart + 'T00:00:00');
			const targetDateObj = new Date(dateStr + 'T00:00:00');
			const diffTime = targetDateObj.getTime() - startDateObj.getTime();
			const diffDays = Math.round(diffTime / (1000 * 3600 * 24));

			const repeatLimit = ev.repeatWeeks && ev.repeatWeeks > 0 ? ev.repeatWeeks : 1;
			const maxDays = repeatLimit * 7;

			if (diffDays > 0 && diffDays <= maxDays) {
				// Nếu ngày trong tuần trùng khớp
				if (diffDays % 7 === 0) {
					return true;
				}
			}
		}

		// 3. Hàng ngày
		if (ev.recurrence === 'daily' && dateStr > evStart) {
			return true;
		}

		// 4. Hàng tháng
		if (ev.recurrence === 'monthly' && dateStr > evStart) {
			const originalDay = parseInt(evStart.substring(8, 10), 10);
			const targetDay = parseInt(dateStr.substring(8, 10), 10);
			if (originalDay === targetDay) return true;
		}

		return false;
	}

	function getEventsForDate(dateStr: string): CalendarEvent[] {
		return filteredEvents.filter((ev) => isEventOnDate(ev, dateStr));
	}

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

		// T2 là ngày bắt đầu tuần (1), CN là (0) -> T2=0, CN=6
		let startingDay = firstDayOfMonth.getDay() - 1;
		if (startingDay < 0) startingDay = 6;

		// Ngày tháng trước
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

		// Ngày trong tháng
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

		// Ngày tháng sau cho đủ 42 ô (6 tuần)
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

	// --- Week View Calculation ---
	interface WeekDay {
		date: Date;
		dateStr: string;
		dayName: string;
		isToday: boolean;
		events: CalendarEvent[];
	}

	let weekDays = $derived.by(() => {
		const days: WeekDay[] = [];
		const dayNames = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
		const curr = new Date(currentDate);

		let dayOfWeek = curr.getDay() - 1;
		if (dayOfWeek < 0) dayOfWeek = 6; // CN = 6

		const monday = new Date(curr);
		monday.setDate(curr.getDate() - dayOfWeek);

		for (let i = 0; i < 7; i++) {
			const d = new Date(monday);
			d.setDate(monday.getDate() + i);
			const dateStr = formatDateToYMD(d);
			days.push({
				date: d,
				dateStr,
				dayName: dayNames[i],
				isToday: checkIfToday(d),
				events: getEventsForDate(dateStr)
			});
		}

		return days;
	});

	// --- Modal Open Helpers ---
	function openCreateModal(dateStr?: string, defaultHour = 9) {
		isEditing = false;
		modalId = '';
		modalTitle = '';
		modalDescription = '';
		const targetDate = dateStr || formatDateToYMD(new Date());
		modalStartDate = targetDate;
		modalEndDate = targetDate;
		modalStartTime = `${String(defaultHour).padStart(2, '0')}:00`;
		modalEndTime = `${String(Math.min(23, defaultHour + 1)).padStart(2, '0')}:00`;
		modalAllDay = false;
		modalColor = 'indigo';
		modalCategory = 'Lịch học';
		modalLocation = '';
		modalReminder = 15;
		modalRecurrence = 'none';
		modalRepeatWeeks = 1;
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
		modalCategory = ev.category || 'Lịch học';
		modalLocation = ev.location || '';
		modalReminder = ev.reminderMinutes ?? 15;
		modalRecurrence = ev.recurrence || 'none';
		modalRepeatWeeks = ev.repeatWeeks && ev.repeatWeeks > 0 ? ev.repeatWeeks : 1;
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
			location: modalLocation.trim(),
			reminderMinutes: Number(modalReminder),
			recurrence: modalRecurrence,
			repeatWeeks: modalRecurrence === 'weekly' ? Number(modalRepeatWeeks) : 1
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

	// Tính vị trí Top và Chiều cao (Height) của block sự kiện trong timeline 24 giờ
	// Mỗi 1 giờ tương đương 64px (1 phút = 64/60 px)
	function getEventTimelineStyle(ev: CalendarEvent, isBeingResized = false) {
		const HOUR_HEIGHT = 64; // px
		if (ev.allDay || !ev.startDate.includes('T')) {
			return { top: 0, height: 32, isAllDay: true };
		}

		const startParts = ev.startDate.substring(11, 16).split(':').map(Number);
		const startMinutes = (startParts[0] || 0) * 60 + (startParts[1] || 0);

		let endMinutes = startMinutes + 60;
		if (isBeingResized && isResizing) {
			endMinutes = resizeCurrentEndMin;
		} else if (ev.endDate && ev.endDate.includes('T')) {
			const endParts = ev.endDate.substring(11, 16).split(':').map(Number);
			endMinutes = (endParts[0] || 0) * 60 + (endParts[1] || 0);
		}

		const durationMin = Math.max(15, endMinutes - startMinutes);
		const top = (startMinutes / 60) * HOUR_HEIGHT;
		const height = (durationMin / 60) * HOUR_HEIGHT;

		return { top, height, isAllDay: false, durationMin };
	}

	function startResizing(ev: CalendarEvent, e: MouseEvent) {
		e.stopPropagation();
		e.preventDefault();
		isResizing = true;
		resizingEvent = ev;
		resizeStartY = e.clientY;

		const endParts = (ev.endDate && ev.endDate.includes('T') ? ev.endDate : ev.startDate).substring(11, 16).split(':').map(Number);
		resizeOriginalEndMin = (endParts[0] || 0) * 60 + (endParts[1] || 0);
		resizeCurrentEndMin = resizeOriginalEndMin;
	}
</script>

<div class="space-y-6 select-none">
	<!-- TOP TOOLBAR -->
	<div class="bg-zinc-900/60 backdrop-blur-xl border border-zinc-800/80 rounded-3xl p-5 shadow-2xl flex flex-col md:flex-row md:items-center justify-between gap-4">
		<!-- Left: Navigation & Current Month/Week/Day -->
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
				<p class="text-xs text-zinc-400">Lập lịch & Quản lý sự kiện, lịch học lưu trữ trực tiếp trên Server Sentinel</p>
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
					placeholder="Tìm sự kiện, phòng, địa điểm..."
					class="bg-zinc-950/80 border border-zinc-800 rounded-xl pl-8 pr-3 py-1.5 text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 w-40 sm:w-52"
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

			<!-- View Mode Switch: Ngày | Tuần | Tháng | Lịch trình -->
			<div class="flex items-center p-1 bg-zinc-950/80 border border-zinc-800 rounded-xl">
				<button
					onclick={() => (currentView = 'day')}
					class="px-2.5 py-1 text-xs font-bold rounded-lg transition-all cursor-pointer {currentView === 'day' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-400 hover:text-white'}"
				>
					Ngày
				</button>
				<button
					onclick={() => (currentView = 'week')}
					class="px-2.5 py-1 text-xs font-bold rounded-lg transition-all cursor-pointer {currentView === 'week' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-400 hover:text-white'}"
				>
					Tuần
				</button>
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
	{:else if currentView === 'day'}
		<!-- DAY VIEW: 24h Timeline with drag resize -->
		{@const todayStr = formatDateToYMD(currentDate)}
		{@const dayEvents = getEventsForDate(todayStr)}
		{@const allDayEvents = dayEvents.filter((e) => e.allDay || !e.startDate.includes('T'))}
		{@const timedEvents = dayEvents.filter((e) => !e.allDay && e.startDate.includes('T'))}

		<div class="bg-zinc-900/40 border border-zinc-800/80 rounded-3xl overflow-hidden shadow-2xl backdrop-blur-xl">
			<!-- Header for Day -->
			<div class="p-4 border-b border-zinc-800 bg-zinc-950/50 flex items-center justify-between">
				<div class="flex items-center gap-3">
					<span class="text-2xl font-black {checkIfToday(currentDate) ? 'text-indigo-400' : 'text-white'}">
						{currentDate.getDate()}
					</span>
					<div>
						<h3 class="text-sm font-bold text-zinc-200">
							{currentDate.toLocaleDateString('vi-VN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
						</h3>
						<span class="text-[11px] text-zinc-500">
							{dayEvents.length} sự kiện được xếp lịch
						</span>
					</div>
				</div>

				<div class="text-xs text-zinc-400 italic">
					💡 Kéo cạnh dưới sự kiện để thay đổi độ dài thời gian
				</div>
			</div>

			<!-- All Day Section (if any) -->
			{#if allDayEvents.length > 0}
				<div class="px-6 py-2 border-b border-zinc-800/80 bg-zinc-950/30 flex items-center gap-3">
					<span class="text-[11px] font-bold text-zinc-500 w-16 uppercase">Cả ngày</span>
					<div class="flex flex-wrap gap-2 flex-1">
						{#each allDayEvents as ev (ev.id)}
							{@const cfg = getColorConfig(ev.color)}
							<button
								type="button"
								onclick={(e) => openEditModal(ev, e)}
								class="px-3 py-1 rounded-lg text-xs font-bold border transition-all hover:scale-105 {cfg.badge} flex items-center gap-1.5"
							>
								<span class="w-2 h-2 rounded-full {cfg.bg}"></span>
								<span>{ev.title}</span>
								{#if ev.location}
									<span class="text-[10px] px-1.5 py-0.2 rounded bg-black/40 font-mono text-zinc-300">
										{ev.category === 'Lịch học' ? `🏫 ${ev.location}` : `📍 ${ev.location}`}
									</span>
								{/if}
							</button>
						{/each}
					</div>
				</div>
			{/if}

			<!-- 24-Hour Scrollable Grid -->
			<div class="relative h-[650px] overflow-y-auto divide-y divide-zinc-800/30">
				{#each HOURS_OF_DAY as hour}
					<div
						role="button"
						tabindex="0"
						onclick={() => openCreateModal(todayStr, hour)}
						onkeydown={(e) => { if (e.key === 'Enter') openCreateModal(todayStr, hour); }}
						class="h-16 flex border-b border-zinc-800/30 hover:bg-zinc-800/10 transition-colors group cursor-pointer relative"
					>
						<!-- Hour Label -->
						<div class="w-16 shrink-0 text-right pr-3 pt-1 text-[11px] font-mono font-medium text-zinc-500 border-r border-zinc-800/50">
							{String(hour).padStart(2, '0')}:00
						</div>
						<!-- Empty Hour Slot -->
						<div class="flex-1 relative">
							<span class="hidden group-hover:inline-block absolute left-3 top-2 text-[10px] text-indigo-400 font-semibold opacity-60">
								+ Bấm để thêm lúc {String(hour).padStart(2, '0')}:00
							</span>
						</div>
					</div>
				{/each}

				<!-- Positioned Events Overlay -->
				<div class="absolute top-0 left-16 right-0 bottom-0 pointer-events-none">
					{#each timedEvents as ev (ev.id)}
						{@const isBeingResized = isResizing && resizingEvent?.id === ev.id}
						{@const st = getEventTimelineStyle(ev, isBeingResized)}
						{@const cfg = getColorConfig(ev.color)}
						<div
							role="button"
							tabindex="0"
							onclick={(e) => openEditModal(ev, e)}
							onkeydown={(e) => { if (e.key === 'Enter') openEditModal(ev); }}
							style="top: {st.top}px; height: {st.height}px;"
							class="absolute left-2 right-4 rounded-xl border p-2 shadow-lg backdrop-blur-md transition-all pointer-events-auto flex flex-col justify-between overflow-hidden group cursor-pointer hover:ring-2 hover:ring-white/40 {cfg.badge}"
						>
							<div class="flex items-start justify-between gap-2">
								<div class="space-y-0.5 min-w-0">
									<div class="flex items-center gap-1.5 font-bold text-xs flex-wrap">
										<span class="w-2 h-2 rounded-full {cfg.bg} shrink-0"></span>
										<span class="truncate text-white font-extrabold">{ev.title}</span>
										<span class="text-[10px] opacity-75 font-mono">
											({formatDisplayTime(ev.startDate)} - {isBeingResized ? formatMinutesToTimeString(resizeCurrentEndMin) : formatDisplayTime(ev.endDate)})
										</span>
										<!-- PHÒNG HỌC / ĐỊA ĐIỂM NỔI BẬT -->
										{#if ev.location}
											<span class="px-2 py-0.5 rounded-md text-[10px] font-bold shadow-sm {ev.category === 'Lịch học' ? 'bg-amber-400 text-zinc-950 font-black' : 'bg-black/40 text-zinc-200 border border-white/10'}">
												{ev.category === 'Lịch học' ? `🏫 Phòng: ${ev.location}` : `📍 ${ev.location}`}
											</span>
										{/if}
									</div>
									{#if ev.description}
										<p class="text-[11px] text-zinc-300 line-clamp-1">{ev.description}</p>
									{/if}
								</div>

								<span class="text-[10px] px-1.5 py-0.5 rounded bg-black/30 font-semibold text-zinc-200 shrink-0">
									{ev.category}
								</span>
							</div>

							<!-- Resize Handle (Kéo để chỉnh độ dài) -->
							<div
								role="slider"
								aria-label="Kéo để chỉnh thời gian kết thúc"
								aria-valuenow={st.durationMin || 60}
								tabindex="0"
								onmousedown={(e) => startResizing(ev, e)}
								class="w-full h-3 -mb-1 cursor-ns-resize flex items-center justify-center hover:bg-white/20 rounded-b transition-colors group-hover:opacity-100 opacity-60"
								title="Kéo thả để điều chỉnh thời gian kết thúc"
							>
								<div class="w-8 h-1 bg-white/60 rounded-full"></div>
							</div>
						</div>
					{/each}
				</div>
			</div>
		</div>
	{:else if currentView === 'week'}
		<!-- WEEK VIEW: 7 Columns with 24 Hours -->
		<div class="bg-zinc-900/40 border border-zinc-800/80 rounded-3xl overflow-hidden shadow-2xl backdrop-blur-xl">
			<!-- Weekday Headers -->
			<div class="grid grid-cols-8 border-b border-zinc-800/80 bg-zinc-950/50 text-center py-3 text-xs font-bold divide-x divide-zinc-800/50">
				<div class="text-zinc-500 font-mono text-[11px] flex items-center justify-center">
					Giờ
				</div>
				{#each weekDays as day}
					<div class="py-1 space-y-1">
						<span class="text-zinc-400 uppercase text-[10px] block">{day.dayName}</span>
						<span class="inline-flex w-7 h-7 items-center justify-center rounded-full font-bold {day.isToday ? 'bg-indigo-600 text-white shadow-md' : 'text-zinc-200'}">
							{day.date.getDate()}
						</span>
					</div>
				{/each}
			</div>

			<!-- Week 24h Grid -->
			<div class="relative h-[650px] overflow-y-auto">
				<div class="grid grid-cols-8 divide-x divide-zinc-800/40">
					<!-- Hours Column -->
					<div class="divide-y divide-zinc-800/30">
						{#each HOURS_OF_DAY as hour}
							<div class="h-16 text-right pr-2 pt-1 text-[11px] font-mono font-medium text-zinc-500">
								{String(hour).padStart(2, '0')}:00
							</div>
						{/each}
					</div>

					<!-- 7 Days Columns -->
					{#each weekDays as day}
						{@const timedDayEvents = day.events.filter((e) => !e.allDay && e.startDate.includes('T'))}
						<div class="relative divide-y divide-zinc-800/30">
							{#each HOURS_OF_DAY as hour}
								<div
									role="button"
									tabindex="0"
									onclick={() => openCreateModal(day.dateStr, hour)}
									onkeydown={(e) => { if (e.key === 'Enter') openCreateModal(day.dateStr, hour); }}
									class="h-16 hover:bg-zinc-800/10 transition-colors cursor-pointer"
								></div>
							{/each}

							<!-- Positioned Events inside day column -->
							<div class="absolute inset-0 pointer-events-none">
								{#each timedDayEvents as ev (ev.id)}
									{@const isBeingResized = isResizing && resizingEvent?.id === ev.id}
									{@const st = getEventTimelineStyle(ev, isBeingResized)}
									{@const cfg = getColorConfig(ev.color)}
									<div
										role="button"
										tabindex="0"
										onclick={(e) => openEditModal(ev, e)}
										onkeydown={(e) => { if (e.key === 'Enter') openEditModal(ev); }}
										style="top: {st.top}px; height: {st.height}px;"
										class="absolute left-1 right-1 rounded-lg border p-1.5 shadow-md backdrop-blur-md transition-all pointer-events-auto flex flex-col justify-between overflow-hidden group cursor-pointer hover:ring-2 hover:ring-white/40 {cfg.badge}"
										title={`${ev.title} (${formatDisplayTime(ev.startDate)} - ${formatDisplayTime(ev.endDate)})${ev.location ? ' - ' + ev.location : ''}`}
									>
										<div class="space-y-0.5">
											<div class="flex items-center gap-1 font-bold text-[11px] truncate">
												<span class="w-1.5 h-1.5 rounded-full {cfg.bg} shrink-0"></span>
												<span class="truncate text-white font-extrabold">{ev.title}</span>
											</div>
											<div class="text-[9px] opacity-75 font-mono truncate">
												{formatDisplayTime(ev.startDate)} - {isBeingResized ? formatMinutesToTimeString(resizeCurrentEndMin) : formatDisplayTime(ev.endDate)}
											</div>
											<!-- Hiển thị Phòng học / Địa điểm trong ô tuần -->
											{#if ev.location}
												<div class="mt-0.5">
													<span class="inline-block max-w-full truncate px-1 py-0.2 rounded text-[9px] font-bold {ev.category === 'Lịch học' ? 'bg-amber-400 text-zinc-950 font-black' : 'bg-black/40 text-zinc-200'}">
														{ev.category === 'Lịch học' ? `🏫 ${ev.location}` : `📍 ${ev.location}`}
													</span>
												</div>
											{/if}
										</div>

										<!-- Resize Handle -->
										<div
											role="slider"
											aria-label="Kéo để chỉnh thời gian kết thúc"
											aria-valuenow={st.durationMin || 60}
											tabindex="0"
											onmousedown={(e) => startResizing(ev, e)}
											class="w-full h-2 cursor-ns-resize flex items-center justify-center hover:bg-white/20 rounded transition-colors group-hover:opacity-100 opacity-40"
											title="Kéo thả để điều chỉnh thời lượng"
										>
											<div class="w-4 h-0.5 bg-white/60 rounded-full"></div>
										</div>
									</div>
								{/each}
							</div>
						</div>
					{/each}
				</div>
			</div>
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
									title={`${ev.title} (${ev.allDay ? 'Cả ngày' : formatDisplayTime(ev.startDate)})${ev.location ? ' - ' + (ev.category === 'Lịch học' ? 'Phòng ' : '') + ev.location : ''}`}
								>
									<span class="w-1.5 h-1.5 rounded-full {cfg.bg} shrink-0"></span>
									<span class="truncate flex-1">{ev.title}</span>
									{#if ev.location}
										<span class="shrink-0 px-1 py-0.2 rounded text-[8px] font-bold {ev.category === 'Lịch học' ? 'bg-amber-400 text-zinc-950 font-black' : 'bg-black/30 text-zinc-300'}">
											{ev.category === 'Lịch học' ? `P: ${ev.location}` : ev.location}
										</span>
									{/if}
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
										<!-- Location or Classroom badge in Agenda -->
										{#if ev.location}
											<span class="px-2 py-0.5 rounded-lg text-[10px] font-bold shadow-sm {ev.category === 'Lịch học' ? 'bg-amber-400 text-zinc-950 font-black' : 'bg-zinc-800 text-zinc-200 border border-zinc-700'}">
												{ev.category === 'Lịch học' ? `🏫 Phòng học: ${ev.location}` : `📍 Địa điểm: ${ev.location}`}
											</span>
										{/if}
										{#if ev.recurrence === 'weekly'}
											<span class="px-2 py-0.5 rounded-lg text-[10px] font-semibold bg-violet-500/15 text-violet-300 border border-violet-500/30">
												🔁 Lặp {ev.repeatWeeks || 1} tuần
											</span>
										{:else if ev.recurrence === 'daily'}
											<span class="px-2 py-0.5 rounded-lg text-[10px] font-semibold bg-blue-500/15 text-blue-300 border border-blue-500/30">
												🔁 Hàng ngày
											</span>
										{:else if ev.recurrence === 'monthly'}
											<span class="px-2 py-0.5 rounded-lg text-[10px] font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
												🔁 Hàng tháng
											</span>
										{/if}
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
						placeholder={modalCategory === 'Lịch học' ? 'Ví dụ: Giải tích 1, Cơ sở dữ liệu, Lập trình Mạng...' : 'Ví dụ: Họp dự án, Backup database, Bảo trì server...'}
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

				<!-- Category & Location / Classroom -->
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

					<!-- Location Input: Tự động đổi nhãn và placeholder nếu là Lịch học -->
					<div>
						<label for="event-location-input" class="block text-zinc-400 font-semibold mb-1">
							{modalCategory === 'Lịch học' ? '🏫 Phòng học' : '📍 Địa điểm'}
						</label>
						<input
							id="event-location-input"
							type="text"
							bind:value={modalLocation}
							placeholder={modalCategory === 'Lịch học' ? 'Ví dụ: P.302-A2, Giảng đường C, Lab 4...' : 'Ví dụ: Phòng họp 1, Tòa nhà B, Google Meet...'}
							class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
						/>
					</div>
				</div>

				<!-- Recurrence & Repeat Weeks Option -->
				<div class="p-3 rounded-xl bg-zinc-950/60 border border-zinc-800/80 space-y-3">
					<div class="flex items-center justify-between">
						<label for="event-recurrence-select" class="font-bold text-zinc-300 flex items-center gap-1.5">
							<span>🔁 Lặp lại sự kiện</span>
						</label>
						<select
							id="event-recurrence-select"
							bind:value={modalRecurrence}
							class="bg-zinc-900 border border-zinc-700 rounded-lg px-2.5 py-1.5 text-zinc-200 text-xs focus:outline-none"
						>
							<option value="none">Không lặp lại</option>
							<option value="daily">Hàng ngày</option>
							<option value="weekly">Hàng tuần</option>
							<option value="monthly">Hàng tháng</option>
						</select>
					</div>

					{#if modalRecurrence === 'weekly'}
						<div class="flex items-center justify-between pt-2 border-t border-zinc-800/60">
							<div>
								<span class="font-semibold text-zinc-200">Số tuần lặp lại</span>
								<p class="text-[11px] text-zinc-500">Sự kiện/Lịch học sẽ tự động lặp trong bao nhiêu tuần tới</p>
							</div>
							<div class="flex items-center gap-2">
								<input
									type="number"
									min="1"
									max="52"
									bind:value={modalRepeatWeeks}
									class="w-16 bg-zinc-900 border border-zinc-700 rounded-lg px-2 py-1 text-center text-zinc-100 font-bold focus:outline-none focus:ring-1 focus:ring-indigo-500"
								/>
								<span class="text-zinc-400 text-xs">tuần</span>
							</div>
						</div>
					{/if}
				</div>

				<!-- Color selection -->
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
					<p class="text-[10px] text-zinc-500">Sentinel sẽ tự động gửi thông báo qua Discord kèm Địa điểm / Phòng học trước khi bắt đầu.</p>
				</div>

				<!-- Description -->
				<div>
					<label for="event-description-input" class="block text-zinc-400 font-semibold mb-1">Ghi chú chi tiết</label>
					<textarea
						id="event-description-input"
						bind:value={modalDescription}
						rows="3"
						placeholder="Thêm mô tả, giáo viên phụ trách, tài liệu học tập hoặc việc cần làm..."
						class="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-3.5 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
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
