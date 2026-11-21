import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { PeoplePage } = await import('./PeoplePage');

describe('PeoplePage', () => {
  beforeEach(() => {
    resetAuth();
    server.use(
      http.get('*/api/me/following', () => HttpResponse.json([])),
      http.get('*/api/me/followers', () =>
        HttpResponse.json([{ id: 'u-3', displayName: 'Camille', trusted: false }])),
      http.get('*/api/me', () => HttpResponse.json({ id: 'me-1', displayName: 'Moi' })),
      http.get('*/api/feed', () => HttpResponse.json({ items: [], page: 0, size: 20, hasMore: false })),
      http.get('*/api/releases/upcoming', () => HttpResponse.json([])),
    );
  });

  test('finds readers by name and follows one at once', async () => {
    const puts: string[] = [];
    server.use(
      http.get('*/api/users', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q');
        return HttpResponse.json(q === 'mar'
          ? [{ id: 'u-1', displayName: 'Marc', trusted: true, following: false },
            { id: 'u-2', displayName: 'Marc', trusted: false, following: true }]
          : []);
      }),
      http.put('*/api/users/:id/follow', ({ params }) => {
        puts.push(String(params.id));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<PeoplePage />, { route: '/people?tab=search' });

    await userEvent.type(screen.getByLabelText('Chercher un lecteur par son nom…'), 'mar');

    // Two "Marc": names are not unique, both are listed.
    expect(await screen.findAllByText('Marc')).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Ne plus suivre Marc' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Suivre Marc' }));
    expect(puts).toEqual(['u-1']);
    expect(screen.getAllByRole('button', { name: 'Ne plus suivre Marc' })).toHaveLength(2);
  });

  test('asks for two letters before searching, and lists who follows the reader', async () => {
    renderWithProviders(<PeoplePage />, { route: '/people?tab=search' });

    expect(await screen.findByText("Tape au moins deux lettres du nom d'un lecteur.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Abonnés' }));
    expect(await screen.findByText('Camille')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suivre Camille' })).toBeInTheDocument();
  });

  /** #210: the feed is what the screen opens on. */
  test('opens on the feed, and its empty state leads to the search', async () => {
    renderWithProviders(<PeoplePage />, { route: '/people' });

    expect(await screen.findByText('Rien de neuf pour l’instant')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fil' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Trouver des lecteurs' }));
    expect(await screen.findByLabelText('Chercher un lecteur par son nom…')).toBeInTheDocument();
  });

  /** #497: every reader listed opens their profile. #605: yours is reached from the navigation. */
  test('links each reader to a profile, with no « Mon profil » button in the header', async () => {
    renderWithProviders(<PeoplePage />, { route: '/people?tab=followers' });

    const camille = await screen.findByRole('link', { name: 'Camille' });
    expect(camille).toHaveAttribute('href', '/people/u-3');
    // #512: the avatar is part of the link, not only the name.
    expect(camille.querySelector('[aria-hidden="true"]')).not.toBeNull();
    expect(screen.queryByRole('link', { name: 'Mon profil' })).not.toBeInTheDocument();
  });
});
