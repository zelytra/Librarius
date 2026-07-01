import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      workbox: {
        // Ne pas servir la SPA (index.html) pour ces routes serveur : sinon le
        // service worker intercepte la redirection OIDC vers Keycloak (/auth) et
        // les appels API (/api, /q), et l'utilisateur « revient » sur le front.
        navigateFallbackDenylist: [/^\/auth/, /^\/api/, /^\/q/],
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
