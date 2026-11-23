import { useTranslation } from 'react-i18next';
import { useGetApiEditionsIdSaleLinks } from '../../api/generated/librarius';
import { Icon } from '../../shared/ui/Icon';
import styles from './CatalogEdit.module.css';

/**
 * "Où l'acheter" (#619): the stores the edition is known on, each opening its page in a new tab.
 * Nothing at all when no store is known — an empty section would only take room.
 */
export function SaleLinks({ editionId }: { editionId: string }) {
  const { t } = useTranslation();
  const { data } = useGetApiEditionsIdSaleLinks(editionId);
  if (!data?.length) return null;
  return (
    <section className={styles.sale} aria-labelledby={`sale-${editionId}`}>
      <h3 id={`sale-${editionId}`} className={styles.saleTitle}>
        {t('saleLinks.title')}
      </h3>
      <ul className={styles.saleLinks}>
        {data.map((link) => (
          <li key={link.store}>
            <a className={styles.saleLink} href={link.url} target="_blank" rel="noopener noreferrer nofollow">
              <Icon name="shopping_bag" size={16} color="var(--accent)" />
              {t('saleLinks.buyOn', { store: t(`saleLinks.stores.${link.store}`) })}
              <span className={styles.srOnly}>{t('saleLinks.newTab')}</span>
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}
