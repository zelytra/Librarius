import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Screen } from '../../shared/ui/primitives';
import { useTheme } from '../../shared/theme/context';
import { THEMES } from '../../shared/theme/themes';
import { ImportSection } from './ImportSection';
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

      {/* External library import. */}
      <ImportSection />

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

      <div className={styles.version}>{t('settings.version', { version: APP_VERSION })}</div>
    </Screen>
  );
}
