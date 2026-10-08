// Simple Service Worker enabling PWA Installability
const CACHE_NAME = 'sentinel-pwa-v1';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  // Passthrough to network
  event.respondWith(fetch(event.request).catch(() => caches.match(event.request)));
});