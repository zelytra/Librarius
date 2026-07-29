import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Screen } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import { activeLanguage } from '../../i18n/languages';
import { useGetApiLibrary, useGetApiStats } from '../../api/generated/librarius';
import { DashboardSections } from './DashboardSections';
import styles from './HomePage.module.css';

/** Shelves shown on the dashboard, and how many covers each of them holds. */
const READING_SHELF_SIZE = 12;
const READ_SHELF_SIZE = 8;

function Dashboard() {
  const { t } = useTranslation();
  // The queries run in parallel and are cached independently: coming back to Home after
  // browsing no longer refetches anything. Each shelf asks the server for its own status
  // rather than downloading the collection to filter it here.
  const readingQuery = useGetApiLibrary({ status: 'READING', size: READING_SHELF_SIZE });
  const readQuery = useGetApiLibrary({ status: 'READ', size: READ_SHELF_SIZE });
  const statsQuery = useGetApiStats();

  const reading = readingQuery.data?.items ?? [];
  const read = readQuery.data?.items ?? [];
  const stats = statsQuery.data;

  // The dashboard is made of the user's own data: as long as none of it has arrived,
  // there is nothing worth rendering. The upcoming releases and the section layout are
  // both deliberately left out of this gate — each fetches its own data and falls back on
  // its own — so neither one being slow or unavailable hides the shelves under it.
  const refetchAll = () => {
    void readingQuery.refetch();
    void readQuery.refetch();
    void statsQuery.refetch();
  };
  if (readingQuery.isPending || readQuery.isPending || statsQuery.isPending) return <Loading />;
  if (readingQuery.isError || readQuery.isError || statsQuery.isError) {
    return <ErrorState message={t('home.error')} onRetry={refetchAll} />;
  }

  // Emptiness comes from the counters, not from a shelf: a library made only of
  // owned-but-unread titles fills neither of the two above.
  const libraryEmpty =
    stats != null && (stats.read ?? 0) + (stats.reading ?? 0) + (stats.toRead ?? 0) === 0;

  return (
    <DashboardSections reading={reading} read={read} stats={stats} libraryEmpty={libraryEmpty} />
  );
}

export function HomePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const today = new Intl.DateTimeFormat(activeLanguage(), { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());

  return (
    <Screen>
      <div className={styles.header}>
        <div>
          <div className={styles.date}>{today}</div>
          <div className={styles.greeting}>{t('home.greeting')}</div>
        </div>
        <button
          onClick={() => navigate('/settings')}
          aria-label={t('settings.title')}
          className={styles.settingsButton}
        >
          <Icon name="settings" size={22} color="var(--on-accent)" />
        </button>
      </div>
      <LoginGate prompt={t('auth.prompts.home')}>
        <Dashboard />
      </LoginGate>
    </Screen>
  );
}
