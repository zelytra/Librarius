import { render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';
import { recentLog, resetDiagnostics } from '../feedback/diagnostics';

afterEach(() => resetDiagnostics());

function Broken(): never {
  throw new Error('rendu cassé');
}

/** #490: a screen that breaks shows the way back, and the error goes to the report log. */
test('shows the fallback and logs the error', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  render(
    <ErrorBoundary fallback={<p>Oups</p>}>
      <Broken />
    </ErrorBoundary>,
  );

  expect(screen.getByText('Oups')).toBeInTheDocument();
  expect(recentLog()).toContain('Error: rendu cassé');
});
