import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // The default injects the registration as a plain <script src> in <head>, which
      // blocks rendering on a 134-byte file whose only job is to register the service
      // worker — Lighthouse measured 482 ms of it. Deferred, it registers just as
      // reliably, only after the page has painted.
      injectRegister: 'script-defer',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      workbox: {
        // Do not serve the SPA (index.html) for these server routes: otherwise the
        // service worker intercepts the OIDC redirect to Keycloak (/auth) and the API
        // calls (/api, /q), and the user "bounces back" to the front end.
        navigateFallbackDenylist: [/^\/auth/, /^\/api/, /^\/q/],
        // The default list is js/css/html/ico/png/svg/webmanifest — explicit here so the
        // self-hosted Material Symbols subset (shared/styles/fonts/*.woff2, issue #161)
        // is precached too. Without it the icons would still be missing on a cold
        // offline start, just from a same-origin file instead of Google Fonts.
        globPatterns: ['**/*.{js,css,html,ico,png,svg,webmanifest,woff2}'],
      },
      manifest: {
        name: 'Ma Bibliothèque',
        short_name: 'Bibliothèque',
        description: 'Votre bibliothèque personnelle de livres et mangas.',
        lang: 'fr',
        theme_color: '#9aab92',
        background_color: '#f3ede3',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'pwa-maskable-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/q': 'http://localhost:8080',
    },
  },
});
