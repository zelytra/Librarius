import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { catalogResult, libraryItem, stats } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { HomePage } = await import('./HomePage');

function libraryReturns(items: unknown[]) {
  server.use(http.get('*/api/library', () => HttpResponse.json(items)));
}

describe('HomePage', () => {
  beforeEach(resetAuth);

  test('renders the library counters', async () => {
    server.use(http.get('*/api/stats', () => HttpResponse.json(stats({ read: 12, reading: 2, toRead: 34 }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('lus')).toBeInTheDocument();
    expect(screen.getByText('en cours')).toBeInTheDocument();
    expect(screen.getByText('à lire')).toBeInTheDocument();
  });

  test('offers to resume the books being read', async () => {
    libraryReturns([libraryItem({ id: 'en-cours', status: 'READING' })]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Reprendre la lecture')).toBeInTheDocument();
    expect(screen.getByText('1 en cours')).toBeInTheDocument();
  });

  test('hides the resume section when nothing is being read', async () => {
    libraryReturns([libraryItem({ status: 'READ' })]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Derniers lus')).toBeInTheDocument();
    expect(screen.queryByText('Reprendre la lecture')).not.toBeInTheDocument();
  });

  test('announces upcoming releases as indicative', async () => {
    server.use(
      http.get('*/api/catalog/upcoming', () =>
        HttpResponse.json([catalogResult({ title: 'Berserk 43', releaseDate: '2026-09-01' })])),
    );
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Berserk 43')).toBeInTheDocument();
    expect(screen.getByText(/Dates indicatives/)).toBeInTheDocument();
  });

  test('points to Discover when the library is empty', async () => {
    libraryReturns([]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Ta bibliothèque est vide/)).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Connecte-toi pour retrouver ta bibliothèque/)).toBeInTheDocument();
  });
});
