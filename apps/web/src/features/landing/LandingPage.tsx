import { Navigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useApiAuth } from '../../shared/api';
import { Button } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import styles from './LandingPage.module.css';

/**
 * The public page a stranger meets, at `/welcome`.
 *
 * Until now the site opened straight onto a sign-in gate: pretty since #170, but still a
 * wall — nothing said what the application is or why it exists, so the only way to find
 * out was to create an account first. This page states the argument before asking for
 * anything, and every claim on it is a feature marked ✅ in `.claude/docs/PRODUCT.md`.
 * Nothing here describes something still to build, and nothing shows a screenshot of a
 * screen that does not exist.
 *
 * It is deliberately code split (see `App.tsx`): a marketing page is read once and never
 * again, so it has no business sitting in the payload every returning reader waits on.
 * The copy is the one exception — it lives in the locale files, which are eager — and it
 * is the reason the page is written short.
 */

/** The state a volume of the illustrated run is in. Mirrors the real Series screen. */
type VolumeState = 'read' | 'owned' | 'missing';

/**
 * The twelve volumes drawn under § "a series is not a pile of books", and the figures
 * printed above them.
 *
 * They are an illustration, labelled as one — not a screenshot, and not a real series:
 * naming a publisher's run on a public page would claim an endorsement nobody gave. What
 * the drawing does reproduce faithfully is the vocabulary of the shipped Series screen
 * (§ 4.8): the same three states, told apart by a fill, an icon *and* an outline, so the
 * illustration stays readable for a colour-blind reader like the screen it stands for.
 */
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

/**
 * Sign in / create an account.
 *
 * One button rather than two, and its label names both halves, because that is what the
 * destination actually offers: sign-up is open on the realm, and Keycloak's own page
 * carries the registration link under the form. Opening the registration form *directly*
 * would need the `registrations` endpoint — `prompt=create` is not honoured by the
 * Keycloak this project runs (25.0.6, checked against the deployed realm) — and
 * `react-oidc-context` gives no way to swap the authorization endpoint for one call. A
 * second button pointing at the same screen would be a lie told twice.
 */
function CallToAction({ onClick }: { onClick: () => void }) {
  const { t } = useTranslation();
  return (
    <Button variant="primary" size="lg" onClick={onClick} className={styles.cta}>
      <Icon name="login" size={20} fill color="var(--on-accent)" />
      {t('landing.cta.label')}
    </Button>
  );
}

export function LandingPage() {
  const { t } = useTranslation();
  const { authed, login } = useApiAuth();

  // A reader who already has a library has no use for the pitch: they are sent to it
  // instead. `replace`, so the back button leaves the site rather than bouncing between
  // the two. Nothing waits on `loading` here — while the session resolves `authed` is
  // false, and the overwhelmingly common visitor at this URL is the stranger this page
  // was written for, who would otherwise be shown a spinner before the argument.
  if (authed) return <Navigate to="/" replace />;

  return (
    <div className={styles.page}>
      <div className={styles.inner}>
        <header className={styles.hero}>
          <p className={styles.brand}>
            <Icon name="auto_stories" size={22} color="var(--accent-deep)" />
            {t('app.name')}
          </p>
          <h1 className={styles.title}>{t('landing.hero.title')}</h1>
          <p className={styles.lead}>{t('landing.hero.lead')}</p>
          <CallToAction onClick={login} />
          <p className={styles.note}>{t('landing.cta.note')}</p>
          {/* Staging, not production: the data on this instance is disposable, and a
              visitor about to type in four hundred volumes deserves to know before they
              start rather than after a reset. The line goes when production opens —
              https://github.com/zelytra/Librarius/issues/103. */}
          <p className={styles.beta}>
            <Icon name="flag" size={16} color="var(--tint-clay-ink)" />
            <span>{t('landing.cta.beta')}</span>
          </p>
        </header>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t('landing.needs.title')}</h2>
          <div className={styles.cards}>
            {NEEDS.map(({ key, icon }) => (
              <article key={key} className={styles.card}>
                <Icon name={icon} size={24} color="var(--accent-deep)" />
                <h3 className={styles.cardTitle}>{t(`landing.needs.${key}.title`)}</h3>
                <p className={styles.cardBody}>{t(`landing.needs.${key}.body`)}</p>
              </article>
            ))}
          </div>
        </section>

        <section className={`${styles.section} ${styles.differentiator}`}>
          <h2 className={styles.sectionTitle}>{t('landing.series.title')}</h2>
          <p className={styles.sectionLead}>{t('landing.series.body')}</p>

          <div className={styles.run}>
            <div className={styles.runHeader}>
              <span className={styles.runCount}>
                {t('landing.series.progress', { owned: RUN.owned, total: RUN.total })}
              </span>
              <span className={styles.runBadge}>{t('landing.series.incomplete')}</span>
            </div>
            {/* One image as far as assistive technology is concerned: twelve tiles read
                out one by one would be noise, and the caption already says what the
                drawing shows. */}
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

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t('landing.audience.title')}</h2>
          <div className={styles.cards}>
            {AUDIENCE.map(({ key, icon }) => (
              <article key={key} className={styles.card}>
                <Icon name={icon} size={24} color="var(--accent-deep)" />
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
          <CallToAction onClick={login} />
        </footer>
      </div>
    </div>
  );
}
