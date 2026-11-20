import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGetApiStatsReadings, type ReadingSpanDto } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Icon } from '../../shared/ui/Icon';
import { colorFor } from '../../shared/ui/coverPalette';
import styles from './ReadingCalendar.module.css';

/** `YYYY-MM-DD` of a UTC date — the calendar works in plain dates, never in time zones. */
const iso = (d: Date) => d.toISOString().slice(0, 10);
const parse = (value: string) => new Date(`${value}T00:00:00Z`);

/** The first day of the month `YYYY-MM`, moved by `delta` months. */
function monthStart(month: string, delta = 0): Date {
  const [y, m] = month.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1 + delta, 1));
}

/** Whether a reading covered `day`, from its start (or its finish alone) to its finish. */
function covers(r: ReadingSpanDto, day: string): boolean {
  return !!r.finishedAt && (r.startedAt ?? r.finishedAt) <= day && day <= r.finishedAt;
}

/** How many month readings show before « voir plus ». */
const LIST_SHOWN = 4;

/** How many readings cover each day of the month, by `YYYY-MM-DD`. */
function coverage(readings: ReadingSpanDto[], first: Date, last: Date): Map<string, number> {
  const counts = new Map<string, number>();
  for (const r of readings) {
    if (!r.finishedAt) continue;
    const end = parse(r.finishedAt);
    let day = r.startedAt ? parse(r.startedAt) : end;
    if (day < first) day = first;
    for (; day <= end && day <= last; day = new Date(day.getTime() + 86_400_000)) {
      counts.set(iso(day), (counts.get(iso(day)) ?? 0) + 1);
    }
  }
  return counts;
}

/**
 * The readings finished in a month (#479), on a calendar: each day a reading covered is
 * tinted — deeper where several overlap — and a book shows as its cover in the day it was
 * finished (#485). The readings are listed under it, with when each started and finished and
 * how many days that took: a few of the month's, or those of the day picked on the grid.
 */
export function ReadingCalendar({ initialMonth }: { initialMonth?: string }) {
  const { t } = useTranslation();
  const locale = activeLanguage();
  const [month, setMonth] = useState(initialMonth ?? iso(new Date()).slice(0, 7));
  const first = monthStart(month);
  const last = new Date(monthStart(month, 1).getTime() - 86_400_000);
  const { data: readings = [] } = useGetApiStatsReadings({ from: iso(first), to: iso(last) });
  const counts = coverage(readings, first, last);
  const [picked, setPicked] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);
  const listed = picked ? readings.filter((r) => covers(r, picked)) : readings;
  const shown = picked || showAll ? listed : listed.slice(0, LIST_SHOWN);

  const fmt = (d: Date, options: Intl.DateTimeFormatOptions) =>
    d.toLocaleDateString(locale, { timeZone: 'UTC', ...options });
  const title = fmt(first, { month: 'long', year: 'numeric' });
  // Monday first; getUTCDay() counts from Sunday.
  const lead = (first.getUTCDay() + 6) % 7;
  const days = Array.from({ length: last.getUTCDate() }, (_, i) => new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth(), i + 1)));
  const weekdays = Array.from({ length: 7 }, (_, i) => fmt(new Date(Date.UTC(2024, 0, 1 + i)), { weekday: 'narrow' }));
  const go = (delta: number) => {
    setMonth(iso(monthStart(month, delta)).slice(0, 7));
    setPicked(null);
    setShowAll(false);
  };

  const span = (r: ReadingSpanDto) => {
    const end = parse(r.finishedAt!);
    if (!r.startedAt) return t('stats.calendar.finishedOn', { date: fmt(end, { day: 'numeric', month: 'long' }) });
    const start = parse(r.startedAt);
    const sameMonth = start.getUTCMonth() === end.getUTCMonth() && start.getUTCFullYear() === end.getUTCFullYear();
    return t('stats.calendar.span', {
      from: sameMonth ? fmt(start, { day: 'numeric' }) : fmt(start, { day: 'numeric', month: 'long' }),
      to: fmt(end, { day: 'numeric', month: 'long' }),
      count: r.days ?? 0,
    });
  };

  return (
    <section className={styles.panel} aria-label={t('stats.calendar.title')}>
      <div className={styles.head}>
        <button type="button" className={styles.nav} aria-label={t('stats.calendar.previous')} onClick={() => go(-1)}>
          <Icon name="arrow_back" size={20} color="var(--ink-soft)" />
        </button>
        <h3 className={styles.title}>{title}</h3>
        <button type="button" className={styles.nav} aria-label={t('stats.calendar.next')} onClick={() => go(1)}>
          <Icon name="arrow_forward" size={20} color="var(--ink-soft)" />
        </button>
      </div>
      <div className={styles.grid}>
        {weekdays.map((w, i) => (
          <span key={`w${i}`} className={styles.weekday} aria-hidden="true">{w}</span>
        ))}
        {Array.from({ length: lead }, (_, i) => <span key={`pad${i}`} />)}
        {days.map((d) => {
          const key = iso(d);
          const n = counts.get(key) ?? 0;
          const className = `${styles.day} ${n > 0 ? (n > 1 ? styles.dayMany : styles.dayRead) : ''}`;
          if (n === 0) return <span key={key} className={className}>{d.getUTCDate()}</span>;
          const finished = readings.filter((r) => r.finishedAt === key);
          const cover = finished.find((r) => r.coverUrl)?.coverUrl;
          return (
            <button
              key={key}
              type="button"
              className={`${className} ${finished.length > 0 ? styles.dayFinished : ''} ${picked === key ? styles.dayPicked : ''}`}
              aria-label={t('stats.calendar.day', { date: fmt(d, { day: 'numeric', month: 'long' }), count: n })}
              aria-pressed={picked === key}
              onClick={() => setPicked(picked === key ? null : key)}
            >
              {cover && (
                <img
                  className={styles.dayCover}
                  src={cover}
                  // The day's label names the readings: the cover is decorative.
                  // eslint-disable-next-line no-restricted-syntax -- empty alt, not user-facing text
                  alt=""
                  loading="lazy"
                />
              )}
              <span className={styles.dayNumber}>{d.getUTCDate()}</span>
              {finished.length > 1 && <span className={styles.dayMore}>{t('stats.calendar.moreOnDay', { count: finished.length - 1 })}</span>}
            </button>
          );
        })}
      </div>
      {readings.length === 0 ? (
        <p className={styles.empty}>{t('stats.calendar.empty')}</p>
      ) : (
        <ul className={styles.list}>
          {shown.map((r) => (
            <li key={r.itemId} className={styles.item}>
              {/* The cover beside each reading (#567): decorative, the title says what it is. */}
              {r.coverUrl ? (
                <img
                  className={styles.itemCover}
                  src={r.coverUrl}
                  // eslint-disable-next-line no-restricted-syntax -- empty alt, not user-facing text
                  alt=""
                />
              ) : (
                <span className={styles.itemCover} style={{ background: colorFor(r.title ?? r.itemId ?? '') }} data-cover aria-hidden="true" />
              )}
              <span className={styles.itemText}>
                <span className={styles.itemTitle}>{r.title}</span>
                <span className={styles.itemSpan}>{span(r)}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
      {listed.length > shown.length && (
        <button type="button" className={styles.more} onClick={() => setShowAll(true)}>
          {t('stats.calendar.more', { count: listed.length - shown.length })}
        </button>
      )}
    </section>
  );
}
