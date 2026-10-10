<script lang="ts">
	let scratchpadText = $state('');

	function loadNotes() {
		try {
			const saved = localStorage.getItem('sentinel_home_scratchpad');
			if (saved) scratchpadText = saved;
		} catch {}
	}

	function saveNotes() {
		try {
			localStorage.setItem('sentinel_home_scratchpad', scratchpadText);
		} catch {}
	}

	$effect(() => {
		loadNotes();
	});
</script>

<div class="space-y-2">
	<textarea
		bind:value={scratchpadText}
		oninput={saveNotes}
		placeholder="Ghi chú nhanh, lệnh terminal, IP..."
		class="w-full h-32 bg-zinc-950/60 border border-zinc-800 rounded-xl p-2.5 text-xs font-mono text-zinc-200 placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none"
	></textarea>
	<div class="text-[10px] text-zinc-500 text-right">Auto-saved</div>
</div>
