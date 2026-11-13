import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { activeLanguage } from '../../i18n/languages';
import { useGetApiStatsValue } from '../../api/generated/librarius';
import styles from './StatsPage.module.css';

/**
 * What the library and the wishlist are worth (#421), at the publisher's price of a new copy
 * — in France the fixed book price, read from the BnF record. Each figure says how many of the
 * titles it covers carry a price, since a title with none contributes nothing.
 */
export function ValuePanel() {
  const { t } = useTranslation();
  const { data } = useGetApiStatsValue();
  if (!data) return null;
  const money = (value: number | undefined) =>
    (value ?? 0).toLocaleString(activeLanguage(), {
      style: 'currency',
      currency: data.currency ?? 'EUR',
      maximumFractionDigits: 0,
    });
  const owned = data.libraryOwned ?? 0;
  const wishes = data.wishlistCount ?? 0;

  return (
    <div className={styles.panel}>
      <div className={styles.panelTitle}>{t('stats.value.title')}</div>
      <div className={styles.valueRow}>
        <div className={styles.valueCell}>
          <Icon name="collections_bookmark" size={20} color="var(--accent-deep)" />
          <div className={styles.valueAmount}>{money(data.library)}</div>
          <div className={styles.valueLabel}>{t('stats.value.library')}</div>
          <div className={styles.valueHint}>
            {t('stats.value.priced', { priced: data.libraryPriced ?? 0, count: owned })}
          </div>
        </div>
        <div className={styles.valueCell}>
          <Icon name="favorite" size={20} color="var(--rose)" />
          <div className={styles.valueAmount}>{money(data.wishlist)}</div>
          <div className={styles.valueLabel}>{t('stats.value.wishlist')}</div>
          <div className={styles.valueHint}>
            {t('stats.value.priced', { priced: data.wishlistPriced ?? 0, count: wishes })}
          </div>
        </div>
      </div>
      <p className={styles.panelEmpty}>{t('stats.value.source')}</p>
    </div>
  );
}
