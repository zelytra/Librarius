import { useTranslation } from 'react-i18next';
import { useGetApiAdminMonitoring, type MonitoringDayDto } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Icon } from '../../shared/ui/Icon';
import { ErrorState, Loading } from '../../shared/ui/states';
import styles from './AdminPage.module.css';

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
            { value: (d) => d.reports ?? 0, className: styles.barReports },
            { value: (d) => d.errors ?? 0, className: styles.barErrors },
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
          series={[{ value: (d) => d.activeUsers ?? 0, className: styles.barActive }]}
        />
      </section>

      <section className={styles.panel}>
        <h3 className={styles.subtitle}>{t('admin.monitoring.servicesTitle')}</h3>
        <ul className={styles.services}>
          {(data.services ?? []).map((service) => (
            <li key={service.name} className={styles.service}>
              <Icon
                name={service.up ? 'check_circle' : 'error'}
                size={20}
                color={service.up ? 'var(--accent-deep)' : 'var(--rose)'}
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

/**
 * One column per day, the series stacked; the figures are read out as a whole by the
 * label, and each column's own on hover.
 */
function Bars({
  days,
  label,
  series,
}: {
  days: MonitoringDayDto[];
  label: string;
  series: { value: (day: MonitoringDayDto) => number; className: string }[];
}) {
  const { t } = useTranslation();
  const max = Math.max(1, ...days.map((d) => series.reduce((sum, s) => sum + s.value(d), 0)));
  return (
    <div className={styles.bars} role="img" aria-label={label}>
      {days.map((day) => (
        <div
          key={day.day}
          className={styles.barColumn}
          title={t('admin.monitoring.barTitle', {
            date: new Date(`${day.day}T00:00:00`).toLocaleDateString(activeLanguage(), { day: 'numeric', month: 'short' }),
            values: series.map((s) => s.value(day)).join(' + '),
          })}
        >
          {series.map((s, i) => (
            <span key={i} className={s.className} style={{ height: `${(s.value(day) / max) * 100}%` }} />
          ))}
        </div>
      ))}
    </div>
  );
}
