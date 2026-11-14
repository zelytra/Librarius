import { screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { FriendsOnWork } from './FriendsOnWork';

describe('FriendsOnWork', () => {
  test('lists the friends who have the title, with what they did and their category', async () => {
    server.use(http.get('*/api/works/:id/friends', () => HttpResponse.json([
      { id: 'u1', displayName: 'Bob', trusted: false, status: 'READ', ownership: 'OWNED', wished: false,
        rating: 4, rankCode: 'or', rankLabel: 'Or', rankColor: '#c9a227' },
      { id: 'u2', displayName: 'Carol', trusted: false, wished: true },
    ])));
    renderWithProviders(<FriendsOnWork workId="w1" />);

    expect(await screen.findByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('Lu · Possédé')).toBeInTheDocument();
    expect(screen.getByText('Or')).toBeInTheDocument();
    expect(screen.getByText('Carol')).toBeInTheDocument();
    expect(screen.getByText('En envie')).toBeInTheDocument();
  });

  test('says nothing when no friend has it', async () => {
    renderWithProviders(<FriendsOnWork workId="w1" />);
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByText("Tes amis l'ont aussi")).not.toBeInTheDocument();
  });
});
