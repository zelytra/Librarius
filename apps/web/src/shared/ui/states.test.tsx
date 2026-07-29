import type { ReactElement } from 'react';
import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../../test/utils';
import { LOADING_DELAY_MS, LOADING_MIN_VISIBLE_MS, Loading } from './states';

/**
 * The indicator is driven entirely by the clock, so every test here runs on fake timers.
 * The providers are only there for i18n: the component reads no query and no route.
 */
function renderLoading(ui: ReactElement) {
  return render(<TestProviders>{ui}</TestProviders>);
}

describe('Loading', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  test('shows nothing while the wait is still short enough to go unnoticed', () => {
    renderLoading(<Loading />);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(LOADING_DELAY_MS - 1);
    });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  test('appears with its wording once the wait has lasted', () => {
    renderLoading(<Loading />);

    act(() => {
      vi.advanceTimersByTime(LOADING_DELAY_MS);
    });

    expect(screen.getByRole('status')).toHaveClass(/loadingLarge/);
    expect(screen.getByText('Chargement…')).toBeInTheDocument();
  });

  /**
   * The point of the delay: a call that answers in a hundred milliseconds must leave no
   * trace at all on screen, not even a frame of one.
   */
  test('never renders anything for a wait that ends before the threshold', () => {
    const { rerender } = renderLoading(<Loading size="compact" pending />);

    act(() => {
      vi.advanceTimersByTime(LOADING_DELAY_MS - 100);
    });
    rerender(
      <TestProviders>
        <Loading size="compact" pending={false} />
      </TestProviders>,
    );
    act(() => {
      vi.advanceTimersByTime(10_000);
    });

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  /** And the point of the floor: an answer landing just past the delay is the same flicker. */
  test('stays on screen once it has appeared, however fast the answer then lands', () => {
    const { rerender } = renderLoading(<Loading size="compact" pending />);
    act(() => {
      vi.advanceTimersByTime(LOADING_DELAY_MS);
    });
    expect(screen.getByRole('status')).toBeInTheDocument();

    rerender(
      <TestProviders>
        <Loading size="compact" pending={false} />
      </TestProviders>,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(LOADING_MIN_VISIBLE_MS);
    });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  /** One component, two formats: the compact one has no room to print its label. */
  test('hides the wording of the compact format without dropping it', () => {
    renderLoading(<Loading size="compact" label="Import en cours" />);

    act(() => {
      vi.advanceTimersByTime(LOADING_DELAY_MS);
    });

    expect(screen.getByRole('status')).toHaveClass(/loadingCompact/);
    expect(screen.getByText('Import en cours')).toHaveClass(/srOnly/);
  });
});
