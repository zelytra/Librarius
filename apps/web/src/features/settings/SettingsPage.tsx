import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { PushSection } from './PushSection';
import { saveAccountPreferences } from '../../shared/accountPreferences';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Button, Screen, Segmented } from '../../shared/ui/primitives';
import { useTheme } from '../../shared/theme/context';
import { useApiAuth } from '../../shared/api';
import { THEMES } from '../../shared/theme/themes';
import { changeLanguage } from '../../i18n';
import { LANGUAGES, activeLanguage } from '../../i18n/languages';
import { ProfileSection } from './ProfileSection';
import { GoalSection } from './GoalSection';
import { BlockedMembersSection } from './BlockedMembersSection';
import { LibraryKindSection } from './LibraryKindSection';
import { HomeLayoutSection } from './HomeLayoutSection';
import { BottomBarSection } from './BottomBarSection';
import { useHighlightHash } from '../../shared/useHighlightHash';
import { Loading } from '../../shared/ui/states';
import { LegalLinks } from '../legal/LegalLinks';
import styles from './SettingsPage.module.css';

// The sheet's previews draw real sheets: their code stays out of Settings until the reading
// category is opened.
const SheetSection = lazy(() => import('./SheetSection').then((m) => ({ default: m.SheetSection })));
// The imports, the export and the two destructive actions: the heaviest category, and the one
// opened least, in a chunk of its own.
const DataCategory = lazy(() => import('./DataCategory').then((m) => ({ default: m.DataCategory })));
// The FAQ (#630) carries its own words: a chunk of its own, read rarely.
const HelpCategory = lazy(() => import('../help/HelpCategory').then((m) => ({ default: m.HelpCategory })));

// Stamped at build time by the image build (`VITE_APP_VERSION`, see apps/web/Dockerfile):
// a released image carries its semantic version, a staging one the commit it was built
// from. Empty on a local `pnpm web:dev`, where nothing has been stamped.
const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'dev';

/**
 * The settings, by category (#546): the single page had grown past a dozen sections. Each
 * category is its own address (`/settings/<id>`), so a link elsewhere in the app can land on
 * the right one.
 */
const SETTINGS_CATEGORIES = [
  { id: 'profile', icon: 'person' },
  { id: 'reading', icon: 'menu_book' },
  { id: 'appearance', icon: 'tune' },
  { id: 'community', icon: 'group' },
  { id: 'data', icon: 'download' },
  { id: 'help', icon: 'info' },
] as const;

type SettingsCategory = (typeof SETTINGS_CATEGORIES)[number]['id'];

const isCategory = (value: string | undefined): value is SettingsCategory =>
  SETTINGS_CATEGORIES.some((c) => c.id === value);

function Block({ children, id }: { children: ReactNode; id?: string }) {
  return (
    <div className={styles.sectionBlock} id={id}>
      {children}
    </div>
  );
}

function ProfileCategory() {
  const { t } = useTranslation();
  const { authed, signOutFully } = useApiAuth();
  return (
    <>
      {/* Identity: display name, language and time zone, persisted to the account (#75). */}
      <Block id="profile">
        <ProfileSection />
      </Block>
      {/* A full sign-out — the Keycloak session included, so the persistent SSO session does
          not sign the user straight back in. */}
      {authed && (
        <Block>
          <h3 className={styles.sectionTitle}>{t('settings.session')}</h3>
          <Button variant="secondary" size="compact" onClick={signOutFully}>
            {t('settings.logout')}
          </Button>
        </Block>
      )}
    </>
  );
}

function ReadingCategory() {
  return (
    <>
      {/* Annual reading goal, which the Home gauge is drawn from. */}
      <Block id="goal">
        <GoalSection />
      </Block>
      <Block id="library-kind">
        <LibraryKindSection />
      </Block>
      <Block id="sheet">
        <Suspense fallback={null}>
          <SheetSection />
        </Suspense>
      </Block>
    </>
  );
}

function AppearanceCategory() {
  const { t } = useTranslation();
  const { theme, setTheme } = useTheme();
  const { authed } = useApiAuth();
  return (
    <>
      <Block id="theme">
        <h3 className={styles.sectionTitle}>{t('settings.theme')}</h3>
        <div className={styles.themes}>
          {THEMES.map((th) => {
            const active = th.id === theme;
            return (
              <button key={th.id} type="button" onClick={() => {
                setTheme(th.id);
                saveAccountPreferences({ theme: th.id });
              }} className={styles.theme} aria-pressed={active}>
                {/* The swatch shows the theme's own background colour. */}
                <div
                  className={`${styles.swatch} ${active ? styles.swatchActive : ''}`}
                  style={{ background: th.swatch }}
                />
                <div className={`${styles.themeLabel} ${active ? styles.themeLabelActive : ''}`}>
                  {t(th.labelKey)}
                </div>
              </button>
            );
          })}
        </div>
      </Block>

      {/* Language. Signed in, it belongs to the profile, which saves it to the account (#75):
          one control, not two that could disagree (#547). Signed out, this device-local
          switcher is the only one. The labels are endonyms, never translated: someone who
          landed on the wrong language has to recognise their own on the screen. */}
      <Block id="language">
        <h3 className={styles.sectionTitle}>{t('settings.language')}</h3>
        {authed ? (
          <Link to="/settings/profile" className={styles.dashboardLink}>
            <Icon name="translate" size={16} color="var(--accent-deep)" />
            {t('settings.languageInProfile')}
          </Link>
        ) : (
          <>
            <p className={styles.sectionIntro}>{t('settings.languageDescription')}</p>
            <Segmented
              options={LANGUAGES.map((language) => ({ id: language.id, label: language.label }))}
              value={activeLanguage()}
              onChange={changeLanguage}
            />
          </>
        )}
      </Block>

      <Block id="home">
        <HomeLayoutSection />
      </Block>

      <Block id="bottom-bar">
        <BottomBarSection />
      </Block>
    </>
  );
}

