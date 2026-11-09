import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ImportSection } = await import('./ImportSection');

/**
 * Records every import the screen starts, and answers the poll that follows: the import runs
 * as a background job now, so a POST hands back a RUNNING job and the screen reads its outcome
 * from `GET /api/import/jobs/{id}`. The CSV handler comes first: `:source` would otherwise
 * swallow `/api/import/csv` too.
 */
function captureImports() {
  const calls: { route: string; body: string }[] = [];
  server.use(
    http.post('*/api/import/csv', async ({ request }) => {
      calls.push({ route: 'csv', body: await request.text() });
      return HttpResponse.json({ id: 'job-csv', status: 'RUNNING', total: 0, imported: 0, skipped: 0 });
    }),
    http.post('*/api/import/:source', async ({ params, request }) => {
      calls.push({ route: String(params.source), body: await request.text() });
      return HttpResponse.json({ id: `job-${params.source}`, status: 'RUNNING', total: 0, imported: 0, skipped: 0 });
    }),
    // The job is already finished when first polled — the counts are those of whatever started it.
    http.get('*/api/import/jobs/:id', ({ params }) => {
      const done = String(params.id) === 'job-csv'
        ? { imported: 2, skipped: 0 }
        : { imported: 3, skipped: 1 };
      return HttpResponse.json({ id: params.id, status: 'DONE', total: done.imported + done.skipped, ...done });
    }),
  );
  return calls;
}

/** The file input is hidden behind its own button, so it carries neither label nor role. */
function fileInputOf(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error('no file input on the import section');
  return input;
}

describe('ImportSection', () => {
  beforeEach(resetAuth);

  test('imports a Booknode library from a handle', async () => {
    const calls = captureImports();
    renderWithProviders(<ImportSection />);

    await userEvent.type(screen.getByLabelText('Pseudo Booknode'), 'alice');
    await userEvent.click(screen.getByRole('button', { name: 'Importer' }));

    await waitFor(() => expect(calls).toEqual([{ route: 'booknode', body: '{"handle":"alice"}' }]));
    // The result is read from the job, once the poll sees it finish.
    expect(await screen.findByText('3 titre(s) importé(s) · 1 déjà présent(s).')).toBeInTheDocument();
  });

  /** A job that ends in failure surfaces the reader-facing message the API put on it. */
  test('shows the message when an import fails', async () => {
    server.use(
      http.post('*/api/import/:source', () =>
        HttpResponse.json({ id: 'job-x', status: 'RUNNING', total: 0, imported: 0, skipped: 0 })),
      http.get('*/api/import/jobs/:id', () =>
        HttpResponse.json({ id: 'job-x', status: 'FAILED', total: 0, imported: 0, skipped: 0,
          error: 'Profil Booknode introuvable.' })),
    );
    renderWithProviders(<ImportSection />);

    await userEvent.type(screen.getByLabelText('Pseudo Booknode'), 'fantome');
    await userEvent.click(screen.getByRole('button', { name: 'Importer' }));

    expect(await screen.findByText('Profil Booknode introuvable.')).toBeInTheDocument();
  });

  /**
   * Babelio has no handle path at all — its library is shown to its signed-in owner only — so
   * it leads to the collector that reads it from the reader's own browser.
   */
  test('points Babelio at the collector instead of offering a handle', async () => {
    const calls = captureImports();
    renderWithProviders(<ImportSection />);

    await userEvent.click(screen.getByRole('button', { name: 'Babelio' }));

    expect(screen.getByText(/utilise la collecte ci-dessous/)).toBeInTheDocument();
    expect(screen.getByText(/Ouvre « Mes livres » sur Babelio/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Pseudo Babelio')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Importer' })).not.toBeInTheDocument();
    expect(calls).toEqual([]);
  });

  test('offers the collector as a bookmark to drag', () => {
    renderWithProviders(<ImportSection />);

    const link = screen.getByText('Librarius : collecter').closest('a');
    expect(link?.getAttribute('href')?.split(':', 1)[0]).toBe('javascript');
  });

  test('imports the file the collector saved, for the source it was collected on', async () => {
    const calls = captureImports();
    const { container } = renderWithProviders(<ImportSection />);
    const collected = {
      format: 'librarius-collect',
      version: 1,
      source: 'babelio',
      collectedAt: '2026-11-09T10:00:00Z',
      pages: ['<table><tr><td><a href="/livres/X/1">Dune</a></td></tr></table>'],
    };

    await userEvent.upload(
      fileInputOf(container),
      new File([JSON.stringify(collected)], 'librarius-babelio.json', { type: 'application/json' }),
    );

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].route).toBe('collected');
    expect(JSON.parse(calls[0].body)).toEqual({ source: 'babelio', pages: collected.pages });
    expect(await screen.findByText('3 titre(s) importé(s) · 1 déjà présent(s).')).toBeInTheDocument();
  });

  test('imports library pages saved from the browser, several at once', async () => {
    const calls = captureImports();
    const { container } = renderWithProviders(<ImportSection />);

    await userEvent.upload(fileInputOf(container), [
      new File(['<div class="book-item">Dune</div>'], 'page1.html', { type: 'text/html' }),
      new File(['<div class="book-item">Fondation</div>'], 'page2.html', { type: 'text/html' }),
    ]);

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(JSON.parse(calls[0].body)).toEqual({
      source: 'booknode',
      pages: ['<div class="book-item">Dune</div>', '<div class="book-item">Fondation</div>'],
    });
  });

  test('refuses a JSON file the collector did not produce', async () => {
    const calls = captureImports();
    const { container } = renderWithProviders(<ImportSection />);

    await userEvent.upload(
      fileInputOf(container),
      new File(['{"books":[]}'], 'other.json', { type: 'application/json' }),
    );

    expect(await screen.findByText("Ce fichier n'a pas été produit par la collecte Librarius.")).toBeInTheDocument();
    expect(calls).toEqual([]);
  });

  test('imports the CSV file picked from the Babelio panel', async () => {
    const calls = captureImports();
    const { container } = renderWithProviders(<ImportSection />);

    await userEvent.click(screen.getByRole('button', { name: 'Babelio' }));
    await userEvent.upload(
      fileInputOf(container),
      new File(['Titre;Auteur\nDune;Herbert\n'], 'babelio.csv', { type: 'text/csv' }),
    );

    await waitFor(() =>
      expect(calls).toEqual([{ route: 'csv', body: 'Titre;Auteur\nDune;Herbert\n' }]));
    expect(await screen.findByText('2 titre(s) importé(s) · 0 déjà présent(s).')).toBeInTheDocument();
  });

  test('brings the handle field back when Booknode is picked again', async () => {
    renderWithProviders(<ImportSection />);

    await userEvent.click(screen.getByRole('button', { name: 'Babelio' }));
    await userEvent.click(screen.getByRole('button', { name: 'Booknode' }));

    expect(screen.getByLabelText('Pseudo Booknode')).toBeInTheDocument();
    expect(screen.queryByText(/utilise la collecte ci-dessous/)).not.toBeInTheDocument();
  });

  test('offers to sign in rather than to import when there is no session', () => {
    setAuthenticated(false);
    renderWithProviders(<ImportSection />);

    expect(screen.getByText('Se connecter pour importer')).toBeInTheDocument();
    expect(screen.queryByLabelText('Pseudo Booknode')).not.toBeInTheDocument();
  });
});
