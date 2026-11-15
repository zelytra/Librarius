import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  useGetApiAdminFeedback,
  useGetApiAdminFeedbackId,
  type FeedbackDto,
} from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import styles from './AdminPage.module.css';

const PAGE_SIZE = 50;

/** Who a report comes from: a member, the web app or the API. */
const originKey = (report: FeedbackDto) =>
  report.source === 'USER' ? `admin.kinds.${report.kind}` : `admin.sources.${report.source}`;

const when = (value?: string) =>
  value ? new Date(value).toLocaleString(activeLanguage(), { dateStyle: 'medium', timeStyle: 'short' }) : '';

/** The reports, the latest seen first (#489, #490); one opens with its logs. */
export function AdminReports() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);
  const { data, isPending, isError, refetch } = useGetApiAdminFeedback({ page, size: PAGE_SIZE });

  if (isPending) return <Loading />;
  if (isError) return <ErrorState message={t('admin.reports.error')} onRetry={() => void refetch()} />;
  const reports = data?.items ?? [];
  if (reports.length === 0) return <EmptyState icon="check_circle" title={t('admin.reports.empty')} />;
  const pages = Math.ceil((data?.total ?? 0) / PAGE_SIZE);

  return (
    <>
      <p className={styles.count}>{t('admin.reports.total', { count: data?.total ?? 0 })}</p>
      <ul className={styles.reports}>
        {reports.map((report) => (
          <li key={report.id}>
            <button type="button" className={styles.report} onClick={() => setOpenId(report.id!)}>
              <span className={styles.reportHead}>
                <span className={`${styles.badge} ${report.source === 'USER' ? '' : styles.badgeError}`}>
                  {t(originKey(report))}
                </span>
                {(report.occurrences ?? 1) > 1 && (
                  <span className={styles.occurrences}>{t('admin.reports.occurrences', { count: report.occurrences })}</span>
                )}
                <span className={styles.date}>{when(report.lastSeenAt)}</span>
              </span>
              <span className={styles.message}>{report.message}</span>
              <span className={styles.meta}>
                {report.source === 'USER'
                  ? t('admin.reports.by', { name: report.reporterName ?? t('admin.reports.deleted') })
                  : report.page}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {pages > 1 && (
        <div className={styles.pager}>
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
            {t('admin.reports.previous')}
          </Button>
          <span className={styles.count}>{t('admin.reports.page', { page: page + 1, pages })}</span>
          <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
            {t('admin.reports.next')}
          </Button>
        </div>
      )}
      {openId && <ReportDetail id={openId} onClose={() => setOpenId(null)} />}
    </>
  );
}

function ReportDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation();
  const { data: report, isPending, isError } = useGetApiAdminFeedbackId(id);
  const rows: [string, string | undefined][] = report
    ? [
        [t('admin.detail.origin'), t(originKey(report))],
        [t('admin.detail.reporter'), report.reporterName ?? (report.source === 'USER' ? t('admin.reports.deleted') : '—')],
        [t('admin.detail.first'), when(report.createdAt)],
        [t('admin.detail.last'), when(report.lastSeenAt)],
        [t('admin.detail.occurrences'), String(report.occurrences ?? 1)],
        [t('admin.detail.page'), report.page],
        [t('admin.detail.version'), report.appVersion],
        [t('admin.detail.browser'), report.userAgent],
      ]
    : [];

  return (
    <Modal title={t('admin.detail.title')} onClose={onClose}>
      {isPending && <Loading />}
      {isError && <p className={styles.error}>{t('admin.reports.error')}</p>}
      {report && (
        <div className={styles.detail}>
          <p className={styles.detailMessage}>{report.message}</p>
          <dl className={styles.fields}>
            {rows
              .filter(([, value]) => value)
              .map(([label, value]) => (
                <div key={label} className={styles.field}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
          </dl>
          {report.details && (
            <>
              <h3 className={styles.subtitle}>{t('admin.detail.logs')}</h3>
              <pre className={styles.logs}>{report.details}</pre>
            </>
          )}
        </div>
      )}
    </Modal>
  );
}
