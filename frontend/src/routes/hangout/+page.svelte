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
	}

	const API_BASE = '/api';

	// States
	let members = $state<HangoutMember[]>([]);
	let isLoading = $state(true);
	let errorMessage = $state('');
	let newMemberName = $state('');
	let isAddingMember = $state(false);
	let activeMemberId = $state<string>(''); // Thành viên người dùng hiện tại đang tick cho chính mình
	let filterOnlyActive = $state(false);

	// Quản lý tuần (Mặc định là ngày Thứ 2 của tuần hiện tại)
	let currentMonday = $state<Date>(getMonday(new Date()));

	function getMonday(d: Date): Date {
		const date = new Date(d);
		const day = date.getDay();
		const diff = date.getDate() - day + (day === 0 ? -6 : 1); // 1 = Thứ 2, 0 = Chủ Nhật -> -6
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
		const dayNames = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ Nhật'];
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
				dayNum: d.getDate(),
				monthNum: d.getMonth() + 1,
				isWeekend: i >= 5,
				isToday: isToday
			});
		}
		return days;
	});

	// Tuần trước & Tuần sau
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

	// Fetch API
	async function fetchMembers() {
		isLoading = true;
		try {
			const res = await fetch(`${API_BASE}/hangout/members`);
			if (!res.ok) throw new Error('Không thể tải lịch hẹn');
			members = await res.json();
			errorMessage = '';

			// Khôi phục activeMemberId từ localStorage nếu có
			if (!activeMemberId && typeof window !== 'undefined') {
				const savedId = localStorage.getItem('hangout_active_member_id');
				if (savedId && members.some((m) => m.id === savedId)) {
					activeMemberId = savedId;
				}
			}
		} catch (err: any) {
			console.error(err);
			errorMessage = 'Không thể kết nối đến máy chủ. Vui lòng thử lại sau.';
		} finally {
			isLoading = false;
		}
	}

	// Thêm thành viên
	async function handleAddMember(e: SubmitEvent) {
		e.preventDefault();
		const name = newMemberName.trim();
		if (!name) return;

		isAddingMember = true;
		try {
			const res = await fetch(`${API_BASE}/hangout/members`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ name, availableDates: [] })
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = [...members, data.member];
				newMemberName = '';
				activeMemberId = data.member.id;
				if (typeof window !== 'undefined') {
					localStorage.setItem('hangout_active_member_id', data.member.id);
				}
			} else {
				alert(data.message || 'Lỗi thêm thành viên');
			}
		} catch (err: any) {
			alert('Lỗi kết nối: ' + err.message);
		} finally {
			isAddingMember = false;
		}
	}

	// Toggle ngày rảnh
	async function handleToggleDate(memberId: string, dateStr: string) {
		// Tối ưu UI lạc quan (Optimistic Update)
		const targetMember = members.find((m) => m.id === memberId);
		if (!targetMember) return;

		const currentDates = targetMember.availableDates || [];
		const isCurrentlyAvailable = currentDates.includes(dateStr);
		const newDates = isCurrentlyAvailable
			? currentDates.filter((d) => d !== dateStr)
			: [...currentDates, dateStr];

		members = members.map((m) => (m.id === memberId ? { ...m, availableDates: newDates } : m));

		try {
			const res = await fetch(`${API_BASE}/hangout/members/${memberId}/toggle-date`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ date: dateStr })
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = members.map((m) => (m.id === memberId ? data.member : m));
			}
		} catch (err) {
			console.error('Toggle date failed, rollbacking', err);
			// Rollback nếu có lỗi
			members = members.map((m) => (m.id === memberId ? targetMember : m));
		}
	}

	// Xóa thành viên
	async function handleDeleteMember(member: HangoutMember) {
		if (!confirm(`Bạn có chắc muốn xóa lịch của "${member.name}"?`)) return;

		try {
			const res = await fetch(`${API_BASE}/hangout/members/${member.id}`, {
				method: 'DELETE'
			});
			const data = await res.json();
			if (data.status === 'success') {
				members = members.filter((m) => m.id !== member.id);
				if (activeMemberId === member.id) {
					activeMemberId = '';
					if (typeof window !== 'undefined') {
						localStorage.removeItem('hangout_active_member_id');
					}
				}
			}
		} catch (err: any) {
			alert('Lỗi khi xóa: ' + err.message);
		}
	}

	function selectActiveMember(id: string) {
		activeMemberId = id;
		if (typeof window !== 'undefined') {
			localStorage.setItem('hangout_active_member_id', id);
		}
	}

	onMount(() => {
		fetchMembers();
	});
