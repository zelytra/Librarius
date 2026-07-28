import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders, renderWithProviders } from '../../test/utils';
import { seriesDetail, seriesVolumes } from '../../test/fixtures';
import { http, HttpResponse, seriesDetailReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import type { ManualBookDto } from '../../api/generated/librarius';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { SeriesPage } = await import('./SeriesPage');

/** Volumes 1 and 2 owned (1 read), 4 owned, so 3 is a hole and 5 is still ahead. */
const SERIES = seriesDetail();

function renderSeries(id = 'series-1') {
  return renderWithProviders(<SeriesPage />, { route: `/series/${id}`, path: '/series/:id' });
}

/**
 * Serves `/api/series/{id}` from a single mutable payload, so a test can check that the
 * screen re-reads the series after a mutation instead of patching its own state.
 */
function servesMutableSeries() {
  let detail = SERIES;
  server.use(http.get('*/api/series/:id', ({ params }) =>
    params.id === detail.id ? HttpResponse.json(detail) : new HttpResponse(null, { status: 404 })));
  return {
    current: () => detail,
    set: (next: typeof SERIES) => {
      detail = next;
    },
  };
}

describe('SeriesPage', () => {
  beforeEach(resetAuth);

  test('renders the series, its progress and its volumes', async () => {
    seriesDetailReturns(SERIES);
    renderSeries();

    expect(await screen.findByRole('heading', { name: 'Vinland Saga' })).toBeInTheDocument();
    expect(screen.getByText('Série en cours')).toBeInTheDocument();
    expect(screen.getByText('3 / 5 tomes')).toBeInTheDocument();
    // French counts 0 and 1 as singular, which i18next resolves from the locale.
    expect(screen.getByText('1 lu')).toBeInTheDocument();
    expect(screen.getByText('Thorfinn poursuit sa vengeance.')).toBeInTheDocument();
  });

  /**
   * The acceptance criterion of #45: the state of a volume is carried by its fill and its
   * icon, so it is readable without the text — and announced to a screen reader.
   */
  test('tells the four volume states apart', async () => {
    seriesDetailReturns(SERIES);
    renderSeries();

    const read = await screen.findByLabelText('Tome 1 — lu');
    const owned = screen.getByLabelText('Tome 2 — possédé');
    const missing = screen.getByLabelText('Tome 3 — manquant');
    const upcoming = screen.getByLabelText('Tome 5 — à paraître');

    // Four distinct fills, and four distinct icons — neither channel on its own.
    const classes = [read, owned, missing, upcoming].map((cell) => cell.className);
    expect(new Set(classes).size).toBe(4);
    const icons = [read, owned, missing, upcoming].map((cell) => cell.textContent);
    expect(new Set(icons).size).toBe(4);

    // And the holes are named under the grid.
    expect(screen.getByText('Il te manque les tomes 3')).toBeInTheDocument();
  });

  test('adds a missing volume to the wishlist without a reload', async () => {
    seriesDetailReturns(SERIES);
    const posted: ManualBookDto[] = [];
    server.use(http.post('*/api/wishlist', async ({ request }) => {
      const body = (await request.json()) as { book: ManualBookDto };
      posted.push(body.book);
      return HttpResponse.json({ id: 'wish-1' }, { status: 201 });
    }));
    renderSeries();

    await userEvent.click(await screen.findByLabelText('Tome 3 — manquant'));
    await userEvent.click(screen.getByText('Ajouter aux souhaits'));

    // The wish carries the series and the volume, which is what attaches it to this run.
    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0].seriesTitle).toBe('Vinland Saga');
    expect(posted[0].volumeNumber).toBe(3);
    expect(posted[0].kind).toBe('MANGA');

    // The screen says so on the volume itself, without being reloaded.
    expect(await screen.findByLabelText('Tome 3 — manquant · dans mes souhaits')).toBeInTheDocument();
  });

  test('adding a missing volume to the collection repaints it as owned', async () => {
    const series = servesMutableSeries();
    server.use(http.post('*/api/library', () => {
      series.set(seriesDetail({
        ownedCount: 4,
        volumes: seriesVolumes({ total: 5, owned: [1, 2, 3, 4], read: [1] }),
      }));
      return HttpResponse.json({ id: 'item-3' }, { status: 201 });
    }));
    renderSeries();

    await userEvent.click(await screen.findByLabelText('Tome 3 — manquant'));
    await userEvent.click(screen.getByText('Ajouter à ma collection'));

    // The series is re-read: the volume is owned and the run has no hole left.
    expect(await screen.findByLabelText('Tome 3 — possédé')).toBeInTheDocument();
    expect(screen.getByText('4 / 5 tomes')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/Il te manque/)).not.toBeInTheDocument());
  });

  test('reports an add that the API refuses', async () => {
    seriesDetailReturns(SERIES);
    server.use(http.post('*/api/wishlist', () => new HttpResponse(null, { status: 500 })));
    renderSeries();

    await userEvent.click(await screen.findByLabelText('Tome 3 — manquant'));
    await userEvent.click(screen.getByText('Ajouter aux souhaits'));

    expect(await screen.findByText('Ajout impossible pour le moment.')).toBeInTheDocument();
  });

  test('following the series flips the button', async () => {
    const series = servesMutableSeries();
    server.use(http.put('*/api/series/:id/follow', () => {
      series.set(seriesDetail({ followed: true }));
      return new HttpResponse(null, { status: 204 });
    }));
    renderSeries();

    await userEvent.click(await screen.findByText('Suivre cette série'));

    expect(await screen.findByText('✓ Série suivie')).toBeInTheDocument();
  });

  test('opens the volume already in the collection', async () => {
    seriesDetailReturns(SERIES);
    render(
      <TestProviders route="/series/series-1">
        <Routes>
          <Route path="/series/:id" element={<SeriesPage />} />
          <Route path="/detail/:id" element={<p>écran du tome</p>} />
        </Routes>
      </TestProviders>,
    );

    // An owned volume carries its library item, so it is a way into its own screen.
    await userEvent.click(await screen.findByLabelText('Tome 2 — possédé'));

    expect(await screen.findByText('écran du tome')).toBeInTheDocument();
  });

  test('states a series whose volumes are unknown', async () => {
    seriesDetailReturns(seriesDetail({
      totalVolumes: undefined,
      ownedCount: 0,
      readCount: 0,
      followed: true,
      volumes: [],
    }));
    renderSeries();

    expect(await screen.findByText('Aucun tome connu pour cette série.')).toBeInTheDocument();
    expect(screen.getByText('0 tome')).toBeInTheDocument();
  });

  /** 404 is also the answer for a series the user neither owns nor follows. */
  test('signals a series that cannot be found', async () => {
    seriesDetailReturns(SERIES);
    renderSeries('inconnue');

    expect(await screen.findByText('Série introuvable.')).toBeInTheDocument();
  });

  test('offers a retry when the series cannot be loaded', async () => {
    let attempts = 0;
    server.use(http.get('*/api/series/:id', () => {
      attempts += 1;
      return attempts === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(SERIES);
    }));
    renderSeries();

    await userEvent.click(await screen.findByText('Réessayer'));

    expect(await screen.findByRole('heading', { name: 'Vinland Saga' })).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderSeries();

    expect(await screen.findByText(/Connecte-toi pour voir cette série/)).toBeInTheDocument();
  });
});
