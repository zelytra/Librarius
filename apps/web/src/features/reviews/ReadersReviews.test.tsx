import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import type { SharedReviewsDto } from '../../api/generated/librarius';
import { ReadersReviews } from './ReadersReviews';

/**
 * Other readers' shared reviews (#207) and reporting one of them (#208). The API has already
 * gated who is listed, so the component only has to show what it is given.
 */

const shared: SharedReviewsDto = {
  average: 4.5,
  count: 2,
  reviews: [
    {
      id: 'review-1',
      authorId: 'bob',
      authorName: 'Bob',
      rating: 5,
      review: 'Un chef-d’œuvre.',
      updatedAt: '2026-10-01T10:00:00Z',
    },
    { id: 'review-2', authorId: 'carol', authorName: 'Carol', rating: 4, updatedAt: '2026-09-01T10:00:00Z' },
  ],
};

describe('readers reviews', () => {
  test('invites to share when nobody visible has shared anything', () => {
    renderWithProviders(<ReadersReviews data={{ count: 0, reviews: [] }} />);

    expect(screen.getByRole('region', { name: 'Avis des lecteurs' })).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });

  test('lists each reader with their rating and review, under the average', () => {
    renderWithProviders(<ReadersReviews data={shared} />);

    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Bob')).toBeInTheDocument();
    expect(within(items[0]).getByText('Un chef-d’œuvre.')).toBeInTheDocument();
    expect(within(items[1]).getByText('Carol')).toBeInTheDocument();
    expect(screen.getByText(/4,5/)).toBeInTheDocument();
  });

  test('reports a review with a review reason, targeting the review itself', async () => {
    const posted: unknown[] = [];
    server.use(
      http.post('*/api/reports', async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json({ id: 'r', status: 'OPEN' }, { status: 201 });
      }),
    );
    renderWithProviders(<ReadersReviews data={shared} kind="SERIES_REVIEW" />);

    await userEvent.click(within(screen.getAllByRole('listitem')[0]).getByText('Signaler'));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText('Signaler cet avis')).toBeInTheDocument();
    expect(dialog.queryByText('Mauvaise couverture')).not.toBeInTheDocument();

    await userEvent.click(dialog.getByText("Divulgâche l'histoire"));
    await userEvent.click(dialog.getByText('Envoyer le signalement'));

    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toMatchObject({ targetType: 'SERIES_REVIEW', targetId: 'review-1', reason: 'SPOILER' });
  });
});
