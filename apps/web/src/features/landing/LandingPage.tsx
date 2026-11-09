import { Navigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useApiAuth, useSignIn } from '../../shared/api';
import { Button } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import styles from './LandingPage.module.css';

/**
 * The public page a stranger meets, at `/welcome` — the shop window of the application.
 *
 * It states the argument before asking for anything, and shows the product rather than
 * describing it: the hero holds a window drawn from the very components the application
 * renders (cover tiles, a series block, the reading card), and each feature row carries the
 * picture of the screen it talks about. Every claim is a shipped feature; nothing here shows
 * a screen that does not exist.
 *
 * The titles in the pictures are invented on purpose: naming a publisher's run on a public
 * page would claim an endorsement nobody gave.
 *
 * It is code split (see `App.tsx`): a marketing page is read once and never again, so it has
 * no business sitting in the payload every returning reader waits on. Its copy is the
 * exception — it lives in the eager locale files — which is why it stays short.
 */

/** The state a volume of the illustrated run is in. Mirrors the real Series screen. */
type VolumeState = 'read' | 'owned' | 'missing';

/** The illustrated run: twelve volumes, in the three states the Series screen draws. */
const RUN = { owned: 12, total: 105 };
const VOLUMES: VolumeState[] = [
  'read', 'read', 'read', 'read', 'read', 'owned',
  'owned', 'missing', 'owned', 'owned', 'missing', 'owned',
];

const VOLUME_ICONS: Record<VolumeState, string> = {
  read: 'check_circle',
  owned: 'book_2',
  missing: 'priority_high',
};

/** The covers of the window in the hero — invented titles, drawn as the collection draws them. */
const SHELF = ['shelf1', 'shelf2', 'shelf3', 'shelf4', 'shelf5'] as const;

/** A year of reading, books per month, for the chart of the reading row. */
const MONTHS = [2, 3, 1, 4, 3, 5, 6, 2, 4, 3, 5, 4];

/** The three needs of PRODUCT § 1, in the order they are stated there. */
const NEEDS = [
  { key: 'own', icon: 'library_books' },
  { key: 'progress', icon: 'menu_book' },
  { key: 'next', icon: 'calendar_month' },
] as const;

/** The three readers of PRODUCT § 2, described by what they do rather than by name. */
const AUDIENCE = [
  { key: 'manga', icon: 'collections_bookmark' },
  { key: 'novels', icon: 'auto_stories' },
  { key: 'import', icon: 'upload_file' },
] as const;

/** What is online today. Every line is a ✅ in PRODUCT — see the list there before adding. */
const FEATURES = [
  { key: 'collection', icon: 'collections_bookmark' },
  { key: 'discover', icon: 'search' },
  { key: 'wishlist', icon: 'shopping_bag' },
  { key: 'stats', icon: 'insights' },
  { key: 'import', icon: 'upload_file' },
  { key: 'export', icon: 'download' },
  { key: 'themes', icon: 'visibility' },
] as const;

/** Where a library can come from, as the Settings import offers it. */
const SOURCES = ['booknode', 'babelio', 'csv'] as const;

/**
 * Sign in / create an account.
 *
 * One button rather than two, and its label names both halves, because that is what the
 * destination offers: sign-up is open on the realm, and Keycloak's own page carries the
 * registration link under the form. `prompt=create` is not honoured by the Keycloak this
 * project runs, so a second button would point at the same screen.
 */
function CallToAction({ onClick, disabled, size = 'lg' }: {
  onClick: () => void;
  disabled: boolean;
  size?: 'lg' | 'compact';
}) {
  const { t } = useTranslation();
  return (
    <Button variant="primary" size={size} onClick={onClick} disabled={disabled} className={styles.cta}>
      <Icon name="login" size={20} fill color="var(--on-accent)" />
      {t('landing.cta.label')}
    </Button>
  );
}

/**
 * The application, as a window: the collection grouped by series and the book being read.
 * Drawn from the components the screens use, so it cannot drift from what a reader gets.
 */
