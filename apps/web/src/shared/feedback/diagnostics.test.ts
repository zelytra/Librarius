import { afterEach, describe, expect, test, vi } from 'vitest';
import { setAccessToken } from '../authToken';
import { context, note, recentLog, reportError, resetDiagnostics, MAX_LOG, MAX_REPORTS } from './diagnostics';

afterEach(() => {
  resetDiagnostics();
  setAccessToken(undefined);
});

/** #489: what the app keeps of its own recent life, to attach to a report. */
describe('the recent log', () => {
  test('keeps the latest lines, oldest first, and forgets beyond its size', () => {
    for (let i = 0; i < MAX_LOG + 5; i++) note(`ligne ${i}`);
    const lines = recentLog().split('\n');
    expect(lines).toHaveLength(MAX_LOG);
    expect(lines[0]).toContain('ligne 5');
    expect(lines.at(-1)).toContain(`ligne ${MAX_LOG + 4}`);
  });

  test('says where the app is, its version and the browser', () => {
    expect(context()).toEqual({
      page: window.location.pathname + window.location.search,
      appVersion: expect.any(String),
      userAgent: navigator.userAgent,
    });
  });
});

/** #490: an error nobody caught is reported by itself — once, and not a flood of them. */
describe('reportError', () => {
  test('sends an error once per session, however often it happens', () => {
    setAccessToken('token');
    const send = vi.fn().mockResolvedValue(undefined);
    const error = new TypeError('x is undefined');
    reportError(error, send);
    reportError(error, send);

    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][0]).toMatchObject({ message: 'TypeError: x is undefined', stack: error.stack });
    expect(recentLog()).toContain('TypeError: x is undefined');
  });

  test(`sends at most ${MAX_REPORTS} distinct errors in a session`, () => {
    setAccessToken('token');
    const send = vi.fn().mockResolvedValue(undefined);
    for (let i = 0; i < MAX_REPORTS + 3; i++) reportError(new Error(`erreur ${i}`), send);
    expect(send).toHaveBeenCalledTimes(MAX_REPORTS);
  });

  test('sends nothing when nobody is signed in, but still logs it', () => {
    const send = vi.fn().mockResolvedValue(undefined);
    reportError('chaîne lancée', send);
    expect(send).not.toHaveBeenCalled();
    expect(recentLog()).toContain('chaîne lancée');
  });

  test('a report that fails to send is not an error of its own', async () => {
    setAccessToken('token');
    const send = vi.fn().mockRejectedValue(new Error('offline'));
    expect(() => reportError(new Error('boom'), send)).not.toThrow();
    await Promise.resolve();
  });
});
