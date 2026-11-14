import { useTranslation } from 'react-i18next';
import type { Kind } from '../../api/generated/librarius';
import { KINDS } from '../kinds';
import { Chip } from './primitives';
import styles from './KindFilter.module.css';

/**
 * Narrows a list to books, manga or comics (#458), like the library's own switch: the to-read
 * pile and the wishlist offer it too, with "Tout" first since a pile mixes kinds.
 */
export function KindFilter({ value, onChange }: { value: Kind | undefined; onChange: (kind: Kind | undefined) => void }) {
  const { t } = useTranslation();
  return (
    <div className={`scroll-x ${styles.row}`} role="group" aria-label={t('kindFilter.label')}>
      <Chip selected={!value} onClick={() => onChange(undefined)}>
        {t('kindFilter.all')}
      </Chip>
      {KINDS.map((k) => (
        <Chip key={k.id} selected={value === k.id} onClick={() => onChange(k.id)}>
          {t(k.labelKey)}
        </Chip>
      ))}
    </div>
  );
}
