import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';

/**
 * Accueil — en-tête fidèle à la maquette (date, salutation, accès réglages).
 * Le contenu du tableau de bord est branché en PR #8.
 */
export function HomePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const today = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
    .format(new Date());

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          margin: '6px 0 22px',
        }}
      >
        <div>
          <div
            style={{ fontSize: 13, color: 'var(--faint)', fontWeight: 500, textTransform: 'capitalize' }}
          >
            {today}
          </div>
          <div style={{ fontFamily: "'Newsreader', serif", fontSize: 27, fontWeight: 500, marginTop: 2 }}>
            {t('home.greeting')}
          </div>
        </div>
        <button
          onClick={() => navigate('/settings')}
          aria-label={t('settings.title')}
          style={{
            width: 46,
            height: 46,
            border: 'none',
            borderRadius: '50%',
            background: 'linear-gradient(135deg,#c9b8d4,#9aab92)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px -3px rgba(122,143,115,0.5)',
          }}
        >
          <Icon name="settings" size={22} color="#fff" />
        </button>
      </div>

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 12,
          textAlign: 'center',
          color: 'var(--faint)',
          padding: '60px 24px',
        }}
      >
        <Icon name="auto_stories" size={42} />
        <p style={{ fontSize: 13.5, lineHeight: 1.5, margin: 0 }}>{t('common.comingSoon')}</p>
      </div>
    </div>
  );
}
