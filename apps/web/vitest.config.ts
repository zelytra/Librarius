import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Separate test configuration, to avoid loading the PWA plugin during the tests.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
