import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { catalogResult } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { DiscoverPage } = await import('./DiscoverPage');

function searchReturns(results: unknown[], status = 200) {
  server.use(
    http.get('*/api/catalog/search', () =>
      status === 200 ? HttpResponse.json(results) : new HttpResponse(null, { status })),
  );
}

async function search(term = 'fourth wing') {
  await userEvent.type(screen.getByPlaceholderText(/Rechercher un titre/), term);
  await userEvent.click(screen.getByLabelText('Rechercher'));
}

describe('DiscoverPage', () => {
  beforeEach(resetAuth);

  test('invites the user to start a search on first render', async () => {
    renderWithProviders(<DiscoverPage />);

    expect(await screen.findByText(/Lancez une recherche/)).toBeInTheDocument();
  });

  test('renders the catalog results', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();

    // The title is rendered twice: in the card, and on the fallback cover.
    expect((await screen.findAllByText('Fourth Wing')).length).toBeGreaterThan(0);
    expect(screen.getByText(/Rebecca Yarros · 2023/)).toBeInTheDocument();
  });

  test('adds a result to the collection', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
  });

  test('adds a result to the wishlist', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Souhaits'));

    expect(await screen.findByText('✓ Ajouté aux souhaits')).toBeInTheDocument();
  });

  test('signals a failed search', async () => {
    searchReturns([], 500);
    renderWithProviders(<DiscoverPage />);

    await search();

    expect(await screen.findByText(/Erreur 500/)).toBeInTheDocument();
  });

  test('tells the user to wait when the catalog quota is exhausted', async () => {
    searchReturns([], 429);
    renderWithProviders(<DiscoverPage />);

    await search();

    // "Error 429" would be useless: what matters is that waiting fixes it.
    expect(await screen.findByText(/Réessaie dans une minute/)).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<DiscoverPage />);

    expect(await screen.findByText(/Connecte-toi pour rechercher/)).toBeInTheDocument();
  });
});
