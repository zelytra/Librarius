import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Configuration de test séparée pour éviter de charger le plugin PWA pendant les tests.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
