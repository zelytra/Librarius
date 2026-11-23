import { useId, useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiAdminCatalogEditsQueryKey,
  getGetApiAdminReportsQueryKey,
  postApiAdminCatalogEditsIdApprove,
  postApiAdminCatalogEditsIdReject,
  postApiAdminCatalogEditsIdRevert,
  putApiAdminReportsIdStatus,
  useGetApiAdminCatalogEdits,
  useGetApiAdminReports,
  type AdminReportDto,
  type CatalogEditDto,
  type FieldChangeDto,
} from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Icon } from '../../shared/ui/Icon';
import { Button, Chip, Segmented } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { toast } from '../../shared/ui/toast';
import styles from './AdminCatalog.module.css';

const PAGE_SIZE = 50;

const when = (value?: string) =>
  value ? new Date(value).toLocaleString(activeLanguage(), { dateStyle: 'medium', timeStyle: 'short' }) : '';

type Section = 'edits' | 'reports';

/**
 * The shared catalog's moderation (#618–#621): the edits members propose, and the reports they
 * file from the book screens — the bug reports of the whole app stay in their own tab.
 */
export function AdminCatalog() {
  const { t } = useTranslation();
  const [section, setSection] = useState<Section>('edits');
  return (
    <div className={styles.catalog}>
      <Segmented<Section>
        value={section}
        onChange={setSection}
        options={[
          { id: 'edits', label: t('admin.catalog.edits') },
          { id: 'reports', label: t('admin.catalog.reports') },
        ]}
      />
      {section === 'edits' ? <Edits /> : <Reports />}
    </div>
  );
}

// ── Edits ──────────────────────────────────────────────────────────────────

const EDIT_STATUSES = ['PENDING', 'APPLIED', 'REJECTED', 'REVERTED'] as const;
type EditStatus = (typeof EDIT_STATUSES)[number];

function Edits() {
  const { t } = useTranslation();
  // What waits for a decision comes first: that is what the tab is for.
  const [filter, setFilter] = useState<EditStatus | null>('PENDING');
  const { data, isPending, isError, refetch } = useGetApiAdminCatalogEdits({
    page: 0,
    size: PAGE_SIZE,
    ...(filter ? { status: filter } : {}),
  });

  const filters = (
    <div className={styles.filters} role="group" aria-label={t('admin.catalog.filter')}>
      {EDIT_STATUSES.map((status) => (
        <Chip key={status} selected={filter === status} onClick={() => setFilter(status)}>
          {t(`admin.catalog.editFilters.${status}`)}
        </Chip>
      ))}
      <Chip selected={filter === null} onClick={() => setFilter(null)}>
        {t('admin.status.all')}
      </Chip>
    </div>
  );

  if (isPending) return <>{filters}<Loading /></>;
  if (isError) return <>{filters}<ErrorState message={t('admin.catalog.error')} onRetry={() => void refetch()} /></>;
  const items = data?.items ?? [];
  if (items.length === 0) return <>{filters}<EmptyState icon="check_circle" title={t('admin.catalog.noEdits')} /></>;
  return (
    <>
      {filters}
      <p className={styles.count}>{t('admin.catalog.editCount', { count: data?.total ?? items.length })}</p>
      <ul className={styles.list}>
        {items.map((edit) => (
          <li key={edit.id}>
            <EditCard edit={edit} />
          </li>
        ))}
      </ul>
    </>
  );
}

