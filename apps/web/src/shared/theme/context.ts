import { createContext, useContext } from 'react';
import type { ThemeId } from './themes';

export interface ThemeContextValue {
  theme: ThemeId;
  setTheme: (theme: ThemeId) => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    // Developer-facing, hence English and outside i18n: the user never sees it.
    throw new Error('useTheme must be used inside a ThemeProvider');
  }
  return ctx;
}
