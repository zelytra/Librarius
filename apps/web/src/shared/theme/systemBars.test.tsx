import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { ThemeId } from './themes';

const setStyle = vi.fn(() => Promise.resolve());
const native = { value: true };

vi.mock('@capacitor/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@capacitor/core')>();
  return {
    ...actual,
    Capacitor: { ...actual.Capacitor, isNativePlatform: () => native.value },
    SystemBars: { setStyle },
  };
});

const { ThemeProvider } = await import('./ThemeProvider');
const { useTheme } = await import('./context');

let setTheme: (id: ThemeId) => void = () => {};
function Grab() {
  setTheme = useTheme().setTheme;
  return null;
}

/**
 * #533: the native container draws the status and gesture bars over the app, and Capacitor
 * coloured their icons from the device's night mode. They now follow the palette the reader
 * chose: dark icons on the light palettes, light ones on Nuit and Ardoise.
 */
describe('system bars', () => {
  beforeEach(() => {
    setStyle.mockClear();
    native.value = true;
    localStorage.clear();
  });
  afterEach(() => localStorage.clear());

  test('a light palette asks for dark icons on both bars', () => {
    localStorage.setItem('librarius.theme', 'creme');
    render(<ThemeProvider><Grab /></ThemeProvider>);
    // No `bar`: the style applies to the status bar and the gesture bar alike.
    expect(setStyle).toHaveBeenLastCalledWith({ style: 'LIGHT' });
  });

  test('switching to a dark palette switches the icons to light', () => {
    localStorage.setItem('librarius.theme', 'sauge');
    render(<ThemeProvider><Grab /></ThemeProvider>);
    act(() => setTheme('nuit'));
    expect(setStyle).toHaveBeenLastCalledWith({ style: 'DARK' });
    act(() => setTheme('ardoise'));
    expect(setStyle).toHaveBeenLastCalledWith({ style: 'DARK' });
    act(() => setTheme('rose'));
    expect(setStyle).toHaveBeenLastCalledWith({ style: 'LIGHT' });
  });

  test('nothing is asked of a browser', () => {
    native.value = false;
    render(<ThemeProvider><Grab /></ThemeProvider>);
    expect(setStyle).not.toHaveBeenCalled();
  });
});
