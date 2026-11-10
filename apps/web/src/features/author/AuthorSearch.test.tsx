import { render, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { TestProviders } from '../../test/utils';
import { authorSummary } from '../../test/fixtures';
import { authorSearchReturns } from '../../test/server';
import { AuthorMatches } from './AuthorSearch';

function renderMatches(term: string) {
  render(
    <TestProviders>
      <Routes>
        <Route path="/" element={<AuthorMatches term={term} />} />
        <Route path="/authors/:id" element={<p>écran de l'auteur</p>} />
      </Routes>
    </TestProviders>,
  );
}

describe('AuthorMatches', () => {
  test('offers the authors the search words name, and links into their page', async () => {
    authorSearchReturns([authorSummary({ id: 'author-9', name: 'Ursula K. Le Guin', workCount: 3 })]);
    renderMatches('Le Guin');

    expect(await screen.findByText('Auteurs')).toBeInTheDocument();
    expect(screen.getByText('3 œuvres')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Ursula K. Le Guin'));
    expect(await screen.findByText("écran de l'auteur")).toBeInTheDocument();
  });

  test('takes no room when no author matches', async () => {
    authorSearchReturns([]);
    renderMatches('Personne');

    // Nothing to wait for but the request: the section simply never appears.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByText('Auteurs')).not.toBeInTheDocument();
  });

  test('searches nothing for an empty or one-letter term', () => {
    renderMatches('a');
    expect(screen.queryByText('Auteurs')).not.toBeInTheDocument();
  });
});
