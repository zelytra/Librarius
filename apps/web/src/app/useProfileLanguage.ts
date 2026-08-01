import { useEffect } from 'react';
import { useApiAuth } from '../shared/api';
import { useGetApiMe } from '../api/generated/librarius';
import { changeLanguage } from '../i18n';
import { activeLanguage, isLanguageId } from '../i18n/languages';

/**
 * Lets the account's saved language drive the interface once the profile is loaded (#75).
 *
 * The app has to paint before the API answers, so the first language comes from
 * `localStorage` or the browser (see `resolveInitialLanguage`). This hook closes the loop:
 * when the profile arrives naming a supported language, it applies it and stores it as the
 * value the next boot starts on — which is what makes the choice follow the account to
 * another device, rather than living only on the one it was made on.
 *
 * It runs on the identity of the fetched profile, so it fires once per load and does not
 * fight the local switcher between fetches: a save invalidates the profile, the refetch
 * carries the new language, and the effect re-applies exactly what the user just picked.
 */
export function useProfileLanguage(): void {
  const auth = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: auth.authed } });
  const serverLocale = me?.locale;

  useEffect(() => {
    if (isLanguageId(serverLocale) && serverLocale !== activeLanguage()) {
      changeLanguage(serverLocale);
    }
  }, [serverLocale]);
}
