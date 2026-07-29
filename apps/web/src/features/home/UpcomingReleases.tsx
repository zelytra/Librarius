import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Button, SectionHeader } from '../../shared/ui/primitives';
import { EmptyState } from '../../shared/ui/states';
import { useGetApiReleasesUpcoming, type UpcomingReleaseDto } from '../../api/generated/librarius';
import { formatReleaseDate, regionLabel, releaseVolumeLabel, sourceLabel } from './releases';
import styles from './UpcomingReleases.module.css';

/** Announcements fetched at once — plenty for a home dashboard section. */
const LIMIT = 5;

/**
 * Personalised "what's coming next": the announcements of the series the caller owns a
 * volume of, has a wish on, or follows — `GET /api/releases/upcoming`, replacing the
 * generic provider trends this section used to show (issue #57).
 *
 * <p>Self-contained on purpose, and outside the dashboard's own loading gate: an empty
 * stake is the ordinary state of a new account, not a failure, and this section being slow
 * or unavailable must not hold up the shelves above it — exactly the reasoning that kept
 * the old catalog-trends block out of that gate.
 *
 * @param libraryEmpty whether the whole dashboard is already showing its own empty state.
 *   That one already invites the user towards Discover; repeating a second invitation here
 *   would just be noise, so an empty result renders nothing rather than a second empty state
 */
export function UpcomingReleases({ libraryEmpty }: { libraryEmpty: boolean }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: releases = [], isPending, isError } = useGetApiReleasesUpcoming({ limit: LIMIT });

  if (isPending || isError) return null;

  if (releases.length === 0) {
    if (libraryEmpty) return null;
    return (
      <section>
        <EmptyState
          icon="calendar_month"
          className={styles.empty}
          title={t('home.upcomingReleases.empty.title')}
          description={t('home.upcomingReleases.empty.description')}
          action={
            <Button variant="secondary" onClick={() => navigate('/collection')}>
              {t('home.upcomingReleases.empty.action')}
            </Button>
          }
        />
      </section>
    );
  }

  return (
    <section>
      <SectionHeader title={t('home.upcomingReleases.title')} />
      <div className={styles.list}>
        {releases.map((release) => (
          <ReleaseRow key={release.id} release={release} onOpen={() => navigate(`/series/${release.seriesId}`)} />
        ))}
      </div>
    </section>
  );
}

function ReleaseRow({ release, onOpen }: { release: UpcomingReleaseDto; onOpen: () => void }) {
  const { t } = useTranslation();
  const volume = releaseVolumeLabel(release, t);

  return (
    <button type="button" onClick={onOpen} className={styles.row}>
      <div
        className={styles.thumb}
        // The cover is a remote image, known only at render time.
        style={release.coverUrl ? { background: `center/cover no-repeat url(${release.coverUrl})` } : undefined}
      />
      <div className={styles.body}>
        <div className={styles.title}>{release.seriesTitle ?? '—'}</div>
        {volume && <div className={styles.volume}>{volume}</div>}
        <div className={styles.meta}>
          <span className={styles.regionBadge}>{regionLabel(release.region, t)}</span>
          <span className={styles.date}>{formatReleaseDate(release, t)}</span>
        </div>
        <div className={styles.source}>{sourceLabel(release, t)}</div>
      </div>
    </button>
  );
}
