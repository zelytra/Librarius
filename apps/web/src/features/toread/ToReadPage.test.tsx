import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { libraryReturns } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ToReadPage } = await import('./ToReadPage');

/** #391: the pile lists the titles owned and not started, with the size of the pile. */
describe('ToReadPage', () => {
  beforeEach(resetAuth);

  test('lists only the owned titles not started yet, with the total', async () => {
    libraryReturns([
      libraryItem({ id: 'a', status: 'OWNED', book: { kind: 'BOOK', title: 'Pas encore ouvert' } }),
      libraryItem({ id: 'b', status: 'OWNED', book: { kind: 'MANGA', title: 'Berserk 1' } }),
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
});
