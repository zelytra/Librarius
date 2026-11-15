import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGetApiStatsReadings, type ReadingSpanDto } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Icon } from '../../shared/ui/Icon';
import styles from './ReadingCalendar.module.css';

/** `YYYY-MM-DD` of a UTC date — the calendar works in plain dates, never in time zones. */
const iso = (d: Date) => d.toISOString().slice(0, 10);
const parse = (value: string) => new Date(`${value}T00:00:00Z`);

/** The first day of the month `YYYY-MM`, moved by `delta` months. */
function monthStart(month: string, delta = 0): Date {
  const [y, m] = month.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1 + delta, 1));
}

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
 * tinted — deeper where several overlap — and the month's readings are listed under it, with
 * when each started and finished and how many days that took.
 */
export function ReadingCalendar({ initialMonth }: { initialMonth?: string }) {
  const { t } = useTranslation();
  const locale = activeLanguage();
  const [month, setMonth] = useState(initialMonth ?? iso(new Date()).slice(0, 7));
  const first = monthStart(month);
  const last = new Date(monthStart(month, 1).getTime() - 86_400_000);
  const { data: readings = [] } = useGetApiStatsReadings({ from: iso(first), to: iso(last) });
  const counts = coverage(readings, first, last);

  const fmt = (d: Date, options: Intl.DateTimeFormatOptions) =>
    d.toLocaleDateString(locale, { timeZone: 'UTC', ...options });
  const title = fmt(first, { month: 'long', year: 'numeric' });
  // Monday first; getUTCDay() counts from Sunday.
  const lead = (first.getUTCDay() + 6) % 7;
  const days = Array.from({ length: last.getUTCDate() }, (_, i) => new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth(), i + 1)));
  const weekdays = Array.from({ length: 7 }, (_, i) => fmt(new Date(Date.UTC(2024, 0, 1 + i)), { weekday: 'narrow' }));
  const go = (delta: number) => setMonth(iso(monthStart(month, delta)).slice(0, 7));

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
          const n = counts.get(iso(d)) ?? 0;
          return (
            <span
              key={iso(d)}
              className={`${styles.day} ${n > 0 ? (n > 1 ? styles.dayMany : styles.dayRead) : ''}`}
              aria-label={n > 0 ? t('stats.calendar.day', { date: fmt(d, { day: 'numeric', month: 'long' }), count: n }) : undefined}
            >
              {d.getUTCDate()}
            </span>
          );
        })}
      </div>
      {readings.length === 0 ? (
        <p className={styles.empty}>{t('stats.calendar.empty')}</p>
      ) : (
        <ul className={styles.list}>
          {readings.map((r) => (
            <li key={r.itemId} className={styles.item}>
              <span className={styles.itemTitle}>{r.title}</span>
              <span className={styles.itemSpan}>{span(r)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
