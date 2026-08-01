import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Segmented } from '../../shared/ui/primitives';
import { TrustBadge } from '../../shared/ui/TrustBadge';
import { useApiAuth } from '../../shared/api';
import { changeLanguage } from '../../i18n';
import { LANGUAGES, isLanguageId, type LanguageId } from '../../i18n/languages';
import {
  getGetApiMeQueryKey,
  useGetApiMe,
  usePatchApiMe,
} from '../../api/generated/librarius';
import { supportedTimeZones } from '../../shared/timeZones';
import styles from './ProfileSection.module.css';

/**
 * The editable profile (#75): display name, interface language and time zone.
 *
 * <p>The language selected here persists server-side and follows the account to another
 * device — the switcher further down stays as the quick, device-local toggle the app boots
 * on before the profile has been fetched (PRODUCT § 4.7). Saving therefore also switches the
 * interface at once through {@link changeLanguage}, which keeps the two in step, and
 * invalidates the profile so the account-deletion confirmation reads the new display name.
 */
export function ProfileSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const queryClient = useQueryClient();

  const { data: me } = useGetApiMe({ query: { enabled: auth.authed } });

  const [displayName, setDisplayName] = useState('');
  const [locale, setLocale] = useState<LanguageId>('fr');
  const [timeZone, setTimeZone] = useState('');
  const [saved, setSaved] = useState(false);

  // The profile arrives after the first render. Syncing on its identity fills the fields
  // once and leaves them alone afterwards, including while a save is in flight — the same
  // shape GoalSection uses for the stored goal.
  useEffect(() => {
    if (!me?.id) return;
    const stored = me.locale;
    setDisplayName(me.displayName ?? '');
    setLocale(isLanguageId(stored) ? stored : 'fr');
    setTimeZone(me.timeZone ?? '');
  }, [me?.id, me?.displayName, me?.locale, me?.timeZone]);

  const { mutate: saveProfile, isPending, isError } = usePatchApiMe({
    mutation: {
      onSuccess: (updated) => {
        setSaved(true);
        void queryClient.invalidateQueries({ queryKey: getGetApiMeQueryKey() });
        // The language is now the account's: apply it immediately and store it as the boot
        // value this device starts on next time, exactly as the switcher does.
        if (isLanguageId(updated.locale)) changeLanguage(updated.locale);
      },
    },
  });

  const trimmed = displayName.trim();
  const valid = trimmed !== '';

  if (!auth.authed) {
    return (
      <>
        <h3 className={styles.title}>{t('settings.profile.title')}</h3>
        <p className={styles.intro}>{t('settings.profile.signIn')}</p>
      </>
    );
  }

  return (
    <>
      <h3 className={styles.title}>{t('settings.profile.title')}</h3>
      <p className={styles.intro}>{t('settings.profile.description')}</p>

      <div className={styles.form}>
        <label className={styles.field}>
          <span className={styles.label}>
            {t('settings.profile.displayName')}
            {/* #186: server-computed, display-only — nothing renders for a regular account. */}
            {me?.trusted && <TrustBadge className={styles.trustBadge} />}
          </span>
          <input
            type="text"
            value={displayName}
            onChange={(e) => {
              setDisplayName(e.target.value);
              setSaved(false);
            }}
            placeholder={t('settings.profile.displayNamePlaceholder')}
            aria-label={t('settings.profile.displayName')}
            className={styles.input}
          />
        </label>

        <div className={styles.field}>
          <span className={styles.label}>{t('settings.profile.language')}</span>
          {/* Endonyms, never translated: someone on the wrong language recognises their own. */}
          <Segmented<LanguageId>
            options={LANGUAGES.map((language) => ({ id: language.id, label: language.label }))}
            value={locale}
            onChange={(next) => {
              setLocale(next);
              setSaved(false);
            }}
          />
        </div>

        <label className={styles.field}>
          <span className={styles.label}>{t('settings.profile.timeZone')}</span>
          <select
            value={timeZone}
            onChange={(e) => {
              setTimeZone(e.target.value);
              setSaved(false);
            }}
            aria-label={t('settings.profile.timeZone')}
            className={styles.input}
          >
            {/* Empty means "follow this device", the default an account never sets one keeps. */}
            <option value="">{t('settings.profile.timeZoneAuto')}</option>
            {supportedTimeZones().map((zone) => (
              <option key={zone} value={zone}>
                {zone}
              </option>
            ))}
          </select>
        </label>

        <Button
          variant="primary"
          size="compact"
          className={styles.submit}
          disabled={!valid || isPending}
          onClick={() =>
            saveProfile({
              data: { displayName: trimmed, locale, timeZone: timeZone || undefined },
            })
          }
        >
          {t(isPending ? 'common.working' : 'settings.profile.submit')}
        </Button>

        {saved && !isPending && <p className={styles.success}>{t('settings.profile.saved')}</p>}
        {isError && <p className={styles.failure}>{t('settings.profile.failed')}</p>}
      </div>
    </>
  );
}