function ProductWindow() {
  const { t } = useTranslation();
  return (
    <div className={styles.window} role="img" aria-label={t('landing.mock.alt')}>
      <div className={styles.windowBar} aria-hidden="true">
        <span className={styles.dot} />
        <span className={styles.dot} />
        <span className={styles.dot} />
        <span className={styles.windowTitle}>{t('landing.mock.collection')}</span>
      </div>
      <div className={styles.windowBody} aria-hidden="true">
        <div className={styles.shelf}>
          <div className={styles.seriesTile}>
            <div className={styles.seriesSheets} />
            <Cover variant="tile" title={t('landing.mock.series')} tag={t('series.block.tag')} caption={null}>
              <span className={styles.seriesBadge}>{t('landing.mock.seriesBadge')}</span>
              <span className={styles.seriesTrack}>
                <span className={styles.seriesFill} />
              </span>
            </Cover>
          </div>
          {SHELF.map((key) => (
            <Cover key={key} variant="tile" title={t(`landing.mock.${key}`)} tag={t('collection.tag.book')} caption={null} />
          ))}
        </div>
        <div className={styles.nowCard}>
          <div className={styles.nowCover}>
            <Cover variant="tile" title={t('landing.mock.now')} caption={null} />
          </div>
          <div className={styles.nowBody}>
            <span className={styles.nowLabel}>{t('home.showcase.now')}</span>
            <span className={styles.nowTitle}>{t('landing.mock.now')}</span>
            <span className={styles.nowTrack}>
              <span className={styles.nowFill} />
            </span>
            <span className={styles.nowMeta}>{t('landing.mock.nowProgress')}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export function LandingPage() {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const { signIn, pending, failed } = useSignIn();

  // A reader who already has a library has no use for the pitch: they are sent to it
  // instead. `replace`, so the back button leaves the site rather than bouncing between
  // the two.
  if (authed) return <Navigate to="/" replace />;

  const signInError = failed && (
    <p className={styles.signInError} role="alert">
      {t('auth.unavailable')}
    </p>
  );

  return (
    <div className={styles.page}>
      <nav className={styles.nav}>
        <p className={styles.brand}>
          <Icon name="auto_stories" size={22} color="var(--accent-deep)" />
          {t('app.name')}
        </p>
        <button type="button" onClick={signIn} disabled={pending} className={styles.navSignIn}>
          {t('landing.nav.signIn')}
        </button>
      </nav>

      <header className={styles.hero}>
        <div className={styles.heroText}>
          <p className={styles.eyebrow}>{t('landing.hero.eyebrow')}</p>
          <h1 className={styles.title}>{t('landing.hero.title')}</h1>
          <p className={styles.lead}>{t('landing.hero.lead')}</p>
          <div className={styles.heroActions}>
            <CallToAction onClick={signIn} disabled={pending} />
            <a href="#how" className={styles.secondary}>
              {t('landing.hero.how')}
              <Icon name="arrow_forward" size={18} color="var(--accent-deep)" />
            </a>
          </div>
          {signInError}
          <p className={styles.note}>{t('landing.cta.note')}</p>
          {/* Staging, not production: the data on this instance is disposable, and a
              visitor about to type in four hundred volumes deserves to know before they
              start. The line goes when production opens —
              https://github.com/zelytra/Librarius/issues/103. */}
          <p className={styles.beta}>
            <Icon name="flag" size={16} color="var(--tint-clay-ink)" />
            <span>{t('landing.cta.beta')}</span>
          </p>
        </div>
        <ProductWindow />
      </header>

      <main className={styles.inner}>
        <section className={styles.section} id="how">
          <h2 className={styles.sectionTitle}>{t('landing.needs.title')}</h2>
          <div className={styles.cards}>
            {NEEDS.map(({ key, icon }) => (
              <article key={key} className={styles.card}>
                <span className={styles.cardIcon}>
                  <Icon name={icon} size={22} color="var(--accent-deep)" />
                </span>
                <h3 className={styles.cardTitle}>{t(`landing.needs.${key}.title`)}</h3>
                <p className={styles.cardBody}>{t(`landing.needs.${key}.body`)}</p>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.row}>
          <div className={styles.rowText}>
            <h2 className={styles.sectionTitle}>{t('landing.series.title')}</h2>
            <p className={styles.sectionLead}>{t('landing.series.body')}</p>
          </div>
          <div className={styles.run}>
            <div className={styles.runHeader}>
              <span className={styles.runCount}>
                {t('landing.series.progress', { owned: RUN.owned, total: RUN.total })}
              </span>
              <span className={styles.runBadge}>{t('landing.series.incomplete')}</span>
            </div>
            {/* One image as far as assistive technology is concerned: twelve tiles read
                out one by one would be noise, and the caption already says what it shows. */}
            <div className={styles.volumes} role="img" aria-label={t('landing.series.alt')}>
              {VOLUMES.map((state, index) => (
                <span key={index} className={`${styles.volume} ${styles[state]}`} aria-hidden="true">
                  <Icon name={VOLUME_ICONS[state]} size={14} />
                  {index + 1}
                </span>
              ))}
            </div>
            <ul className={styles.legend}>
              {(['read', 'owned', 'missing'] as const).map((state) => (
                <li key={state} className={styles.legendItem}>
                  <span className={`${styles.swatch} ${styles[state]}`} aria-hidden="true" />
                  {t(`landing.series.states.${state}`)}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className={`${styles.row} ${styles.rowReverse}`}>
          <div className={styles.rowText}>
            <h2 className={styles.sectionTitle}>{t('landing.reading.title')}</h2>
            <p className={styles.sectionLead}>{t('landing.reading.body')}</p>
          </div>
          <div className={styles.panel} role="img" aria-label={t('landing.reading.alt')}>
            <div className={styles.goalHead} aria-hidden="true">
              <span className={styles.goalTitle}>{t('landing.reading.goal')}</span>
              <span className={styles.goalValue}>{t('landing.reading.goalValue')}</span>
            </div>
            <span className={styles.goalTrack} aria-hidden="true">
              <span className={styles.goalFill} />
            </span>
            <div className={styles.chart} aria-hidden="true">
              {MONTHS.map((books, index) => (
                // The height is the value itself: it can only be inline.
                <span key={index} className={styles.bar} style={{ height: `${books * 14}%` }} />
              ))}
            </div>
          </div>
        </section>

        <section className={styles.row}>
          <div className={styles.rowText}>
            <h2 className={styles.sectionTitle}>{t('landing.import.title')}</h2>
            <p className={styles.sectionLead}>{t('landing.import.body')}</p>
          </div>
          <div className={styles.panel}>
            <div className={styles.sources}>
              {SOURCES.map((source) => (
                <span key={source} className={styles.source}>
                  {t(`landing.import.sources.${source}`)}
                </span>
              ))}
            </div>
            <ol className={styles.steps}>
              {(['step1', 'step2', 'step3'] as const).map((step, index) => (
                <li key={step} className={styles.step}>
                  <span className={styles.stepNumber}>{index + 1}</span>
                  {t(`landing.import.${step}`)}
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t('landing.audience.title')}</h2>
          <div className={styles.cards}>
            {AUDIENCE.map(({ key, icon }) => (
              <article key={key} className={styles.card}>
                <span className={styles.cardIcon}>
                  <Icon name={icon} size={22} color="var(--accent-deep)" />
                </span>
                <h3 className={styles.cardTitle}>{t(`landing.audience.${key}.title`)}</h3>
                <p className={styles.cardBody}>{t(`landing.audience.${key}.body`)}</p>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t('landing.features.title')}</h2>
          <ul className={styles.features}>
            {FEATURES.map(({ key, icon }) => (
              <li key={key} className={styles.feature}>
                <Icon name={icon} size={18} color="var(--accent-deep)" />
                {t(`landing.features.${key}`)}
              </li>
            ))}
          </ul>
        </section>

        <footer className={styles.closing}>
          <h2 className={styles.closingTitle}>{t('landing.closing.title')}</h2>
          <p className={styles.closingBody}>{t('landing.closing.body')}</p>
          <CallToAction onClick={signIn} disabled={pending} />
          {signInError}
        </footer>

        <p className={styles.footnote}>{t('landing.footer', { name: t('app.name') })}</p>
      </main>
    </div>
  );
}
