import { writable } from 'svelte/store';

export type ThemeMode = 'default' | 'anime-pink';

const THEME_STORAGE_KEY = 'sentinel_app_theme';

function createThemeStore() {
	const initialTheme: ThemeMode = (typeof window !== 'undefined' && (localStorage.getItem(THEME_STORAGE_KEY) as ThemeMode)) || 'default';
	const { subscribe, set } = writable<ThemeMode>(initialTheme);

	return {
		subscribe,
		setTheme: (theme: ThemeMode) => {
			if (typeof window !== 'undefined') {
				localStorage.setItem(THEME_STORAGE_KEY, theme);
				if (theme === 'anime-pink') {
					document.documentElement.setAttribute('data-theme', 'anime-pink');
				} else {
					document.documentElement.removeAttribute('data-theme');
				}
			}
			set(theme);
		},
		initTheme: () => {
			if (typeof window !== 'undefined') {
				const saved = (localStorage.getItem(THEME_STORAGE_KEY) as ThemeMode) || 'default';
				if (saved === 'anime-pink') {
					document.documentElement.setAttribute('data-theme', 'anime-pink');
				} else {
					document.documentElement.removeAttribute('data-theme');
				}
				set(saved);
			}
		}
	};
}

export const themeStore = createThemeStore();
