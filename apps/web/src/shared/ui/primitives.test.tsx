import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { Grid, Screen } from './primitives';

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
