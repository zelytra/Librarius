import { lazy, Suspense, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGetApiAdminMonitoring, type MonitoringDayDto } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Icon } from '../../shared/ui/Icon';
import { ErrorState, Loading } from '../../shared/ui/states';
import styles from './AdminPage.module.css';

// The globe carries the land outlines: its own chunk, fetched when the monitoring is opened (#573).
const AdminGlobe = lazy(() => import('./AdminGlobe').then((m) => ({ default: m.AdminGlobe })));

/** Refreshed every half minute: "right now" has to stay true while the screen is open. */
const REFRESH_MS = 30_000;

/**
 * The monitoring of the instance (#491): members active right now, reports and errors and
 * active members over the last 30 days, and whether the database and Keycloak answer.
 */
export function AdminMonitoring() {
  const { t } = useTranslation();
  const { data, isPending, isError, refetch } = useGetApiAdminMonitoring({
    query: { refetchInterval: REFRESH_MS },
  });

  if (isPending) return <Loading />;
  if (isError || !data) return <ErrorState message={t('admin.monitoring.error')} onRetry={() => void refetch()} />;
  const days = data.days ?? [];
  const reports = days.reduce((sum, d) => sum + (d.reports ?? 0), 0);
  const errors = days.reduce((sum, d) => sum + (d.errors ?? 0), 0);

  return (
    <div className={styles.monitoring}>
      <div className={styles.tiles}>
        <div className={styles.tile}>
          <span className={styles.tileValue}>{data.activeNow}</span>
          <span className={styles.tileLabel}>{t('admin.monitoring.activeNow')}</span>
        </div>
        <div className={styles.tile}>
          <span className={styles.tileValue}>{reports}</span>
          <span className={styles.tileLabel}>{t('admin.monitoring.reports30')}</span>
        </div>
        <div className={styles.tile}>
          <span className={styles.tileValue}>{errors}</span>
          <span className={styles.tileLabel}>{t('admin.monitoring.errors30')}</span>
        </div>
      </div>
      <p className={styles.note}>{t('admin.monitoring.note')}</p>

      <section className={styles.panel}>
        <h3 className={styles.subtitle}>{t('admin.monitoring.reportsTitle')}</h3>
        <Bars
          days={days}
          label={t('admin.monitoring.reportsChart', { reports, errors })}
          series={[
            { name: t('admin.monitoring.legendReports'), value: (d) => d.reports ?? 0, className: styles.barReports },
            { name: t('admin.monitoring.legendErrors'), value: (d) => d.errors ?? 0, className: styles.barErrors },
          ]}
        />
        <p className={styles.legend}>
          <span className={`${styles.swatch} ${styles.barReports}`} /> {t('admin.monitoring.legendReports')}
          <span className={`${styles.swatch} ${styles.barErrors}`} /> {t('admin.monitoring.legendErrors')}
        </p>
      </section>

      <section className={styles.panel}>
        <h3 className={styles.subtitle}>{t('admin.monitoring.activeTitle')}</h3>
        <Bars
          days={days}
          label={t('admin.monitoring.activeChart', {
            max: Math.max(0, ...days.map((d) => d.activeUsers ?? 0)),
            today: days.at(-1)?.activeUsers ?? 0,
          })}
          series={[{ name: t('admin.monitoring.legendActive'), value: (d) => d.activeUsers ?? 0, className: styles.barActive }]}
        />
      </section>

      <section className={styles.panel}>
        <h3 className={styles.subtitle}>{t('admin.globe.title')}</h3>
        <Suspense fallback={<Loading />}>
          <AdminGlobe />
        </Suspense>
      </section>

      <section className={styles.panel}>
        <h3 className={styles.subtitle}>{t('admin.monitoring.servicesTitle')}</h3>
        <ul className={styles.services}>
          {(data.services ?? []).map((service) => (
            <li key={service.name} className={styles.service}>
              <Icon
                name={service.up ? 'check_circle' : 'error'}
                size={20}
                color={service.up ? 'var(--success-ink)' : 'var(--danger-ink)'}
              />
              <span className={styles.serviceName}>{t(`admin.services.${service.name}`, service.name!)}</span>
              <span className={service.up ? styles.serviceUp : styles.serviceDown}>
                {service.up ? t('admin.monitoring.up') : t('admin.monitoring.down', { detail: service.detail ?? '' })}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

const shortDate = (day: string | undefined) =>
  day ? new Date(`${day}T00:00:00`).toLocaleDateString(activeLanguage(), { day: 'numeric', month: 'short' }) : '';

/**
 * One column per day, the series stacked, between a value axis and a time axis (#574). Each
 * column is a focusable target that reads out its day and its figures — in a tooltip on hover
 * or focus, and to a screen reader through its name; the whole chart is summed up by `label`.
 */
function Bars({
  days,
  label,
  series,
}: {
  days: MonitoringDayDto[];
  label: string;
  series: { name: string; value: (day: MonitoringDayDto) => number; className: string }[];
}) {
  const [shown, setShown] = useState<number | null>(null);
  const totals = days.map((d) => series.reduce((sum, s) => sum + s.value(d), 0));
  const max = Math.max(1, ...totals);
  const describe = (day: MonitoringDayDto) =>
    [shortDate(day.day), ...series.map((s) => `${s.name} ${s.value(day)}`)].join(' · ');
  const ticks = [...new Set([max, Math.round(max / 2), 0])];
  const middle = Math.floor((days.length - 1) / 2);

  return (
    <figure className={styles.chart} aria-label={label}>
      <div className={styles.yAxis} data-testid="y-axis" aria-hidden="true">
        {ticks.map((tick) => (
          <span key={tick} style={{ bottom: `${(tick / max) * 100}%` }}>{tick}</span>
        ))}
      </div>
      <div className={styles.bars} onMouseLeave={() => setShown(null)}>
        {days.map((day, index) => (
          <button
            key={day.day}
            type="button"
            className={styles.barColumn}
            aria-label={describe(day)}
            onMouseEnter={() => setShown(index)}
            onMouseLeave={() => setShown(null)}
            onFocus={() => setShown(index)}
            onBlur={() => setShown(null)}
          >
            {series.map((s, i) => (
              <span key={i} className={s.className} style={{ height: `${(s.value(day) / max) * 100}%` }} />
            ))}
            {shown === index && (
              <span
                role="tooltip"
                className={styles.tooltip}
                // Anchored to the side it would otherwise run off, on a narrow screen.
                data-edge={index < days.length / 3 ? 'start' : index >= (days.length * 2) / 3 ? 'end' : undefined}
              >
                {describe(day)}
              </span>
            )}
          </button>
        ))}
      </div>
      <div className={styles.xAxis} data-testid="x-axis" aria-hidden="true">
        {[0, middle, days.length - 1]
          .filter((i, at, all) => i >= 0 && all.indexOf(i) === at)
          .map((i) => (
            <span key={i} style={{ left: `${((i + 0.5) / days.length) * 100}%` }}>{shortDate(days[i]?.day)}</span>
          ))}
      </div>
    </figure>
  );
}
