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
    // provides. The files below are the exceptions, and not so they can be applied: a
    // stubbed stylesheet comes back as an empty string, and these four are read as text by
    // a test. `tokens.css` for `shared/ui/breakpoints.test.ts`, which checks that the
    // breakpoints declared in CSS and in TypeScript still agree; `global.css` for
    // `global.test.ts`; the shell's two stylesheets for `app/safeAreas.test.ts`, which
    // checks that the frame and the bottom bar still hold the system bars off (#529);
    // `AdminPage.module.css` for `features/admin/reportScroll.test.ts` (#564);
    // `ReadingSheetPage.module.css` for `features/sheet/sheetInk.test.ts` (#596).
    css: {
      include: [/tokens\.css/, /global\.css/, /AppShell\.module\.css/, /BottomNav\.module\.css/, /AdminPage\.module\.css/, /ReadingSheetPage\.module\.css/],
    },
  },
});
