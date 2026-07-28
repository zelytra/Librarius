import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { EmptyState, Screen, SectionHeader } from '../../shared/ui/primitives';
import { LoginGate } from '../../shared/LoginGate';
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

function Dashboard() {
  const navigate = useNavigate();
  // The queries run in parallel and are cached independently: coming back to Home after
  // browsing no longer refetches anything. Each shelf asks the server for its own status
  // rather than downloading the collection to filter it here.
  const { data: inProgress } = useGetApiLibrary({ status: 'READING', size: READING_SHELF_SIZE });
  const { data: finished } = useGetApiLibrary({ status: 'READ', size: READ_SHELF_SIZE });
  const { data: stats } = useGetApiStats();
  const { data: upcoming = [] } = useGetApiCatalogUpcoming({ kind: 'MANGA', limit: 5 });

  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });
  const reading = inProgress?.items ?? [];
  const read = finished?.items ?? [];

  // Emptiness comes from the counters, not from a shelf: a library made only of
  // owned-but-unread titles fills neither of the two above.
  const libraryEmpty =
    stats != null && (stats.read ?? 0) + (stats.reading ?? 0) + (stats.toRead ?? 0) === 0;

  const mini = [
    { value: String(stats?.read ?? 0), label: 'lus', tone: styles.miniSage },
    { value: String(stats?.reading ?? 0), label: 'en cours', tone: styles.miniRose },
    { value: String(stats?.toRead ?? 0), label: 'à lire', tone: styles.miniSand },
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
          <SectionHeader title="Reprendre la lecture" action={`${reading.length} en cours`} />
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

      {upcoming.length > 0 && (
        <section>
          <SectionHeader title="Prochaines sorties" />
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
          <p className={styles.footnote}>Dates indicatives (édition d'origine).</p>
        </section>
      )}

      {read.length > 0 && (
        <section>
          <SectionHeader title="Derniers lus" />
          <div className={`scroll-x ${styles.shelf}`}>{read.map(cover)}</div>
        </section>
      )}

      {libraryEmpty && (
        <EmptyState icon="auto_stories" className={styles.empty}>
          Ta bibliothèque est vide. Va sur <strong>Découvrir</strong> pour ajouter tes premiers
          titres.
        </EmptyState>
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
      <LoginGate prompt="Connecte-toi pour retrouver ta bibliothèque et tes lectures.">
        <Dashboard />
      </LoginGate>
    </Screen>
  );
}
