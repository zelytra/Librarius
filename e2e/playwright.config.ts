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
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
