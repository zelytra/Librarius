import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { goal } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { GoalSection } = await import('./GoalSection');

/** A goal belongs to a calendar year, so the fixtures follow the clock. */
const YEAR = new Date().getFullYear();

/** Records what reaches `PUT /api/goals/{year}`, which is what the form is for. */
function captureSaves() {
  const saved: { year: string; body: unknown }[] = [];
  server.use(
    http.put('*/api/goals/:year', async ({ params, request }) => {
      saved.push({ year: String(params.year), body: await request.json() });
      return HttpResponse.json({ id: 'goal-1' });
    }),
  );
  return saved;
}

function goalsReturn(goals: ReturnType<typeof goal>[]) {
  server.use(http.get('*/api/goals', () => HttpResponse.json(goals)));
}

describe('GoalSection', () => {
  beforeEach(resetAuth);

  test('saves a target for the year in progress', async () => {
    const saved = captureSaves();
    renderWithProviders(<GoalSection />);

    await userEvent.type(await screen.findByLabelText('Nombre à atteindre'), '30');
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toEqual({ year: String(YEAR), body: { targetCount: 30, unit: 'BOOKS' } });
    expect(await screen.findByText('Objectif enregistré : 30 livres.')).toBeInTheDocument();
  });

  test('sends the unit the user picked', async () => {
    const saved = captureSaves();
    renderWithProviders(<GoalSection />);

    await userEvent.click(await screen.findByText('pages'));
    await userEvent.type(screen.getByLabelText('Nombre à atteindre'), '12000');
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].body).toEqual({ targetCount: 12000, unit: 'PAGES' });
  });

  /** Opening the form after the fact shows what was chosen, not an empty field. */
  test('starts from the goal already set for the year', async () => {
    goalsReturn([goal({ year: YEAR, targetCount: 24, unit: 'VOLUMES' })]);
    renderWithProviders(<GoalSection />);

    await waitFor(() =>
      expect(screen.getByLabelText('Nombre à atteindre')).toHaveValue(24));
    expect(screen.getByText('tomes')).toHaveClass(/segmentOn/);
  });

  /** A goal from a year already over is history: it must not fill this year's form. */
  test('leaves the form empty when only a past year has a goal', async () => {
    goalsReturn([goal({ year: YEAR - 1, targetCount: 24, unit: 'BOOKS' })]);
    renderWithProviders(<GoalSection />);

    expect(await screen.findByLabelText('Nombre à atteindre')).toHaveValue(null);
  });

  test('refuses a target that is not a positive whole number', async () => {
    const saved = captureSaves();
    renderWithProviders(<GoalSection />);

    const submit = await screen.findByText('Enregistrer');
    expect(submit).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Nombre à atteindre'), '0');
    expect(submit).toBeDisabled();

    await userEvent.click(submit);
    expect(saved).toHaveLength(0);
  });

  test('says so when saving fails rather than pretending it worked', async () => {
    server.use(http.put('*/api/goals/:year', () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<GoalSection />);

    await userEvent.type(await screen.findByLabelText('Nombre à atteindre'), '30');
    await userEvent.click(screen.getByText('Enregistrer'));

    expect(await screen.findByText(/Impossible d'enregistrer l'objectif/)).toBeInTheDocument();
    expect(screen.queryByText(/Objectif enregistré/)).not.toBeInTheDocument();
  });

  test('asks for a session instead of a target when there is none', async () => {
    setAuthenticated(false);
    renderWithProviders(<GoalSection />);

    expect(await screen.findByText(/Connecte-toi pour définir un objectif/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Nombre à atteindre')).not.toBeInTheDocument();
  });
});
