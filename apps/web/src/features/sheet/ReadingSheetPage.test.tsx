import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { http, HttpResponse, libraryItemReturns, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';
import { SHEET_THEME_STORAGE_KEY, readingDays } from './readingSheet';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { BlankSheetPage, ReadingSheetPage, ReadingSheetsPage } = await import('./ReadingSheetPage');

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
  beforeEach(() => {
    resetAuth();
    localStorage.removeItem(SHEET_THEME_STORAGE_KEY);
  });

  /** #548: the sheet is drawn at its paper's exact size, which fills the printed page. */
  test.each([
    ['A4', '210mm', '297mm'],
    ['A5', '148mm', '210mm'],
    ['A6', '105mm', '148mm'],
  ] as const)('draws the %s sheet at %s × %s', async (format, width, height) => {
    libraryItemReturns(FINISHED);
    renderSheet();

    const sheet = await screen.findByRole('article', { name: 'Fiche de lecture' });
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`^${format} ·`) }));
    expect(sheet.style.getPropertyValue('--sheet-width')).toBe(width);
    expect(sheet.style.getPropertyValue('--sheet-height')).toBe(height);
  });

  /** #549: the sheet is a keepsake, not a receipt — no export date on it. */
  test('prints no export date', async () => {
    libraryItemReturns(FINISHED);
    renderSheet();

    const sheet = await screen.findByRole('article', { name: 'Fiche de lecture' });
    expect(sheet).not.toHaveTextContent(/fiche du/i);
    const today = new Intl.DateTimeFormat('fr', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
    expect(sheet).not.toHaveTextContent(today);
  });

  /** #549, #550: handwritten by default, the theme picked in Settings otherwise. */
  test('is handwritten by default, and follows the theme picked in Settings', async () => {
    libraryItemReturns(FINISHED);
    const first = renderSheet();
    expect(await screen.findByRole('article', { name: 'Fiche de lecture' })).toHaveAttribute('data-theme', 'handwritten');
    first.unmount();

    localStorage.setItem(SHEET_THEME_STORAGE_KEY, 'notebook');
    renderSheet();
    expect(await screen.findByRole('article', { name: 'Fiche de lecture' })).toHaveAttribute('data-theme', 'notebook');
  });

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

  /** #432: an A5 half page, and a wider inner margin for a ring binder. */
  test('offers A5, and moves the text clear of the binder holes', async () => {
    libraryItemReturns(FINISHED);
    const { container } = renderSheet();

    const sheet = await screen.findByRole('article', { name: 'Fiche de lecture' });
    await userEvent.click(screen.getByRole('button', { name: 'A5 · demi-page' }));
    expect(sheet).toHaveAttribute('data-format', 'A5');
    expect(container.querySelector('style')?.textContent).toContain('size: 148mm 210mm');
    expect(sheet.style.getPropertyValue('--print-margin')).toBe('10mm 10mm 10mm 10mm');

    await userEvent.click(screen.getByLabelText('Classeur à anneaux (marge pour les trous)'));
    expect(sheet).toHaveAttribute('data-rings', 'true');
    expect(sheet.style.getPropertyValue('--print-margin')).toBe('10mm 10mm 10mm 20mm');
  });

  /** #521: a page margin is where the browser prints its date, title, URL and page number. */
  test.each(['A4', 'A5', 'A6'] as const)('leaves the browser no margin to print its own text in (%s)', async (format) => {
    libraryItemReturns(FINISHED);
    const { container } = renderSheet();

    const sheet = await screen.findByRole('article', { name: 'Fiche de lecture' });
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`^${format} ·`) }));
    await userEvent.click(screen.getByLabelText('Classeur à anneaux (marge pour les trous)'));
    expect(sheet).toHaveAttribute('data-format', format);
    expect(container.querySelector('style')?.textContent).toMatch(/@page \{ size: [^;]+; margin: 0; \}/);
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

describe('BlankSheetPage (#550)', () => {
  test('leaves every field to fill in, and frames an empty cover only when the cover is ticked', async () => {
    renderWithProviders(<BlankSheetPage />);

    const sheet = screen.getByRole('article', { name: 'Fiche de lecture' });
    expect(screen.getByText('Titre')).toBeInTheDocument();
    expect(screen.getByText('Auteur')).toBeInTheDocument();
    expect(screen.getByText('Commencé le')).toBeInTheDocument();
    expect(screen.getByLabelText('Lignes à compléter à la main')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Emplacement de la couverture' })).toBeInTheDocument();
    expect(sheet).toHaveAttribute('data-format', 'A4');

    await userEvent.click(screen.getByLabelText('Inclure la couverture'));
    expect(screen.queryByRole('img', { name: 'Emplacement de la couverture' })).not.toBeInTheDocument();
  });

  test("switches the paper size like a book's sheet", async () => {
    const { container } = renderWithProviders(<BlankSheetPage />);

    await userEvent.click(screen.getByRole('button', { name: 'A6 · carte' }));
    expect(screen.getByRole('article', { name: 'Fiche de lecture' })).toHaveAttribute('data-format', 'A6');
    expect(container.querySelector('style')?.textContent).toContain('size: 105mm 148mm');
  });
});

describe('ReadingSheetsPage (#551)', () => {
  beforeEach(resetAuth);

  test('prints one sheet per selected title, a selected series as its volumes held', async () => {
    const titles: Record<string, string> = { a: 'Premier', b: 'Second', v1: 'Tome un', v2: 'Tome deux' };
    server.use(
      http.get('*/api/library/:id', ({ params }) =>
        HttpResponse.json(libraryItem({ id: String(params.id), book: { kind: 'BOOK', title: titles[String(params.id)] } }))),
      http.get('*/api/series/:id', () =>
        HttpResponse.json({
          id: 's1',
          title: 'Une série',
          volumes: [
            { volumeNumber: 1, libraryItemId: 'v1', owned: true },
            { volumeNumber: 2, libraryItemId: 'v2', owned: true },
            { volumeNumber: 3, owned: false },
          ],
        })),
    );
    renderWithProviders(<ReadingSheetsPage />, { route: '/sheets?items=a,b&series=s1', path: '/sheets' });

    expect(await screen.findByRole('heading', { name: 'Premier' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Tome deux' })).toBeInTheDocument();
    expect(screen.getAllByRole('article', { name: 'Fiche de lecture' })).toHaveLength(4);
  });

  test('says so when nothing was selected', async () => {
    renderWithProviders(<ReadingSheetsPage />, { route: '/sheets', path: '/sheets' });
    expect(await screen.findByText('Aucun titre à imprimer.')).toBeInTheDocument();
  });
});

describe('readingDays', () => {
  test('counts both ends, and needs both', () => {
    expect(readingDays('2026-01-01', '2026-01-10')).toBe(10);
    expect(readingDays('2026-01-01', null)).toBeNull();
    expect(readingDays('2026-02-01', '2026-01-01')).toBeNull();
  });
});
