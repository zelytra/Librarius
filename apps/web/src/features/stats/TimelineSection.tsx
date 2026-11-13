import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Segmented } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { toUnit } from '../../shared/goal';
import { activeLanguage } from '../../i18n/languages';
import {
  useGetApiStatsBreakdown,
  useGetApiStatsTimeline,
  type BreakdownCountDto,
  type KindGoalDto,
  type StatsDto,
} from '../../api/generated/librarius';
import {
  chartMax,
  cumulative,
  monthLabels,
  monthlySeries,
  type Metric,
  type MonthPoint,
  offeredYears,
  KIND_GROUPS,
  kindGroupOf,
  type KindGroup,
} from './timeline';
import styles from './TimelineSection.module.css';

/**
 * Reading over time.
 *
 * <p>Charted with a handful of SVG elements rather than a charting library: a bar is a
 * rectangle and a trend is a polyline, both of them painted with the same tokens as the
 * rest of the screen so they follow the theme. A library would have cost more bundle than
 * the whole screen weighs.
 *
 * <p>Every chart shares one viewBox, twelve slots wide, and scales uniformly to whatever
 * width it is given: the narrowest screen the app targets is 375 px, which leaves about
 * 27 px per month.
 */

/** Geometry of the plot area, in viewBox units. One slot per month. */
const SLOT = 20;
const PLOT_WIDTH = SLOT * 12;
const PLOT_HEIGHT = 100;

/** How many labels a breakdown shows, matching what the API returns. */
const BREAKDOWN_BARS = [styles.bar1, styles.bar2, styles.bar3, styles.bar4];

/** The dimensions the finished titles can be ranked by, beyond the genres. */
const BREAKDOWNS = ['byAuthor', 'byPublisher', 'byLanguage', 'byRank'] as const;
type BreakdownKey = (typeof BREAKDOWNS)[number];

/** Colour of each kind group on the split chart, and in its legend. */
const KIND_FILL: Record<KindGroup, string> = {
  BOOK: styles.fillBook,
  MANGA: styles.fillManga,
  COMIC: styles.fillComic,
  OTHER: styles.fillOther,
};

/**
 * Twelve columns, one per month, the figure printed over each: a chart that has to be hovered
 * to be read is a chart most people do not read. Hovering, focusing or tapping a month opens
 * its details — books, pages and, split by kind, how many of each. Split, each column stacks
 * the kinds in their own colours; together, it is one bar for everything read.
 */
