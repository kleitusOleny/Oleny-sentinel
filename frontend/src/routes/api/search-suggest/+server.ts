import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ url }) => {
	const query = url.searchParams.get('q') || '';
	if (!query.trim()) {
		return new Response(JSON.stringify([]), {
			headers: { 'Content-Type': 'application/json' }
		});
	}

	try {
		const bingUrl = `https://api.bing.com/osjson.aspx?query=${encodeURIComponent(query)}`;
		const res = await fetch(bingUrl, {
			headers: {
				'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
			}
		});

		if (!res.ok) {
			return new Response(JSON.stringify([]), {
				headers: { 'Content-Type': 'application/json' }
			});
		}

		const data = await res.json();
		const suggestions = Array.isArray(data) && Array.isArray(data[1]) ? data[1] : [];

		return new Response(JSON.stringify(suggestions), {
			headers: {
				'Content-Type': 'application/json',
				'Cache-Control': 'public, max-age=300'
			}
		});
	} catch {
		return new Response(JSON.stringify([]), {
			headers: { 'Content-Type': 'application/json' }
		});
	}
};
