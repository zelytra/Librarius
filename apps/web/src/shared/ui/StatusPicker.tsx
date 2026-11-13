import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';
import {
  OWNERSHIPS,
  READING_STATUSES,
  ownershipLabelKey,
  statusLabelKey,
  type Ownership,
  type ReadingStatus,
} from '../statuses';
import styles from './StatusPicker.module.css';

interface ChipGroupProps<T extends string> {
  label: string;
  options: { id: T; icon: string }[];
  value: T | undefined;
  labelOf: (id: T) => string;
  onChange: (id: T) => void;
  disabled?: boolean;
}

/** One family of statuses as a row of chips; the chosen one pops when it is picked. */
function ChipGroup<T extends string>({ label, options, value, labelOf, onChange, disabled }: ChipGroupProps<T>) {
  return (
    <div className={styles.group}>
      <span className={styles.groupLabel}>{label}</span>
      <div className={styles.chips} role="radiogroup" aria-label={label}>
        {options.map((option) => {
          const on = option.id === value;
          return (
            <button
              key={option.id}
              type="button"
              role="radio"
              aria-checked={on}
              disabled={disabled}
              onClick={() => {
                if (!on) onChange(option.id);
              }}
              className={`${styles.chip} ${on ? styles.chipOn : ''}`}
            >
              <Icon name={option.icon} size={17} color={on ? 'var(--on-accent)' : 'var(--muted)'} />
              {labelOf(option.id)}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * The two status families of a title (#411) side by side: the reading status and the
 * ownership. Used on Detail, in the add sheet and on the scan screen.
 */
export function StatusPicker({
  status,
  ownership,
  onStatus,
  onOwnership,
  disabled,
}: {
  status: ReadingStatus | undefined;
  ownership: Ownership | undefined;
  onStatus: (status: ReadingStatus) => void;
  onOwnership: (ownership: Ownership) => void;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  return (
    <div className={styles.picker}>
      <ChipGroup
        label={t('statuses.readingTitle')}
        options={READING_STATUSES}
        value={status}
        labelOf={(id) => t(statusLabelKey(id))}
        onChange={onStatus}
        disabled={disabled}
      />
      <ChipGroup
        label={t('statuses.ownershipTitle')}
        options={OWNERSHIPS}
        value={ownership}
        labelOf={(id) => t(ownershipLabelKey(id))}
        onChange={onOwnership}
        disabled={disabled}
      />
      {ownership && <p className={styles.hint}>{t(`statuses.ownershipHint.${ownership}`)}</p>}
    </div>
  );
}
