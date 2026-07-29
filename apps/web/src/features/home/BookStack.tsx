import { useTranslation } from 'react-i18next';
import { SectionHeader } from '../../shared/ui/primitives';
import { activeLanguage } from '../../i18n/languages';
import type { StatsDto } from '../../api/generated/librarius';
import { SHEET_THICKNESS_MM, formatPaperHeight, stackSpines } from './readingStack';
import styles from './BookStack.module.css';

/**
 * The stack of everything the user has read (#181).
 *
 * <p>Drawn from the design tokens rather than shipped as a picture: five palettes and two
 * locales make a raster wrong on arrival, and an SVG or a drawing library would land in the
 * initial payload — Home is the one screen that is not code split. So a spine is a `<span>`
 * with a width, a tint and a radius, and the whole variation between them cycles through
 * `:nth-child` in the stylesheet: no per-element inline style, and nothing in JavaScript
 * beyond how many to render.
 *
 * <p>Assistive technology gets a text equivalent carrying the figures, not a pile of
 * anonymous rectangles — the same treatment the landing page's run of volumes got.
 *
 * <p>Nothing at all on a library with nothing read yet — heading included, which is why the
 * section is rendered here rather than around this component: the dashboard hides its empty
 * shelves rather than drawing them empty, and a stack of zero books is a caption under a
 * blank.
 */
export function BookStack({ stats }: { stats: StatsDto | undefined }) {
  const { t } = useTranslation();

  const booksRead = stats?.read ?? 0;
  const pagesRead = stats?.pagesRead ?? 0;
  if (booksRead <= 0) return null;

  // The figures are grouped the way the language in force groups them — « 12 480 » against
  // "12,480" — so `count` is handed over only to pick the plural form, never printed.
  const spines = stackSpines(booksRead);
  const height = formatPaperHeight(pagesRead);
  const books = booksRead.toLocaleString(activeLanguage());
  const pages = pagesRead.toLocaleString(activeLanguage());
  const thickness = SHEET_THICKNESS_MM.toLocaleString(activeLanguage());

  return (
    <section>
      <SectionHeader title={t('home.bookStack.title')} />
      <div className={styles.card}>
        {/* One image as far as assistive technology is concerned: a dozen identical
            rectangles read out one by one would be noise, and the label carries the figures
            the drawing is standing in for. */}
        <div
          className={styles.stack}
          role="img"
          aria-label={t('home.bookStack.alt', { books: booksRead, pages, height })}
        >
          {Array.from({ length: spines }, (_, index) => (
            <span key={index} className={styles.spine} aria-hidden="true" />
          ))}
        </div>

        <div className={styles.figures}>
          <p className={styles.books}>{t('home.bookStack.books', { count: booksRead, books })}</p>
          <p className={styles.pages}>{t('home.bookStack.pages', { count: pagesRead, pages })}</p>
          <p className={styles.height}>{t('home.bookStack.height', { height })}</p>
          {/* The conversion is an estimate and says so, the way a provider's release date is
              captioned as indicative rather than presented as a fact (PRODUCT.md § 6.5). */}
          <p className={styles.estimate}>{t('home.bookStack.estimate', { thickness })}</p>
        </div>
      </div>
    </section>
  );
}
