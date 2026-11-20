import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { HIGHLIGHT_CLASS, useHighlightHash } from './useHighlightHash';

function Screen() {
  useHighlightHash();
  return (
    <>
      <section id="other">Autre</section>
      <section id="goal">Objectif</section>
    </>
  );
}

function renderAt(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Screen />
    </MemoryRouter>,
  );
}

describe('useHighlightHash (#560)', () => {
  afterEach(() => vi.useRealTimers());

  test('scrolls to the section the link named, and highlights it for a moment', () => {
    vi.useFakeTimers();
    const scrolled = vi.fn();
    Element.prototype.scrollIntoView = scrolled;
    renderAt('/settings/reading#goal');

    const target = screen.getByText('Objectif');
    expect(scrolled).toHaveBeenCalledTimes(1);
    expect(scrolled.mock.contexts[0]).toBe(target);
    expect(target).toHaveClass(HIGHLIGHT_CLASS);
    expect(screen.getByText('Autre')).not.toHaveClass(HIGHLIGHT_CLASS);

    vi.advanceTimersByTime(3000);
    expect(target).not.toHaveClass(HIGHLIGHT_CLASS);
  });

  test('does nothing without a hash, or for one naming nothing', () => {
    const scrolled = vi.fn();
    Element.prototype.scrollIntoView = scrolled;
    renderAt('/settings/reading');
    renderAt('/settings/reading#nowhere');

    expect(scrolled).not.toHaveBeenCalled();
  });
});
