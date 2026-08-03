import { useNavigate } from 'react-router';
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
import { ImportSection } from './ImportSection';
import { ExportSection } from './ExportSection';
import { BlockedMembersSection } from './BlockedMembersSection';
import { DeleteAccountSection } from './DeleteAccountSection';
import styles from './SettingsPage.module.css';

// Stamped at build time by the image build (`VITE_APP_VERSION`, see apps/web/Dockerfile):
// a released image carries its semantic version, a staging one the commit it was built
// from. Empty on a local `pnpm web:dev`, where nothing has been stamped.
const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'dev';

export function SettingsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { theme, setTheme } = useTheme();
  const { authed, signOutFully } = useApiAuth();

  return (
    <Screen>
      <div className={styles.header}>
        <button
          onClick={() => navigate(-1)}
          aria-label={t('common.back')}
          className={styles.backButton}
        >
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <h2 className={styles.title}>{t('settings.title')}</h2>
      </div>

      {/* Single stacked column at every width, phone included — see `.sections`. */}
      <div className={styles.sections}>
        {/* Identity: display name, language and time zone, persisted to the account (#75). */}
        <div className={styles.sectionBlock}>
          <ProfileSection />
        </div>

        {/* Annual reading goal, which the Home gauge is drawn from. */}
        <div className={styles.sectionBlock}>
          <GoalSection />
        </div>

        {/* Reordering and hiding sections happens on Home itself (#54) — this is only the
            way there for someone who would not otherwise think to look on the dashboard. */}
        <div className={styles.sectionBlock}>
          <h3 className={styles.sectionTitle}>{t('settings.dashboard')}</h3>
          <p className={styles.sectionIntro}>{t('settings.dashboardDescription')}</p>
          <button className={styles.dashboardLink} onClick={() => navigate('/')}>
            <Icon name="tune" size={16} color="var(--accent-deep)" />
            {t('settings.dashboardAction')}
          </button>

          {/* The first-run tour (#76) is skippable and never re-appears on its own once it
              has been — this is the only way back to it, for someone who skipped it too
              fast or wants to point a friend at the import step again. Router state rather
              than a query string: it carries no meaning to bookmark or share. */}
          <button
            className={styles.dashboardLink}
            onClick={() => navigate('/', { state: { showOnboarding: true } })}
          >
            <Icon name="auto_stories" size={16} color="var(--accent-deep)" />
            {t('settings.onboardingAction')}
          </button>
        </div>

        {/* External library import. */}
        <div className={styles.sectionBlock}>
          <ImportSection />
        </div>

        {/* Getting the data back out (GDPR art. 20). */}
        <div className={styles.sectionBlock}>
          <ExportSection />
        </div>

        {/* Appearance: theme switcher (functional). */}
        <div className={styles.sectionBlock}>
          <h3 className={styles.sectionTitle}>{t('settings.appearance')}</h3>
          <div className={styles.themes}>
            {THEMES.map((th) => {
              const active = th.id === theme;
              return (
                <button key={th.id} onClick={() => setTheme(th.id)} className={styles.theme}>
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
        </div>

        {/* Language. The labels are endonyms, never translated: someone who landed on the
            wrong language has to recognise their own on the screen in front of them. */}
        <div className={styles.sectionBlock}>
          <h3 className={styles.sectionTitle}>{t('settings.language')}</h3>
          <p className={styles.sectionIntro}>{t('settings.languageDescription')}</p>
          <div className={styles.languages}>
            <Segmented
              options={LANGUAGES.map((language) => ({ id: language.id, label: language.label }))}
              value={activeLanguage()}
              onChange={changeLanguage}
            />
          </div>
        </div>

        {/* Members the caller has blocked, with a way to take it back (#203). */}
        <div className={styles.sectionBlock}>
          <BlockedMembersSection />
        </div>

        {/* Ending the current session. A full sign-out — the Keycloak session included, so
            the persistent SSO session does not sign the user straight back in. */}
        {authed && (
          <div className={styles.sectionBlock}>
            <h3 className={styles.sectionTitle}>{t('settings.session')}</h3>
            <Button variant="secondary" size="compact" onClick={signOutFully}>
              {t('settings.logout')}
            </Button>
          </div>
        )}

        {/* Last, and behind a confirmation: erasing the account (GDPR art. 17). */}
        <div className={styles.sectionBlock}>
          <DeleteAccountSection />
        </div>
      </div>

      <div className={styles.version}>{t('settings.version', { version: APP_VERSION })}</div>
    </Screen>
  );
}
