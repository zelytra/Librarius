import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { CollectionPage } = await import('./CollectionPage');

const ROMAN = libraryItem({ id: 'roman-1', book: { kind: 'BOOK', title: 'Le Nom du vent', authors: 'Patrick Rothfuss' } });
const MANGA = libraryItem({ id: 'manga-1', book: { kind: 'MANGA', title: 'Vinland Saga', authors: 'Makoto Yukimura' } });

function libraryReturns(items: unknown[]) {
  server.use(http.get('*/api/library', () => HttpResponse.json(items)));
}

describe('CollectionPage', () => {
  beforeEach(resetAuth);

  test('renders the collection titles', async () => {
    libraryReturns([ROMAN]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Le Nom du vent')).toBeInTheDocument();
  });

  test('shows only the selected kind and switches to the manga shelf', async () => {
    libraryReturns([ROMAN, MANGA]);
    renderWithProviders(<CollectionPage />);

    // Default "Bibliothèque" view: the manga is hidden.
    expect(await screen.findByText('Le Nom du vent')).toBeInTheDocument();
    expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument();

    await userEvent.click(screen.getByText('Mangathèque'));

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument();
  });

  test('counts the displayed titles', async () => {
    libraryReturns([ROMAN, libraryItem({ id: 'roman-2', book: { kind: 'BOOK', title: 'La Peur du sage' } })]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('2 titres')).toBeInTheDocument();
  });

  test('filters by rank', async () => {
    libraryReturns([
      libraryItem({ id: 'or-1', rankCode: 'or', book: { kind: 'BOOK', title: 'Titre doré' } }),
      libraryItem({ id: 'sans', book: { kind: 'BOOK', title: 'Titre sans rang' } }),
    ]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Titre sans rang')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Or'));

    expect(await screen.findByText('Titre doré')).toBeInTheDocument();
    expect(screen.queryByText('Titre sans rang')).not.toBeInTheDocument();
  });

  test('removing a title drops it from the list', async () => {
    // The list is re-read from the server after the mutation, so the handler has to
    // actually apply the deletion — the screen no longer patches its own state.
    let items = [ROMAN];
    server.use(
      http.get('*/api/library', () => HttpResponse.json(items)),
      http.delete('*/api/library/:id', ({ params }) => {
        items = items.filter((it) => it.id !== params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<CollectionPage />);

    await screen.findByText('Le Nom du vent');
    await userEvent.click(screen.getByLabelText('Retirer'));

    await waitFor(() => expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument());
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText(/Connecte-toi pour voir ta collection/)).toBeInTheDocument();
  });
});
