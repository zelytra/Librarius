import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  STORAGE_KEY,
  applyTheme,
  darkModeQuery,
  readStoredTheme,
  resolveTheme,
  type ThemeId,
} from './themes';
import { ThemeContext, type ThemeContextValue } from './context';

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeId>(readStoredTheme);

  useEffect(() => {
    applyTheme(resolveTheme(theme));

    // Following the system preference means following it while the app is open,
    // not only at load time.
    if (theme !== 'systeme') return;
    const query = darkModeQuery();
    if (!query) return;
    const onChange = () => applyTheme(resolveTheme(theme));
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [theme]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      setTheme: (next) => {
        setThemeState(next);
        try {
          localStorage.setItem(STORAGE_KEY, next);
        } catch {
          /* storage unavailable: keep the theme in memory only */
        }
      },
    }),
    [theme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
