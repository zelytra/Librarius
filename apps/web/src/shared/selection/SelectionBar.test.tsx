import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router';
import { describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../../test/utils';
import { SERIES_KEY, type Selection } from './useSelection';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { SelectionBar } = await import('./SelectionBar');

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