function EditCard({ edit }: { edit: CatalogEditDto }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const noteId = useId();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);

  async function decide(decision: 'approve' | 'reject' | 'revert') {
    setSaving(true);
    try {
      const body = { note: note.trim() || undefined };
      if (decision === 'approve') await postApiAdminCatalogEditsIdApprove(edit.id!, body);
      else if (decision === 'reject') await postApiAdminCatalogEditsIdReject(edit.id!, body);
      else await postApiAdminCatalogEditsIdRevert(edit.id!, body);
      toast.success(t(`admin.catalog.decided.${decision}`));
      void queryClient.invalidateQueries({ queryKey: getGetApiAdminCatalogEditsQueryKey().slice(0, 1) });
    } catch {
      toast.error(t('admin.catalog.decisionFailed'));
    } finally {
      setSaving(false);
    }
  }

  const status = edit.status as EditStatus;
  return (
    <article className={styles.card}>
      <header className={styles.cardHead}>
        <h3 className={styles.cardTitle}>{edit.targetTitle ?? t('admin.catalog.gone')}</h3>
        <span className={styles.badge}>{t(`admin.catalog.editStatus.${status}`)}</span>
      </header>
      <p className={styles.meta}>
        <span>{t(`admin.catalog.targets.${edit.targetType}`)}</span>
        <span>{t('admin.catalog.by', { name: edit.submitter?.displayName ?? t('admin.reports.deleted') })}</span>
        <time dateTime={edit.createdAt}>{when(edit.createdAt)}</time>
      </p>

      <Verification edit={edit} />

      <dl className={styles.changes}>
        {(edit.changes ?? []).map((change) => (
          <Change key={change.field} change={change} />
        ))}
      </dl>

      {edit.reviewNote && <p className={styles.note}>{t('admin.catalog.reviewNote', { note: edit.reviewNote })}</p>}

      {(status === 'PENDING' || status === 'APPLIED') && (
        <div className={styles.decision}>
          <label htmlFor={noteId} className={styles.label}>
            {t('admin.catalog.noteLabel')}
          </label>
          <textarea
            id={noteId}
            rows={2}
            maxLength={1000}
            className={styles.input}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <div className={styles.actions}>
            {status === 'PENDING' ? (
              <>
                <Button variant="primary" size="compact" disabled={saving} onClick={() => void decide('approve')}>
                  <Icon name="check_circle" size={16} color="currentColor" />
                  {t('admin.catalog.approve')}
                </Button>
                <Button variant="secondary" size="compact" disabled={saving} onClick={() => void decide('reject')}>
                  <Icon name="do_not_disturb_on" size={16} color="currentColor" />
                  {t('admin.catalog.reject')}
                </Button>
              </>
            ) : (
              <Button variant="secondary" size="compact" disabled={saving} onClick={() => void decide('revert')}>
                <Icon name="replay" size={16} color="currentColor" />
                {t('admin.catalog.revert')}
              </Button>
            )}
            <Loading size="compact" pending={saving} />
          </div>
        </div>
      )}
    </article>
  );
}

/** Whether a store page was read, and what it said — told whatever the outcome (#620). */
function Verification({ edit }: { edit: CatalogEditDto }) {
  const { t } = useTranslation();
  if (edit.appliedByTrust) {
    return (
      <p className={`${styles.verification} ${styles.passed}`}>
        <Icon name="workspace_premium" size={16} color="currentColor" />
        <span>{t('admin.catalog.appliedByTrust')}</span>
      </p>
    );
  }
  if (edit.verification === 'NOT_RUN') {
    return <p className={styles.verification}>{t('admin.catalog.verification.NOT_RUN')}</p>;
  }
  const passed = edit.verification === 'PASSED';
  return (
    <p className={`${styles.verification} ${passed ? styles.passed : styles.failed}`}>
      <Icon name={passed ? 'check_circle' : 'error'} size={16} color="currentColor" />
      <span>
        {passed
          ? t('admin.catalog.verification.PASSED')
          : t('admin.catalog.verification.FAILED', {
              reason: t(`admin.catalog.verification.reasons.${edit.verificationDetail ?? 'FETCH_FAILED'}`),
            })}
      </span>
      {edit.verificationUrl && (
        <a href={edit.verificationUrl} target="_blank" rel="noopener noreferrer nofollow" className={styles.storeLink}>
          {hostOf(edit.verificationUrl)}
        </a>
      )}
    </p>
  );
}

