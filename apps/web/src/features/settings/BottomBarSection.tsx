import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NAV_DESTINATIONS, NOTIFICATIONS_DESTINATION, PEOPLE_DESTINATION } from '../../app/navigation';
import { BOTTOM_BAR_CHOICES, BOTTOM_BAR_MAX, readBottomBar, storeBottomBar } from '../../shared/bottomBar';
import styles from './SettingsPage.module.css';

const LABEL_KEY: Record<string, string> = Object.fromEntries(
  [...NAV_DESTINATIONS, PEOPLE_DESTINATION, NOTIFICATIONS_DESTINATION].map((d) => [d.to, d.labelKey]),
);

/**
 * The pages the phone's bottom bar shows (#561): four at most — a fifth would cut the labels
 * short in a 360 px bar — and one at least; every other page stays in « Plus ».
 */
export function BottomBarSection() {
  const { t } = useTranslation();
  const [picked, setPicked] = useState<readonly string[]>(readBottomBar);
  const toggle = (to: string) => {
    const next = picked.includes(to)
      ? picked.filter((p) => p !== to)
      : BOTTOM_BAR_CHOICES.filter((c) => c === to || picked.includes(c));
    setPicked(next);
    storeBottomBar(next);
  };
  return (
    <fieldset className={styles.fieldset}>
      <legend className={styles.sectionTitle}>{t('settings.bottomBar.title')}</legend>
      <p className={styles.sectionIntro}>{t('settings.bottomBar.description', { max: BOTTOM_BAR_MAX })}</p>
      <div className={styles.checks}>
        {BOTTOM_BAR_CHOICES.map((to) => {
          const on = picked.includes(to);
          const locked = on ? picked.length === 1 : picked.length >= BOTTOM_BAR_MAX;
          return (
            <label key={to} className={styles.checkRow}>
              <input type="checkbox" checked={on} disabled={locked} onChange={() => toggle(to)} />
              {t(LABEL_KEY[to])}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
