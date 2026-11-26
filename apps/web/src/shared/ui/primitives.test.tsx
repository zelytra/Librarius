import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, test } from 'vitest';
import { Grid, Screen, Segmented } from './primitives';

/**
 * The two layout primitives every desktop issue of the milestone builds on. What is
 * asserted here is the contract the screens rely on — which shape a grid takes, and that
 * a screen can add to it without replacing it. The widths themselves are CSS, measured in
 * a browser rather than in jsdom, which lays nothing out.
 */
describe('Screen', () => {
  test('wraps its content in the padded, width-capped container', () => {
    render(
      <Screen>
        <p>content</p>
      </Screen>,
    );

    expect(screen.getByText('content').parentElement).toHaveClass(/screen/);
  });

  test('keeps the class a screen adds for its own spacing', () => {
    render(
      <Screen className="page">
        <p>content</p>
      </Screen>,
    );

    const container = screen.getByText('content').parentElement;
    expect(container).toHaveClass(/screen/);
    expect(container).toHaveClass('page');
  });
});

describe('Grid', () => {
  test('lays covers out by default, the shape both grid screens need', () => {
    render(
      <Grid>
        <span>tile</span>
      </Grid>,
    );

    const grid = screen.getByText('tile').parentElement;
    expect(grid).toHaveClass(/gridCover/);
    expect(grid).not.toHaveClass(/gridPanel/);
  });

  test('switches to the wider panel column on request', () => {
    render(
      <Grid shape="panel">
        <span>bucket</span>
      </Grid>,
    );

    const grid = screen.getByText('bucket').parentElement;
    expect(grid).toHaveClass(/gridPanel/);
    expect(grid).not.toHaveClass(/gridCover/);
  });

  /** A screen tunes the track size through a class; it never restates the template. */
  test('keeps the class a screen adds on top of the shape', () => {
    render(
      <Grid className="volumes">
        <span>tile</span>
      </Grid>,
    );

    const grid = screen.getByText('tile').parentElement;
    expect(grid).toHaveClass(/gridCover/);
    expect(grid).toHaveClass('volumes');
  });
});

/**
 * #655: sixteen years do not fit a phone's width. The chosen option is scrolled into view —
 * 2011 picked used to stay out of sight behind 2026–2022 — and the pill says when it scrolls.
 */
describe('Segmented with more options than fit', () => {
  const years = Array.from({ length: 16 }, (_, i) => ({ id: String(2026 - i), label: String(2026 - i) }));

  /** jsdom lays nothing out: each option is given the place a phone would give it. */
  function laidOut(pill: HTMLElement) {
    Object.defineProperty(pill, 'scrollWidth', { configurable: true, value: 1440 });
    Object.defineProperty(pill, 'clientWidth', { configurable: true, value: 360 });
    pill.querySelectorAll('button').forEach((button, index) => {
      Object.defineProperty(button, 'offsetLeft', { configurable: true, value: index * 90 });
      Object.defineProperty(button, 'offsetWidth', { configurable: true, value: 90 });
    });
  }

  test('scrolls the pill to the chosen option, the page staying put', async () => {
    function Years() {
      const [value, setValue] = useState('2026');
      return <Segmented options={years} value={value} onChange={setValue} />;
    }
    const { container } = render(<Years />);
    const pill = container.firstElementChild as HTMLElement;
    laidOut(pill);

    await userEvent.click(screen.getByRole('button', { name: '2011' }));

    // 2011 is the 16th option: 15 × 90, centred in 360.
    expect(pill.scrollLeft).toBe(15 * 90 - (360 - 90) / 2);
    window.dispatchEvent(new Event('resize'));
    await waitFor(() => expect(pill).toHaveAttribute('data-scrollable', 'true'));
  });

  /** A screen re-drawing its pill after a choice (the Stats timeline does) still shows the choice. */
  test('shows a chosen option out of the first view as soon as it is drawn', () => {
    const { container, rerender } = render(<Segmented options={years} value="2026" onChange={() => {}} />);
    const pill = container.firstElementChild as HTMLElement;
    laidOut(pill);

    rerender(<Segmented options={years} value="2011" onChange={() => {}} />);

    expect(pill.scrollLeft).toBeGreaterThan(0);
  });
});
