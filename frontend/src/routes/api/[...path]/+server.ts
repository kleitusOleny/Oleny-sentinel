import type { RequestHandler } from './$types';
import { env } from '$env/dynamic/private';

// Spring Boot backend API base URL
// Use BACKEND_URL environment variable, defaulting to local dev url http://localhost:8081/api
const BACKEND_URL = env.BACKEND_URL || 'http://localhost:8081/api';

export const fallback: RequestHandler = async ({ request, params }) => {
	// Construct the destination URL
	const destUrl = `${BACKEND_URL}/${params.path}${new URL(request.url).search}`;

	// Forward headers (excluding Host, Connection, Expect, and Content-Length when streaming)
	const headers = new Headers();
	const ignoredHeaders = new Set(['host', 'connection', 'expect', 'content-length']);
	// @ts-ignore
	request.headers.forEach((value, key) => {
		if (!ignoredHeaders.has(key.toLowerCase())) {
			headers.set(key, value);
		}
	});

	try {
		// Forward request body as stream without buffering in memory
		let reqBody: any = undefined;
		if (request.method !== 'GET' && request.method !== 'HEAD') {
			reqBody = request.body;
		}

		// Fetch from the backend
		const response = await fetch(destUrl, {
			method: request.method,
			headers,
			body: reqBody,
			duplex: 'half' // required when forwarding stream body in node fetch
		} as any);

		// Copy response headers
		const responseHeaders = new Headers();
		response.headers.forEach((value, key) => {
			responseHeaders.set(key, value);
		});

		return new Response(response.body, {
			status: response.status,
			statusText: response.statusText,
			headers: responseHeaders
		});
	} catch (err: any) {
		console.error(`[API Proxy Error] Failed to fetch from backend ${destUrl}:`, err);
		return new Response(JSON.stringify({ status: 'error', message: `Backend connection failed: ${err.message}` }), {
			status: 502,
			headers: {
				'Content-Type': 'application/json'
			}
		});
	}
};
