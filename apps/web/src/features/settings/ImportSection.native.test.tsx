import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import type { CollectedFile } from './collector';

const native = { value: true };
const collectInApp = vi.fn<(source: CollectedFile['source']) => Promise<CollectedFile | null>>();

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));
vi.mock('@capacitor/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@capacitor/core')>();
  return { ...actual, Capacitor: { ...actual.Capacitor, isNativePlatform: () => native.value } };
});
vi.mock('./inAppImport', () => ({ collectInApp }));

const { ImportSection } = await import('./ImportSection');

function captureCollected() {
  const bodies: unknown[] = [];
  server.use(
    http.post('*/api/import/collected', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({ id: 'job-1', status: 'RUNNING', total: 0, imported: 0, skipped: 0 });
    }),
    http.get('*/api/import/jobs/:id', ({ params }) =>
      HttpResponse.json({ id: params.id, status: 'DONE', total: 4, imported: 3, skipped: 1 })),
  );
  return bodies;
}

const FILE: CollectedFile = {
  format: 'librarius-collect',
  version: 2,
  source: 'babelio',
  collectedAt: '2026-11-21T10:00:00Z',
  pages: ['<ul class="lib"></ul>'],
};

/**
 * #570: in the app, the import signs in to the source site in a window the app owns, and the
 * file the collector posts back is imported at once — no bookmark, no download, no file picker.
 */
describe('ImportSection in the app', () => {
  beforeEach(() => {
    resetAuth();
    setAuthenticated(true);
    native.value = true;
    collectInApp.mockReset();
  });

  test('offers to sign in to the chosen site, and no bookmark', async () => {
    renderWithProviders(<ImportSection />);
    expect(screen.getByRole('button', { name: 'Se connecter à Booknode et importer' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Babelio' }));
    expect(screen.getByRole('button', { name: 'Se connecter à Babelio et importer' })).toBeInTheDocument();
    expect(screen.queryByText('Librarius : collecter')).not.toBeInTheDocument();
  });

  test('imports the file collected in the window', async () => {
    const bodies = captureCollected();
    collectInApp.mockResolvedValue(FILE);
    renderWithProviders(<ImportSection />);
    await userEvent.click(screen.getByRole('button', { name: 'Babelio' }));

    await userEvent.click(screen.getByRole('button', { name: 'Se connecter à Babelio et importer' }));

    expect(collectInApp).toHaveBeenCalledWith('babelio');
    await waitFor(() => expect(bodies).toEqual([{ source: 'babelio', pages: FILE.pages }]));
    expect(await screen.findByText('Import terminé')).toBeInTheDocument();
  });

  test('does nothing when the reader closes the window', async () => {
    const bodies = captureCollected();
    collectInApp.mockResolvedValue(null);
    renderWithProviders(<ImportSection />);

    await userEvent.click(screen.getByRole('button', { name: 'Se connecter à Booknode et importer' }));

    await waitFor(() => expect(collectInApp).toHaveBeenCalledWith('booknode'));
    expect(bodies).toEqual([]);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('a browser keeps the bookmark, and has no in-app sign-in', () => {
    native.value = false;
    renderWithProviders(<ImportSection />);
    expect(screen.getByText('Librarius : collecter')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Se connecter à .* et importer/ })).not.toBeInTheDocument();
  });
});
