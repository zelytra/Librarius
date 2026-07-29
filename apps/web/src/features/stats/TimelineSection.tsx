import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Segmented } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { toUnit } from '../../shared/goal';
import { activeLanguage } from '../../i18n/languages';
import {
  useGetApiStatsTimeline,
  type BreakdownCountDto,
  type StatsDto,
} from '../../api/generated/librarius';
import {
  chartMax,
  cumulative,
  monthLabels,
  monthlySeries,
  type Metric,
  type MonthPoint,
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
const BAR_WIDTH = 12;
const PLOT_WIDTH = SLOT * 12;
const PLOT_HEIGHT = 100;

/** How many labels a breakdown shows, matching what the API returns. */
const BREAKDOWN_BARS = [styles.bar1, styles.bar2, styles.bar3, styles.bar4];

/** The dimensions the finished titles can be ranked by, beyond the genres. */
const BREAKDOWNS = ['byAuthor', 'byPublisher', 'byLanguage', 'byRank'] as const;
type BreakdownKey = (typeof BREAKDOWNS)[number];

function MonthlyBars({ values, labels, caption }: {
  values: number[];
  labels: string[];
  caption: string;
}) {
  const max = chartMax(values);

  return (
    <div className={styles.chart}>
      <svg
        viewBox={`0 0 ${PLOT_WIDTH} ${PLOT_HEIGHT}`}
        className={styles.plot}
        role="img"
        aria-label={caption}
      >
        {values.map((value, index) => {
          const height = (value / max) * PLOT_HEIGHT;
          return (
            <rect
              key={index}
              className={value > 0 ? styles.bar : styles.barEmpty}
              x={index * SLOT + (SLOT - BAR_WIDTH) / 2}
              // SVG grows downwards, so a bar starts where it stops being empty. An empty
              // month keeps a sliver of track so the year reads as twelve slots.
              y={PLOT_HEIGHT - Math.max(height, 2)}
              width={BAR_WIDTH}
              height={Math.max(height, 2)}
              rx="3"
            />
          );
        })}
      </svg>
      {/* Labels in HTML rather than in the SVG: they must not scale with the plot. */}
      <div className={styles.axis}>
        {labels.map((label, index) => (
          <span key={index} className={styles.axisLabel}>
            {label}
          </span>
        ))}
      </div>
    </div>
  );
}

/**
 * The running total against the goal: the curve is what has been read, the dashed line the
 * steady pace that meets the target by 31 December.
 */
function CumulativeLine({ values, target, caption }: {
  values: number[];
  target: number;
  caption: string;
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
      <polyline className={styles.curve} points={curve} />
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

/** One derived figure, or a dash when the window holds nothing to derive it from. */
function Figure({ value, label }: { value: string; label: string }) {
  return (
    <div className={styles.figure}>
      <div className={styles.figureValue}>{value}</div>
      <div className={styles.figureLabel}>{label}</div>
    </div>
  );
}

export function TimelineSection({ stats }: { stats: StatsDto }) {
  const { t } = useTranslation();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [metric, setMetric] = useState<Metric>('books');
  const [dimension, setDimension] = useState<BreakdownKey>('byAuthor');

  const timeline = useGetApiStatsTimeline({
    from: `${year}-01-01`,
    to: `${year}-12-31`,
    granularity: 'month',
  });

  if (timeline.isPending) return <Loading />;
  if (timeline.isError || !timeline.data) {
    return (
      <ErrorState message={t('stats.timeline.unavailable')} onRetry={() => void timeline.refetch()} />
    );
  }

  const months: MonthPoint[] = monthlySeries(timeline.data.points, year);
  const values = months.map((month) => month[metric]);
  const narrow = monthLabels('narrow');
  const long = monthLabels('long');

  // The goal is only a meaningful overlay on the series it is counted in, and only for the
  // year it was set for: a books target says nothing about a curve of pages.
  const goalTarget = stats.goalTarget ?? 0;
  const goalUnit = toUnit(stats.goalUnit);
  const goalMatches =
    year === thisYear && goalTarget > 0 && (goalUnit === 'PAGES') === (metric === 'pages');

  const bestMonth = months.reduce((best, month) => (month.books > best.books ? month : best), months[0]);
  const pagesPerDay = timeline.data.pagesPerDay ?? 0;
  const daysPerBook = timeline.data.daysPerBook;
  const readAnything = (timeline.data.books ?? 0) > 0;

  const years = [thisYear, thisYear - 1].map((value) => ({
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

        <Segmented<Metric>
          options={[
            { id: 'books', label: t('stats.timeline.metrics.books') },
            { id: 'pages', label: t('stats.timeline.metrics.pages') },
          ]}
          value={metric}
          onChange={setMetric}
        />

        {readAnything ? (
          <>
            <MonthlyBars
              values={values}
              labels={narrow}
              caption={t(`stats.timeline.chartLabel.${metric}`, { year })}
            />
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
            <Figure
              value={
                daysPerBook == null
                  ? t('stats.timeline.noFigure')
                  : daysPerBook.toLocaleString(activeLanguage(), { maximumFractionDigits: 1 })
              }
              label={t('stats.timeline.daysPerBook')}
            />
            <Figure
              value={long[(bestMonth?.month ?? 1) - 1]}
              label={t('stats.timeline.bestMonth', { books: bestMonth?.books ?? 0 })}
            />
          </div>
        </div>
      )}

      {/* A flat curve at zero under a panel that just said the year holds no reading says
          nothing twice: the running total only appears once there is a total. */}
      {goalMatches && readAnything && (
        <div className={styles.panel}>
          <div className={styles.panelTitle}>{t('stats.timeline.cumulativeTitle')}</div>
          <CumulativeLine
            values={[0, ...cumulative(values)]}
            target={goalTarget}
            caption={t('stats.timeline.cumulativeLabel', {
              target: goalTarget,
              unit: t(`goal.units.${goalUnit}`, { count: goalTarget }),
            })}
          />
          <p className={styles.legend}>{t('stats.timeline.cumulativeLegend')}</p>
        </div>
      )}

      <div className={styles.panel}>
        <div className={styles.panelTitle}>{t('stats.timeline.breakdownTitle')}</div>
        <Segmented<BreakdownKey>
          options={BREAKDOWNS.map((id) => ({ id, label: t(`stats.timeline.breakdowns.${id}`) }))}
          value={dimension}
          onChange={setDimension}
        />
        {(timeline.data[dimension] ?? []).length === 0 ? (
          <p className={styles.panelEmpty}>{t('stats.timeline.noBreakdown')}</p>
        ) : (
          <Breakdown rows={timeline.data[dimension] ?? []} />
        )}
      </div>
    </>
  );
}
