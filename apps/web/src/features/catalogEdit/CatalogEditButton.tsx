import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiCatalogEditsFieldsQueryKey,
  useGetApiCatalogEditsFields,
  useGetApiMe,
  usePostApiCatalogEdits,
  type CatalogEditDto,
  type CatalogEditFieldsDto,
  type CatalogEditTarget,
} from '../../api/generated/librarius';
import { apiErrorStatus } from '../../shared/apiClient';
import { Icon } from '../../shared/ui/Icon';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { toast } from '../../shared/ui/toast';
import styles from './CatalogEdit.module.css';

type Input = 'text' | 'textarea' | 'number' | 'date' | 'url' | 'series-status';

interface FieldSpec {
  name: string;
  input: Input;
}

/** The fields of a book, grouped as the reader thinks of them: the work, then this edition. */
const EDITION_GROUPS: { title: string; fields: FieldSpec[] }[] = [
  {
    title: 'work',
    fields: [
      { name: 'title', input: 'text' },
      { name: 'authors', input: 'text' },
      { name: 'synopsis', input: 'textarea' },
      { name: 'genres', input: 'text' },
      { name: 'originalYear', input: 'number' },
      { name: 'volumeNumber', input: 'number' },
    ],
  },
  {
    title: 'edition',
    fields: [
      { name: 'isbn13', input: 'text' },
      { name: 'isbn10', input: 'text' },
      { name: 'publisher', input: 'text' },
      { name: 'language', input: 'text' },
      { name: 'pageCount', input: 'number' },
      { name: 'format', input: 'text' },
      { name: 'releaseDate', input: 'date' },
      { name: 'coverUrl', input: 'url' },
      { name: 'listPrice', input: 'text' },
    ],
  },
];

const SERIES_GROUPS: { title: string; fields: FieldSpec[] }[] = [
  {
    title: 'series',
    fields: [
      { name: 'title', input: 'text' },
      { name: 'originalTitle', input: 'text' },
      { name: 'totalVolumes', input: 'number' },
      { name: 'status', input: 'series-status' },
      { name: 'synopsis', input: 'textarea' },
      { name: 'coverUrl', input: 'url' },
    ],
  },
];

const SERIES_STATUSES = ['ONGOING', 'COMPLETED', 'HIATUS'] as const;

/**
 * "Proposer une modification" on a shared record (#618): an edition — with its work's fields —
 * or a series. The proposal waits for an admin, unless a store page confirms the book (#620).
 */
export function CatalogEditButton({ targetType, targetId }: { targetType: CatalogEditTarget; targetId: string }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className={styles.trigger} onClick={() => setOpen(true)}>
        <Icon name="edit" size={16} color="var(--faint)" />
        {t('catalogEdit.trigger')}
      </button>
      {open && <EditDialog targetType={targetType} targetId={targetId} onClose={() => setOpen(false)} />}
    </>
  );
}

