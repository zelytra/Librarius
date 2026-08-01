import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';
import styles from './TrustBadge.module.css';

/**
 * The trusted-member badge (#186): shown next to a display name once the server-computed
 * `trusted` flag (V16, #180) is set on that account. Icon and visible text together, never
 * colour alone — the same state-encoding rule the Series volume grid follows
 * (PRODUCT § 4.8) — so a colour-blind reader reads it exactly like everyone else.
 *
 * <p>There is nothing to render for an untrusted account: the caller decides whether to
 * mount this at all, and there is deliberately no "not yet trusted" placeholder (#186).
 * Ships once, next to the caller's own name in Settings, and reusable wherever another
 * member's name starts appearing later — a follow list, an attribution, a profile.
 */
export function TrustBadge({ className }: { className?: string }) {
  const { t } = useTranslation();
  return (
    <span
      className={className ? `${styles.badge} ${className}` : styles.badge}
      title={t('trust.badge.tooltip')}
    >
      <Icon name="workspace_premium" size={12} color="var(--tint-sage-ink)" />
      {t('trust.badge.label')}
    </span>
  );
}
