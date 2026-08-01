import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { wishlistItem, wishlistPage } from '../../test/fixtures';
import { http, HttpResponse, server, wishlistReturns } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { WishlistPage } = await import('./WishlistPage');

describe('WishlistPage', () => {
  beforeEach(resetAuth);

  test('renders the wishes with their priority and price', async () => {
    wishlistReturns([wishlistItem()]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.getByText('Priorité')).toBeInTheDocument();
    expect(screen.getByText('24,90 €')).toBeInTheDocument();
  });

  test('sums up the estimated budget', async () => {
    wishlistReturns([
      wishlistItem({ id: 'w1', estimatedPrice: 10 }),
      wishlistItem({ id: 'w2', estimatedPrice: 15.5 }),
    ]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/2 titres · estimé 25,50 €/)).toBeInTheDocument();
  });

  test('points to Discover when the list is empty', async () => {
    wishlistReturns([]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/Ta liste de souhaits est vide/)).toBeInTheDocument();
  });

  // ── Desktop layout (#174) ────────────────────────────────────────────────────

  test('lays the priority buckets out in the shared panel grid', async () => {
    wishlistReturns([wishlistItem()]);
    renderWithProviders(<WishlistPage />);

    const bucket = await screen.findByText('Priorité');
    expect(bucket.closest('[class*="groups"]')).toBeInTheDocument();
  });

  test('removing a wish drops it from the list', async () => {
    // The list is re-read after the mutation, so the handler applies the deletion.
    let items = [wishlistItem()];
    server.use(
      http.get('*/api/wishlist', () => HttpResponse.json(wishlistPage(items))),
      http.delete('*/api/wishlist/:id', ({ params }) => {
        items = items.filter((it) => it.id !== params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByLabelText('Retirer'));

    await waitFor(() => expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument());
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/Connecte-toi pour voir tes souhaits/)).toBeInTheDocument();
  });

  // ── Buckets, editing and purchase ──────────────────────────────────────────

  test('groups the wishes by priority, each with its own subtotal', async () => {
    wishlistReturns([
      wishlistItem({ id: 'w1', priority: 'PRIORITY', estimatedPrice: 20 }),
      wishlistItem({
        id: 'w2',
        priority: 'SOMEDAY',
        estimatedPrice: 5,
        book: { kind: 'BOOK', title: 'Les Misérables' },
      }),
      wishlistItem({
        id: 'w3',
        priority: 'SOMEDAY',
        estimatedPrice: 7.5,
        book: { kind: 'BOOK', title: 'Notre-Dame de Paris' },
      }),
    ]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText('Priorité')).toBeInTheDocument();
    expect(screen.getByText('1 titre(s) · 20,00 €')).toBeInTheDocument();
    expect(screen.getByText('Un jour')).toBeInTheDocument();
    expect(screen.getByText('2 titre(s) · 12,50 €')).toBeInTheDocument();
    // A bucket nobody wants anything in is absent, not shown as an empty zero.
    expect(screen.queryByText('Bientôt')).not.toBeInTheDocument();
  });

  test('editing a wish sends the priority, the price and the note it now carries', async () => {
    let items = [wishlistItem({ priority: 'SOMEDAY', estimatedPrice: 12, note: 'Poche' })];
    let sent: unknown = null;
    server.use(
      http.get('*/api/wishlist', () => HttpResponse.json(wishlistPage(items))),
      http.put('*/api/wishlist/:id', async ({ request }) => {
        sent = await request.json();
        items = [{ ...items[0], ...(sent as object) }];
        return HttpResponse.json(items[0]);
      }),
    );
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByLabelText('Modifier'));

    const price = screen.getByLabelText('Prix estimé');
    await userEvent.clear(price);
    await userEvent.type(price, '18,50');
    await userEvent.click(screen.getByText('Priorité'));
    await userEvent.click(screen.getByText('Enregistrer'));

    // A comma is what a French keyboard types; the API only knows about a decimal.
    await waitFor(() =>
      expect(sent).toEqual({ priority: 'PRIORITY', estimatedPrice: 18.5, note: 'Poche' }));
    expect(await screen.findByText('18,50 €')).toBeInTheDocument();
  });

  test('the total is recomputed once an edited price comes back', async () => {
    let items = [wishlistItem({ estimatedPrice: 12 })];
    server.use(
      http.get('*/api/wishlist', () => HttpResponse.json(wishlistPage(items))),
      http.put('*/api/wishlist/:id', async ({ request }) => {
        items = [{ ...items[0], ...((await request.json()) as object) }];
        return HttpResponse.json(items[0]);
      }),
    );
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/estimé 12,00 €/)).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Modifier'));
    const price = screen.getByLabelText('Prix estimé');
    await userEvent.clear(price);
    await userEvent.type(price, '30');
    await userEvent.click(screen.getByText('Enregistrer'));

    expect(await screen.findByText(/estimé 30,00 €/)).toBeInTheDocument();
  });

  test('an empty price clears it rather than blocking the save', async () => {
    let sent: unknown = null;
    server.use(
      http.put('*/api/wishlist/:id', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 'wish-1' });
      }),
    );
    wishlistReturns([wishlistItem()]);
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByLabelText('Modifier'));
    await userEvent.clear(screen.getByLabelText('Prix estimé'));
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(sent).toEqual({ priority: 'PRIORITY', note: 'Édition collector' }));
  });

  /**
   * The editor used to swap its button's label for an ellipsis and nothing else: the
   * shared compact indicator replaces it, and only shows up on a save that actually waits.
   */
  test('shows the compact indicator while a wish is being saved', async () => {
    wishlistReturns([wishlistItem()]);
    server.use(http.put('*/api/wishlist/:id', () => new Promise<Response>(() => {})));
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByLabelText('Modifier'));
    await userEvent.click(screen.getByText('Enregistrer'));

    expect(await screen.findByRole('status', undefined, { timeout: 3000 })).toBeInTheDocument();
  });

  test('a price that is not a number refuses to be saved', async () => {
    wishlistReturns([wishlistItem()]);
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByLabelText('Modifier'));
    await userEvent.clear(screen.getByLabelText('Prix estimé'));
    await userEvent.type(screen.getByLabelText('Prix estimé'), 'gratuit');

    expect(screen.getByText('Enregistrer')).toBeDisabled();
  });

  test('"I bought it" moves the wish into the collection in one gesture', async () => {
    let items = [wishlistItem()];
    let acquired: { id?: string; body?: unknown } = {};
    server.use(
      http.get('*/api/wishlist', () => HttpResponse.json(wishlistPage(items))),
      http.post('*/api/wishlist/:id/acquire', async ({ params, request }) => {
        acquired = { id: String(params.id), body: await request.json() };
        // The server removes the wish and creates the title, in one transaction.
        items = [];
        return HttpResponse.json({ id: 'item-9' }, { status: 201 });
      }),
    );
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByText("Je l'ai acheté"));

    await waitFor(() => expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument());
    expect(acquired.id).toBe('wish-1');
    expect(acquired.body).toMatchObject({ status: 'OWNED' });
  });

  test('a failed purchase says so and leaves the wish alone', async () => {
    wishlistReturns([wishlistItem()]);
    server.use(
      http.post('*/api/wishlist/:id/acquire', () => new HttpResponse(null, { status: 500 })),
    );
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByText("Je l'ai acheté"));

    expect(await screen.findByText(/Impossible de déplacer ce titre/)).toBeInTheDocument();
    expect(screen.getByText('Vinland Saga')).toBeInTheDocument();
  });

  // ── Server-side pagination ─────────────────────────────────────────────────

  /** Sixty wishes, i.e. more than the fifty of a page. */
  const MANY = Array.from({ length: 60 }, (_, i) =>
    wishlistItem({ id: `wish-${i}`, estimatedPrice: 1, book: { kind: 'BOOK', title: `Souhait ${String(i).padStart(2, '0')}` } }));

  test('loads one page and announces the server total', async () => {
    wishlistReturns(MANY);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/60 titres/)).toBeInTheDocument();
    expect(screen.queryByText('Souhait 59')).not.toBeInTheDocument();
  });

  test('the budget covers the whole wishlist and not the loaded page', async () => {
    // Sixty wishes at one euro, fifty of them loaded: a budget summed client-side would
    // announce fifty euros and understate what the list is worth.
    wishlistReturns(MANY);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/60 titres · estimé 60,00 €/)).toBeInTheDocument();
    expect(screen.getByText('60 titre(s) · 60,00 €')).toBeInTheDocument();
  });

  test('the load-more button appends the next page', async () => {
    wishlistReturns(MANY);
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Souhait 00');
    await userEvent.click(await screen.findByText('Voir plus (50 / 60)'));

    expect(await screen.findByText('Souhait 59')).toBeInTheDocument();
    expect(screen.getByText('Souhait 00')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/Voir plus/)).not.toBeInTheDocument());
  });
});
