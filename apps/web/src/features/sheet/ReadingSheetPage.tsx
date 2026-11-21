import { useState, type CSSProperties, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueries } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { Icon } from '../../shared/ui/Icon';
import { Segmented } from '../../shared/ui/primitives';
import { activeLanguage } from '../../i18n/languages';
import {
  getApiLibraryId,
  getApiSeriesId,
  getGetApiLibraryIdQueryKey,
  getGetApiSeriesIdQueryKey,
  useGetApiCategories,
  useGetApiLibraryId,
  type CategoryDto,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import {
  SHEET_FORMATS,
  pageRule,
  printMargin,
  readSheetTheme,
  readingDays,
  sheetIdsFromParams,
  sheetSize,
  type SheetFormat,
  type SheetTheme,
} from './readingSheet';
import styles from './ReadingSheetPage.module.css';

/** Ruled lines left to write on by hand, where the reader typed nothing, per format. */
const BLANK_LINES: Record<SheetFormat, number> = { A4: 6, A5: 4, A6: 3 };

/**
 * The handwriting face of the default theme (#549), fetched by the sheet alone: no other
 * screen pays for it, and the stylesheet is not on the app's critical path. Until it arrives,
 * or offline, the system's own handwriting faces stand in (see `--sheet-hand`).
 */
const HAND_FONT = 'https://fonts.googleapis.com/css2?family=Caveat:wght@400..700&display=swap';

function formatDate(value: string | undefined | null): string | null {
  if (!value) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(activeLanguage(), { day: 'numeric', month: 'long', year: 'numeric' }).format(parsed);
}

/** Five outlined stars, the rated ones filled: legible in black and white, a few dots of ink. */
function Stars({ value }: { value: number }) {
  const { t } = useTranslation();
  return (
    <span className={styles.stars} role="img" aria-label={t('sheet.ratingLabel', { rating: value })}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= value ? styles.starOn : styles.starOff} aria-hidden="true">
          {n <= value ? '★' : '☆'}
        </span>
      ))}
    </span>
  );
}

function Lines({ count, label }: { count: number; label?: string }) {
  return (
    <div className={styles.lines} aria-label={label}>
      {Array.from({ length: count }, (_, i) => (
        <span key={i} className={styles.line} />
      ))}
    </div>
  );
}

/** What the toolbar sets, shared by every sheet of a print. */
export interface SheetOptions {
  format: SheetFormat;
  rings: boolean;
  withCover: boolean;
}

/**
 * One reading sheet (#412): what the reader thought of a title and what it was, drawn at the
 * exact size of its paper (#548). Without an item it is the blank sheet (#550), every field a
 * line to fill in by hand and the cover an empty frame.
 *
 * <p>Drawn for a printer: dark ink on white, hairlines and outlines instead of filled areas,
 * so it prints the same in greyscale as in colour and costs a few strokes of ink.
 */
