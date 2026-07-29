import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { BUILTIN_CATEGORIES, libraryItem } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { DetailPage } = await import('./DetailPage');

/**
 * The end-of-reading sheet (#164 and #165), driven through the screen that opens it: the
 * two buttons, the transition they record and the writes the sheet sends afterwards are
 * one behaviour, and testing the component on its own would assert none of it.
 */

/** A title being read, three hundred pages long, stopped at page 30. */
const READING = libraryItem({
  id: 'item-1',
  status: 'READING',
  book: { kind: 'BOOK', title: 'Le Nom du vent', authors: 'Patrick Rothfuss', pageCount: 300 },
  progress: { currentPage: 30, percent: 10 },
});

/** A shelf of the user's own, which the screen used to have no way of assigning. */
const CUSTOM_CATEGORY = {
  id: 'cat-coup-de-coeur',
  code: 'coup-de-coeur',
  label: 'Coup de cœur',
  color: '#c0577a',
  builtin: false,
};

interface Recorder {
  progress: Record<string, unknown>[];
  review: Record<string, unknown>[];
  rank: Record<string, unknown>[];
  current: () => ReturnType<typeof libraryItem>;
}

/**
 * Serves the one title from a mutable copy and records every write, so a test can assert
 * both what was sent and what the screen shows once it has re-read the item.
 */
function servesTitle(initial = READING): Recorder {
  let item = { ...initial };
  const recorder: Recorder = { progress: [], review: [], rank: [], current: () => item };

  server.use(
    http.get('*/api/library/:id', ({ params }) =>
      params.id === item.id ? HttpResponse.json(item) : new HttpResponse(null, { status: 404 })),
    http.get('*/api/categories', () => HttpResponse.json([...BUILTIN_CATEGORIES, CUSTOM_CATEGORY])),
    http.put('*/api/library/:id/progress', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      recorder.progress.push(body);
      item = { ...item, status: body.status as string };
      return new HttpResponse(null, { status: 204 });
    }),
    http.put('*/api/library/:id/review', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      recorder.review.push(body);
      item = { ...item, rating: body.rating as number, review: body.review as string };
      return HttpResponse.json(item);
    }),
    http.put('*/api/library/:id/rank', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      recorder.rank.push(body);
      const category = [...BUILTIN_CATEGORIES, CUSTOM_CATEGORY]
        .find((c) => c.id === body.categoryId);
      item = { ...item, rankCode: category?.code };
      return HttpResponse.json(item);
    }),
  );

  return recorder;
}

function renderDetail() {
  return renderWithProviders(<DetailPage />, { route: '/detail/item-1', path: '/detail/:id' });
}

/** The sheet, once it is open. Everything inside is scoped to it: the screen behind still
    carries its own stars and its own row of shelves. */
function sheet() {
  return within(screen.getByRole('dialog'));
}

/** Opens the sheet the way a user does, and waits for it. */
async function endReading(action: string) {
  await userEvent.click(await screen.findByText(action));
  return await screen.findByRole('dialog');
}

const FINISH = 'Marquer comme lu';
const GIVE_UP = "J'abandonne ce livre";

