import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import {
  getGetApiDashboardLayoutQueryKey,
  usePutApiDashboardLayout,
} from '../../api/generated/librarius';
import { moveSection, toggleHidden, type DashboardSectionPref } from './dashboardLayout';
import styles from './DashboardEditor.module.css';

/** Section code → the i18n key of its label in this panel. */
const SECTION_LABEL_KEY: Record<string, string> = {
  resumeReading: 'home.resumeReading',
  toRead: 'home.toRead',
  counters: 'home.customize.sections.counters',
  goal: 'home.customize.sections.goal',
  upcoming: 'home.upcoming',
  recentlyRead: 'home.customize.sections.recentlyRead',
};

interface DashboardEditorProps {
  sections: DashboardSectionPref[];
  onClose: () => void;
}

/**
 * Reorders and hides the Home sections (#54).
 *
 * <p>Move buttons rather than a drag gesture: they need no pointer geometry a test can
 * exercise, they are exactly as usable with a finger as with a mouse or a keyboard, and
 * they work for a screen reader where a drag never would. Every known section is always
 * listed here, hidden ones included — hiding one only flips a flag, it never drops it from
 * this list, which is what lets it be found again without knowing it ever existed.
 */
export function DashboardEditor({ sections, onClose }: DashboardEditorProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(sections);

  const { mutate: save, isPending, isError } = usePutApiDashboardLayout({
    mutation: {
      onSuccess: (saved) => {
        queryClient.setQueryData(getGetApiDashboardLayoutQueryKey(), saved);
        onClose();
      },
    },
  });

  const label = (code: string) => t(SECTION_LABEL_KEY[code] ?? code);

  return (
    <div className={styles.panel} role="dialog" aria-modal="false" aria-label={t('home.customize.title')}>
      <h3 className={styles.title}>{t('home.customize.title')}</h3>
      <p className={styles.hint}>{t('home.customize.hint')}</p>

      <ul className={styles.list}>
        {draft.map((section, index) => (
          <li key={section.code} className={styles.row}>
            <span className={`${styles.label} ${section.hidden ? styles.labelHidden : ''}`}>
              {label(section.code)}
            </span>
            {section.hidden && (
              <span className={styles.hiddenBadge}>{t('home.customize.hiddenBadge')}</span>
            )}
            <div className={styles.controls}>
              <button
                type="button"
                disabled={index === 0}
                onClick={() => setDraft((d) => moveSection(d, index, -1))}
                aria-label={t('home.customize.moveUp', { section: label(section.code) })}
                className={styles.iconButton}
              >
                <Icon name="keyboard_arrow_up" size={20} />
              </button>
              <button
                type="button"
                disabled={index === draft.length - 1}
                onClick={() => setDraft((d) => moveSection(d, index, 1))}
                aria-label={t('home.customize.moveDown', { section: label(section.code) })}
                className={styles.iconButton}
              >
                <Icon name="keyboard_arrow_down" size={20} />
              </button>
              <button
                type="button"
                onClick={() => setDraft((d) => toggleHidden(d, index))}
                aria-pressed={!section.hidden}
                aria-label={t(section.hidden ? 'home.customize.show' : 'home.customize.hide', {
                  section: label(section.code),
                })}
                className={styles.iconButton}
              >
                <Icon name={section.hidden ? 'visibility_off' : 'visibility'} size={20} />
              </button>
            </div>
          </li>
        ))}
      </ul>

      {isError && <p className={styles.failure}>{t('home.customize.failed')}</p>}

      <div className={styles.actions}>
        <Button variant="ghost" size="compact" onClick={onClose} disabled={isPending}>
          {t('home.customize.cancel')}
        </Button>
        <Button
          variant="primary"
          size="compact"
          onClick={() => save({ data: { sections: draft } })}
          disabled={isPending}
        >
          {t(isPending ? 'common.working' : 'home.customize.save')}
        </Button>
      </div>
    </div>
  );
}