export function ReadingSheet({
  item,
  rankLabel,
  options,
  theme,
}: {
  item?: LibraryItemDto;
  rankLabel?: string;
  options: SheetOptions;
  theme: SheetTheme;
}) {
  const { t } = useTranslation();
  const { format, rings, withCover } = options;
  const b = item?.book ?? {};
  const started = formatDate(item?.progress?.startedAt);
  const finished = formatDate(item?.progress?.finishedAt);
  const days = readingDays(item?.progress?.startedAt, item?.progress?.finishedAt);
  const series = b.seriesTitle
    ? b.volumeNumber != null
      ? t('sheet.seriesVolume', { series: b.seriesTitle, volume: b.volumeNumber })
      : b.seriesTitle
    : null;

  const facts: { label: string; value: string | null | undefined }[] = [
    { label: t('sheet.facts.series'), value: series },
    { label: t('sheet.facts.publisher'), value: b.publisher },
    {
      label: t('sheet.facts.pages'),
      value: b.pageCount != null ? `${b.pageCountEstimated ? '≈ ' : ''}${b.pageCount}` : null,
    },
    { label: t('sheet.facts.year'), value: b.originalYear != null ? String(b.originalYear) : null },
    { label: t('sheet.facts.genres'), value: b.genres },
    { label: t('sheet.facts.isbn'), value: b.isbn13 },
    { label: t('sheet.facts.started'), value: started },
    { label: t('sheet.facts.finished'), value: finished },
    { label: t('sheet.facts.duration'), value: days != null ? t('sheet.days', { count: days }) : null },
    { label: t('sheet.facts.rank'), value: rankLabel },
  ];
  // A blank sheet keeps a line under every label, to be filled in by hand.
  // A card holds what fits it (#590): the ISBN and the genres stay on the larger formats.
  const onCard = (f: { label: string }) =>
    format !== 'A6' || (f.label !== t('sheet.facts.isbn') && f.label !== t('sheet.facts.genres'));
  const shown = (item ? facts.filter((f) => f.value) : facts.filter((f) => f.label !== t('sheet.facts.isbn')))
    .filter(onCard);
  const size = sheetSize(format);

  return (
    <article
      className={[styles.sheet, styles[format], rings && styles.rings].filter(Boolean).join(' ')}
      aria-label={t('sheet.title')}
      data-format={format}
      data-rings={rings}
      data-theme={theme}
      style={
        {
          '--print-margin': printMargin(format, rings),
          '--sheet-width': size.width,
          '--sheet-height': size.height,
        } as CSSProperties
      }
    >
      <header className={styles.header}>
        <span className={styles.kicker}>{t('sheet.title')}</span>
        <span className={styles.brand}>{t('app.name')}</span>
      </header>

      <div className={styles.identity}>
        {withCover && item && b.coverUrl && (
          <img className={styles.cover} src={b.coverUrl} alt={t('sheet.coverAlt', { title: b.title ?? '' })} />
        )}
        {/* Nothing to print on a blank sheet: a frame of the cover's size, to paste one in. */}
        {withCover && !item && (
          <span className={styles.coverFrame} role="img" aria-label={t('sheet.coverFrame')} />
        )}
        <div className={styles.identityText}>
          {item ? (
            <h1 className={styles.bookTitle}>{b.title ?? '—'}</h1>
          ) : (
            <>
              <span className={styles.fieldLabel}>{t('sheet.blankTitle')}</span>
              <Lines count={1} />
            </>
          )}
          {item && b.authors && <p className={styles.authors}>{b.authors}</p>}
          {!item && (
            <>
              <span className={styles.fieldLabel}>{t('sheet.blankAuthor')}</span>
              <Lines count={1} />
            </>
          )}
          <div className={styles.ratingRow}>
            <Stars value={item?.rating ?? 0} />
            {item && item.rating == null && <span className={styles.unrated}>{t('sheet.unrated')}</span>}
          </div>
        </div>
      </div>

      <dl className={styles.facts}>
        {shown.map((f) => (
          <div key={f.label} className={styles.fact}>
            <dt>{f.label}</dt>
            <dd>{f.value ?? ''}</dd>
          </div>
        ))}
      </dl>

      <section className={styles.block}>
        <h2 className={styles.blockTitle}>{t('sheet.review')}</h2>
        {item?.review ? (
          <p className={styles.review}>{item.review}</p>
        ) : (
          <Lines count={BLANK_LINES[format]} label={t('sheet.blank')} />
        )}
      </section>

      {/* A card has no room for the summary: the book itself carries it. */}
      {b.synopsis && format !== 'A6' && (
        <section className={styles.block}>
          <h2 className={styles.blockTitle}>{t('sheet.summary')}</h2>
          <p className={styles.synopsis}>{b.synopsis}</p>
        </section>
      )}

      {/* Last, and stretched to the bottom of the page (#548): the ruling fills whatever room
          the sheet has left, so a short one still reaches the edge of its paper. */}
      <section className={`${styles.block} ${styles.notes}`}>
        <h2 className={styles.blockTitle}>{t('sheet.notes')}</h2>
        <div className={styles.ruled} />
      </section>
    </article>
  );
}

/**
 * The page around one or several sheets: the toolbar that never prints, the @page rule, and
 * the sheets themselves, one per printed page (#551).
 */
