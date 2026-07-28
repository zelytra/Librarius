import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { wishlistItem } from '../../test/fixtures';
import { wishlistReturns } from '../../test/server';
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

  test('removing a wish drops it from the list', async () => {
    wishlistReturns([wishlistItem()]);
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
