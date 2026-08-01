import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test } from 'vitest';
import { vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { BlockedMembersSection } = await import('./BlockedMembersSection');

function blockedReturns(members: { id: string; displayName: string; trusted: boolean }[]) {
  server.use(http.get('*/api/me/blocked', () => HttpResponse.json(members)));
}

describe('BlockedMembersSection', () => {
  beforeEach(resetAuth);

  test('lists the members the caller blocks', async () => {
    blockedReturns([{ id: 'bob', displayName: 'Bob', trusted: false }]);
    renderWithProviders(<BlockedMembersSection />);

    expect(await screen.findByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('Débloquer')).toBeInTheDocument();
  });

  test('shows an empty state when nobody is blocked', async () => {
    blockedReturns([]);
    renderWithProviders(<BlockedMembersSection />);

    expect(await screen.findByText("Tu n'as bloqué personne.")).toBeInTheDocument();
  });

  /** Unblocking calls DELETE /api/users/{id}/block for that member. */
  test('unblocks a member', async () => {
    blockedReturns([{ id: 'bob', displayName: 'Bob', trusted: false }]);
    const deleted: string[] = [];
    server.use(
      http.delete('*/api/users/:id/block', ({ params }) => {
        deleted.push(String(params.id));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<BlockedMembersSection />);

    await userEvent.click(await screen.findByText('Débloquer'));

    await waitFor(() => expect(deleted).toEqual(['bob']));
  });

  test('asks for a session when signed out', async () => {
    setAuthenticated(false);
    renderWithProviders(<BlockedMembersSection />);

    expect(
      await screen.findByText(/Connecte-toi pour gérer les membres que tu bloques/),
    ).toBeInTheDocument();
  });
});
