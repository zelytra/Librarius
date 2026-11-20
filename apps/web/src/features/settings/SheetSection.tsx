import { useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { ReadingSheet } from '../sheet/ReadingSheetPage';
import { SHEET_THEMES, readSheetTheme, storeSheetTheme, type SheetTheme } from '../sheet/readingSheet';
import type { LibraryItemDto } from '../../api/generated/librarius';
import styles from './SheetSection.module.css';

/**
 * The reading sheet's look (#550): each theme is shown as a miniature of the sheet itself, so
 * the choice is made on what will be printed rather than on a name. The blank sheet, to fill
 * in by hand, is printed from here.
 */
export function SheetSection() {
  const { t } = useTranslation();
  const [theme, setTheme] = useState<SheetTheme>(readSheetTheme);
  const pick = (next: SheetTheme) => {
    setTheme(next);
    storeSheetTheme(next);
  };
  const sample: LibraryItemDto = {
    id: 'sample',
    status: 'READ',
    rating: 4,
    review: t('settings.sheet.sampleReview'),
    book: { kind: 'BOOK', title: t('settings.sheet.sampleTitle'), authors: t('settings.sheet.sampleAuthor') },
  };

  return (
    <>
      <h3 className={styles.title} id="settings-sheet-theme">{t('settings.sheet.title')}</h3>
      <p className={styles.intro}>{t('settings.sheet.description')}</p>
      <div className={styles.themes} role="group" aria-labelledby="settings-sheet-theme">
        {SHEET_THEMES.map((id) => (
          <button
            key={id}
            type="button"
            className={styles.theme}
            aria-pressed={theme === id}
            onClick={() => pick(id)}
          >
            {/* A real sheet, drawn small: what the reader picks is what prints. */}
            <span className={styles.preview} aria-hidden="true" inert>
              <span className={styles.previewInner}>
                <ReadingSheet item={sample} options={{ format: 'A6', rings: false, withCover: false }} theme={id} />
              </span>
            </span>
            <span className={styles.label}>{t(`settings.sheet.themes.${id}`)}</span>
          </button>
        ))}
      </div>
      <Link to="/sheet/blank" className={styles.blank}>
        <Icon name="print" size={18} color="var(--accent-deep)" />
        {t('settings.sheet.blank')}
      </Link>
    </>
  );
}
