<script lang="ts">
	import { onMount, tick } from 'svelte';

	interface ContainerItem {
		Id?: string;
		id?: string;
		Names?: string[];
		name?: string;
		Image?: string;
		image?: string;
		State?: string;
		state?: string;
	}

	interface Props {
		apiBase?: string;
		containers?: ContainerItem[];
	}

	let { apiBase = '/api', containers = [] }: Props = $props();

	// Target: "host" (máy chủ sentinel) hoặc "container" (docker container cụ thể)
	let targetType = $state<'host' | 'container'>('host');
	let selectedContainerId = $state('');

	// Input command & History
	let currentCommand = $state('');
	let commandHistory = $state<string[]>([]);
	let historyIndex = $state(-1);
	let isExecuting = $state(false);

	// Terminal outputs
	interface TerminalEntry {
		id: string;
		timestamp: string;
		target: string;
		command: string;
		output: string;
		status: 'success' | 'failed' | 'timeout' | 'error';
		exitCode: number;
	}

	let terminalLogs = $state<TerminalEntry[]>([]);
	let terminalContainerRef: HTMLDivElement;
	let inputRef: HTMLInputElement;

	// Shortcut quick commands
	const quickCommands = [
		{ label: 'docker ps', cmd: 'docker ps' },
		{ label: 'Disk (df -h)', cmd: 'df -h' },
		{ label: 'RAM (free -m)', cmd: 'free -m' },
		{ label: 'Uptime', cmd: 'uptime' },
		{ label: 'Top processes', cmd: 'ps aux --sort=-%cpu | head -n 10' },
		{ label: 'Network', cmd: 'ip a || ifconfig' }
	];

	function getContainerDisplayName(c: ContainerItem) {
		const name = (c.Names?.[0] || c.name || '').replace(/^\//, '');
		const id = (c.Id || c.id || '').substring(0, 12);
		return `${name || 'unknown'} (${id})`;
	}

	async function runCommand(cmdToRun?: string) {
		const cmd = (cmdToRun !== undefined ? cmdToRun : currentCommand).trim();
		if (!cmd || isExecuting) return;

		// Thêm vào lịch sử nếu lệnh mới
		if (cmdToRun === undefined) {
			commandHistory = [cmd, ...commandHistory.filter((c) => c !== cmd)].slice(0, 50);
			historyIndex = -1;
			currentCommand = '';
		}

		isExecuting = true;

		const targetLabel = targetType === 'host' 
			? 'host' 
			: `container:${selectedContainerId ? selectedContainerId.substring(0, 12) : 'any'}`;

		try {
			const res = await fetch(`${apiBase}/terminal/exec`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					command: cmd,
					target: targetType,
					containerId: targetType === 'container' ? selectedContainerId : undefined
				})
			});

			const data = await res.json();

			const newEntry: TerminalEntry = {
				id: Math.random().toString(36).substring(2),
				timestamp: new Date().toLocaleTimeString('vi-VN', { hour12: false }),
				target: targetLabel,
				command: cmd,
				output: data.output || '(No output produced)',
				status: data.status || (data.exitCode === 0 ? 'success' : 'failed'),
				exitCode: data.exitCode ?? 0
			};

			terminalLogs = [...terminalLogs, newEntry];
		} catch (err: any) {
			terminalLogs = [
				...terminalLogs,
				{
					id: Math.random().toString(36).substring(2),
					timestamp: new Date().toLocaleTimeString('vi-VN', { hour12: false }),
					target: targetLabel,
					command: cmd,
					output: `Network error: ${err.message}`,
					status: 'error',
					exitCode: -1
				}
			];
		} finally {
			isExecuting = false;
			await tick();
			scrollToBottom();
			if (inputRef) inputRef.focus();
		}
	}

	function handleKeyDown(e: KeyboardEvent) {
		if (e.key === 'Enter') {
			e.preventDefault();
			runCommand();
		} else if (e.key === 'ArrowUp') {
			e.preventDefault();
			if (commandHistory.length > 0 && historyIndex < commandHistory.length - 1) {
				historyIndex++;
				currentCommand = commandHistory[historyIndex];
			}
		} else if (e.key === 'ArrowDown') {
			e.preventDefault();
			if (historyIndex > 0) {
				historyIndex--;
				currentCommand = commandHistory[historyIndex];
			} else if (historyIndex === 0) {
				historyIndex = -1;
				currentCommand = '';
			}
		}
	}

	function clearTerminal() {
		terminalLogs = [];
	}

	function copyOutput(text: string) {
		navigator.clipboard.writeText(text);
	}

	function scrollToBottom() {
		if (terminalContainerRef) {
			terminalContainerRef.scrollTop = terminalContainerRef.scrollHeight;
		}
	}

	onMount(() => {
		if (containers.length > 0) {
			selectedContainerId = containers[0].Id || containers[0].id || '';
		}
		// Banner chào mừng ban đầu
		terminalLogs = [
			{
				id: 'init-banner',
				timestamp: new Date().toLocaleTimeString('vi-VN', { hour12: false }),
				target: 'system',
				command: 'welcome',
				output: '⚡ Sentinel Web Terminal v1.0 connected.\nSelect target (Host or Docker container) and type bash commands below.\nPress [Up]/[Down] arrows to browse command history.',
				status: 'success',
				exitCode: 0
			}
		];
		if (inputRef) inputRef.focus();
	});
