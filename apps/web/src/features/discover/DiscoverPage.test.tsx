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

  test('invite à lancer une recherche au premier affichage', async () => {
    renderWithProviders(<DiscoverPage />);

    expect(await screen.findByText(/Lancez une recherche/)).toBeInTheDocument();
  });

  test('affiche les résultats du catalogue', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();

    // Le titre est rendu deux fois : dans la fiche, et sur la couverture de repli.
    expect((await screen.findAllByText('Fourth Wing')).length).toBeGreaterThan(0);
    expect(screen.getByText(/Rebecca Yarros · 2023/)).toBeInTheDocument();
  });

  test('ajoute un résultat à la collection', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
  });

  test('ajoute un résultat aux souhaits', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Souhaits'));

    expect(await screen.findByText('✓ Ajouté aux souhaits')).toBeInTheDocument();
  });

  test('signale une recherche en échec', async () => {
    searchReturns([], 500);
    renderWithProviders(<DiscoverPage />);

    await search();

    expect(await screen.findByText(/Erreur 500/)).toBeInTheDocument();
  });

  test('invite à se connecter quand la session est absente', async () => {
    setAuthenticated(false);
    renderWithProviders(<DiscoverPage />);

    expect(await screen.findByText(/Connecte-toi pour rechercher/)).toBeInTheDocument();
  });
});