export function SheetPrintLayout({
  children,
}: {
  children: (options: SheetOptions, theme: SheetTheme) => ReactNode;
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  // The cover by default (#427): the sheet is a keepsake first; leaving it out saves ink.
  const [withCover, setWithCover] = useState(true);
  const [format, setFormat] = useState<SheetFormat>('A4');
  // A ring binder needs a wider inner margin, or the holes go through the text (#432).
  const [rings, setRings] = useState(false);
  // Picked in Settings (#550), read once: the print screen is not where it is changed.
  const [theme] = useState(readSheetTheme);

  return (
    <div className={styles.page}>
      {/* No `precedence`: React would hold the sheet back until the font arrives, and offline
          forever. Unhoisted, the sheet paints at once in the fallback faces. */}
      {theme === 'handwritten' && <link rel="stylesheet" href={HAND_FONT} />}
      <div className={`sticky-header ${styles.toolbar}`}>
        <button type="button" className={styles.tool} onClick={() => navigate(-1)}>
          <Icon name="arrow_back" size={18} color="currentColor" />
          {t('common.back')}
        </button>
        <Segmented<SheetFormat>
          options={SHEET_FORMATS.map((id) => ({ id, label: t(`sheet.formats.${id}`) }))}
          value={format}
          onChange={setFormat}
        />
        <label className={styles.toggle}>
          <input type="checkbox" checked={rings} onChange={(e) => setRings(e.target.checked)} />
          {t('sheet.rings')}
        </label>
        <label className={styles.toggle}>
          <input type="checkbox" checked={withCover} onChange={(e) => setWithCover(e.target.checked)} />
          {t('sheet.withCover')}
        </label>
        <button type="button" className={`${styles.tool} ${styles.print}`} onClick={() => window.print()}>
          <Icon name="print" size={18} color="currentColor" />
          {t('sheet.print')}
        </button>
      </div>
      <p className={styles.inkNote}>{t('sheet.inkNote')}</p>

      {/* The paper size can only be told to the printer through @page, which no class can
          scope: the rule follows the format picked. */}
      <style>{pageRule(format)}</style>
      <div className={styles.stack}>{children({ format, rings, withCover }, theme)}</div>
    </div>
  );
}

const rankOf = (categories: CategoryDto[], item: LibraryItemDto) =>
  categories.find((c) => c.code === item.rankCode)?.label;

function ReadingSheetContent({ id }: { id: string }) {
  const { t } = useTranslation();
  const { data: item, isPending, isError, refetch } = useGetApiLibraryId(id);
  const { data: categories = [] } = useGetApiCategories();
  if (isPending) return <Loading />;
  if (isError || !item) return <ErrorState message={t('detail.error')} onRetry={() => void refetch()} />;
  return (
    <SheetPrintLayout>
      {(options, theme) => <ReadingSheet item={item} rankLabel={rankOf(categories, item)} options={options} theme={theme} />}
    </SheetPrintLayout>
  );
}

/** The sheet of one title, from its screen (#412). */
export function ReadingSheetPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  return (
    <LoginGate prompt={t('auth.prompts.detail')}>
      <ReadingSheetContent id={id} />
    </LoginGate>
  );
}

/** A sheet with nothing on it, printed from Settings to fill in by hand (#550). */
export function BlankSheetPage() {
  return (
    <SheetPrintLayout>{(options, theme) => <ReadingSheet options={options} theme={theme} />}</SheetPrintLayout>
  );
}

function ReadingSheetsContent() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const ids = sheetIdsFromParams(params);
  const { data: categories = [] } = useGetApiCategories();
  // A selected series prints every volume of it the reader holds.
  const seriesQueries = useQueries({
    queries: ids.series.map((id) => ({
      queryKey: getGetApiSeriesIdQueryKey(id),
      queryFn: () => getApiSeriesId(id),
    })),
  });
  const volumeIds = seriesQueries.flatMap(
    (q) => q.data?.volumes?.filter((v) => v.owned && v.libraryItemId).map((v) => v.libraryItemId as string) ?? [],
  );
  const itemIds = [...new Set([...ids.items, ...volumeIds])];
  const itemQueries = useQueries({
    queries: itemIds.map((id) => ({
      queryKey: getGetApiLibraryIdQueryKey(id),
      queryFn: () => getApiLibraryId(id),
    })),
  });
  const all = [...seriesQueries, ...itemQueries];
  if (all.some((q) => q.isPending)) return <Loading />;
  if (all.some((q) => q.isError)) {
    return <ErrorState message={t('sheet.multiError')} onRetry={() => all.forEach((q) => void q.refetch())} />;
  }
  const items = itemQueries.map((q) => q.data).filter((it): it is LibraryItemDto => it != null);
  if (items.length === 0) return <EmptyState icon="print" title={t('sheet.multiEmpty')} />;
  return (
    <SheetPrintLayout>
      {(options, theme) =>
        items.map((item) => (
          <ReadingSheet
            key={item.id}
            item={item}
            rankLabel={rankOf(categories, item)}
            options={options}
            theme={theme}
          />
        ))
      }
    </SheetPrintLayout>
  );
}

/** The sheets of every title of a selection, one per page (#551). */
export function ReadingSheetsPage() {
  const { t } = useTranslation();
  return (
    <LoginGate prompt={t('auth.prompts.detail')}>
      <ReadingSheetsContent />
    </LoginGate>
  );
}
