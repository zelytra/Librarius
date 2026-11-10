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
    renderWithProviders(<PeoplePage />);

    await userEvent.type(screen.getByLabelText('Chercher un lecteur par son nom…'), 'mar');

    // Two "Marc": names are not unique, both are listed.
    expect(await screen.findAllByText('Marc')).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Ne plus suivre Marc' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Suivre Marc' }));
    expect(puts).toEqual(['u-1']);
    expect(screen.getAllByRole('button', { name: 'Ne plus suivre Marc' })).toHaveLength(2);
  });

  test('asks for two letters before searching, and lists who follows the reader', async () => {
    renderWithProviders(<PeoplePage />);

    expect(await screen.findByText("Tape au moins deux lettres du nom d'un lecteur.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Abonnés' }));
    expect(await screen.findByText('Camille')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suivre Camille' })).toBeInTheDocument();
  });
});
