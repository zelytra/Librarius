import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router';
import { act } from 'react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../../test/utils';
import { SERIES_KEY, type Selection } from './useSelection';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { SelectButton, SelectionBar } = await import('./SelectionBar');

function selection(ids: string[]): Selection {
  return { active: true, selected: new Set(ids), start: vi.fn(), stop: vi.fn(), toggle: vi.fn() };
}

function Where() {
  const { pathname, search } = useLocation();
  return <p>{`${pathname}${search}`}</p>;
}

describe('SelectionBar — reading sheets (#551)', () => {
  test('opens the sheets of every selected title and series, one per page', async () => {
    render(
      <TestProviders route="/collection">
        <Routes>
          <Route path="/collection" element={<SelectionBar selection={selection(['a', `${SERIES_KEY}s1`, 'b'])} />} />
          <Route path="/sheets" element={<Where />} />
        </Routes>
      </TestProviders>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Imprimer les fiches de lecture' }));

    expect(screen.getByText('/sheets?items=a%2Cb&series=s1')).toBeInTheDocument();
  });

  test('cannot print an empty selection', () => {
    render(
      <TestProviders>
        <SelectionBar selection={selection([])} />
      </TestProviders>,
    );

    expect(screen.getByRole('button', { name: 'Imprimer les fiches de lecture' })).toBeDisabled();
  });
});

/**
 * #557: once the screen's Select button has scrolled out of sight, the same action floats at
 * the bottom of the screen, the way a photo gallery keeps its actions in reach.
 */
describe('SelectButton — out of sight (#557)', () => {
  let report: (visible: boolean) => void = () => {};
  const original = globalThis.IntersectionObserver;

  function observe() {
    globalThis.IntersectionObserver = class {
      constructor(callback: IntersectionObserverCallback) {
        report = (visible) => callback([{ isIntersecting: visible } as IntersectionObserverEntry], this as never);
      }
      observe() {}
      disconnect() {}
      unobserve() {}
      takeRecords() { return []; }
      root = null;
      rootMargin = '';
      thresholds = [];
    } as unknown as typeof IntersectionObserver;
  }

  afterEach(() => {
    globalThis.IntersectionObserver = original;
  });

  test('floats a Select button once the toolbar one has scrolled away, and starts selecting', async () => {
    observe();
    const start = vi.fn();
    render(
      <TestProviders>
        <SelectButton selection={{ ...selection([]), active: false, start }} />
      </TestProviders>,
    );

    expect(screen.getAllByRole('button', { name: 'Sélectionner' })).toHaveLength(1);
    act(() => report(false));
    const floating = screen.getAllByRole('button', { name: 'Sélectionner' });
    expect(floating).toHaveLength(2);

    await userEvent.click(floating[1]);
    expect(start).toHaveBeenCalled();

    act(() => report(true));
    expect(screen.getAllByRole('button', { name: 'Sélectionner' })).toHaveLength(1);
  });

  test('shows nothing while selecting', () => {
    observe();
    render(
      <TestProviders>
        <SelectButton selection={selection(['a'])} />
      </TestProviders>,
    );
    act(() => report(false));
    expect(screen.queryByRole('button', { name: 'Sélectionner' })).not.toBeInTheDocument();
  });
});
