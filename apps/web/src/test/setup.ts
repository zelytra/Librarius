import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { server } from './server';

// Node >= 22 exposes a native `localStorage` that takes precedence over the jsdom one,
// and whose getItem is unusable without a storage file: any component reading a
// preference (the theme) then fails to render. We substitute an in-memory storage.
// No effect on Node 20, the CI version, whose jsdom localStorage works fine.
if (typeof globalThis.localStorage?.getItem !== 'function') {
  const entries = new Map<string, string>();
  const memoryStorage: Storage = {
    get length() {
      return entries.size;
    },
    clear: () => entries.clear(),
    getItem: (key) => entries.get(key) ?? null,
    key: (index) => [...entries.keys()][index] ?? null,
    removeItem: (key) => void entries.delete(key),
    setItem: (key, value) => void entries.set(key, String(value)),
  };
  for (const target of [globalThis, typeof window !== 'undefined' ? window : undefined]) {
    if (target) {
      Object.defineProperty(target, 'localStorage', {
        value: memoryStorage,
        configurable: true,
        writable: true,
      });
    }
  }
}

// An unhandled request is an error: it flags a call missing from the handlers, hence a
// test that does not verify what it thinks it verifies.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
});

afterAll(() => server.close());
