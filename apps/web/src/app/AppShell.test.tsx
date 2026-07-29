import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { AuthProvider } from 'react-oidc-context';
import { QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, test } from 'vitest';
import App from '../App';
import { ThemeProvider } from '../shared/theme/ThemeProvider';
import { createTestQueryClient } from '../test/utils';
import { oidcConfig } from '../auth/oidc';
import '../i18n';

/**
 * Which navigation the shell mounts, at which width.
 *
 * The choice is made in JS on purpose — a component hidden with `display: none` is still
 * mounted and still in the accessibility tree — so it is the one half of #172 a test in
 * jsdom can actually assert. How each navigation is *drawn* lives in `--nav-side-*` and
 * was checked in a browser instead.
 */

/**
 * jsdom implements no `matchMedia` at all, which is why every other test in the suite
 * gets the phone layout for free. This stub answers `(min-width: Npx)` against a width,
 * and nothing else — a `prefers-color-scheme` query comes back false, i.e. the light
 * palette, which is what those tests already assume.
 *
 * `matches` is a getter rather than a value, and the listeners are kept, so `resizeTo`
 * below can move the window under a mounted shell exactly as a browser does.
 */
let width = 0;
const listeners = new Set<() => void>();

function viewport(next: number) {
  width = next;
  window.matchMedia = (query: string): MediaQueryList => {
    const minWidth = /\(\s*min-width:\s*(\d+)px\s*\)/.exec(query);
    return {
      get matches() {
        return minWidth != null && width >= Number(minWidth[1]);
      },
      media: query,
      onchange: null,
      addEventListener: (_: string, listener: () => void) => void listeners.add(listener),
      removeEventListener: (_: string, listener: () => void) => void listeners.delete(listener),
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    } as unknown as MediaQueryList;
  };
}

/** Resizes the window under a shell that is already mounted. */
function resizeTo(next: number) {
  width = next;
  act(() => listeners.forEach((listener) => listener()));
}

afterEach(() => {
  Reflect.deleteProperty(window, 'matchMedia');
  listeners.clear();
});

function renderAt(path: string) {
  return render(
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={createTestQueryClient()}>
        <ThemeProvider>
          <MemoryRouter initialEntries={[path]}>
            <App />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </AuthProvider>,
  );
}

/** The side navigation is the labelled one; the bottom bar has never carried a name. */
function sideNav() {
  return screen.queryByRole('navigation', { name: 'Navigation principale' });
}

/**
 * Where the rendered navigation points, in order. Read from `href` rather than from the
 * label: the icon glyph sits in the same element as the text, so `textContent` carries a
 * Private Use Area character the assertion diff cannot show.
 */
function destinations() {
  return screen.getAllByRole('link').map((link) => link.getAttribute('href'));
}

const DESTINATIONS = ['/', '/collection', '/discover', '/wishlist', '/stats'];

describe('the navigation the shell mounts', () => {
  test('a phone gets the bottom bar, and only that', () => {
    viewport(390);
    renderAt('/');

    const navigations = screen.getAllByRole('navigation');
    expect(navigations).toHaveLength(1);
    expect(navigations[0]).not.toHaveAccessibleName();
    expect(sideNav()).toBeNull();
  });

  test('the tablet breakpoint swaps the bar for the side navigation', () => {
    viewport(600);
    renderAt('/');

    expect(screen.getAllByRole('navigation')).toHaveLength(1);
    expect(sideNav()).toBeInTheDocument();
  });

  test('one pixel below it, the bar is still there', () => {
    viewport(599);
    renderAt('/');

    expect(sideNav()).toBeNull();
    expect(screen.getAllByRole('navigation')[0]).not.toHaveAccessibleName();
  });

  test('a desktop keeps the side navigation, and never the bar', () => {
    viewport(1440);
    renderAt('/');

    expect(screen.getAllByRole('navigation')).toHaveLength(1);
    expect(sideNav()).toBeInTheDocument();
  });

  test('the bar carries the five destinations, and no way to Settings', () => {
    viewport(390);
    renderAt('/');

    expect(destinations()).toEqual(DESTINATIONS);
    expect(screen.queryByRole('link', { name: 'Réglages' })).toBeNull();
  });

  test('the side navigation carries the same five in the same order, plus Settings', () => {
    viewport(1440);
    renderAt('/');

    expect(destinations()).toEqual([...DESTINATIONS, '/settings']);
    expect(screen.getByRole('link', { name: 'Réglages' })).toBeInTheDocument();
  });

  /**
   * The one thing the browser sweep could not check: the preview pane resizes without
   * dispatching the `change` event a real window fires, so every width there was measured
   * on a freshly rendered shell. This is the subscription itself — dragging a desktop
   * window across the breakpoint has to swap the navigation, not wait for a reload.
   */
  test('follows a window dragged across the breakpoint, without a reload', () => {
    viewport(390);
    renderAt('/');
    expect(sideNav()).toBeNull();

    resizeTo(1280);
    expect(sideNav()).toBeInTheDocument();
    expect(screen.getAllByRole('navigation')).toHaveLength(1);

    resizeTo(500);
    expect(sideNav()).toBeNull();
    expect(screen.getAllByRole('navigation')[0]).not.toHaveAccessibleName();
  });
});

describe('the active destination', () => {
  test('is marked on the bottom bar', () => {
    viewport(390);
    renderAt('/collection');

    expect(screen.getByRole('link', { name: 'Collection' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('link', { name: 'Accueil' })).not.toHaveAttribute('aria-current');
  });

  test('is marked on the side navigation, which is what draws its pill', () => {
    viewport(1440);
    renderAt('/collection');

    expect(screen.getByRole('link', { name: 'Collection' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('link', { name: 'Accueil' })).not.toHaveAttribute('aria-current');
  });

  test('Home stays inactive on every other route', () => {
    viewport(1440);
    renderAt('/wishlist');

    expect(screen.getByRole('link', { name: 'Accueil' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('link', { name: 'Souhaits' })).toHaveAttribute('aria-current', 'page');
  });
});

/**
 * `HIDDEN_NAV_PREFIXES` is a phone's reasoning — the bar costs height on a 900px frame,
 * and Detail carries its own way back. A column beside the page costs width instead, so
 * the side navigation stays put on those routes.
 */
describe('a full-screen route', () => {
  test('drops the bottom bar on a phone', async () => {
    viewport(390);
    renderAt('/settings');

    expect(await screen.findByText('Apparence')).toBeInTheDocument();
    expect(screen.queryAllByRole('navigation')).toHaveLength(0);
  });

  test('keeps the side navigation on a desktop', async () => {
    viewport(1440);
    renderAt('/settings');

    expect(await screen.findByText('Apparence')).toBeInTheDocument();
    expect(sideNav()).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Réglages' })).toHaveAttribute('aria-current', 'page');
  });
});