function MonthlyBars({ months, metric, split, caption }: {
  months: MonthPoint[];
  metric: Metric;
  split: boolean;
  caption: string;
}) {
  const { t } = useTranslation();
  const [active, setActive] = useState<number | null>(null);
  const values = months.map((month) => month[metric]);
  const max = chartMax(values);
  const narrow = monthLabels('narrow');
  const long = monthLabels('long');
  const locale = activeLanguage();

  const details = (month: MonthPoint) => {
    const head = t('stats.timeline.tooltip', {
      month: long[month.month - 1],
      books: month.books,
      pages: month.pages.toLocaleString(locale),
    });
    const kinds = KIND_GROUPS.filter((kind) => month.byKind[kind] > 0)
      .map((kind) => `${t(`stats.timeline.kinds.${kind}`)} ${month.byKind[kind]}`)
      .join(' · ');
    return kinds ? `${head} — ${kinds}` : head;
  };

  return (
    <div className={styles.chart} role="group" aria-label={caption}>
      <div className={styles.columns}>
        {months.map((month, index) => {
          const value = values[index];
          const height = (value / max) * 100;
          return (
            <button
              key={month.month}
              type="button"
              className={styles.column}
              aria-label={details(month)}
              aria-expanded={active === index}
              onMouseEnter={() => setActive(index)}
              onMouseLeave={() => setActive((current) => (current === index ? null : current))}
              onFocus={() => setActive(index)}
              onBlur={() => setActive((current) => (current === index ? null : current))}
              onClick={() => setActive((current) => (current === index ? null : index))}
            >
              <span className={styles.columnValue}>{value > 0 ? value.toLocaleString(locale) : ''}</span>
              <span className={styles.columnTrack}>
                <span className={value > 0 ? styles.columnBar : styles.columnEmpty} style={{ height: `${Math.max(height, 2)}%` }}>
                  {split && value > 0 &&
                    KIND_GROUPS.filter((kind) => month.byKind[kind] > 0).map((kind) => (
                      <span
                        key={kind}
                        className={`${styles.segment} ${KIND_FILL[kind]}`}
                        // The share of the month's titles of that kind.
                        style={{ flexGrow: month.byKind[kind] }}
                      />
                    ))}
                </span>
              </span>
              <span className={styles.axisLabel}>{narrow[index]}</span>
              {active === index && (
                <span className={styles.tooltip} role="tooltip">
                  {details(month)}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {split && (
        <div className={styles.legend} aria-hidden="true">
          {KIND_GROUPS.map((kind) => (
            <span key={kind} className={styles.legendItem}>
              <span className={`${styles.legendDot} ${KIND_FILL[kind]}`} />
              {t(`stats.timeline.kinds.${kind}`)}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/** Colour of each kind group's running total on the cumulative charts (#420). */
const KIND_STROKE: Record<KindGroup, string> = {
  BOOK: styles.curveBook,
  MANGA: styles.curveManga,
  COMIC: styles.curveComic,
  OTHER: styles.curveOther,
};

function CumulativeLine({ values, target, caption, tone }: {
  values: number[];
  target: number;
  caption: string;
  tone?: KindGroup;
}) {
  const max = chartMax([...values, target]);
  const x = (index: number) => (index * PLOT_WIDTH) / (values.length - 1);
  const y = (value: number) => PLOT_HEIGHT - (value / max) * PLOT_HEIGHT;
  const curve = values.map((value, index) => `${x(index)},${y(value)}`).join(' ');

  return (
    <svg
      viewBox={`0 0 ${PLOT_WIDTH} ${PLOT_HEIGHT}`}
      className={styles.plot}
      role="img"
      aria-label={caption}
    >
      <polygon
        className={styles.area}
        points={`0,${PLOT_HEIGHT} ${curve} ${PLOT_WIDTH},${PLOT_HEIGHT}`}
      />
      <polyline className={`${styles.curve} ${tone ? KIND_STROKE[tone] : ''}`} points={curve} />
      <line
        className={styles.goalLine}
        x1="0"
        y1={PLOT_HEIGHT}
        x2={PLOT_WIDTH}
        y2={y(target)}
      />
    </svg>
  );
}

function Breakdown({ rows }: { rows: BreakdownCountDto[] }) {
  const max = chartMax(rows.map((row) => row.count ?? 0));

  return (
    <div className={styles.rows}>
      {rows.map((row, index) => (
        <div key={row.label}>
          <div className={styles.rowHead}>
            <span className={styles.rowName}>{row.label}</span>
            <span className={styles.rowCount}>{row.count ?? 0}</span>
          </div>
          <div className={styles.track}>
            <div
              className={`${styles.rowBar} ${BREAKDOWN_BARS[index % BREAKDOWN_BARS.length]}`}
              // The width is the share of the most read label.
              style={{ width: `${Math.round(((row.count ?? 0) / max) * 100)}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

/** The year's titles per kind, under the split chart. */
function KindTotals({ months }: { months: MonthPoint[] }) {
  const { t } = useTranslation();
  const totals = KIND_GROUPS.map((kind) => ({
    kind,
    count: months.reduce((sum, month) => sum + month.byKind[kind], 0),
  })).filter((row) => row.count > 0);
  return (
    <p className={styles.total}>
      {totals.map((row) => `${t(`stats.timeline.kinds.${row.kind}`)} : ${row.count}`).join(' · ')}
    </p>
  );
}

/**
 * The running total of one kind against its own goal (#420): books, manga and comics each
 * have a goal since #393, so each gets its curve, counted on that kind only and in the
 * goal's own unit, with the steady pace that reaches it on 31 December.
 */
function KindGoalChart({ goal, months, year }: { goal: KindGoalDto; months: MonthPoint[]; year: number }) {
  const { t } = useTranslation();
  const group = kindGroupOf(goal.kind ?? 'BOOK');
  const unit = toUnit(goal.unit);
  const target = goal.target ?? 0;
  const perMonth = months.map((month) => (unit === 'PAGES' ? month.pagesByKind[group] : month.byKind[group]));
  const values = cumulative(perMonth);
  const reached = values[values.length - 1] ?? 0;
  const units = (count: number) => t(`goal.units.${unit}`, { count });
  return (
    <div className={styles.kindGoal}>
      <div className={styles.kindGoalHead}>
        <span className={`${styles.legendDot} ${KIND_FILL[group]}`} aria-hidden="true" />
        <span className={styles.kindGoalTitle}>{t(`goal.kinds.${goal.kind ?? 'BOOK'}`)}</span>
        <span className={styles.kindGoalFigure}>
          {t('stats.timeline.kindGoalFigure', {
            current: reached.toLocaleString(activeLanguage()),
            target: target.toLocaleString(activeLanguage()),
            unit: units(target),
          })}
        </span>
      </div>
      <CumulativeLine
        values={[0, ...values]}
        target={target}
        tone={group}
        caption={t('stats.timeline.cumulativeKindLabel', {
          kind: t(`goal.kinds.${goal.kind ?? 'BOOK'}`),
          target,
          unit: units(target),
          year,
        })}
      />
    </div>
  );
}

/** One derived figure, or a dash when the window holds nothing to derive it from. */
function Figure({ value, label }: { value: string; label: string }) {
  return (
    <div className={styles.figure}>
      <div className={styles.figureValue}>{value}</div>
      <div className={styles.figureLabel}>{label}</div>
    </div>
  );
}

/** Far enough back for any reading history an import can bring. */
const HISTORY_START = '1900-01-01';

export function TimelineSection({ stats }: { stats: StatsDto }) {
  const { t } = useTranslation();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [metric, setMetric] = useState<Metric>('books');
  // Everything read together, or the books, manga and comics of the year apart.
  // Split by kind by default (#396): the reader files by book, manga and comic.
  const [split, setSplit] = useState(true);
  const [dimension, setDimension] = useState<BreakdownKey>('byAuthor');
  // "What you read" over everything read by default (#419): a year's finished titles left
  // out every title read without a date, which is most of an imported library.
  const [scope, setScope] = useState<'all' | 'year'>('all');
  const allTime = useGetApiStatsBreakdown();

  const timeline = useGetApiStatsTimeline({
    from: `${year}-01-01`,
    to: `${year}-12-31`,
    granularity: 'month',
  });
  // Every year the reader finished something in, one bucket each: the years to offer. A fixed
  // "this year and last" hid the whole history of an imported library — readings from 2019 to
  // 2023 counted in the totals and could not be looked at.
  const history = useGetApiStatsTimeline({
    from: HISTORY_START,
    to: `${thisYear}-12-31`,
    granularity: 'year',
  });

  if (timeline.isPending) return <Loading />;
  if (timeline.isError || !timeline.data) {
    return (
      <ErrorState message={t('stats.timeline.unavailable')} onRetry={() => void timeline.refetch()} />
    );
  }

  const months: MonthPoint[] = monthlySeries(timeline.data.points, year);
  const long = monthLabels('long');
  const breakdownRows: BreakdownCountDto[] =
    scope === 'all' ? (allTime.data?.[dimension] ?? []) : (timeline.data[dimension] ?? []);

  // One curve per kind that has a goal this year (#420), each in its goal's own unit.
  const kindGoals = year === thisYear ? (stats.goals ?? []).filter((goal) => (goal.target ?? 0) > 0) : [];

  const bestMonth = months.reduce((best, month) => (month.books > best.books ? month : best), months[0]);
  const pagesPerDay = timeline.data.pagesPerDay ?? 0;
  const booksPerMonth = timeline.data.booksPerMonth ?? 0;
  const readAnything = (timeline.data.books ?? 0) > 0;

  const years = offeredYears(thisYear, year, history.data?.points).map((value) => ({
    id: String(value),
    label: String(value),
  }));

  return (
    <>
      <div className={styles.panel}>
        <div className={styles.panelHead}>
          <div className={styles.panelTitle}>{t('stats.timeline.title')}</div>
          <Segmented options={years} value={String(year)} onChange={(id) => setYear(Number(id))} />
        </div>

        {/* The two switches on one row, with room before the chart (#396): the month
            values printed above the columns used to touch the buttons. */}
        <div className={styles.controls}>
          <Segmented<Metric>
            options={[
              { id: 'books', label: t('stats.timeline.metrics.books') },
              { id: 'pages', label: t('stats.timeline.metrics.pages') },
            ]}
            value={metric}
            onChange={setMetric}
          />
          {readAnything && (
            <Segmented<'all' | 'byKind'>
              options={[
                { id: 'all', label: t('stats.timeline.views.all') },
                { id: 'byKind', label: t('stats.timeline.views.byKind') },
              ]}
              value={split ? 'byKind' : 'all'}
              onChange={(id) => setSplit(id === 'byKind')}
            />
          )}
        </div>

        {readAnything ? (
          <>
            <MonthlyBars
              months={months}
              metric={metric}
              split={split}
              caption={t(`stats.timeline.chartLabel.${metric}`, { year })}
            />
            {split && <KindTotals months={months} />}
            <p className={styles.total}>
              {t('stats.timeline.total', {
                books: timeline.data.books ?? 0,
                pages: (timeline.data.pages ?? 0).toLocaleString(activeLanguage()),
                year,
              })}
            </p>
          </>
        ) : (
          <p className={styles.panelEmpty}>{t('stats.timeline.empty', { year })}</p>
        )}
      </div>

      {readAnything && (
        <div className={styles.panel}>
          <div className={styles.panelTitle}>{t('stats.timeline.paceTitle')}</div>
          <div className={styles.figures}>
            <Figure
              value={pagesPerDay.toLocaleString(activeLanguage(), { maximumFractionDigits: 1 })}
              label={t('stats.timeline.pagesPerDay')}
            />
            {/* Titles a month (#418), where "days per title" needed a start date imported
                titles never carry and so printed a dash. */}
            <Figure
              value={booksPerMonth.toLocaleString(activeLanguage(), { maximumFractionDigits: 1 })}
              label={t('stats.timeline.booksPerMonth')}
            />
            <Figure
              value={long[(bestMonth?.month ?? 1) - 1]}
              label={t('stats.timeline.bestMonth', { books: bestMonth?.books ?? 0 })}
            />
          </div>
        </div>
      )}

      {/* One running total per kind with a goal (#420), the way the monthly chart splits
          the kinds — nothing when the year holds no reading or no goal. */}
      {kindGoals.length > 0 && readAnything && (
        <div className={styles.panel}>
          <div className={styles.panelTitle}>{t('stats.timeline.cumulativeTitle')}</div>
          <div className={styles.kindGoals}>
            {kindGoals.map((goal) => (
              <KindGoalChart key={goal.kind} goal={goal} months={months} year={year} />
            ))}
          </div>
          <p className={styles.legend}>{t('stats.timeline.cumulativeLegend')}</p>
        </div>
      )}

      <div className={`${styles.panel} ${styles.panelWide}`}>
        <div className={styles.panelHead}>
          <div className={styles.panelTitle}>{t('stats.timeline.breakdownTitle')}</div>
          <Segmented<'all' | 'year'>
            options={[
              { id: 'all', label: t('stats.timeline.scopes.all') },
              { id: 'year', label: String(year) },
            ]}
            value={scope}
            onChange={setScope}
          />
        </div>
        <Segmented<BreakdownKey>
          options={BREAKDOWNS.map((id) => ({ id, label: t(`stats.timeline.breakdowns.${id}`) }))}
          value={dimension}
          onChange={setDimension}
        />
        {breakdownRows.length === 0 ? (
          <p className={styles.panelEmpty}>{t('stats.timeline.noBreakdown')}</p>
        ) : (
          <Breakdown rows={breakdownRows} />
        )}
      </div>
    </>
  );
}
