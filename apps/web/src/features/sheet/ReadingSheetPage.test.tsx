import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { libraryItemReturns } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';
import { readingDays } from './readingSheet';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ReadingSheetPage } = await import('./ReadingSheetPage');

const FINISHED = libraryItem({
  id: 'item-1',
  status: 'READ',
  rating: 4,
  review: 'Une prose qui chante.',
  rankCode: 'or',
  book: {
    kind: 'BOOK',
    title: 'Le Nom du vent',
    authors: 'Patrick Rothfuss',
    pageCount: 720,
    publisher: 'Bragelonne',
    coverUrl: 'https://cover.test/vent.jpg',
  },
  progress: { percent: 100, startedAt: '2026-01-01', finishedAt: '2026-01-10' },
});

function renderSheet() {
  return renderWithProviders(<ReadingSheetPage />, { route: '/detail/item-1/fiche', path: '/detail/:id/fiche' });
}

describe('ReadingSheetPage (#412)', () => {
  beforeEach(resetAuth);

  test('lays out the rating, the review and the facts of the book', async () => {
    libraryItemReturns(FINISHED);
    renderSheet();

    expect(await screen.findByRole('heading', { name: 'Le Nom du vent' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Note : 4 sur 5' })).toBeInTheDocument();
    expect(screen.getByText('Une prose qui chante.')).toBeInTheDocument();
    expect(screen.getByText('Bragelonne')).toBeInTheDocument();
    expect(screen.getByText('10 jours')).toBeInTheDocument();
    expect(await screen.findByText('Or')).toBeInTheDocument();
  });

  /** #427: the cover is on by default, and can still be left out to save ink. */
  test('includes the cover by default, and lets it go', async () => {
    libraryItemReturns(FINISHED);
    renderSheet();

    expect(await screen.findByRole('img', { name: 'Couverture de Le Nom du vent' })).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Inclure la couverture'));
    expect(screen.queryByRole('img', { name: 'Couverture de Le Nom du vent' })).not.toBeInTheDocument();
  });

  /** #427: A4 by default, A6 as a card — the summary left out, the printer told the size. */
  test('switches between the A4 page and the A6 card', async () => {
    libraryItemReturns({ ...FINISHED, book: { ...FINISHED.book, synopsis: 'Kvothe raconte sa vie.' } });
    const { container } = renderSheet();

    const sheet = await screen.findByRole('article', { name: 'Fiche de lecture' });
    expect(sheet).toHaveAttribute('data-format', 'A4');
    expect(screen.getByText('Kvothe raconte sa vie.')).toBeInTheDocument();
    expect(container.querySelector('style')?.textContent).toContain('size: 210mm 297mm');

    await userEvent.click(screen.getByRole('button', { name: 'A6 · carte' }));
    expect(sheet).toHaveAttribute('data-format', 'A6');
    expect(screen.queryByText('Kvothe raconte sa vie.')).not.toBeInTheDocument();
    expect(container.querySelector('style')?.textContent).toContain('size: 105mm 148mm');
  });

  test('prints from its toolbar', async () => {
    libraryItemReturns(FINISHED);
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    renderSheet();

    await userEvent.click(await screen.findByRole('button', { name: 'Imprimer' }));
    expect(print).toHaveBeenCalled();
  });

  test('offers lines to write on when there is no review', async () => {
    libraryItemReturns({ ...FINISHED, review: undefined });
    renderSheet();

    expect(await screen.findByLabelText('Lignes à compléter à la main')).toBeInTheDocument();
  });
});

describe('readingDays', () => {
  test('counts both ends, and needs both', () => {
    expect(readingDays('2026-01-01', '2026-01-10')).toBe(10);
    expect(readingDays('2026-01-01', null)).toBeNull();
    expect(readingDays('2026-02-01', '2026-01-01')).toBeNull();
  });
});
