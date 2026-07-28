import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { BUILTIN_CATEGORIES, libraryItem } from '../../test/fixtures';
import { http, HttpResponse, libraryItemReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { DetailPage } = await import('./DetailPage');

const ITEM = libraryItem({ id: 'item-1' });

function renderDetail(id = 'item-1') {
  return renderWithProviders(<DetailPage />, { route: `/detail/${id}`, path: '/detail/:id' });
}

/**
 * Serves `/api/library/{id}` from a single mutable item, so a test can check that the
 * screen re-reads it after a mutation instead of patching its own state.
 */
function servesMutableItem() {
  let item = { ...ITEM };
  server.use(http.get('*/api/library/:id', ({ params }) =>
    params.id === ITEM.id ? HttpResponse.json(item) : new HttpResponse(null, { status: 404 })));
  return {
    current: () => item,
    set: (next: typeof ITEM) => {
      item = next;
    },
  };
}

describe('DetailPage', () => {
  beforeEach(resetAuth);

  test('renders the title details', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    // The title shows up twice: in the header, and on the fallback cover.
    expect(await screen.findByRole('heading', { name: 'Le Nom du vent' })).toBeInTheDocument();
    expect(screen.getByText('Patrick Rothfuss')).toBeInTheDocument();
    expect(screen.getByText('720')).toBeInTheDocument();
    expect(screen.getByText('Chronique du tueur de roi')).toBeInTheDocument();
  });

  test('offers the three ranks', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    expect(await screen.findByText('Or')).toBeInTheDocument();
    expect(screen.getByText('Argent')).toBeInTheDocument();
    expect(screen.getByText('Bronze')).toBeInTheDocument();
  });

  test('assigning a rank is reflected immediately', async () => {
    // The screen re-reads the item after the mutation instead of patching its own
    // state, so the handler has to record the new rank.
    const item = servesMutableItem();
    server.use(http.put('*/api/library/:id/rank', async ({ request }) => {
      const body = (await request.json()) as { categoryId?: string };
      const category = BUILTIN_CATEGORIES.find((c) => c.id === body.categoryId);
      item.set({ ...item.current(), rankCode: category?.code });
      return HttpResponse.json(item.current());
    }));
    renderDetail();

    await userEvent.click(await screen.findByText('Or'));

    // The button switches to the selected state: the accent border is applied.
    await waitFor(() =>
      expect(screen.getByText('Or').closest('button')).toHaveStyle({ borderColor: '#d9b94e' }));
  });

  test('marking as read toggles the label', async () => {
    const item = servesMutableItem();
    server.use(http.put('*/api/library/:id/progress', async ({ request }) => {
      const body = (await request.json()) as { status?: string };
      item.set({ ...item.current(), status: body.status });
      return new HttpResponse(null, { status: 204 });
    }));
    renderDetail();

    await userEvent.click(await screen.findByText('Marquer comme lu'));

    expect(await screen.findByText('✓ Lu')).toBeInTheDocument();
  });

  /** An unknown identifier answers 404, as does one belonging to another user. */
  test('signals a title that cannot be found', async () => {
    libraryItemReturns(ITEM);
    renderDetail('inconnu');

    expect(await screen.findByText('Titre introuvable.')).toBeInTheDocument();
  });

  /** The paginated collection is never downloaded to display one title. */
  test('fetches the single title rather than the collection', async () => {
    const urls: string[] = [];
    server.use(http.get('*/api/library/:id', ({ request }) => {
      urls.push(request.url);
      return HttpResponse.json(ITEM);
    }));
    renderDetail();

    await screen.findByRole('heading', { name: 'Le Nom du vent' });
    expect(urls.some((url) => url.endsWith('/api/library/item-1'))).toBe(true);
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderDetail();

    expect(await screen.findByText(/Connecte-toi pour voir ce titre/)).toBeInTheDocument();
  });
});
