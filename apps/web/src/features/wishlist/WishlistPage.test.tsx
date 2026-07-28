import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { wishlistItem } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { WishlistPage } = await import('./WishlistPage');

function wishlistReturns(items: unknown[]) {
  server.use(http.get('*/api/wishlist', () => HttpResponse.json(items)));
}

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

  test('removing a wish drops it from the list', async () => {
    // The list is re-read after the mutation, so the handler applies the deletion.
    let items = [wishlistItem()];
    server.use(
      http.get('*/api/wishlist', () => HttpResponse.json(items)),
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
});
