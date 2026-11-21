import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { LEGAL_DOCS, type LegalDoc } from './content';
import styles from './Legal.module.css';

/**
 * The three legal documents (#74), as a row of links: under the Settings list, at the foot of
 * the landing page, on every legal page and in the acceptance request. `current` marks the
 * document being read rather than linking it to itself.
 */
export function LegalLinks({ current, docs = LEGAL_DOCS }: { current?: LegalDoc; docs?: readonly LegalDoc[] }) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t('legal.nav')}>
      <ul className={styles.links}>
        {docs.map((doc) => (
          <li key={doc}>
            <Link
              to={`/legal/${doc}`}
              className={styles.link}
              aria-current={doc === current ? 'page' : undefined}
            >
              {t(`legal.${doc}`)}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
