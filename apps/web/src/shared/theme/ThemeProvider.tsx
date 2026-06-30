import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { DEFAULT_THEME, isThemeId, type ThemeId } from './themes';
import { ThemeContext, type ThemeContextValue } from './context';

const STORAGE_KEY = 'librarius.theme';

function readInitialTheme(): ThemeId {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null;
  return isThemeId(stored) ? stored : DEFAULT_THEME;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeId>(readInitialTheme);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      setTheme: (next) => {
        setThemeState(next);
        try {
          localStorage.setItem(STORAGE_KEY, next);
        } catch {
          /* stockage indisponible : on garde le thème en mémoire seulement */
        }
      },
    }),
    [theme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
