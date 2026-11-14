import { screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { ExternalAuthor, ExternalRatings } from './ExternalOpinions';

describe('ExternalRatings', () => {
  test('credits each source, links it, and says what its readers think', async () => {
    server.use(http.get('*/api/works/:id/external', () => HttpResponse.json([
      { source: 'Open Library', average: 4.2, count: 1080, readers: 1768, wanted: 21220,
        url: 'https://openlibrary.org/works/OL1W' },
    ])));
    renderWithProviders(<ExternalRatings workId="w1" />);

    const link = await screen.findByRole('link', { name: 'Open Library' });
    expect(link).toHaveAttribute('href', 'https://openlibrary.org/works/OL1W');
    expect(screen.getByText(/★ 4,2 \/ 5/)).toBeInTheDocument();
    expect(screen.getByText(/1\s080 notes/)).toBeInTheDocument();
    expect(screen.getByText(/21\s220 veulent le lire/)).toBeInTheDocument();
  });

  test('shows nothing when no source knows the title', async () => {
    renderWithProviders(<ExternalRatings workId="w1" />);
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByText('Ailleurs')).not.toBeInTheDocument();
  });
});

describe('ExternalAuthor', () => {
  test('shows the article opening with its source and licence', async () => {
    server.use(http.get('*/api/authors/:id/external', () => HttpResponse.json([
      { source: 'Wikipédia', extract: 'Écrivain français.', url: 'https://fr.wikipedia.org/wiki/X', licence: 'CC BY-SA 4.0' },
    ])));
    renderWithProviders(<ExternalAuthor authorId="a1" />);

    expect(await screen.findByText('Écrivain français.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Source : Wikipédia — CC BY-SA 4.0' }))
      .toHaveAttribute('href', 'https://fr.wikipedia.org/wiki/X');
  });
});
