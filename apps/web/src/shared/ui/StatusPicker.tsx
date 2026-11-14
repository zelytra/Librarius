import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';
import {
  FORMATS,
  KINDS,
  kindLabelKey,
  type BookKind,
  formatLabelKey,
  type BookFormat,
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
  /** Tapping the chosen chip again clears the choice — for an optional family. */
  onClear?: () => void;
}

/** One family of statuses as a row of chips; the chosen one pops when it is picked. */
function ChipGroup<T extends string>({ label, options, value, labelOf, onChange, disabled, onClear }: ChipGroupProps<T>) {
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
                else onClear?.();
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
  format,
  onFormat,
  kind,
  onKind,
}: {
  status: ReadingStatus | undefined;
  ownership: Ownership | undefined;
  onStatus: (status: ReadingStatus) => void;
  onOwnership: (ownership: Ownership) => void;
  disabled?: boolean;
  /** The format of the copy (#447), shown when `onFormat` is given; null when unsaid. */
  format?: BookFormat | null;
  onFormat?: (format: BookFormat | null) => void;
  /** The kind the title is filed under (#454), shown when `onKind` is given. */
  kind?: BookKind;
  onKind?: (kind: BookKind) => void;
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
      {onFormat && (
        <ChipGroup
          label={t('statuses.formatTitle')}
          options={FORMATS}
          value={format ?? undefined}
          labelOf={(id) => t(formatLabelKey(id))}
          onChange={onFormat}
          onClear={() => onFormat(null)}
          disabled={disabled}
        />
      )}
      {onKind && (
        <ChipGroup
          label={t('statuses.kindTitle')}
          options={KINDS}
          value={kind}
          labelOf={(id) => t(kindLabelKey(id))}
          onChange={onKind}
          disabled={disabled}
        />
      )}
    </div>
  );
}
