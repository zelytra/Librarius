import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';

/**
 * Écran en attente de contenu (branché dans les PR #6–#8). Affiche le titre
 * traduit pour matérialiser la navigation et le design system.
 */
export function Placeholder({ titleKey }: { titleKey: string }) {
  const { t } = useTranslation();
  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 24px' }}>{t(titleKey)}</h2>
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
