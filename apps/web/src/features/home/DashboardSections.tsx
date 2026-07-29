import { lazy, Suspense, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Cover } from '../../shared/ui/Cover';
import { Icon } from '../../shared/ui/Icon';
import { Button, SectionHeader } from '../../shared/ui/primitives';
import { EmptyState } from '../../shared/ui/states';
import {
  useGetApiDashboardLayout,
  type LibraryItemDto,
  type StatsDto,
} from '../../api/generated/librarius';
import { GoalCard } from './GoalCard';
import { ToReadPile } from './ToReadPile';
import { UpcomingReleases } from './UpcomingReleases';
import { defaultLayout } from './dashboardLayout';
import styles from './HomePage.module.css';

// The editor is only fetched once the user actually opens it: someone who never
// customizes the dashboard — the common case — never pays for its bundle.
const DashboardEditor = lazy(() =>
  import('./DashboardEditor').then((m) => ({ default: m.DashboardEditor })),
);

interface DashboardSectionsProps {
  reading: LibraryItemDto[];
  read: LibraryItemDto[];
  stats: StatsDto | undefined;
  libraryEmpty: boolean;
}

/**
 * The Home dashboard's sections, in the order and visibility the user chose (#54).
 *
 * <p>The layout query is deliberately left out of {@code HomePage}'s loading/error gate,
 * the same treatment the upcoming-releases block already got: a slow or failing answer
 * falls back to {@link defaultLayout} instead of hiding every shelf underneath it, so the
 * feature stays invisible to an account that never touched it.
 */
export function DashboardSections({ reading, read, stats, libraryEmpty }: DashboardSectionsProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const { data } = useGetApiDashboardLayout();
  const sections = data?.sections ?? defaultLayout();
  const isHidden = (code: string) => sections.find((s) => s.code === code)?.hidden ?? false;

  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });

  const cover = (it: LibraryItemDto) => (
    <Cover
      key={it.id}
      title={it.book?.title ?? '—'}
      imageUrl={it.book?.coverUrl}
      caption={it.book?.authors}
      onClick={() => open(it)}
    />
  );

  /**
   * Same cover, with where the reader stands drawn on it. "Resume reading" without that
   * was a shelf of titles the user had opened and no hint of how far in they were.
   */
  const readingCover = (it: LibraryItemDto) => {
    const percent = it.progress?.percent;
    return (
      <Cover
        key={it.id}
        title={it.book?.title ?? '—'}
        imageUrl={it.book?.coverUrl}
        caption={it.book?.authors}
        onClick={() => open(it)}
      >
        {percent != null && (
          <>
            <span className={styles.progressBadge}>{t('home.progressBadge', { percent })}</span>
            <span
              className={styles.progressTrack}
              role="progressbar"
              aria-label={t('home.progressLabel', { percent })}
              aria-valuenow={percent}
            >
              {/* The fill is the value itself: its width can only be inline. */}
              <span className={styles.progressFill} style={{ width: `${percent}%` }} />
            </span>
          </>
        )}
      </Cover>
    );
  };

  const mini = [
    { value: String(stats?.read ?? 0), label: t('home.counters.read'), tone: styles.miniSage },
    { value: String(stats?.reading ?? 0), label: t('home.counters.reading'), tone: styles.miniRose },
    { value: String(stats?.toRead ?? 0), label: t('home.counters.toRead'), tone: styles.miniSand },
  ];

  function renderSection(code: string): ReactNode {
    if (isHidden(code)) return null;
    switch (code) {
      case 'resumeReading':
        return (
          reading.length > 0 && (
            <section key={code}>
              <SectionHeader
                title={t('home.resumeReading')}
                action={t('home.resumeCount', { reading: reading.length })}
              />
              <div className={`scroll-x ${styles.shelf}`}>{reading.map(readingCover)}</div>
            </section>
          )
        );

      case 'toRead':
        // Self-contained, like `upcoming` below: it fetches its own page of the pile and
        // hides itself when there is nothing waiting. Nothing to hand it — the shelves
        // above are fetched by status too, and this one is no different.
        return <ToReadPile key={code} />;

      case 'counters':
        return (
          <section key={code}>
            <div className={styles.miniRow}>
              {mini.map((s) => (
                <div key={s.label} className={`${styles.miniTile} ${s.tone}`}>
                  <div className={styles.miniValue}>{s.value}</div>
                  <div className={styles.miniLabel}>{s.label}</div>
                </div>
              ))}
            </div>
          </section>
        );

      case 'goal':
        // The annual goal, or the invitation to set one — never a gauge at zero, and
        // never shown at all on a library with nothing in it yet.
        return (
          !libraryEmpty && (
            <section key={code}>
              <GoalCard stats={stats} />
            </section>
          )
        );

      case 'upcoming':
        // Self-contained since #57: it fetches its own announcements and decides on its
        // own whether it has anything to say, section included. Nothing to hand it but
        // whether the dashboard is already showing its empty state.
        return <UpcomingReleases key={code} libraryEmpty={libraryEmpty} />;

      case 'recentlyRead':
        return (
          read.length > 0 && (
            <section key={code}>
              <SectionHeader title={t('home.recentlyRead')} />
              <div className={`scroll-x ${styles.shelf}`}>{read.map(cover)}</div>
            </section>
          )
        );

      default:
        // A code neither this build nor the API's normalize() recognises: nothing to
        // render rather than a crash — see dashboardLayout.ts.
        return null;
    }
  }

  return (
    <div className={styles.sections}>
      <button type="button" onClick={() => setEditing(true)} className={styles.customizeButton}>
        <Icon name="tune" size={16} />
        {t('home.customize.open')}
      </button>

      {editing && (
        <Suspense fallback={null}>
          <DashboardEditor sections={sections} onClose={() => setEditing(false)} />
        </Suspense>
      )}

      {sections.map((s) => renderSection(s.code))}

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