function EditDialog({
  targetType,
  targetId,
  onClose,
}: {
  targetType: CatalogEditTarget;
  targetId: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const params = { targetType, targetId };
  // Always the latest values: a form seeded from a stale copy would propose undoing a change.
  const current = useGetApiCatalogEditsFields(params, { query: { staleTime: 0, gcTime: 0 } });

  return (
    <Modal title={t('catalogEdit.title')} description={t('catalogEdit.description')} onClose={onClose}>
      {current.isPending && <Loading />}
      {current.isError && <ErrorState message={t('catalogEdit.loadError')} onRetry={() => void current.refetch()} />}
      {current.data &&
        (current.data.pendingByMe ? (
          <>
            <p className={styles.notice}>{t('catalogEdit.alreadyPending')}</p>
            <Button variant="secondary" size="block" onClick={onClose}>
              {t('common.close')}
            </Button>
          </>
        ) : (
          <EditForm targetType={targetType} targetId={targetId} current={current.data} onClose={onClose} />
        ))}
    </Modal>
  );
}

function EditForm({
  targetType,
  targetId,
  current,
  onClose,
}: {
  targetType: CatalogEditTarget;
  targetId: string;
  current: CatalogEditFieldsDto;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const formId = useId();
  const initial = current.fields ?? {};
  const initialLinks = (current.saleLinks ?? []).map((link) => link.url ?? '');
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(Object.entries(initial).map(([name, value]) => [name, value ?? ''])),
  );
  const [links, setLinks] = useState<string[]>(initialLinks);
  const [refused, setRefused] = useState<'refused' | 'conflict' | 'failed' | null>(null);
  // A trusted member's change applies at once (#631): said before they send it.
  const trusted = Boolean(useGetApiMe().data?.trusted);
  const isEdition = targetType === 'EDITION';

  const changed = Object.fromEntries(
    Object.entries(values).filter(([name, value]) => value.trim() !== (initial[name] ?? '').trim()),
  );
  const cleanLinks = links.map((link) => link.trim()).filter(Boolean);
  const linksChanged = isEdition && cleanLinks.join('\n') !== initialLinks.join('\n');
  const nothingChanged = Object.keys(changed).length === 0 && !linksChanged;

  const { mutate, isPending } = usePostApiCatalogEdits({
    mutation: {
      onSuccess: (edit) => {
        announce(edit);
        // An applied edit changes what every screen showing the record reads.
        if (edit.status === 'APPLIED') void queryClient.invalidateQueries();
        else void queryClient.invalidateQueries({ queryKey: getGetApiCatalogEditsFieldsQueryKey({ targetType, targetId }) });
        onClose();
      },
      onError: (error: unknown) => {
        const status = apiErrorStatus(error);
        setRefused(status === 409 ? 'conflict' : status === 400 ? 'refused' : 'failed');
      },
    },
  });

  function announce(edit: CatalogEditDto) {
    if (edit.status === 'APPLIED') {
      toast.success(
        t(
          edit.appliedByTrust
            ? 'catalogEdit.done.trusted'
            : edit.verification === 'PASSED'
              ? 'catalogEdit.done.verified'
              : 'catalogEdit.done.applied',
        ),
      );
    } else if (edit.verification === 'FAILED') {
      toast.info(t('catalogEdit.done.unverified'));
    } else {
      toast.success(t('catalogEdit.done.pending'));
    }
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (nothingChanged) return;
    setRefused(null);
    mutate({
      data: {
        targetType,
        targetId,
        fields: changed,
        ...(linksChanged ? { saleLinks: cleanLinks } : {}),
      },
    });
  }

  return (
    <form id={formId} className={styles.form} onSubmit={submit} noValidate>
      {trusted && <p className={styles.trustedNote}>{t('catalogEdit.trustedNote')}</p>}
      {(isEdition ? EDITION_GROUPS : SERIES_GROUPS).map((group) => (
        <fieldset key={group.title} className={styles.group}>
          <legend className={styles.legend}>{t(`catalogEdit.groups.${group.title}`)}</legend>
          {group.fields.map((field) => (
            <FieldInput
              key={field.name}
              spec={field}
              label={t(`catalogEdit.fields.${field.name}`)}
              value={values[field.name] ?? ''}
              onChange={(value) => setValues((before) => ({ ...before, [field.name]: value }))}
            />
          ))}
        </fieldset>
      ))}

      {isEdition && (
        <fieldset className={styles.group}>
          <legend className={styles.legend}>{t('catalogEdit.groups.sale')}</legend>
          <p className={styles.hint}>{t('catalogEdit.saleHint')}</p>
          {links.map((link, index) => (
            <div key={index} className={styles.linkRow}>
              <input
                type="url"
                inputMode="url"
                className={styles.input}
                value={link}
                placeholder={t('catalogEdit.linkPlaceholder')}
                aria-label={t('catalogEdit.linkLabel', { n: index + 1 })}
                onChange={(event) => setLinks((before) => before.map((l, i) => (i === index ? event.target.value : l)))}
              />
              <button
                type="button"
                className={styles.remove}
                aria-label={t('catalogEdit.removeLink', { n: index + 1 })}
                onClick={() => setLinks((before) => before.filter((_, i) => i !== index))}
              >
                <Icon name="close" size={18} color="var(--ink-soft)" />
              </button>
            </div>
          ))}
          {links.length < 8 && (
            <Button type="button" variant="secondary" size="compact" onClick={() => setLinks((before) => [...before, ''])}>
              <Icon name="add" size={16} color="currentColor" />
              {t('catalogEdit.addLink')}
            </Button>
          )}
        </fieldset>
      )}

      {refused && (
        <p role="alert" className={styles.error}>
          {t(`catalogEdit.errors.${refused}`)}
        </p>
      )}

      <div className={styles.actions}>
        <Button type="submit" variant="primary" size="block" disabled={nothingChanged || isPending}>
          {t('catalogEdit.submit')}
          <Loading size="compact" pending={isPending} />
        </Button>
        <Button type="button" variant="ghost" size="block" onClick={onClose}>
          {t('common.cancel')}
        </Button>
      </div>
    </form>
  );
}

function FieldInput({
  spec,
  label,
  value,
  onChange,
}: {
  spec: FieldSpec;
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation();
  const id = useId();
  let control;
  if (spec.input === 'textarea') {
    control = (
      <textarea id={id} rows={4} className={styles.input} value={value} onChange={(e) => onChange(e.target.value)} />
    );
  } else if (spec.input === 'series-status') {
    control = (
      <select id={id} className={styles.input} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{t('catalogEdit.unknown')}</option>
        {SERIES_STATUSES.map((status) => (
          <option key={status} value={status}>
            {t(`catalogEdit.seriesStatus.${status}`)}
          </option>
        ))}
      </select>
    );
  } else {
    control = (
      <input
        id={id}
        // Numbers stay text with a numeric keyboard: a number input turns "12a" into "" silently.
        type={spec.input === 'date' ? 'date' : spec.input === 'url' ? 'url' : 'text'}
        inputMode={spec.input === 'number' ? 'numeric' : undefined}
        className={styles.input}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  }
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      {control}
    </div>
  );
}
