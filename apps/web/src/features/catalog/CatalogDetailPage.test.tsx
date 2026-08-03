import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { ThemeProvider } from '../../shared/theme/ThemeProvider';
import { createTestQueryClient } from '../../test/utils';
import { catalogResult } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { CatalogDetailPage } = await import('./CatalogDetailPage');

/**
 * Renders the fiche at its route, with the picked result carried in navigation state the way
 * the search list hands it over — or without it, to exercise the cold-open fallback.
 */
function renderFiche(result?: ReturnType<typeof catalogResult>) {
  const entry = result
    ? { pathname: '/catalog/openlibrary/OL123W', state: { result } }
    : { pathname: '/catalog/openlibrary/OL123W' };
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/catalog/:provider/:ref" element={<CatalogDetailPage />} />
            <Route path="/discover" element={<p>écran de recherche</p>} />
            <Route path="/authors/:id" element={<p>écran de l'auteur</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('CatalogDetailPage', () => {
  beforeEach(resetAuth);

  test('renders the fiche of a result handed over in navigation state', async () => {
    renderFiche(catalogResult({
      title: 'Fourth Wing',
      authors: 'Rebecca Yarros',
      year: 2023,
      pageCount: 512,
      seriesTitle: 'The Empyrean',
      volumeNumber: 1,
      synopsis: 'Une école de dragons.',
      publisher: 'Piatkus',
    }));

    expect(await screen.findByRole('heading', { name: 'Fourth Wing' })).toBeInTheDocument();
    expect(screen.getByText('Rebecca Yarros')).toBeInTheDocument();
    expect(screen.getByText('512')).toBeInTheDocument();
    expect(screen.getByText(/The Empyrean/)).toBeInTheDocument();
    expect(screen.getByText('Une école de dragons.')).toBeInTheDocument();
  });

  test('adds the shown result to the collection, carrying its provider reference', async () => {
    const posts: Record<string, unknown>[] = [];
    server.use(http.post('*/api/library', async ({ request }) => {
      posts.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ id: 'nouveau' }, { status: 201 });
    }));
    renderFiche(catalogResult());

    await userEvent.click(await screen.findByText('Ajouter à ma collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
    expect(posts[0].book).toMatchObject({ provider: 'openlibrary', providerRef: 'OL123W' });
  });

  test('adds the shown result to the wishlist', async () => {
    renderFiche(catalogResult());

    await userEvent.click(await screen.findByText('Ajouter aux souhaits'));

    expect(await screen.findByText('✓ Ajouté aux souhaits')).toBeInTheDocument();
  });

  test('sends a fiche reached without its result back to the search', async () => {
    renderFiche();

    await userEvent.click(await screen.findByText('Retour à la recherche'));

    expect(await screen.findByText('écran de recherche')).toBeInTheDocument();
  });

  /** The credit line is clickable here too, through the same resolver the rest of the app uses. */
  test('links a credited author that resolves to a known one', async () => {
    server.use(http.get('*/api/authors', ({ request }) => {
      const q = (new URL(request.url).searchParams.get('q') ?? '').trim().toLowerCase();
      return q === 'rebecca yarros'
        ? HttpResponse.json([{ id: 'author-1', name: 'Rebecca Yarros', workCount: 2, followed: false }])
        : HttpResponse.json([]);
    }));
    renderFiche(catalogResult({ authors: 'Rebecca Yarros' }));

    await userEvent.click(await screen.findByRole('link', { name: 'Rebecca Yarros' }));

    expect(await screen.findByText("écran de l'auteur")).toBeInTheDocument();
  });
});