</script>

<div class="space-y-4">
	<!-- TOP TOOLBAR & CONTROLS -->
	<div class="bg-zinc-900/60 border border-zinc-800/80 rounded-2xl p-4 backdrop-blur-md flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-xl">
		<!-- Left: Target Selector -->
		<div class="flex flex-wrap items-center gap-3">
			<div class="flex items-center gap-1.5 p-1 bg-zinc-950/80 border border-zinc-800 rounded-xl text-xs">
				<button
					onclick={() => (targetType = 'host')}
					class="px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer {targetType === 'host' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-400 hover:text-zinc-200'}"
				>
					🖥️ Host Server
				</button>
				<button
					onclick={() => (targetType = 'container')}
					class="px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer {targetType === 'container' ? 'bg-indigo-600 text-white shadow' : 'text-zinc-400 hover:text-zinc-200'}"
				>
					🐳 Docker Exec
				</button>
			</div>

			{#if targetType === 'container'}
				<div class="flex items-center gap-2">
					<select
						bind:value={selectedContainerId}
						class="bg-zinc-950/90 text-zinc-200 border border-zinc-700/80 rounded-xl px-3 py-1.5 text-xs font-mono focus:outline-none focus:border-indigo-500 cursor-pointer max-w-xs"
					>
						{#each containers as c}
							<option value={c.Id || c.id}>
								{getContainerDisplayName(c)}
							</option>
						{/each}
					</select>
				</div>
			{/if}
		</div>

		<!-- Right: Action Buttons -->
		<div class="flex items-center gap-2">
			<button
				onclick={clearTerminal}
				class="px-3 py-1.5 rounded-xl bg-zinc-800/60 hover:bg-zinc-800 text-zinc-300 hover:text-white border border-zinc-700/60 text-xs font-medium transition-all cursor-pointer flex items-center gap-1.5"
				title="Xóa màn hình console"
			>
				<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5">
					<path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
				</svg>
				Clear
			</button>
		</div>
	</div>

	<!-- QUICK COMMAND CHIPS -->
	<div class="flex items-center gap-2 overflow-x-auto pb-1 text-xs select-none">
		<span class="text-zinc-500 font-semibold shrink-0 text-[11px]">Lệnh nhanh:</span>
		{#each quickCommands as q}
			<button
				onclick={() => runCommand(q.cmd)}
				disabled={isExecuting}
				class="shrink-0 px-2.5 py-1 rounded-lg bg-zinc-900/80 hover:bg-indigo-600/20 text-zinc-400 hover:text-indigo-300 border border-zinc-800 hover:border-indigo-500/40 font-mono text-[11px] transition-all cursor-pointer disabled:opacity-50"
			>
				{q.label}
			</button>
		{/each}
	</div>

	<!-- TERMINAL CONSOLE SCREEN -->
	<div class="bg-zinc-950 border border-zinc-800/90 rounded-2xl shadow-2xl overflow-hidden flex flex-col h-[520px]">
		<!-- Terminal Window Header -->
		<div class="bg-zinc-900/80 px-4 py-2.5 border-b border-zinc-800 flex items-center justify-between select-none">
			<div class="flex items-center gap-2">
				<div class="w-3 h-3 rounded-full bg-rose-500/80"></div>
				<div class="w-3 h-3 rounded-full bg-amber-500/80"></div>
				<div class="w-3 h-3 rounded-full bg-emerald-500/80"></div>
				<span class="ml-2 font-mono text-xs text-zinc-400 font-semibold flex items-center gap-1.5">
					<span>sentinel@{targetType === 'host' ? 'server' : 'docker'}:~</span>
				</span>
			</div>
			<div class="text-[11px] font-mono text-zinc-500">
				{#if isExecuting}
					<span class="text-amber-400 animate-pulse font-bold flex items-center gap-1.5">
						<span class="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
						Executing...
					</span>
				{:else}
					<span class="text-emerald-500/80">● Ready</span>
				{/if}
			</div>
		</div>

		<!-- Scrollable Output Logs -->
		<div
			bind:this={terminalContainerRef}
			class="flex-1 p-4 overflow-y-auto space-y-4 font-mono text-xs text-zinc-300 leading-relaxed select-text"
		>
			{#each terminalLogs as log (log.id)}
				<div class="space-y-1">
					<!-- Command Header Line -->
					<div class="flex items-center justify-between text-zinc-500 text-[11px] select-none">
						<div class="flex items-center gap-2">
							<span class="text-indigo-400 font-bold">[{log.timestamp}]</span>
							<span class="text-cyan-400">{log.target} $</span>
							<span class="text-zinc-100 font-bold">{log.command}</span>
						</div>
						<div class="flex items-center gap-2">
							<span class="text-[10px] px-1.5 py-0.2 rounded {log.status === 'success' ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-800/40' : 'bg-rose-950/60 text-rose-400 border border-rose-800/40'}">
								exit: {log.exitCode}
							</span>
							<button
								onclick={() => copyOutput(log.output)}
								class="text-zinc-500 hover:text-zinc-300 transition-colors p-1"
								title="Sao chép output"
							>
								📋
							</button>
						</div>
					</div>

					<!-- Output Text -->
					<pre class="p-3 rounded-xl bg-zinc-900/50 border border-zinc-800/60 whitespace-pre-wrap break-all text-zinc-300 font-mono text-xs select-text overflow-x-auto">{log.output}</pre>
				</div>
			{/each}
		</div>

		<!-- Bottom Input Prompt -->
		<div class="p-3 bg-zinc-900/90 border-t border-zinc-800/80 flex items-center gap-2.5">
			<span class="font-mono text-xs font-bold text-emerald-400 shrink-0 select-none">
				{targetType === 'host' ? 'host' : 'container'}$
			</span>
			<input
				bind:this={inputRef}
				bind:value={currentCommand}
				onkeydown={handleKeyDown}
				disabled={isExecuting}
				type="text"
				placeholder={isExecuting ? "Waiting for command to finish..." : "Nhập lệnh shell và nhấn Enter (ví dụ: ls -la, docker ps, free -m)..."}
				class="flex-1 bg-transparent text-sm text-zinc-100 placeholder-zinc-600 font-mono focus:outline-none disabled:opacity-50"
			/>
			{#if isExecuting}
				<span class="w-4 h-4 rounded-full border-2 border-indigo-400 border-t-transparent animate-spin shrink-0"></span>
			{:else}
				<button
					onclick={() => runCommand()}
					disabled={!currentCommand.trim()}
					class="px-3 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:bg-zinc-800 text-white disabled:text-zinc-600 text-xs font-bold font-mono transition-all cursor-pointer disabled:cursor-not-allowed shrink-0"
				>
					Run
				</button>
			{/if}
		</div>
	</div>
</div>
