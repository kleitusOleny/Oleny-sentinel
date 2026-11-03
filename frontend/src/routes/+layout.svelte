<script lang="ts">
	import { onMount } from 'svelte';
	import favicon from '$lib/assets/favicon.svg';
	import { themeStore } from '$lib/stores/themeStore';
	import '../app.css';

	let { children } = $props();

	onMount(() => {
		themeStore.initTheme();
	});
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

{@render children()}