describe('end-of-reading sheet', () => {
  beforeEach(resetAuth);

  // ── Finishing a title (#164) ───────────────────────────────────────────────

  /**
   * The status is recorded before the sheet opens, not on confirmation: that is what makes
   * both questions below optional rather than a two-step form.
   */
  test('records the finish, then asks for a rating and a shelf', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);

    expect(calls.progress).toHaveLength(1);
    expect(calls.progress[0]).toMatchObject({ status: 'READ' });
    expect(sheet().getByText('Terminé !')).toBeInTheDocument();
  });

  test('saves the rating given on the sheet', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);
    await userEvent.click(sheet().getByLabelText('Noter 4 sur 5'));
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(calls.review[0]).toMatchObject({ rating: 4 }));
    expect(calls.current().rating).toBe(4);
  });

  test('files the title under the shelf chosen on the sheet', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);
    await userEvent.click(sheet().getByText('Or'));
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(calls.rank[0]).toEqual({ categoryId: 'cat-or' }));
    expect(calls.current().rankCode).toBe('or');
  });

  /** The whole point of "optional": the title is read, and nothing else was touched. */
  test('leaves the title read and untouched when the sheet is dismissed', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);
    await userEvent.click(sheet().getByText('Plus tard'));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(calls.review).toEqual([]);
    expect(calls.rank).toEqual([]);
    expect(calls.current().status).toBe('READ');
  });

  /** Confirming a sheet nobody touched is not two requests writing what is already there. */
  test('sends nothing when a sheet nobody touched is confirmed', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(calls.review).toEqual([]);
    expect(calls.rank).toEqual([]);
  });

  /** Escape closes any dialog, and this one has nothing to lose by being closed. */
  test('closes on Escape without writing anything', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);
    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(calls.review).toEqual([]);
    expect(calls.rank).toEqual([]);
  });

  /** The guided moment belongs to the transition, not to every later tap on the button. */
  test('asks nothing when a title already read is marked read again', async () => {
    servesTitle({ ...READING, status: 'READ' });
    renderDetail();

    await userEvent.click(await screen.findByText('✓ Lu'));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  // ── Giving up on a title (#165) ────────────────────────────────────────────

  test('opens on the Abandon shelf when a title is given up on', async () => {
    servesTitle();
    renderDetail();

    await endReading(GIVE_UP);

    expect(sheet().getByText('Abandonné')).toBeInTheDocument();
    expect(sheet().getByText('Abandon').closest('button')).toHaveAttribute('aria-pressed', 'true');
  });

  test('files an abandoned title under Abandon when the sheet is confirmed as it opened', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(GIVE_UP);
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(calls.rank[0]).toEqual({ categoryId: 'cat-abandon' }));
    expect(calls.current().rankCode).toBe('abandon');
  });

  /** The pre-selection is a starting point, not a constraint. */
  test('files it elsewhere when the pre-selected shelf is overridden', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(GIVE_UP);
    await userEvent.click(sheet().getByText('Bronze'));
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(calls.rank[0]).toEqual({ categoryId: 'cat-bronze' }));
    expect(calls.current().rankCode).toBe('bronze');
  });

  test('leaves an abandoned title unshelved when the sheet is skipped', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(GIVE_UP);
    await userEvent.click(sheet().getByText('Plus tard'));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(calls.rank).toEqual([]);
    expect(calls.current().status).toBe('ABANDONED');
    expect(calls.current().rankCode).toBeUndefined();
  });

  /**
   * The one thing an abandonment exists to record is the page the reader stopped on. The
   * sheet writes a rating and a shelf and nothing else: a second `PUT /progress` from here
   * would be exactly how page 30 becomes 100 %.
   */
  test('never writes a reading position from the sheet', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(GIVE_UP);
    await userEvent.click(sheet().getByLabelText('Noter 2 sur 5'));
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(calls.review).toHaveLength(1));
    // The transition itself, and nothing after it.
    expect(calls.progress).toHaveLength(1);
    expect(calls.progress[0]).toMatchObject({ status: 'ABANDONED', currentPage: 30, percent: 10 });
  });

  // ── Every shelf, not only the three metals ─────────────────────────────────

  /**
   * A shelving screen that cannot reach the user's own shelves is pointless — and until
   * this row was opened up, neither could the `abandon` shelf #163 seeded for it.
   */
  test('offers the categories the user created as well as the built-in ones', async () => {
    servesTitle();
    renderDetail();

    await endReading(FINISH);

    for (const label of ['Or', 'Argent', 'Bronze', 'Abandon', 'Coup de cœur']) {
      expect(sheet().getByText(label)).toBeInTheDocument();
    }
  });

  test('files a title under a category of the user own making', async () => {
    const calls = servesTitle();
    renderDetail();

    await endReading(FINISH);
    await userEvent.click(sheet().getByText('Coup de cœur'));
    await userEvent.click(sheet().getByText('Enregistrer'));

    await waitFor(() => expect(calls.rank[0]).toEqual({ categoryId: CUSTOM_CATEGORY.id }));
  });

  /** A title already filed somewhere opens on that shelf rather than on nothing. */
  test('opens on the shelf the title already carries', async () => {
    servesTitle({ ...READING, rankCode: 'argent' });
    renderDetail();

    await endReading(FINISH);

    expect(sheet().getByText('Argent').closest('button')).toHaveAttribute('aria-pressed', 'true');
  });
});
