import { lazy, Suspense, useState } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { useGetApiDashboardLayout } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';
import { defaultLayout } from '../home/dashboardLayout';
import styles from './SettingsPage.module.css';

// Fetched once the reader opens it, as it was on Home: most never reorder their dashboard.
const DashboardEditor = lazy(() =>
  import('../home/DashboardEditor').then((m) => ({ default: m.DashboardEditor })),
);

/**
 * Organising Home (#554): reordering and hiding its sections, moved here from Home's header,
 * where it crowded the phone's row of buttons (#542). The first-run tour's way back sits
 * with it.
 */
export function HomeLayoutSection() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { authed } = useApiAuth();
  const [editing, setEditing] = useState(false);
  const { data } = useGetApiDashboardLayout({ query: { enabled: authed } });
  const sections = data?.sections ?? defaultLayout();

  return (
    <>
      <h3 className={styles.sectionTitle}>{t('settings.dashboard')}</h3>
      <p className={styles.sectionIntro}>{t('settings.dashboardDescription')}</p>
      {authed && (
        <button type="button" className={styles.dashboardLink} onClick={() => setEditing(true)}>
          <Icon name="tune" size={16} color="var(--accent-deep)" />
          {t('home.customize.open')}
        </button>
      )}
      {/* The first-run tour (#76) is skippable and never re-appears on its own once it has
          been — this is the only way back to it. Router state rather than a query string: it
          carries no meaning to bookmark or share. */}
      <button
        type="button"
        className={styles.dashboardLink}
        onClick={() => navigate('/', { state: { showOnboarding: true } })}
      >
        <Icon name="auto_stories" size={16} color="var(--accent-deep)" />
        {t('settings.onboardingAction')}
      </button>
      {editing && (
        <Suspense fallback={null}>
          <DashboardEditor sections={sections} onClose={() => setEditing(false)} />
        </Suspense>
      )}
    </>
  );
}
