import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { useTheme } from '../../shared/theme/context';
import { THEMES } from '../../shared/theme/themes';

const APP_VERSION = '1.0';

export function SettingsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { theme, setTheme } = useTheme();

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24 }}>
        <button
          onClick={() => navigate(-1)}
          aria-label={t('common.back')}
          style={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            background: 'var(--surface)',
            border: '1px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
          }}
        >
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <h2 style={{ fontSize: 25 }}>{t('settings.title')}</h2>
      </div>

      {/* Apparence : commutateur de thème (fonctionnel). */}
      <h3 style={{ fontSize: 16, margin: '0 0 14px' }}>{t('settings.appearance')}</h3>
      <div style={{ display: 'flex', gap: 12, marginBottom: 26 }}>
        {THEMES.map((th) => {
          const active = th.id === theme;
          return (
            <button
              key={th.id}
              onClick={() => setTheme(th.id)}
              style={{
                flex: 1,
                textAlign: 'center',
                background: 'none',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
              }}
            >
              <div
                style={{
                  height: 54,
                  borderRadius: 14,
                  background: th.swatch,
                  border: active ? '2px solid var(--accent)' : '2px solid transparent',
                  boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.04)',
                }}
              />
              <div
                style={{
                  fontSize: 11,
                  color: active ? 'var(--accent-deep)' : 'var(--muted)',
                  marginTop: 6,
                  fontWeight: active ? 700 : 500,
                }}
              >
                {t(th.labelKey)}
              </div>
            </button>
          );
        })}
      </div>

      <div style={{ textAlign: 'center', fontSize: 11.5, color: 'var(--faint)' }}>
        {t('settings.version', { version: APP_VERSION })}
      </div>
    </div>
  );
}
