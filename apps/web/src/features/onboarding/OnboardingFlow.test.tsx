import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, test, vi } from 'vitest';
import '../../i18n';
import { OnboardingFlow } from './OnboardingFlow';

/** The sheet, once it is open. Mirrors the same helper `OutcomeSheet.test.tsx` uses. */
function sheet() {
  return within(screen.getByRole('dialog'));
}

function renderFlow(onDismiss = vi.fn()) {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route
          path="/"
          element={<OnboardingFlow onDismiss={onDismiss} />}
        />
        <Route path="/settings" element={<p>écran des réglages</p>} />
        <Route path="/discover" element={<p>écran de découverte</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return onDismiss;
}

describe('OnboardingFlow', () => {
  test('opens on the import step, the strongest pitch for someone with an existing library', () => {
    renderFlow();

    expect(sheet().getByText('Tu as déjà une bibliothèque ?')).toBeInTheDocument();
  });

  test('moves through the three steps in order', async () => {
    renderFlow();

    expect(sheet().getByText('Tu as déjà une bibliothèque ?')).toBeInTheDocument();

    await userEvent.click(sheet().getByText('Suivant'));
    expect(sheet().getByText('Trouve tes premiers titres')).toBeInTheDocument();

    await userEvent.click(sheet().getByText('Suivant'));
    expect(sheet().getByText('Fixe-toi un objectif (facultatif)')).toBeInTheDocument();
    // The last step has no more "Suivant" — "Terminer" replaces it.
    expect(screen.queryByText('Suivant')).not.toBeInTheDocument();
  });

  test('the primary action on a step leaves the tour and opens the screen it names', async () => {
    const onDismiss = renderFlow();

    await userEvent.click(sheet().getByText('Importer ma bibliothèque'));

    expect(await screen.findByText('écran des réglages')).toBeInTheDocument();
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  test('the discover step leads to Discover', async () => {
    const onDismiss = renderFlow();

    await userEvent.click(sheet().getByText('Suivant'));
    await userEvent.click(sheet().getByText('Découvrir des titres'));

    expect(await screen.findByText('écran de découverte')).toBeInTheDocument();
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  test('can be skipped from any step', async () => {
    const onDismiss = renderFlow();

    await userEvent.click(sheet().getByText('Passer la visite guidée'));

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  test('closes on Escape', async () => {
    const onDismiss = renderFlow();

    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(onDismiss).toHaveBeenCalledTimes(1));
  });

  test('closes on the close button without navigating anywhere', async () => {
    const onDismiss = renderFlow();

    await userEvent.click(sheet().getByLabelText('Fermer'));

    expect(onDismiss).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('écran des réglages')).not.toBeInTheDocument();
  });

  test('finishing the last step dismisses the tour', async () => {
    const onDismiss = renderFlow();

    await userEvent.click(sheet().getByText('Suivant'));
    await userEvent.click(sheet().getByText('Suivant'));
    await userEvent.click(sheet().getByText('Terminer'));

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
