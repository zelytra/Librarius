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

  const reading = (id: string, title: string, startedAt: string | undefined, finishedAt: string, coverUrl?: string) =>
    ({ itemId: id, title, kind: 'BOOK', startedAt, finishedAt, days: 1, coverUrl });

  function readingsReturn(readings: unknown[]) {
    server.use(http.get('*/api/stats/readings', () => HttpResponse.json(readings)));
  }

  /** #567: each row of the list under the grid carries the book's cover. */
  test('shows each listed reading with its cover', async () => {
    readingsReturn([
      reading('i1', 'Dune', '2021-03-05', '2021-03-19', 'https://cover.test/dune.jpg'),
      reading('i2', 'Sans couverture', '2021-03-02', '2021-03-03'),
    ]);
    renderWithProviders(<ReadingCalendar initialMonth="2021-03" />);

    const dune = (await screen.findByText('Dune')).closest('li')!;
    expect(dune.querySelector('img')).toHaveAttribute('src', 'https://cover.test/dune.jpg');
    // Without an image, a coloured spine stands in, as everywhere else.
    const bare = screen.getByText('Sans couverture').closest('li')!;
    expect(bare.querySelector('[data-cover]')).not.toBeNull();
  });

  /** #643: a listed reading opens the book's screen. */
  test('links each listed reading to the book’s Detail screen', async () => {
    readingsReturn([
      reading('i1', 'Dune', '2021-03-05', '2021-03-19', 'https://cover.test/dune.jpg'),
      reading('i2', 'Hyperion', undefined, '2021-03-12'),
    ]);
    renderWithProviders(<ReadingCalendar initialMonth="2021-03" />);

    expect(await screen.findByRole('link', { name: /Dune/ })).toHaveAttribute('href', '/detail/i1');
    expect(screen.getByRole('link', { name: /Hyperion/ })).toHaveAttribute('href', '/detail/i2');
  });

  /** #485: a book shows in the day it was finished, as its cover. */
  test('places each finished book in its day, as its cover', async () => {
    readingsReturn([
      reading('i1', 'Dune', '2021-03-05', '2021-03-19', 'https://covers.example/dune.jpg'),
      reading('i2', 'Hyperion', undefined, '2021-03-19'),
    ]);
    renderWithProviders(<ReadingCalendar initialMonth="2021-03" />);

    const day = await screen.findByRole('button', { name: '19 mars : 2 lectures' });
    expect(day.querySelector('img')).toHaveAttribute('src', 'https://covers.example/dune.jpg');
    // The second book of the day is counted on the cell, not hidden.
    expect(day).toHaveTextContent('+1');
    // A day covered by a reading but not its last carries no cover.
    expect(screen.getByRole('button', { name: '12 mars : 1 lecture' }).querySelector('img')).toBeNull();
  });

  /** #485: a day picked lists its own readings only; picked again, the month comes back. */
  test('lists the readings of the day picked on the grid', async () => {
    readingsReturn([
      reading('i1', 'Dune', '2021-03-05', '2021-03-08'),
      reading('i2', 'Hyperion', '2021-03-20', '2021-03-22'),
    ]);
    renderWithProviders(<ReadingCalendar initialMonth="2021-03" />);

    const day = await screen.findByRole('button', { name: '21 mars : 1 lecture' });
    await userEvent.click(day);
    expect(day).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('Hyperion')).toBeInTheDocument();
    expect(screen.queryByText('Dune')).not.toBeInTheDocument();

    await userEvent.click(day);
    expect(day).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText('Dune')).toBeInTheDocument();
  });

  /** #485: a busy month no longer runs down the page. */
  test('lists the first readings of the month and the rest on demand', async () => {
    readingsReturn(Array.from({ length: 7 }, (_, i) => reading(`i${i}`, `Livre ${i}`, undefined, `2021-03-1${i}`)));
    renderWithProviders(<ReadingCalendar initialMonth="2021-03" />);

    expect(await screen.findByText('Livre 0')).toBeInTheDocument();
    expect(screen.queryByText('Livre 4')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Voir les 3 autres lectures' }));
    expect(screen.getByText('Livre 6')).toBeInTheDocument();
  });
});
