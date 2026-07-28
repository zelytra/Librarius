import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { DetailPage } = await import('./DetailPage');

const ITEM = libraryItem({ id: 'item-1' });

function libraryReturns(items: unknown[]) {
  server.use(http.get('*/api/library', () => HttpResponse.json(items)));
}

function renderDetail(id = 'item-1') {
  return renderWithProviders(<DetailPage />, { route: `/detail/${id}`, path: '/detail/:id' });
}

describe('DetailPage', () => {
  beforeEach(resetAuth);

  test('affiche la fiche du titre', async () => {
    libraryReturns([ITEM]);
    renderDetail();

    // Le titre apparaît deux fois : en en-tête, et sur la couverture de repli.
    expect(await screen.findByRole('heading', { name: 'Le Nom du vent' })).toBeInTheDocument();
    expect(screen.getByText('Patrick Rothfuss')).toBeInTheDocument();
    expect(screen.getByText('720')).toBeInTheDocument();
    expect(screen.getByText('Chronique du tueur de roi')).toBeInTheDocument();
  });

  test('propose les trois rangs', async () => {
    libraryReturns([ITEM]);
    renderDetail();

    expect(await screen.findByText('Or')).toBeInTheDocument();
    expect(screen.getByText('Argent')).toBeInTheDocument();
    expect(screen.getByText('Bronze')).toBeInTheDocument();
  });

  test('attribuer un rang le reflète immédiatement', async () => {
    libraryReturns([ITEM]);
    server.use(http.put('*/api/library/:id/rank', () => HttpResponse.json({ id: 'item-1', rankCode: 'or' })));
    renderDetail();

    await userEvent.click(await screen.findByText('Or'));

    // Le bouton passe à l'état sélectionné : la bordure d'accent est appliquée.
    expect(screen.getByText('Or').closest('button')).toHaveStyle({ borderColor: '#d9b94e' });
  });

  test('marquer comme lu bascule le libellé', async () => {
    libraryReturns([ITEM]);
    renderDetail();

    await userEvent.click(await screen.findByText('Marquer comme lu'));

    expect(await screen.findByText('✓ Lu')).toBeInTheDocument();
  });

  test('signale un titre introuvable', async () => {
    libraryReturns([]);
    renderDetail('inconnu');

    expect(await screen.findByText('Titre introuvable.')).toBeInTheDocument();
  });

  test('invite à se connecter quand la session est absente', async () => {
    setAuthenticated(false);
    renderDetail();

    expect(await screen.findByText(/Connecte-toi pour voir ce titre/)).toBeInTheDocument();
  });
});
