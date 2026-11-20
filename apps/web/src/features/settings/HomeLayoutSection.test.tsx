import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../../test/utils';
import { dashboardLayout, stats } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { HomeLayoutSection } = await import('./HomeLayoutSection');
const { HomePage } = await import('../home/HomePage');

const YEAR = new Date().getFullYear();

const HIDDEN_GOAL = dashboardLayout({
  sections: [
    { code: 'resumeReading', hidden: false },
    { code: 'counters', hidden: false },
    { code: 'goal', hidden: true },
    { code: 'upcoming', hidden: false },
    { code: 'recentlyRead', hidden: false },
  ],
});

const openEditor = async () =>
  userEvent.click(await screen.findByRole('button', { name: "Personnaliser l'accueil" }));

/**
 * Organising Home (#54), from Settings since #554. These are the editor's tests from Home's,
 * moved with it.
 */
describe('HomeLayoutSection', () => {
  beforeEach(resetAuth);

  /**
   * #54's requirement 2: hiding a section is not deleting it. The panel goes on listing a
   * hidden one, or there would be no way back to it short of guessing it used to exist.
   */
  test('a hidden section stays listed, and marked, in the customize panel', async () => {
    server.use(http.get('*/api/dashboard/layout', () => HttpResponse.json(HIDDEN_GOAL)));
    render(<TestProviders><HomeLayoutSection /></TestProviders>);

    await openEditor();

    expect(await screen.findByText('Objectif de lecture annuel')).toBeInTheDocument();
    expect(screen.getByText('Masquée')).toBeInTheDocument();
  });

  /** Home and Settings share the layout's cache entry: saving here redraws the dashboard. */
  test('unhiding a section and saving brings it back on the dashboard', async () => {
    const saved: unknown[] = [];
    server.use(
      http.get('*/api/dashboard/layout', () => HttpResponse.json(HIDDEN_GOAL)),
      http.put('*/api/dashboard/layout', async ({ request }) => {
        const body = await request.json();
        saved.push(body);
        return HttpResponse.json(body);
      }),
      http.get('*/api/stats', () => HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 12, goalUnit: 'BOOKS' }))),
    );
    render(
      <TestProviders>
        <HomeLayoutSection />
        <HomePage />
      </TestProviders>,
    );
    await screen.findByText('lus');
    await waitFor(() => expect(screen.queryByText(`Objectif ${YEAR} · Romans`)).not.toBeInTheDocument());

    await openEditor();
    await userEvent.click(await screen.findByRole('button', { name: 'Afficher « Objectif de lecture annuel »' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({ sections: expect.arrayContaining([{ code: 'goal', hidden: false }]) });
    expect(await screen.findByText(`Objectif ${YEAR} · Romans`)).toBeInTheDocument();
  });

  test('moving a section up changes the order it is saved in', async () => {
    const saved: { sections?: { code: string }[] }[] = [];
    server.use(
      http.get('*/api/dashboard/layout', () => HttpResponse.json(dashboardLayout())),
      http.put('*/api/dashboard/layout', async ({ request }) => {
        const body = (await request.json()) as { sections?: { code: string }[] };
        saved.push(body);
        return HttpResponse.json(body);
      }),
    );
    render(<TestProviders><HomeLayoutSection /></TestProviders>);

    await openEditor();
    // "Compteurs de lecture" is the third row by default; moving it up swaps it with the pile.
    await userEvent.click(await screen.findByRole('button', { name: 'Déplacer « Compteurs de lecture » vers le haut' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].sections?.map((s) => s.code)).toEqual([
      'resumeReading', 'counters', 'toRead', 'bookStack', 'goal', 'upcoming', 'recentlyRead',
    ]);
  });

  /** #416: the editor is a modal dialog, closed by Escape or its button. */
  test('opens the panel as a modal, closed by Escape or the close button', async () => {
    render(<TestProviders><HomeLayoutSection /></TestProviders>);

    await openEditor();
    const dialog = await screen.findByRole('dialog', { name: "Personnaliser l'accueil" });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await openEditor();
    await userEvent.click(await screen.findByRole('button', { name: 'Fermer' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  test('cancelling the panel discards the changes', async () => {
    let putCalled = false;
    server.use(http.put('*/api/dashboard/layout', () => {
      putCalled = true;
      return HttpResponse.json(dashboardLayout());
    }));
    render(<TestProviders><HomeLayoutSection /></TestProviders>);

    await openEditor();
    await screen.findByText(/Réordonne les sections/);
    await userEvent.click(screen.getByRole('button', { name: 'Annuler' }));

    expect(screen.queryByText(/Réordonne les sections/)).not.toBeInTheDocument();
    expect(putCalled).toBe(false);
  });
});
