import type { Handle } from '@sveltejs/kit';

export const handle: Handle = async ({ event, resolve }) => {
	// Bypass SvelteKit's default cross-site check for API routes
	return resolve(event);
};