function CommunityCategory() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <>
      {/* Other readers: finding them, who the reader follows and who follows them (#202). */}
      <Block>
        <h3 className={styles.sectionTitle}>{t('settings.findReaders')}</h3>
        <p className={styles.sectionIntro}>{t('settings.communityDescription')}</p>
        <button type="button" className={styles.dashboardLink} onClick={() => navigate('/people')}>
          <Icon name="search" size={18} color="var(--accent-deep)" />
          {t('people.open')}
        </button>
      </Block>
      {/* Members the caller has blocked, with a way to take it back (#203). */}
      <Block>
        <BlockedMembersSection />
      </Block>
      {/* Push notifications, opt-in one category at a time (#69). */}
      <Block id="push">
        <PushSection />
      </Block>
    </>
  );
}

const CATEGORY_CONTENT: Record<SettingsCategory, ComponentType> = {
  profile: ProfileCategory,
  reading: ReadingCategory,
  appearance: AppearanceCategory,
  community: CommunityCategory,
  data: DataCategory,
  help: HelpCategory,
};

export function SettingsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { category } = useParams();
  const location = useLocation();

  // Back from the list leaves Settings (#591): to the screen before it, or Home when the list
  // was reached from a category opened directly — never into a category again.
  const entered = (location.state as { entry?: boolean } | null)?.entry === true;
  const leave = () => (entered || location.key === 'default' ? navigate('/') : navigate(-1));

  if (category !== undefined && !isCategory(category)) return <Navigate to="/settings" replace />;

  if (category === undefined) {
    return (
      <Screen measure="reading">
        <div className={`sticky-header ${styles.header}`}>
          <button type="button" onClick={leave} aria-label={t('common.back')} className={styles.backButton}>
            <Icon name="arrow_back" size={24} color="var(--ink)" />
          </button>
          <h2 className={styles.title}>{t('settings.title')}</h2>
        </div>
        {/* A list of links, not a landmark: the app's navigation is the bar or the rail. */}
        <div className={styles.sections}>
          <ul className={styles.categories}>
            {SETTINGS_CATEGORIES.map((c) => (
              <li key={c.id}>
                <Link to={`/settings/${c.id}`} state={{ fromList: true }} className={styles.category}>
                  <span className={styles.categoryIcon} aria-hidden="true">
                    <Icon name={c.icon} size={22} color="var(--accent-deep)" />
                  </span>
                  <span className={styles.categoryText}>
                    <span className={styles.categoryTitle}>{t(`settings.categories.${c.id}.title`)}</span>
                    <span className={styles.categoryDescription}>{t(`settings.categories.${c.id}.description`)}</span>
                  </span>
                  <Icon name="chevron_right" size={22} color="var(--faint)" />
                </Link>
              </li>
            ))}
          </ul>
        </div>
        <div className={styles.version}>{t('settings.version', { version: APP_VERSION })}</div>
        <LegalLinks />
      </Screen>
    );
  }

  return <CategoryScreen category={category} />;
}

function CategoryScreen({ category }: { category: SettingsCategory }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  // Opened from the list, back is that list (#591); opened from a link elsewhere, back replaces
  // the category with the list, which then leaves Settings rather than coming back here.
  const fromList = (location.state as { fromList?: boolean } | null)?.fromList === true;
  const back = () => (fromList ? navigate(-1) : navigate('/settings', { replace: true, state: { entry: true } }));
  // A link from elsewhere names the section it is about (#560): `/settings/reading#goal`.
  useHighlightHash();
  const Content = CATEGORY_CONTENT[category];
  return (
    <Screen measure="reading">
      <div className={`sticky-header ${styles.header}`}>
        <button type="button" onClick={back} aria-label={t('settings.backToCategories')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <div>
          <div className={styles.kicker}>{t('settings.title')}</div>
          <h2 className={styles.title}>{t(`settings.categories.${category}.title`)}</h2>
        </div>
      </div>
      {/* Single stacked column at every width, phone included — see `.sections`. */}
      <div className={styles.sections}>
        <Suspense fallback={<Loading />}>
          <Content />
        </Suspense>
      </div>
    </Screen>
  );
}
