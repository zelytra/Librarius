import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { LoginGate } from '../../shared/LoginGate';
import { ErrorState, Loading } from '../../shared/ui/states';
import { Icon } from '../../shared/ui/Icon';
import { Segmented } from '../../shared/ui/primitives';
import { activeLanguage } from '../../i18n/languages';
import { useGetApiCategories, useGetApiLibraryId, type LibraryItemDto } from '../../api/generated/librarius';
import { pageRule, readingDays, type SheetFormat } from './readingSheet';
import styles from './ReadingSheetPage.module.css';

/** Ruled lines left to write on by hand, where the reader typed nothing, per format. */
const BLANK_LINES: Record<SheetFormat, number> = { A4: 6, A6: 3 };
const NOTE_LINES: Record<SheetFormat, number> = { A4: 4, A6: 2 };

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

/**
 * The reading sheet of a title (#412): what the reader thought of it and what it was, on one
 * printable A4 page in the app's typefaces.
 *
 * <p>Drawn for a printer: black text on white, hairlines and outlines instead of filled
 * areas, the accent only as a thin rule, so it prints the same in greyscale as in colour and
 * costs a few strokes of ink. The cover is left out by default — it is the one heavy element —
 * and can be added from the toolbar, which itself never prints.
 */
export function ReadingSheet({ item, rankLabel }: { item: LibraryItemDto; rankLabel?: string }) {
  const { t } = useTranslation();
  // The cover by default (#427): the sheet is a keepsake first; leaving it out saves ink.
  const [withCover, setWithCover] = useState(true);
  const [format, setFormat] = useState<SheetFormat>('A4');
  const navigate = useNavigate();
  const b = item.book ?? {};
  const started = formatDate(item.progress?.startedAt);
  const finished = formatDate(item.progress?.finishedAt);
  const days = readingDays(item.progress?.startedAt, item.progress?.finishedAt);
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
  const shown = facts.filter((f) => f.value);
  const today = formatDate(new Date().toISOString().slice(0, 10));

  return (
    <div className={styles.page}>
      <div className={styles.toolbar}>
        <button type="button" className={styles.tool} onClick={() => navigate(-1)}>
          <Icon name="arrow_back" size={18} color="currentColor" />
          {t('common.back')}
        </button>
        <Segmented<SheetFormat>
          options={[
            { id: 'A4', label: t('sheet.formats.A4') },
            { id: 'A6', label: t('sheet.formats.A6') },
          ]}
          value={format}
          onChange={setFormat}
        />
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
      <article
        className={`${styles.sheet} ${format === 'A6' ? styles.a6 : ''}`}
        aria-label={t('sheet.title')}
        data-format={format}
      >
        <header className={styles.header}>
          <span className={styles.kicker}>{t('sheet.title')}</span>
          <span className={styles.brand}>{t('app.name')}</span>
        </header>

        <div className={styles.identity}>
          {withCover && b.coverUrl && <img className={styles.cover} src={b.coverUrl} alt={t('sheet.coverAlt', { title: b.title ?? '' })} />}
          <div>
            <h1 className={styles.bookTitle}>{b.title ?? '—'}</h1>
            {b.authors && <p className={styles.authors}>{b.authors}</p>}
            <div className={styles.ratingRow}>
              <Stars value={item.rating ?? 0} />
              {item.rating == null && <span className={styles.unrated}>{t('sheet.unrated')}</span>}
            </div>
          </div>
        </div>

        <dl className={styles.facts}>
          {shown.map((f) => (
            <div key={f.label} className={styles.fact}>
              <dt>{f.label}</dt>
              <dd>{f.value}</dd>
            </div>
          ))}
        </dl>

        <section className={styles.block}>
          <h2 className={styles.blockTitle}>{t('sheet.review')}</h2>
          {item.review ? (
            <p className={styles.review}>{item.review}</p>
          ) : (
            <div className={styles.lines} aria-label={t('sheet.blank')}>
              {Array.from({ length: BLANK_LINES[format] }, (_, i) => (
                <span key={i} className={styles.line} />
              ))}
            </div>
          )}
        </section>

        {/* A card has no room for the summary: the book itself carries it. */}
        {b.synopsis && format === 'A4' && (
          <section className={styles.block}>
            <h2 className={styles.blockTitle}>{t('sheet.summary')}</h2>
            <p className={styles.synopsis}>{b.synopsis}</p>
          </section>
        )}

        <section className={styles.block}>
          <h2 className={styles.blockTitle}>{t('sheet.notes')}</h2>
          <div className={styles.lines}>
            {Array.from({ length: NOTE_LINES[format] }, (_, i) => (
              <span key={i} className={styles.line} />
            ))}
          </div>
        </section>

        <footer className={styles.footer}>{t('sheet.footer', { date: today })}</footer>
      </article>
    </div>
  );
}

function ReadingSheetContent({ id }: { id: string }) {
  const { t } = useTranslation();
  const { data: item, isPending, isError, refetch } = useGetApiLibraryId(id);
  const { data: categories = [] } = useGetApiCategories();
  if (isPending) return <Loading />;
  if (isError || !item) return <ErrorState message={t('detail.error')} onRetry={() => void refetch()} />;
  const rank = categories.find((c) => c.code === item.rankCode)?.label;
  return <ReadingSheet item={item} rankLabel={rank} />;
}

export function ReadingSheetPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  return (
    <LoginGate prompt={t('auth.prompts.detail')}>
      <ReadingSheetContent id={id} />
    </LoginGate>
  );
}
