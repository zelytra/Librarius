import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Separate test configuration, to avoid loading the PWA plugin during the tests.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // CSS stays out of the run: jsdom lays nothing out, so processing it would only cost
    // time — components are asserted on their class names, which the default stub still
    // provides. `tokens.css` is the one exception, and not so it can be applied:
    // `shared/ui/breakpoints.test.ts` imports its text to check that the breakpoints
    // declared in CSS and the ones declared in TypeScript still agree, and a stubbed file
    // comes back as an empty string.
    css: { include: [/tokens\.css/] },
  },
});
