import { defineConfig, devices } from '@playwright/test';
import { BASE_URL } from './support/config';

export default defineConfig({
  testDir: './tests',
  // Every journey drives the same `alice` account against a single database: run in
  // parallel they would delete each other's fixtures.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // One retry in CI absorbs the occasional cold-start hiccup; a test that fails twice is
  // a real failure, and its trace is kept.
  retries: process.env.CI ? 1 : 0,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  globalSetup: './support/global-setup.ts',
  globalTeardown: './support/global-teardown.ts',
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: BASE_URL,
    // The journeys assert the French copy (see support/ui.ts), and since #77 the app
    // follows the browser on a first visit: Chromium advertises `en-US` by default, which
    // would boot every journey in English. This is the browser of the French reader the
    // suite describes, not a workaround.
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
