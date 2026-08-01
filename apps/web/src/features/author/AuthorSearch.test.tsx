import { render, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { TestProviders } from '../../test/utils';
import { authorSummary } from '../../test/fixtures';
import { authorSearchReturns } from '../../test/server';
import { AuthorSearch } from './AuthorSearch';

describe('AuthorSearch', () => {
  test('finds an author by name and links into their page', async () => {
    authorSearchReturns([authorSummary({ id: 'author-9', name: 'Ursula K. Le Guin', workCount: 3 })]);
    render(
      <TestProviders>
        <Routes>
          <Route path="/" element={<AuthorSearch />} />
          <Route path="/authors/:id" element={<p>écran de l'auteur</p>} />
        </Routes>
      </TestProviders>,
    );

    await userEvent.type(screen.getByPlaceholderText('Rechercher un auteur…'), 'Le Guin');

    expect(await screen.findByText('Ursula K. Le Guin')).toBeInTheDocument();
    expect(screen.getByText('3 œuvres')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Ursula K. Le Guin'));

    expect(await screen.findByText("écran de l'auteur")).toBeInTheDocument();
  });

  test('says nothing matched rather than showing an empty list silently', async () => {
    authorSearchReturns([]);
    render(
      <TestProviders>
        <AuthorSearch />
      </TestProviders>,
    );

    await userEvent.type(screen.getByPlaceholderText('Rechercher un auteur…'), 'Personne');

    expect(await screen.findByText('Aucun auteur ne correspond à cette recherche.')).toBeInTheDocument();
  });

  test('searches nothing before anything is typed', () => {
    render(
      <TestProviders>
        <AuthorSearch />
      </TestProviders>,
    );

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
