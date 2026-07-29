import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ImportSection } = await import('./ImportSection');

/**
 * Records every import the screen asks for, whichever route it takes. The CSV handler
 * comes first: `:source` would otherwise swallow `/api/import/csv` too.
 */
function captureImports() {
  const calls: { route: string; body: string }[] = [];
  server.use(
    http.post('*/api/import/csv', async ({ request }) => {
      calls.push({ route: 'csv', body: await request.text() });
      return HttpResponse.json({ imported: 2, skipped: 0 });
    }),
    http.post('*/api/import/:source', async ({ params, request }) => {
      calls.push({ route: String(params.source), body: await request.text() });
      return HttpResponse.json({ imported: 3, skipped: 1 });
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
    expect(await screen.findByText('3 titre(s) importé(s) · 1 déjà présent(s).')).toBeInTheDocument();
  });

  /**
   * Babelio has no handle path at all — the API refuses every one of them — so offering a
   * field and a submit button only taught that through a failed request.
   */
  test('points Babelio at the CSV export instead of offering a handle', async () => {
    const calls = captureImports();
    renderWithProviders(<ImportSection />);

    await userEvent.click(screen.getByRole('button', { name: 'Babelio' }));

    expect(screen.getByText(/sa bibliothèque n'est pas publique/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Pseudo Babelio')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Importer' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Importer un fichier CSV' })).toBeInTheDocument();
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
    expect(screen.queryByText(/sa bibliothèque n'est pas publique/)).not.toBeInTheDocument();
  });

  test('offers to sign in rather than to import when there is no session', () => {
    setAuthenticated(false);
    renderWithProviders(<ImportSection />);

    expect(screen.getByText('Se connecter pour importer')).toBeInTheDocument();
    expect(screen.queryByLabelText('Pseudo Booknode')).not.toBeInTheDocument();
  });
});
