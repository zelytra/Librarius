import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { Toaster } from './Toaster';
import { resetToasts, toast } from './toast';

describe('Toaster (#413)', () => {
  afterEach(() => {
    vi.useRealTimers();
    resetToasts();
  });

  test('announces an outcome, then lets it go', () => {
    vi.useFakeTimers();
    render(<Toaster />);

    act(() => {
      toast.success('Ajouté à ta bibliothèque');
    });
    expect(screen.getByRole('status')).toHaveTextContent('Ajouté à ta bibliothèque');

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.queryByText('Ajouté à ta bibliothèque')).not.toBeInTheDocument();
  });

  test('reads an error out as an alert', () => {
    render(<Toaster />);
    act(() => {
      toast.error('Échec');
    });
    expect(screen.getByRole('alert')).toHaveTextContent('Échec');
  });

  test('keeps at most three on screen', () => {
    render(<Toaster />);
    act(() => {
      ['a', 'b', 'c', 'd'].forEach((m) => toast.info(m));
    });
    expect(screen.queryByText('a')).not.toBeInTheDocument();
    expect(screen.getByText('d')).toBeInTheDocument();
  });
});
