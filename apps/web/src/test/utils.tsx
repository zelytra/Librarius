// Test-only module: it is never served to the browser, so hot refresh does not apply.
// It has to export both components and helpers.
/* eslint-disable react-refresh/only-export-components */
import type { ReactElement, ReactNode } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '../shared/theme/ThemeProvider';
import '../i18n';

/**
 * A throwaway client per render: caching across tests would let one test observe data
 * fetched by another. Retries are off so a deliberate failure surfaces immediately
 * instead of being retried until the test times out.
 *
 * `gcTime` is deliberately left at its default: setting it to 0 collects an entry the
 * moment its last observer unmounts, which drops results mid-render.
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

/**
 * Stubs `window.matchMedia` against a fixed viewport width — the same shim
 * `AppShell.test.tsx` uses to choose which navigation mounts, lifted here so any screen
 * that reads `useViewportAtLeast` (Home's shelves, Collection's chip rows) can assert its
 * desktop layout without redeclaring the stub. jsdom implements no `matchMedia` at all, so
 * every test that never calls this gets the phone layout for free — callers that do should
 * restore it (`delete (window as { matchMedia?: unknown }).matchMedia`) in an `afterEach`.
 */
export function mockViewportWidth(width: number) {
  window.matchMedia = (query: string): MediaQueryList => {
    const minWidth = /\(\s*min-width:\s*(\d+)px\s*\)/.exec(query);
    return {
      matches: minWidth != null && width >= Number(minWidth[1]),
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    } as unknown as MediaQueryList;
  };
}

export function TestProviders({ children, route = '/' }: { children: ReactNode; route?: string }) {
  return (
    <QueryClientProvider client={createTestQueryClient()}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

/**
 * Mounts a screen with the providers it needs (query client, theme, i18n, router).
 *
 * Authentication is not mounted here: the test files that need it replace
 * `react-oidc-context` with `test/oidcMock`.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', path }: { route?: string; path?: string } = {},
) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[route]}>
          {path ? (
            <Routes>
              <Route path={path} element={ui} />
            </Routes>
          ) : (
            ui
          )}
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}