function hostOf(url: string) {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

function Change({ change }: { change: FieldChangeDto }) {
  const { t } = useTranslation();
  const field = change.field ?? '';
  const label = field === 'saleLinks' ? t('catalogEdit.groups.sale') : t(`catalogEdit.fields.${field}`);
  const changedSince = (change.current ?? null) !== (change.before ?? null) && change.current !== change.after;
  return (
    <div className={styles.change}>
      <dt className={styles.field}>{label}</dt>
      <dd className={styles.values}>
        <Value field={field} value={change.before} kind="before" />
        <Icon name="arrow_forward" size={16} color="var(--faint)" />
        <Value field={field} value={change.after} kind="after" />
        {changedSince && (
          <span className={styles.since}>{t('admin.catalog.changedSince', { value: change.current ?? '—' })}</span>
        )}
      </dd>
    </div>
  );
}

function Value({ field, value, kind }: { field: string; value?: string; kind: 'before' | 'after' }) {
  const { t } = useTranslation();
  const className = kind === 'before' ? styles.before : styles.after;
  const srLabel = t(`admin.catalog.${kind}`);
  if (!value) {
    return (
      <span className={className}>
        <span className={styles.srOnly}>{srLabel} </span>
        {t('admin.catalog.empty')}
      </span>
    );
  }
  if (field === 'coverUrl') {
    return (
      <span className={className}>
        <img src={value} alt={t(`admin.catalog.cover.${kind}`)} className={styles.cover} loading="lazy" />
      </span>
    );
  }
  return (
    <span className={className}>
      <span className={styles.srOnly}>{srLabel} </span>
      {value.split('\n').map((line) => (
        <span key={line} className={styles.line}>
          {line}
        </span>
      ))}
    </span>
  );
}

// ── Reports ────────────────────────────────────────────────────────────────

const REPORT_STATUSES = ['OPEN', 'UPHELD', 'DISMISSED'] as const;
type ReportStatus = (typeof REPORT_STATUSES)[number];

function Reports() {
  const { t } = useTranslation();
  const [filter, setFilter] = useState<ReportStatus | null>('OPEN');
  const { data, isPending, isError, refetch } = useGetApiAdminReports({
    page: 0,
    size: PAGE_SIZE,
    ...(filter ? { status: filter } : {}),
  });

  const filters = (
    <div className={styles.filters} role="group" aria-label={t('admin.catalog.filter')}>
      {REPORT_STATUSES.map((status) => (
        <Chip key={status} selected={filter === status} onClick={() => setFilter(status)}>
          {t(`admin.catalog.reportFilters.${status}`)}
        </Chip>
      ))}
      <Chip selected={filter === null} onClick={() => setFilter(null)}>
        {t('admin.status.all')}
      </Chip>
    </div>
  );

  if (isPending) return <>{filters}<Loading /></>;
  if (isError) return <>{filters}<ErrorState message={t('admin.catalog.error')} onRetry={() => void refetch()} /></>;
  const items = data?.items ?? [];
  if (items.length === 0) return <>{filters}<EmptyState icon="check_circle" title={t('admin.catalog.noReports')} /></>;
  return (
    <>
      {filters}
      <p className={styles.count}>{t('admin.catalog.reportCount', { count: data?.total ?? items.length })}</p>
      <ul className={styles.list}>
        {items.map((report) => (
          <li key={report.id}>
            <ReportCard report={report} />
          </li>
        ))}
      </ul>
    </>
  );
}

function ReportCard({ report }: { report: AdminReportDto }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [saving, setSaving] = useState(false);

  async function setStatus(status: ReportStatus) {
    setSaving(true);
    try {
      await putApiAdminReportsIdStatus(report.id!, { status });
      toast.success(t(`admin.catalog.reportSaved.${status}`));
      void queryClient.invalidateQueries({ queryKey: getGetApiAdminReportsQueryKey().slice(0, 1) });
    } catch {
      toast.error(t('admin.catalog.decisionFailed'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className={styles.card}>
      <header className={styles.cardHead}>
        <h3 className={styles.cardTitle}>{report.targetTitle ?? t('admin.catalog.gone')}</h3>
        <span className={styles.badge}>{t(`admin.catalog.reportStatus.${report.status}`)}</span>
      </header>
      <p className={styles.meta}>
        <span>{t(`admin.catalog.reportTargets.${report.targetType}`)}</span>
        <span>{t('admin.catalog.reportedBy', { name: report.reporter?.displayName ?? t('admin.reports.deleted') })}</span>
        <time dateTime={report.createdAt}>{when(report.createdAt)}</time>
      </p>
      <p className={styles.reason}>{t(`report.reasons.${report.reason}`)}</p>
      {report.targetDetail && <p className={styles.detail}>{report.targetDetail}</p>}
      {report.comment && <p className={styles.quote}>{t('notifications.quote', { comment: report.comment })}</p>}
      <div className={styles.actions}>
        {report.seriesId && (
          <Link to={`/series/${report.seriesId}`} className={styles.open}>
            {t('admin.catalog.openSeries')}
          </Link>
        )}
        <Button
          variant={report.status === 'UPHELD' ? 'primary' : 'secondary'}
          size="compact"
          aria-pressed={report.status === 'UPHELD'}
          disabled={saving}
          onClick={() => void setStatus('UPHELD')}
        >
          {t('admin.catalog.uphold')}
        </Button>
        <Button
          variant={report.status === 'DISMISSED' ? 'primary' : 'secondary'}
          size="compact"
          aria-pressed={report.status === 'DISMISSED'}
          disabled={saving}
          onClick={() => void setStatus('DISMISSED')}
        >
          {t('admin.catalog.dismiss')}
        </Button>
        <Loading size="compact" pending={saving} />
      </div>
    </article>
  );
}
