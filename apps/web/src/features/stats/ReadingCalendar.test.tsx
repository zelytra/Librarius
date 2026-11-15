import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { ReadingCalendar } from './ReadingCalendar';

/** #479: the readings of a month, start to finish, with how long each took. */
describe('ReadingCalendar', () => {
  test('lists the month’s readings with their dates and duration, and tints the days read', async () => {
    const asked: string[] = [];
    server.use(http.get('*/api/stats/readings', ({ request }) => {
      const url = new URL(request.url);
      asked.push(`${url.searchParams.get('from')}..${url.searchParams.get('to')}`);
      return HttpResponse.json(url.searchParams.get('from') === '2021-03-01'
        ? [{ itemId: 'i1', title: 'Dune', kind: 'BOOK', startedAt: '2021-03-05', finishedAt: '2021-03-19', days: 15 }]
        : []);
    }));
    renderWithProviders(<ReadingCalendar initialMonth="2021-03" />);

    expect(await screen.findByText('Dune')).toBeInTheDocument();
    expect(screen.getByText('Du 5 au 19 mars · 15 jours')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /mars 2021/i })).toBeInTheDocument();
    // The days the reading covered are marked on the grid, for a screen reader too.
    expect(screen.getByLabelText('12 mars : 1 lecture')).toBeInTheDocument();
    expect(screen.queryByLabelText('20 mars : 1 lecture')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Mois suivant' }));
    expect(await screen.findByText('Aucune lecture terminée ce mois-ci.')).toBeInTheDocument();
    await waitFor(() => expect(asked).toContain('2021-04-01..2021-04-30'));
  });
});