</script>

<svelte:head>
	<title>Hangout Scheduler - Lên Lịch Đi Chơi</title>
	<meta name="description" content="Lên lịch rảnh đi chơi theo tuần cùng nhóm bạn không cần đăng nhập" />
</svelte:head>

<div class="min-h-screen bg-[#09090b] text-zinc-100 font-sans selection:bg-pink-500 selection:text-white pb-16">
	<!-- TOP NAVIGATION / BRAND HEADER -->
	<header class="border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur-xl sticky top-0 z-30 shadow-lg">
		<div class="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
			<!-- Logo / Title -->
			<div class="flex items-center gap-3">
				<a href="/" class="flex items-center gap-2.5 hover:opacity-85 transition-opacity" title="Về trang chủ Sentinel">
					<img src={nekoIcon} alt="Sentinel Neko Icon" class="h-9 w-9 object-contain drop-shadow-[0_2px_8px_rgba(244,63,134,0.4)]" />
					<div>
						<div class="flex items-center gap-2">
							<span class="font-extrabold tracking-wider bg-gradient-to-r from-pink-400 via-rose-400 to-amber-300 bg-clip-text text-transparent text-lg">
								HANGOUT
							</span>
							<span class="px-2 py-0.5 rounded-full bg-pink-500/15 border border-pink-500/30 text-pink-300 font-bold text-[10px]">
								Công khai
							</span>
						</div>
						<span class="block text-[10px] text-zinc-400 font-semibold tracking-wide">Lên Lịch Hẹn & Chọn Ngày Đi Chơi</span>
					</div>
				</a>
			</div>

			<!-- Quick Info / Status -->
			<div class="flex items-center gap-3">
				<img src={nanallyGif} alt="Nanally Mascot" class="h-10 w-10 object-contain hidden sm:block drop-shadow" />
				<a
					href="/"
					class="px-3 py-1.5 rounded-xl border border-zinc-800 hover:border-zinc-700 bg-zinc-900/60 hover:bg-zinc-800 text-xs font-semibold text-zinc-300 hover:text-white transition-all flex items-center gap-1.5"
				>
					<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
						<path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
					</svg>
					Trang chủ
				</a>
			</div>
		</div>
	</header>

	<main class="max-w-7xl mx-auto px-4 sm:px-6 pt-6 sm:pt-8 space-y-6">
		<!-- HERO & INSTRUCTIONS BANNER -->
		<div class="relative bg-gradient-to-r from-zinc-900/80 via-zinc-900/40 to-pink-950/20 border border-zinc-800/80 rounded-3xl p-5 sm:p-6 shadow-2xl backdrop-blur-md overflow-hidden">
			<div class="absolute -right-10 -bottom-10 w-48 h-48 bg-pink-500/10 rounded-full blur-3xl pointer-events-none"></div>

			<div class="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
				<div class="space-y-1.5">
					<h1 class="text-xl sm:text-2xl font-black text-white flex items-center gap-2">
						<span>🎉</span>
						So sánh Lịch Rảnh & Tìm Ngày Đi Chơi Tối Ưu
					</h1>
					<p class="text-zinc-400 text-xs sm:text-sm max-w-2xl leading-relaxed">
						Mọi người <strong class="text-pink-300">không cần đăng nhập</strong>. Hãy thêm tên bạn vào danh sách, chọn ô ngày bạn rảnh để hệ thống tự động tổng hợp ngày nào đông đủ nhất!
					</p>
				</div>

				<!-- Quick Add Form -->
				<form onsubmit={handleAddMember} class="flex items-center gap-2 w-full md:w-auto shrink-0">
					<input
						type="text"
						placeholder="Nhập tên của bạn..."
						bind:value={newMemberName}
						class="bg-zinc-950 border border-zinc-700/80 focus:border-pink-500 text-sm text-zinc-100 rounded-xl px-3.5 py-2 w-full sm:w-56 focus:outline-none focus:ring-1 focus:ring-pink-500 font-medium placeholder-zinc-500"
						required
					/>
					<button
						type="submit"
						disabled={isAddingMember}
						class="px-4 py-2 bg-gradient-to-r from-pink-500 to-rose-500 hover:from-pink-600 hover:to-rose-600 text-white font-bold text-xs rounded-xl shadow-lg shadow-pink-500/20 transition-all cursor-pointer shrink-0 disabled:opacity-50 flex items-center gap-1.5"
					>
						<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4">
							<path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
						</svg>
						Thêm tên
					</button>
				</form>
			</div>
		</div>

		<!-- ERROR ALERT -->
		{#if errorMessage}
			<div class="p-4 rounded-2xl bg-rose-950/40 border border-rose-800/80 text-rose-300 text-sm flex items-center gap-2.5">
				<span>⚠️</span>
				<span>{errorMessage}</span>
			</div>
		{/if}

		<!-- CONTROLS & WEEK SELECTOR -->
		<div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4 bg-zinc-900/40 border border-zinc-800/80 rounded-2xl p-4 shadow-xl backdrop-blur-md">
			<!-- Week Navigator -->
			<div class="flex items-center gap-2">
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
					class="px-3 py-1.5 rounded-xl bg-zinc-950 border border-zinc-800 hover:border-zinc-700 text-xs font-bold text-zinc-300 hover:text-white transition-all cursor-pointer"
					title="Về tuần hiện tại"
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

				<!-- Display Range -->
				<div class="ml-2 font-mono text-xs sm:text-sm font-bold text-zinc-200">
					Tuần: <span class="text-pink-400">{weekDays[0].dayNum}/{weekDays[0].monthNum}</span> &rarr;
					<span class="text-pink-400">{weekDays[6].dayNum}/{weekDays[6].monthNum}/{weekDays[6].date.getFullYear()}</span>
				</div>
			</div>

			<!-- Select Active Member Indicator -->
			<div class="flex items-center gap-2 flex-wrap">
				<span class="text-xs text-zinc-400 font-medium">Bạn đang đánh dấu với tư cách:</span>
				<select
					bind:value={activeMemberId}
					onchange={(e) => selectActiveMember((e.target as HTMLSelectElement).value)}
					class="bg-zinc-950 border border-pink-500/40 text-pink-300 font-bold text-xs rounded-xl px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-pink-500"
				>
					<option value="">-- Chọn tên của bạn --</option>
					{#each members as m (m.id)}
						<option value={m.id}>{m.name}</option>
					{/each}
				</select>
			</div>
		</div>

		<!-- MAIN SCHEDULE MATRIX TABLE -->
		<div class="bg-zinc-900/30 border border-zinc-800/80 rounded-3xl shadow-2xl backdrop-blur-xl overflow-hidden">
			{#if isLoading}
				<div class="p-16 text-center text-zinc-400 flex flex-col items-center justify-center gap-3">
					<div class="w-8 h-8 rounded-full border-2 border-pink-500 border-t-transparent animate-spin"></div>
					<span class="text-xs font-mono font-semibold">Đang tải danh sách lịch rảnh...</span>
				</div>
			{:else}
				<div class="overflow-x-auto">
					<table class="w-full text-left border-collapse select-none">
						<!-- TABLE HEADER: THỨ VÀ NGÀY TRONG TUẦN -->
						<thead>
							<tr class="bg-zinc-950/80 border-b border-zinc-800 text-xs">
								<!-- Cột Tên thành viên -->
								<th class="py-4 px-4 sm:px-6 w-52 sm:w-64 font-bold text-zinc-300 uppercase tracking-wider sticky left-0 bg-zinc-950 z-20 border-r border-zinc-800/80 shadow-[2px_0_10px_rgba(0,0,0,0.5)]">
									<div class="flex items-center justify-between">
										<span>Thành viên ({members.length})</span>
									</div>
								</th>

								<!-- 7 Cột Ngày trong tuần -->
								{#each weekDays as day (day.dateStr)}
									<th class="py-3 px-2 sm:px-4 text-center min-w-[100px] border-r border-zinc-800/50 last:border-r-0 {day.isToday ? 'bg-pink-500/10' : ''}">
										<div class="flex flex-col items-center justify-center gap-0.5">
											<span class="text-[11px] font-bold {day.isWeekend ? 'text-amber-400' : 'text-zinc-400'}">
												{day.name}
											</span>
											<span class="font-mono text-base font-extrabold {day.isToday ? 'text-pink-400 underline underline-offset-4' : 'text-zinc-100'}">
												{day.dayNum}
											</span>
											{#if day.isToday}
												<span class="text-[9px] px-1.5 py-0.2 rounded-full bg-pink-500 text-white font-bold uppercase tracking-wider">
													Hôm nay
												</span>
											{/if}
										</div>
									</th>
								{/each}

								<!-- Cột Tổng ngày rảnh trong tuần -->
								<th class="py-3 px-3 text-center w-24 font-bold text-zinc-400 text-[11px] uppercase tracking-wider">
									Tổng rảnh
								</th>
							</tr>
						</thead>

						<!-- TABLE BODY: CÁC HÀNG THÀNH VIÊN -->
						<tbody class="divide-y divide-zinc-800/60 text-sm font-medium">
							{#if members.length === 0}
								<tr>
									<td colspan="9" class="py-12 text-center text-zinc-500 text-xs italic">
										Chưa có ai trong danh sách. Hãy nhập tên bạn ở phía trên để bắt đầu! 🚀
									</td>
								</tr>
							{/if}

							{#each members as member (member.id)}
								{@const isCurrentSelected = activeMemberId === member.id}
								{@const countThisWeek = weekDays.filter((d) => member.availableDates?.includes(d.dateStr)).length}
								<tr class="hover:bg-zinc-800/30 transition-colors {isCurrentSelected ? 'bg-pink-500/5' : ''}">
									<!-- Member Name Column (Sticky) -->
									<td class="py-3.5 px-4 sm:px-6 sticky left-0 bg-zinc-950/95 z-10 border-r border-zinc-800/80 shadow-[2px_0_10px_rgba(0,0,0,0.5)]">
										<div class="flex items-center justify-between gap-2">
											<div class="flex items-center gap-2.5 overflow-hidden">
												<div
													class="w-7 h-7 rounded-lg text-white font-black text-xs flex items-center justify-center shrink-0 shadow-md"
													style="background-color: {member.color || '#f43f86'}"
												>
													{member.name.charAt(0).toUpperCase()}
												</div>
												<div class="truncate">
													<div class="font-bold text-xs sm:text-sm text-zinc-100 flex items-center gap-1.5 truncate">
														<span class="truncate">{member.name}</span>
														{#if isCurrentSelected}
															<span class="shrink-0 text-[10px] px-1.5 py-0.2 rounded bg-pink-500/20 text-pink-300 border border-pink-500/40 font-semibold">
																Bạn
															</span>
														{/if}
													</div>
												</div>
											</div>

											<!-- Quick Actions -->
											<div class="flex items-center gap-1 shrink-0">
												{#if !isCurrentSelected}
													<button
														type="button"
														onclick={() => selectActiveMember(member.id)}
														class="text-[10px] text-zinc-500 hover:text-pink-400 p-1 hover:bg-zinc-800 rounded transition-all"
														title="Chọn làm bạn để tick nhanh"
													>
														Tôi
													</button>
												{/if}
												<button
													type="button"
													onclick={() => handleDeleteMember(member)}
													class="text-zinc-600 hover:text-rose-400 p-1 hover:bg-rose-500/10 rounded transition-all"
													title="Xóa thành viên này"
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
										<td class="p-2 text-center border-r border-zinc-800/40 last:border-r-0 {day.isToday ? 'bg-pink-500/5' : ''}">
											<button
												type="button"
												onclick={() => handleToggleDate(member.id, day.dateStr)}
												class="w-full h-11 rounded-xl transition-all flex items-center justify-center cursor-pointer {isAvailable
													? 'bg-emerald-500/20 border-2 border-emerald-500 text-emerald-300 shadow-[0_0_12px_rgba(16,185,129,0.3)] hover:bg-emerald-500/30'
													: 'bg-zinc-950/60 border border-zinc-800/80 text-zinc-600 hover:border-zinc-700 hover:text-zinc-400'}"
												title="{member.name} {isAvailable ? 'RẢNH' : 'BẬN'} vào {day.name} ({day.dateStr})"
											>
												{#if isAvailable}
													<span class="flex items-center gap-1 font-bold text-xs">
														<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4 text-emerald-400">
															<path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clip-rule="evenodd" />
														</svg>
														<span>Rảnh</span>
													</span>
												{:else}
													<span class="text-xs font-mono text-zinc-600 opacity-40">-</span>
												{/if}
											</button>
										</td>
									{/each}

									<!-- Total Available Days in Current Week -->
									<td class="py-3 px-3 text-center">
										<span class="font-mono text-xs font-bold px-2.5 py-1 rounded-full {countThisWeek > 0 ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40' : 'text-zinc-600 bg-zinc-950'}">
											{countThisWeek}/7
										</span>
									</td>
								</tr>
							{/each}
						</tbody>

						<!-- TABLE FOOTER: TỔNG HỢP & SO SÁNH NGÀY ĐÔNG ĐỦ NHẤT -->
						<tfoot>
							<tr class="bg-zinc-950 border-t-2 border-zinc-800 text-xs font-bold">
								<td class="py-4 px-4 sm:px-6 sticky left-0 bg-zinc-950 z-10 border-r border-zinc-800 text-zinc-300">
									<div class="flex items-center gap-2">
										<span class="text-sm">🔥</span>
										<span>Tổng số người rảnh:</span>
									</div>
								</td>

								<!-- 7 Cột tổng kết -->
								{#each weekDays as day (day.dateStr)}
									{@const count = dayStats[day.dateStr]?.count || 0}
									{@const isTopDay = maxAvailableCount > 0 && count === maxAvailableCount}
									<td class="py-3 px-2 text-center border-r border-zinc-800/40 last:border-r-0 {isTopDay ? 'bg-amber-500/10' : ''}">
										<div class="flex flex-col items-center justify-center gap-1">
											<div class="flex items-baseline gap-1">
												<span class="font-mono text-base font-black {isTopDay ? 'text-amber-400 scale-110 drop-shadow-[0_0_8px_rgba(251,191,36,0.5)]' : count > 0 ? 'text-emerald-400' : 'text-zinc-600'}">
													{count}
												</span>
												<span class="text-[10px] text-zinc-500 font-mono">/{members.length}</span>
											</div>

											{#if isTopDay && count > 0}
												<span class="px-1.5 py-0.5 rounded-full bg-amber-500/20 border border-amber-400/50 text-amber-300 text-[9px] font-bold uppercase tracking-wider animate-pulse whitespace-nowrap">
													Tốt nhất 🌟
												</span>
											{/if}
										</div>
									</td>
								{/each}

								<td class="text-center text-zinc-500 text-[11px] font-mono">
									-
								</td>
							</tr>
						</tfoot>
					</table>
				</div>
			{/if}
		</div>

		<!-- SUMMARY CARD: SUGGESTED DAYS -->
		{#if members.length > 0 && maxAvailableCount > 0}
			{@const bestDays = weekDays.filter((d) => (dayStats[d.dateStr]?.count || 0) === maxAvailableCount)}
			<div class="bg-gradient-to-r from-amber-950/20 via-zinc-900/60 to-zinc-900/40 border border-amber-500/30 rounded-3xl p-5 sm:p-6 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
				<div class="flex items-center gap-3.5">
					<div class="w-12 h-12 rounded-2xl bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-2xl shrink-0 shadow-[0_0_15px_rgba(251,191,36,0.2)]">
						🎯
					</div>
					<div>
						<h3 class="font-bold text-base text-zinc-100 flex items-center gap-2">
							Gợi ý thời gian đi chơi lý tưởng nhất tuần này:
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
						class="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 hover:text-white rounded-xl text-xs font-bold transition-all border border-zinc-700 cursor-pointer flex items-center gap-1.5 w-full md:w-auto justify-center"
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
</div>
