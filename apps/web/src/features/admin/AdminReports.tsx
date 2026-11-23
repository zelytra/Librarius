import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiAdminFeedbackIdQueryKey,
  getGetApiAdminFeedbackQueryKey,
  getGetApiAdminPendingQueryKey,
  putApiAdminFeedbackIdStatus,
  useGetApiAdminFeedback,
  useGetApiAdminFeedbackId,
  type FeedbackDto,
} from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Icon } from '../../shared/ui/Icon';
import { Modal } from '../../shared/ui/Modal';
import { Button, Chip } from '../../shared/ui/primitives';
import { toast } from '../../shared/ui/toast';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import styles from './AdminPage.module.css';

const PAGE_SIZE = 50;

/** Where an admin stands with a report (#495), each told by an icon and a word, never colour alone. */
const STATUSES = ['NEW', 'SEEN', 'HANDLED', 'IRRELEVANT'] as const;
type Status = (typeof STATUSES)[number];
const STATUS_ICON: Record<Status, string> = {
  NEW: 'radio_button_unchecked',
  SEEN: 'visibility',
  HANDLED: 'check_circle',
  IRRELEVANT: 'do_not_disturb_on',
};
const statusOf = (report: FeedbackDto): Status =>
  (STATUSES as readonly string[]).includes(report.status ?? '') ? (report.status as Status) : 'NEW';

function StatusBadge({ status }: { status: Status }) {
  const { t } = useTranslation();
  return (
    <span className={`${styles.status} ${styles[`status${status}`]}`}>
      <Icon name={STATUS_ICON[status]} size={14} color="currentColor" />
      {t(`admin.status.${status}`)}
    </span>
  );
}

/** Who a report comes from: a member, the web app or the API. */
const originKey = (report: FeedbackDto) =>
  report.source === 'USER' ? `admin.kinds.${report.kind}` : `admin.sources.${report.source}`;

const when = (value?: string) =>
  value ? new Date(value).toLocaleString(activeLanguage(), { dateStyle: 'medium', timeStyle: 'short' }) : '';

/** The reports, the latest seen first (#489, #490); one opens with its logs. */
export function AdminReports() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<Status | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const queryClient = useQueryClient();
  // Opening a new report marked it seen: the list is read again once the dialog closes.
  const close = () => {
    setOpenId(null);
    void queryClient.invalidateQueries({ queryKey: getGetApiAdminFeedbackQueryKey().slice(0, 1) });
    void queryClient.invalidateQueries({ queryKey: getGetApiAdminPendingQueryKey() });
  };
  const { data, isPending, isError, refetch } = useGetApiAdminFeedback({
    page,
    size: PAGE_SIZE,
    ...(filter ? { status: filter } : {}),
  });
  const choose = (next: Status | null) => {
    setFilter(next);
    setPage(0);
  };

  // The filter stays on screen whatever the list holds: an empty "handled" list is no reason
  // to lose the way back to the others.
  const filters = (
    <div className={styles.filters} role="group" aria-label={t('admin.status.filter')}>
      <Chip selected={filter === null} onClick={() => choose(null)}>
        {t('admin.status.all')}
      </Chip>
      {STATUSES.map((status) => (
        <Chip key={status} icon={STATUS_ICON[status]} selected={filter === status} onClick={() => choose(status)}>
          {t(`admin.status.filters.${status}`)}
        </Chip>
      ))}
    </div>
  );

  if (isPending) return <>{filters}<Loading /></>;
  if (isError) return <>{filters}<ErrorState message={t('admin.reports.error')} onRetry={() => void refetch()} /></>;
  const reports = data?.items ?? [];
  if (reports.length === 0) return <>{filters}<EmptyState icon="check_circle" title={t('admin.reports.empty')} /></>;
  const pages = Math.ceil((data?.total ?? 0) / PAGE_SIZE);

  return (
    <>
      {filters}
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
                <StatusBadge status={statusOf(report)} />
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
      {openId && <ReportDetail id={openId} onClose={close} />}
    </>
  );
}

function ReportDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [saving, setSaving] = useState(false);
  // Opening a new report marks it seen on the server: the list behind is refreshed for it.
  const { data: report, isPending, isError } = useGetApiAdminFeedbackId(id);
  const [chosen, setChosen] = useState<Status | null>(null);
  const current = chosen ?? (report ? statusOf(report) : null);

  async function setStatus(status: Status) {
    setSaving(true);
    try {
      await putApiAdminFeedbackIdStatus(id, { status });
      setChosen(status);
      toast.success(t(`admin.status.saved.${status}`));
      void queryClient.invalidateQueries({ queryKey: getGetApiAdminFeedbackIdQueryKey(id) });
      // The lists, and the navigation's count of unread reports with them (#565, #635).
      void queryClient.invalidateQueries({ queryKey: getGetApiAdminFeedbackQueryKey().slice(0, 1) });
      void queryClient.invalidateQueries({ queryKey: getGetApiAdminPendingQueryKey() });
    } catch {
      toast.error(t('admin.status.failed'));
    } finally {
      setSaving(false);
    }
  }

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
          <div className={styles.triage} role="group" aria-label={t('admin.status.label')}>
            {current && <StatusBadge status={current} />}
            <div className={styles.triageActions}>
              {(['SEEN', 'HANDLED', 'IRRELEVANT'] as const).map((status) => (
                <Button
                  key={status}
                  variant={current === status ? 'primary' : 'secondary'}
                  size="compact"
                  aria-pressed={current === status}
                  disabled={saving}
                  onClick={() => void setStatus(status)}
                >
                  <Icon name={STATUS_ICON[status]} size={16} color="currentColor" />
                  {t(`admin.status.mark.${status}`)}
                </Button>
              ))}
            </div>
          </div>
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
