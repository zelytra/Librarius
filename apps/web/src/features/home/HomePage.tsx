import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { Button, Screen, SectionHeader } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import { goalPace, goalUnitOf } from '../../shared/goal';
import {
  useGetApiCatalogUpcoming,
  useGetApiLibrary,
  useGetApiStats,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import styles from './HomePage.module.css';

/** Shelves shown on the dashboard, and how many covers each of them holds. */
const READING_SHELF_SIZE = 12;
const READ_SHELF_SIZE = 8;

interface ReadingGoalProps {
  /** Target of the running year, absent when the user has not set one. */
  target?: number;
  /** What has been read this year, in the goal's unit. */
  current: number;
  unit?: string;
  onSetGoal: () => void;
}

/**
 * The annual goal: where the user stands, and what the rest of the year asks for.
 *
 * With no goal set this is an invitation rather than a gauge at zero — an empty bar
 * reads like a failure, when nothing has been aimed at yet.
 */
function ReadingGoal({ target, current, unit, onSetGoal }: ReadingGoalProps) {
  const { t } = useTranslation();
  // The unit agrees with the number it qualifies, which is not the same one on both
  // lines: "1 / 30 livres" but "encore 1 livre".
  const units = (count: number) => t(`common.goalUnits.${goalUnitOf(unit)}`, { count });

  if (!target) {
    return (
      <section className={`${styles.goal} ${styles.goalEmpty}`}>
        <div className={styles.goalBody}>
          <div className={styles.goalTitle}>{t('home.goal.unsetTitle')}</div>
          <p className={styles.goalHint}>{t('home.goal.unsetHint')}</p>
        </div>
        <Button variant="secondary" onClick={onSetGoal}>
          {t('home.goal.set')}
        </Button>
      </section>
    );
  }

  const pace = goalPace(target, current, new Date());

  return (
    <section className={styles.goal}>
      <div className={styles.goalHead}>
        <div className={styles.goalTitle}>
          {t('home.goal.title', { year: new Date().getFullYear() })}
        </div>
        <div className={styles.goalCount}>
          {t('home.goal.progress', { current, target, unit: units(target) })}
        </div>
      </div>

      <div className={styles.track} role="progressbar" aria-valuenow={pace.percent} aria-valuemin={0} aria-valuemax={100}>
        {/* The filled part is the progress itself, known only at render time. */}
        <div className={styles.bar} style={{ width: `${pace.percent}%` }} />
      </div>

      <p className={styles.goalHint}>
        {pace.reached
          ? t('home.goal.reached')
          : t('home.goal.pace', {
              remaining: pace.remaining,
              unit: units(pace.remaining),
              perWeek: pace.perWeek,
            })}
      </p>
    </section>
  );
}

function Dashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  // The queries run in parallel and are cached independently: coming back to Home after
  // browsing no longer refetches anything. Each shelf asks the server for its own status
  // rather than downloading the collection to filter it here.
  const readingQuery = useGetApiLibrary({ status: 'READING', size: READING_SHELF_SIZE });
  const readQuery = useGetApiLibrary({ status: 'READ', size: READ_SHELF_SIZE });
  const statsQuery = useGetApiStats();
  const { data: upcoming = [] } = useGetApiCatalogUpcoming({ kind: 'MANGA', limit: 5 });

  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });
  const reading = readingQuery.data?.items ?? [];
  const read = readQuery.data?.items ?? [];
  const stats = statsQuery.data;

  // The dashboard is made of the user's own data: as long as none of it has arrived,
  // there is nothing worth rendering. Upcoming releases come from a third-party catalog
  // and are deliberately left out — their outage must not hide the shelves.
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

  const mini = [
    { value: String(stats?.read ?? 0), label: t('home.counters.read'), tone: styles.miniSage },
    { value: String(stats?.reading ?? 0), label: t('home.counters.reading'), tone: styles.miniRose },
    { value: String(stats?.toRead ?? 0), label: t('home.counters.toRead'), tone: styles.miniSand },
  ];

  const cover = (it: LibraryItemDto) => (
    <Cover
      key={it.id}
      title={it.book?.title ?? '—'}
      imageUrl={it.book?.coverUrl}
      caption={it.book?.authors}
      onClick={() => open(it)}
    />
  );

  return (
    <div className={styles.sections}>
      {reading.length > 0 && (
        <section>
          <SectionHeader
            title={t('home.resumeReading')}
            action={t('home.resumeCount', { reading: reading.length })}
          />
          <div className={`scroll-x ${styles.shelf}`}>{reading.map(cover)}</div>
        </section>
      )}

      <section>
        <div className={styles.miniRow}>
          {mini.map((s) => (
            <div key={s.label} className={`${styles.miniTile} ${s.tone}`}>
              <div className={styles.miniValue}>{s.value}</div>
              <div className={styles.miniLabel}>{s.label}</div>
            </div>
          ))}
        </div>
      </section>

      <ReadingGoal
        target={stats?.goalTarget}
        current={stats?.goalCurrent ?? 0}
        unit={stats?.goalUnit}
        onSetGoal={() => navigate('/settings')}
      />

      {upcoming.length > 0 && (
        <section>
          <SectionHeader title={t('home.upcoming')} />
          <div className={styles.upcomingList}>
            {upcoming.map((u, i) => (
              <div key={`${u.providerRef ?? i}`} className={styles.upcomingRow}>
                <div
                  className={styles.upcomingThumb}
                  // The cover is a remote image, known only at render time.
                  style={
                    u.coverUrl
                      ? { background: `center/cover no-repeat url(${u.coverUrl})` }
                      : undefined
                  }
                />
                <div className={styles.upcomingBody}>
                  <div className={styles.upcomingTitle}>{u.title}</div>
                  <div className={styles.upcomingAuthors}>{u.authors}</div>
                </div>
                {u.releaseDate && <span className={styles.releaseBadge}>{u.releaseDate}</span>}
              </div>
            ))}
          </div>
          <p className={styles.footnote}>{t('home.upcomingNote')}</p>
        </section>
      )}

      {read.length > 0 && (
        <section>
          <SectionHeader title={t('home.recentlyRead')} />
          <div className={`scroll-x ${styles.shelf}`}>{read.map(cover)}</div>
        </section>
      )}

      {libraryEmpty && (
        <EmptyState
          icon="auto_stories"
          className={styles.empty}
          title={t('home.empty.title')}
          description={t('home.empty.description')}
          action={
            <Button variant="secondary" onClick={() => navigate('/discover')}>
              {t('home.empty.action')}
            </Button>
          }
        />
      )}
    </div>
  );
}

export function HomePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const today = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());

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
