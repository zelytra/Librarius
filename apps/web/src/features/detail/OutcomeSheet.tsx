import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import type { CategoryDto, LibraryItemDto } from '../../api/generated/librarius';
import { CategoryChoice } from './CategoryChoice';
import { StarRating } from './StarRating';
import styles from './OutcomeSheet.module.css';

/** The two ways a reading ends, and the only two this sheet is opened for. */
export type Outcome = 'READ' | 'ABANDONED';

/** What the reader chose, as the Detail screen has to write it. */
export interface OutcomeChoice {
  rating?: number;
  categoryId?: string;
}

/** The shelf a title given up on is filed under, and the one that sheet starts from. */
const ABANDON_CODE = 'abandon';

/** The two headings, kept as keys so both live in the locale file like the rest. */
const COPY: Record<Outcome, { title: string; subtitle: string }> = {
  READ: { title: 'detail.outcome.read.title', subtitle: 'detail.outcome.read.subtitle' },
  ABANDONED: {
    title: 'detail.outcome.abandoned.title',
    subtitle: 'detail.outcome.abandoned.subtitle',
  },
};

/**
 * The guided moment at the end of a reading: rate the title, choose where to shelve it.
 *
 * <p>One sheet for the two transitions ([#164](https://github.com/zelytra/Librarius/issues/164)
 * and [#165](https://github.com/zelytra/Librarius/issues/165)), because they are the same
 * screen reached from two buttons — only the heading and the shelf it opens on differ. A
 * title given up on starts on the built-in *Abandon* shelf; one read to the end starts
 * wherever it was already filed, which is usually nowhere.
 *
 * <p>**Nothing here is written until the sheet is confirmed.** The pre-selected shelf is a
 * suggestion the reader can override or clear, so it has to be a draft — and a rating half
 * given while the reader is still deciding is not a rating. The transition itself was
 * recorded before the sheet opened, which is what makes both choices genuinely optional:
 * dismissing it, or closing the tab on it, still leaves the title in the state the button
 * promised.
 *
 * <p>It writes no reading position either, and must not: an abandonment is the one status
 * that keeps the page the reader stopped on, and a second `PUT /progress` sent from here
 * would be exactly how that page gets rounded up to 100 % and lost.
 */
export function OutcomeSheet({
  outcome,
  item,
  categories,
  onConfirm,
  onSkip,
}: {
  outcome: Outcome;
  item: LibraryItemDto;
  categories: CategoryDto[];
  onConfirm: (choice: OutcomeChoice) => void;
  onSkip: () => void;
}) {
  const { t } = useTranslation();
  const headingId = useId();
  const panel = useRef<HTMLDivElement>(null);

  // `rankCode` is what the item carries; the picker and the API both speak identifiers.
  const filedUnder = categories.find((c) => c.code === item.rankCode)?.id;
  const abandonShelf = categories.find((c) => c.code === ABANDON_CODE)?.id;

  // Seeded once, because the sheet is mounted only while it is open: every opening starts
  // from what the title carries at that moment rather than from a stale draft.
  const [rating, setRating] = useState<number | undefined>(item.rating ?? undefined);
  const [categoryId, setCategoryId] = useState<string | undefined>(
    outcome === 'ABANDONED' ? (abandonShelf ?? filedUnder) : filedUnder,
  );

  // Escape is the way out of any dialog, and this one has nothing to lose by being closed.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onSkip();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onSkip]);

  // The sheet covers the screen it opened over, so the reading cursor has to follow it in.
  useEffect(() => {
    panel.current?.focus();
  }, []);

  return (
    // Clicking beside a sheet closes it, the way it does anywhere else. The panel stops the
    // click so choosing a star does not dismiss the very sheet it was chosen in.
    <div className={styles.scrim} onClick={onSkip}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        tabIndex={-1}
        className={styles.panel}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={onSkip}
          aria-label={t('detail.outcome.close')}
          className={styles.close}
        >
          <Icon name="close" size={20} color="var(--ink-soft)" />
        </button>

        <h3 id={headingId} className={styles.title}>
          {t(COPY[outcome].title)}
        </h3>
        <p className={styles.subtitle}>
          {t(COPY[outcome].subtitle, { title: item.book?.title ?? '' })}
        </p>

        <div className={styles.block}>
          <h4 className={styles.sectionTitle}>{t('detail.review.title')}</h4>
          <StarRating value={rating} onRate={setRating} />
        </div>

        <div className={styles.block}>
          <h4 className={styles.sectionTitle}>{t('detail.ranking')}</h4>
          <CategoryChoice categories={categories} selectedId={categoryId} onSelect={setCategoryId} />
          <p className={styles.hint}>{t('detail.outcome.shelfHint')}</p>
        </div>

        <div className={styles.actions}>
          <Button variant="primary" size="block" onClick={() => onConfirm({ rating, categoryId })}>
            {t('detail.outcome.confirm')}
          </Button>
          <Button variant="ghost" size="block" onClick={onSkip}>
            {t('detail.outcome.skip')}
          </Button>
        </div>
      </div>
    </div>
  );
}
