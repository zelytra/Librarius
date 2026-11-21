import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { CollectedFile } from './collector';

/** The native window, faked: what the app opens, injects and hears back. */
type Listener = (event: { detail?: Record<string, unknown> }) => void;
const listeners = new Map<string, Listener>();
const plugin = {
  openWebView: vi.fn(() => Promise.resolve({ id: 'w1' })),
  executeScript: vi.fn(() => Promise.resolve()),
  close: vi.fn(() => Promise.resolve()),
  addListener: vi.fn((event: string, listener: Listener) => {
    listeners.set(event, listener);
    return Promise.resolve({ remove: () => Promise.resolve(listeners.delete(event)) });
  }),
};

vi.mock('@capgo/inappbrowser', () => ({ InAppBrowser: plugin }));

const { collectInApp, IN_APP_START } = await import('./inAppImport');
const { inAppCollectorScript } = await import('./collector');

const FILE: CollectedFile = {
  format: 'librarius-collect',
  version: 2,
  source: 'babelio',
  collectedAt: '2026-11-21T10:00:00Z',
  pages: ['<ul class="lib"></ul>'],
};

/** Lets the awaited calls inside `collectInApp` run before the test fires an event. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

/**
 * #570: the in-app import opens the source site in a window the app owns, injects the collector
 * into every page the reader loads, and resolves with the file the collector posts back.
 */
describe('collectInApp', () => {
  beforeEach(() => {
    listeners.clear();
    vi.clearAllMocks();
  });
  afterEach(() => listeners.clear());

  test('opens the sign-in page of the source', async () => {
    void collectInApp('babelio');
    await settle();
    expect(plugin.openWebView).toHaveBeenCalledWith(expect.objectContaining({ url: IN_APP_START.babelio }));
  });

  test('injects the collector into every page the reader loads', async () => {
    void collectInApp('mangacollec');
    await settle();
    listeners.get('browserPageLoaded')?.({});
    listeners.get('browserPageLoaded')?.({});
    expect(plugin.executeScript).toHaveBeenCalledTimes(2);
    expect(plugin.executeScript).toHaveBeenCalledWith({ code: inAppCollectorScript() });
  });

  test('resolves with the collected file, closes the window and stops listening', async () => {
    const result = collectInApp('babelio');
    await settle();
    listeners.get('messageFromWebview')?.({ detail: { something: 'else' } });
    listeners.get('messageFromWebview')?.({ detail: FILE as unknown as Record<string, unknown> });
    await expect(result).resolves.toEqual(FILE);
    expect(plugin.close).toHaveBeenCalled();
    await settle();
    expect(listeners.size).toBe(0);
  });

  test('resolves with nothing when the reader closes the window', async () => {
    const result = collectInApp('booknode');
    await settle();
    listeners.get('closeEvent')?.({});
    await expect(result).resolves.toBeNull();
    await settle();
    expect(listeners.size).toBe(0);
  });
});
