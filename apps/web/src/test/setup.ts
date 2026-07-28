import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { server } from './server';

// Node ≥ 22 expose un `localStorage` natif qui prend le pas sur celui de jsdom et dont
// getItem est inutilisable sans fichier de stockage : tout composant lisant une
// préférence (le thème) échoue alors au rendu. On lui substitue un stockage mémoire.
// Sans effet sur Node 20, la version de la CI, dont le localStorage jsdom fonctionne.
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

// Une requête non interceptée est une erreur : elle signale un appel absent des
// handlers, donc un test qui ne vérifie pas ce qu'il croit vérifier.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
});

afterAll(() => server.close());
