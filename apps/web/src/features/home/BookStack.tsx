import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Modal } from '../../shared/ui/Modal';
import { SectionHeader } from '../../shared/ui/primitives';
import { activeLanguage } from '../../i18n/languages';
import { pagesRead } from '../../shared/pagesRead';
import type { StatsDto } from '../../api/generated/librarius';
import { SHEET_THICKNESS_MM, formatPaperHeight, stackSpines } from './readingStack';
import { formatDistance, skyPosition } from './skyAltitude';
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

  const [detailsOpen, setDetailsOpen] = useState(false);
  const booksRead = stats?.read ?? 0;
  // The exact sum, or — while lengths are being filled in — the estimate the pile is drawn from.
  const estimate = pagesRead(stats);
  const pagesTotal = estimate.value;
  // How many of the books read those pages come from. An imported library starts out with
  // almost no lengths — the catalog fills them in over the following hours — and a bare
  // "0 pages · 0 mm" under two thousand books read as broken rather than incomplete.
  const withLength = stats?.readWithPageCount ?? booksRead;
  if (booksRead <= 0) return null;
  const known = withLength.toLocaleString(activeLanguage());

  // The figures are grouped the way the language in force groups them — « 12 480 » against
  // "12,480" — so `count` is handed over only to pick the plural form, never printed.
  const spines = stackSpines(booksRead);
  const height = formatPaperHeight(pagesTotal);
  const books = booksRead.toLocaleString(activeLanguage());
  const pages = (estimate.estimated ? '≈ ' : '') + pagesTotal.toLocaleString(activeLanguage());
  const thickness = SHEET_THICKNESS_MM.toLocaleString(activeLanguage());
  // The sky behind the pile (#415): how high it would reach, on a story scale.
  const sky = skyPosition(pagesTotal);
  const units = { au: t('home.sky.units.au'), ly: t('home.sky.units.ly'), mly: t('home.sky.units.mly') };
  const reached = t(`home.sky.scenes.${sky.current.scene}`);
  const distance = formatDistance(sky.km, activeLanguage(), units);
  const nextLabel = sky.next
    ? t('home.sky.next', {
        place: t(`home.sky.scenes.${sky.next.scene}`),
        count: sky.pagesToNext,
        pages: sky.pagesToNext.toLocaleString(activeLanguage()),
      })
    : '';

  return (
    <section>
      <SectionHeader title={t('home.bookStack.title')} />
      <div
        className={styles.card}
        data-scene={sky.current.scene}
        // The blend towards the next milestone is the one value computed at render time.
        style={{ ['--sky-progress' as string]: sky.progress.toFixed(3) }}
      >
        <div className={styles.sky} aria-hidden="true">
          <span className={styles.body} />
          <span className={styles.ring} />
          <span className={styles.cloud} />
          <span className={styles.ground} />
        </div>
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

        {/* The minimum on the sky (#426): what the stack is and where it reaches. Everything
            else — the paper height, the estimates, the scale — is one tap away, so the
            picture is what the card shows. */}
        <div className={styles.label}>
          <p className={styles.books}>{t('home.bookStack.books', { count: booksRead, books })}</p>
          <p className={styles.pages}>{t('home.bookStack.pages', { count: pagesTotal, pages })}</p>
          <p className={styles.reached}>{t('home.sky.reached', { place: reached })}</p>
        </div>
        <button
          type="button"
          className={styles.info}
          onClick={() => setDetailsOpen(true)}
          aria-label={t('home.sky.details')}
          title={t('home.sky.details')}
        >
          <Icon name="info" size={20} color="currentColor" />
        </button>
      </div>
      {detailsOpen && (
        <Modal title={t('home.bookStack.title')} onClose={() => setDetailsOpen(false)}>
          <div className={styles.details}>
            <p className={styles.books}>{t('home.bookStack.books', { count: booksRead, books })}</p>
            <p className={styles.pages}>{t('home.bookStack.pages', { count: pagesTotal, pages })}</p>
            <p className={styles.height}>{t('home.bookStack.height', { height })}</p>
            {withLength < booksRead && (
              <p className={styles.estimate}>
                {withLength > 0
                  ? t('home.bookStack.partial', { count: booksRead, known, books })
                  : t('home.bookStack.noLength')}
              </p>
            )}
            {/* The conversion is an estimate and says so (PRODUCT.md § 6.5). */}
            <p className={styles.estimate}>{t('home.bookStack.estimate', { thickness })}</p>
            <div className={styles.altitude}>
              <p className={styles.reached}>{t('home.sky.reached', { place: reached })}</p>
              <p className={styles.distance}>{t('home.sky.distance', { distance })}</p>
              {sky.next && (
                <>
                  <div
                    className={styles.altTrack}
                    role="progressbar"
                    aria-label={nextLabel}
                    aria-valuenow={Math.round(sky.progress * 100)}
                  >
                    <span className={styles.altFill} style={{ width: `${Math.round(sky.progress * 100)}%` }} />
                  </div>
                  <p className={styles.nextStep}>{nextLabel}</p>
                </>
              )}
              <p className={styles.scaleNote}>{t('home.sky.scale')}</p>
            </div>
          </div>
        </Modal>
      )}
    </section>
  );
}
