import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Screen, Segmented } from '../../shared/ui/primitives';
import { useTheme } from '../../shared/theme/context';
import { THEMES } from '../../shared/theme/themes';
import { changeLanguage } from '../../i18n';
import { LANGUAGES, activeLanguage } from '../../i18n/languages';
import { GoalSection } from './GoalSection';
import { ImportSection } from './ImportSection';
import { ExportSection } from './ExportSection';
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

      {/* Annual reading goal, which the Home gauge is drawn from. */}
      <GoalSection />

      {/* Reordering and hiding sections happens on Home itself (#54) — this is only the
          way there for someone who would not otherwise think to look on the dashboard. */}
      <h3 className={styles.sectionTitle}>{t('settings.dashboard')}</h3>
      <p className={styles.sectionIntro}>{t('settings.dashboardDescription')}</p>
      <button className={styles.dashboardLink} onClick={() => navigate('/')}>
        <Icon name="tune" size={16} color="var(--accent-deep)" />
        {t('settings.dashboardAction')}
      </button>

      {/* External library import. */}
      <ImportSection />

      {/* Getting the data back out (GDPR art. 20). */}
      <ExportSection />

      {/* Appearance: theme switcher (functional). */}
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

      {/* Language. The labels are endonyms, never translated: someone who landed on the
          wrong language has to recognise their own on the screen in front of them. */}
      <h3 className={styles.sectionTitle}>{t('settings.language')}</h3>
      <p className={styles.sectionIntro}>{t('settings.languageDescription')}</p>
      <div className={styles.languages}>
        <Segmented
          options={LANGUAGES.map((language) => ({ id: language.id, label: language.label }))}
          value={activeLanguage()}
          onChange={changeLanguage}
        />
      </div>

      {/* Last, and behind a confirmation: erasing the account (GDPR art. 17). */}
      <DeleteAccountSection />

      <div className={styles.version}>{t('settings.version', { version: APP_VERSION })}</div>
    </Screen>
  );
}
