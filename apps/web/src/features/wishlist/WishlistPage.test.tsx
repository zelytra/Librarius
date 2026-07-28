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

  test('affiche les souhaits avec leur priorité et leur prix', async () => {
    wishlistReturns([wishlistItem()]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.getByText('Priorité')).toBeInTheDocument();
    expect(screen.getByText('24,90 €')).toBeInTheDocument();
  });

  test('totalise le budget estimé', async () => {
    wishlistReturns([
      wishlistItem({ id: 'w1', estimatedPrice: 10 }),
      wishlistItem({ id: 'w2', estimatedPrice: 15.5 }),
    ]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/2 titres · estimé 25,50 €/)).toBeInTheDocument();
  });

  test('propose Découvrir quand la liste est vide', async () => {
    wishlistReturns([]);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/Ta liste de souhaits est vide/)).toBeInTheDocument();
  });

  test('retirer un souhait le fait disparaître', async () => {
    wishlistReturns([wishlistItem()]);
    renderWithProviders(<WishlistPage />);

    await screen.findByText('Vinland Saga');
    await userEvent.click(screen.getByLabelText('Retirer'));

    await waitFor(() => expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument());
  });

  test('invite à se connecter quand la session est absente', async () => {
    setAuthenticated(false);
    renderWithProviders(<WishlistPage />);

    expect(await screen.findByText(/Connecte-toi pour voir tes souhaits/)).toBeInTheDocument();
  });
});
