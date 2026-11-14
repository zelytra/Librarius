import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { http, HttpResponse, libraryReturns, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ToReadPage } = await import('./ToReadPage');

/** #391: the pile lists the titles owned and not started, with the size of the pile. */
describe('ToReadPage', () => {
  beforeEach(resetAuth);

  test('lists only the owned titles not started yet, with the total', async () => {
    libraryReturns([
      libraryItem({ id: 'a', status: 'TO_READ', book: { kind: 'BOOK', title: 'Pas encore ouvert' } }),
      libraryItem({ id: 'b', status: 'TO_READ', book: { kind: 'MANGA', title: 'Berserk 1' } }),
      libraryItem({ id: 'c', status: 'READING', book: { kind: 'BOOK', title: 'En cours' } }),
      libraryItem({ id: 'd', status: 'READ', book: { kind: 'BOOK', title: 'Déjà fini' } }),
    ]);
    renderWithProviders(<ToReadPage />);

    expect(await screen.findByText('Pas encore ouvert')).toBeInTheDocument();
    expect(screen.getByText('Berserk 1')).toBeInTheDocument();
    expect(screen.queryByText('En cours')).not.toBeInTheDocument();
    expect(screen.queryByText('Déjà fini')).not.toBeInTheDocument();
    expect(screen.getByText('2 livres à lire')).toBeInTheDocument();
  });

  test('says the pile is empty when every title is started', async () => {
    libraryReturns([libraryItem({ id: 'd', status: 'READ', book: { title: 'Déjà fini' } })]);
    renderWithProviders(<ToReadPage />);

    expect(await screen.findByText('Ta pile à lire est vide')).toBeInTheDocument();
  });

  /** #430: the pile can be searched, on the server, and says when nothing matches. */
  test('searches the pile', async () => {
    libraryReturns([
      libraryItem({ id: 'a', status: 'TO_READ', book: { kind: 'BOOK', title: 'Pas encore ouvert' } }),
      libraryItem({ id: 'b', status: 'TO_READ', book: { kind: 'MANGA', title: 'Berserk 1' } }),
    ]);
    renderWithProviders(<ToReadPage />);

    await screen.findByText('Berserk 1');
    await userEvent.type(screen.getByLabelText('Rechercher dans ma pile à lire…'), 'berserk');
    await waitFor(() => expect(screen.queryByText('Pas encore ouvert')).not.toBeInTheDocument());
    expect(screen.getByText('Berserk 1')).toBeInTheDocument();
    expect(screen.getByText('1 livre à lire')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Rechercher dans ma pile à lire…'));
    await userEvent.type(screen.getByLabelText('Rechercher dans ma pile à lire…'), 'zzz');
    expect(await screen.findByText('Aucun titre de ta pile ne correspond à « zzz ».')).toBeInTheDocument();
  });

  /** #458: the pile narrows to books, manga or comics, like the library. */
  test('narrows the pile to one kind', async () => {
    libraryReturns([
      libraryItem({ id: 'a', status: 'TO_READ', book: { kind: 'BOOK', title: 'Pas encore ouvert' } }),
      libraryItem({ id: 'b', status: 'TO_READ', book: { kind: 'MANGA', title: 'Berserk 1' } }),
    ]);
    renderWithProviders(<ToReadPage />);

    await screen.findByText('Pas encore ouvert');
    await userEvent.click(screen.getByRole('button', { name: 'Mangas' }));
    await waitFor(() => expect(screen.queryByText('Pas encore ouvert')).not.toBeInTheDocument());
    expect(screen.getByText('Berserk 1')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'BD' }));
    expect(await screen.findByText('Aucun titre de ce type dans ta pile.')).toBeInTheDocument();
  });

  /** #457: several titles of the pile are picked, then rated and filed in one go. */
  test('selects several titles and applies one action to all of them', async () => {
    libraryReturns([
      libraryItem({ id: 'a', status: 'TO_READ', book: { kind: 'BOOK', title: 'Premier' } }),
      libraryItem({ id: 'b', status: 'TO_READ', book: { kind: 'BOOK', title: 'Deuxième' } }),
      libraryItem({ id: 'c', status: 'TO_READ', book: { kind: 'BOOK', title: 'Troisième' } }),
    ]);
    const sent: unknown[] = [];
    server.use(http.post('*/api/library/bulk', async ({ request }) => {
      const body = await request.json();
      sent.push(body);
      return HttpResponse.json({ updated: 2 });
    }));
    renderWithProviders(<ToReadPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Sélectionner' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Choisir « Premier »' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Choisir « Troisième »' }));
    expect(screen.getByText('2 sélectionnés')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Modifier' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Or' }));

    await waitFor(() => expect(sent).toEqual([{ ids: ['a', 'c'], categoryId: 'cat-or' }]));
  });
});
