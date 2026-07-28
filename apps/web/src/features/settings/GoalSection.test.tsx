import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { GoalSection } = await import('./GoalSection');

const YEAR = new Date().getFullYear();

describe('GoalSection', () => {
  beforeEach(resetAuth);

  test('sets a goal for the running year', async () => {
    let sent: { year?: string; body?: unknown } = {};
    server.use(
      http.get('*/api/goals', () => HttpResponse.json([])),
      http.put('*/api/goals/:year', async ({ params, request }) => {
        sent = { year: String(params.year), body: await request.json() };
        return HttpResponse.json({ id: 'g1', year: YEAR, targetCount: 30, unit: 'BOOKS' });
      }),
    );
    renderWithProviders(<GoalSection />);

    await userEvent.type(await screen.findByLabelText('Objectif'), '30');
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(sent.year).toBe(String(YEAR)));
    expect(sent.body).toEqual({ targetCount: 30, unit: 'BOOKS' });
    expect(await screen.findByText('Objectif enregistré.')).toBeInTheDocument();
  });

  test('sends the unit the user picked', async () => {
    let sent: unknown = null;
    server.use(
      http.get('*/api/goals', () => HttpResponse.json([])),
      http.put('*/api/goals/:year', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 'g1', year: YEAR, targetCount: 12000, unit: 'PAGES' });
      }),
    );
    renderWithProviders(<GoalSection />);

    await userEvent.type(await screen.findByLabelText('Objectif'), '12000');
    await userEvent.click(screen.getByText('Pages'));
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(sent).toEqual({ targetCount: 12000, unit: 'PAGES' }));
  });

  test('starts from the goal already set for the year', async () => {
    server.use(http.get('*/api/goals', () =>
      HttpResponse.json([{ id: 'g1', year: YEAR, targetCount: 24, unit: 'VOLUMES' }])));
    renderWithProviders(<GoalSection />);

    expect(await screen.findByLabelText('Objectif')).toHaveValue('24');
    // Nothing to carry over: the year already has a goal of its own.
    expect(screen.queryByText(/Reprendre ton objectif/)).not.toBeInTheDocument();
  });

  /** The first of January: last year's goal is offered rather than retyped. */
  test('offers to carry the previous year over when the new one has no goal', async () => {
    let sent: unknown = null;
    server.use(
      http.get('*/api/goals', () =>
        HttpResponse.json([{ id: 'g1', year: YEAR - 1, targetCount: 30, unit: 'BOOKS' }])),
      http.put('*/api/goals/:year', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 'g2', year: YEAR, targetCount: 30, unit: 'BOOKS' });
      }),
    );
    renderWithProviders(<GoalSection />);

    await userEvent.click(
      await screen.findByText(`Reprendre ton objectif ${YEAR - 1} : 30 livres`));

    // Carrying over fills the form; the user still confirms it.
    expect(screen.getByLabelText('Objectif')).toHaveValue('30');
    expect(sent).toBeNull();

    await userEvent.click(screen.getByText('Enregistrer'));
    await waitFor(() => expect(sent).toEqual({ targetCount: 30, unit: 'BOOKS' }));
  });

  test('only ever offers the most recent past year', async () => {
    server.use(http.get('*/api/goals', () =>
      HttpResponse.json([
        { id: 'g1', year: YEAR - 3, targetCount: 10, unit: 'BOOKS' },
        { id: 'g2', year: YEAR - 1, targetCount: 30, unit: 'BOOKS' },
      ])));
    renderWithProviders(<GoalSection />);

    expect(await screen.findByText(`Reprendre ton objectif ${YEAR - 1} : 30 livres`))
      .toBeInTheDocument();
    expect(screen.queryByText(new RegExp(`${YEAR - 3}`))).not.toBeInTheDocument();
  });

  test('refuses a target that is not a whole number of at least one', async () => {
    server.use(http.get('*/api/goals', () => HttpResponse.json([])));
    renderWithProviders(<GoalSection />);

    const target = await screen.findByLabelText('Objectif');
    await userEvent.type(target, '0');
    expect(screen.getByText('Enregistrer')).toBeDisabled();

    await userEvent.clear(target);
    await userEvent.type(target, 'beaucoup');
    expect(screen.getByText('Enregistrer')).toBeDisabled();

    await userEvent.clear(target);
    await userEvent.type(target, '20');
    expect(screen.getByText('Enregistrer')).toBeEnabled();
  });

  test('says so when saving fails', async () => {
    server.use(
      http.get('*/api/goals', () => HttpResponse.json([])),
      http.put('*/api/goals/:year', () => new HttpResponse(null, { status: 500 })),
    );
    renderWithProviders(<GoalSection />);

    await userEvent.type(await screen.findByLabelText('Objectif'), '30');
    await userEvent.click(screen.getByText('Enregistrer'));

    expect(await screen.findByText(/Enregistrement impossible/)).toBeInTheDocument();
  });

  /** Nothing to set without a session, and nothing to fetch either. */
  test('stays out of the way when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<GoalSection />);

    await waitFor(() =>
      expect(screen.queryByText(/Objectif de lecture/)).not.toBeInTheDocument());
  });
});
