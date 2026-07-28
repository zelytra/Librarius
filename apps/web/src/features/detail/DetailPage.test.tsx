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

  test('renders the title details', async () => {
    libraryReturns([ITEM]);
    renderDetail();

    // The title shows up twice: in the header, and on the fallback cover.
    expect(await screen.findByRole('heading', { name: 'Le Nom du vent' })).toBeInTheDocument();
    expect(screen.getByText('Patrick Rothfuss')).toBeInTheDocument();
    expect(screen.getByText('720')).toBeInTheDocument();
    expect(screen.getByText('Chronique du tueur de roi')).toBeInTheDocument();
  });

  test('offers the three ranks', async () => {
    libraryReturns([ITEM]);
    renderDetail();

    expect(await screen.findByText('Or')).toBeInTheDocument();
    expect(screen.getByText('Argent')).toBeInTheDocument();
    expect(screen.getByText('Bronze')).toBeInTheDocument();
  });

  test('assigning a rank is reflected immediately', async () => {
    libraryReturns([ITEM]);
    server.use(http.put('*/api/library/:id/rank', () => HttpResponse.json({ id: 'item-1', rankCode: 'or' })));
    renderDetail();

    await userEvent.click(await screen.findByText('Or'));

    // The button switches to the selected state: the accent border is applied.
    expect(screen.getByText('Or').closest('button')).toHaveStyle({ borderColor: '#d9b94e' });
  });

  test('marking as read toggles the label', async () => {
    libraryReturns([ITEM]);
    renderDetail();

    await userEvent.click(await screen.findByText('Marquer comme lu'));

    expect(await screen.findByText('✓ Lu')).toBeInTheDocument();
  });

  test('signals a title that cannot be found', async () => {
    libraryReturns([]);
    renderDetail('inconnu');

    expect(await screen.findByText('Titre introuvable.')).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderDetail();

    expect(await screen.findByText(/Connecte-toi pour voir ce titre/)).toBeInTheDocument();
  });
});
