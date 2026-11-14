import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiViewsQueryKey,
  useDeleteApiViewsId,
  usePostApiViews,
  usePutApiViewsId,
  type LibraryViewDto,
  type LibraryViewInput,
} from '../../api/generated/librarius';
import { Button, Chip } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { Modal } from '../../shared/ui/Modal';
import { toast } from '../../shared/ui/toast';
import styles from './ViewsBar.module.css';

/** What the screen shows right now, which a view saves (all of it but the name). */
export type ViewSettings = Omit<LibraryViewInput, 'name'>;

/**
 * The reader's saved views (#451) as one row of chips over the library: "Toute la
 * bibliothèque", then each view, then a button that saves what the screen shows as a new one.
 * The view in force can be renamed, overwritten with the screen's settings, or deleted.
 */
export function ViewsBar({
  views,
  activeId,
  current,
  onSelect,
}: {
  views: LibraryViewDto[];
  activeId: string | null;
  current: ViewSettings;
  onSelect: (view: LibraryViewDto | null) => void;
}) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState<LibraryViewDto | 'new' | null>(null);
  const active = views.find((v) => v.id === activeId) ?? null;

  return (
    <div className={styles.bar}>
      <div className={`scroll-x ${styles.chips}`} role="toolbar" aria-label={t('views.title')}>
        <Chip selected={!active} onClick={() => onSelect(null)}>
          {t('views.all')}
        </Chip>
        {views.map((view) => (
          <Chip key={view.id} selected={view.id === activeId} onClick={() => onSelect(view)}>
            {view.name}
          </Chip>
        ))}
        {active && (
          <button type="button" className={styles.action} onClick={() => setEditing(active)}>
            <Icon name="tune" size={15} color="var(--accent-deep)" />
            {t('views.edit')}
          </button>
        )}
        <button type="button" className={styles.action} onClick={() => setEditing('new')}>
          <Icon name="add" size={15} color="var(--accent-deep)" />
          {t('views.new')}
        </button>
      </div>
      {editing && (
        <ViewEditor
          view={editing === 'new' ? null : editing}
          current={current}
          onClose={() => setEditing(null)}
          onSaved={(view) => {
            setEditing(null);
            if (view) onSelect(view);
            else onSelect(null);
          }}
        />
      )}
    </div>
  );
}

function ViewEditor({
  view,
  current,
  onClose,
  onSaved,
}: {
  view: LibraryViewDto | null;
  current: ViewSettings;
  onClose: () => void;
  /** The view saved, or null once it is deleted. */
  onSaved: (view: LibraryViewDto | null) => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [name, setName] = useState(view?.name ?? '');
  const [byCategory, setByCategory] = useState(view?.groupByCategory ?? Boolean(current.groupByCategory));
  // A view being edited keeps its own settings unless the reader asks for the screen's.
  const [takeCurrent, setTakeCurrent] = useState(view == null);
  const refresh = () => void queryClient.invalidateQueries({ queryKey: getGetApiViewsQueryKey() });
  const failed = () => toast.error(t('toasts.failed'));

  const { mutate: create, isPending: creating } = usePostApiViews({
    mutation: {
      onSuccess: (saved) => {
        refresh();
        toast.success(t('views.created', { name: saved.name }), 'check_circle');
        onSaved(saved);
      },
      onError: failed,
    },
  });
  const { mutate: update, isPending: updating } = usePutApiViewsId({
    mutation: {
      onSuccess: (saved) => {
        refresh();
        toast.success(t('views.saved', { name: saved.name }), 'check_circle');
        onSaved(saved);
      },
      onError: failed,
    },
  });
  const { mutate: remove, isPending: removing } = useDeleteApiViewsId({
    mutation: {
      onSuccess: () => {
        refresh();
        toast.info(t('views.deleted'), 'delete');
        onSaved(null);
      },
      onError: failed,
    },
  });

  const busy = creating || updating || removing;
  const save = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    const settings: ViewSettings = takeCurrent || !view
      ? current
      : { kind: view.kind as ViewSettings['kind'], shelf: view.shelf, sort: view.sort, layout: view.layout };
    const data: LibraryViewInput = { ...settings, name: trimmed, groupByCategory: byCategory };
    if (view?.id) update({ id: view.id, data });
    else create({ data });
  };

  return (
    <Modal
      title={t(view ? 'views.editTitle' : 'views.newTitle')}
      onClose={onClose}
      description={t('views.explain')}
    >
      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <label className={styles.field}>
          <span>{t('views.name')}</span>
          <input value={name} maxLength={60} onChange={(e) => setName(e.target.value)} className={styles.input} />
        </label>
        <label className={styles.check}>
          <input type="checkbox" checked={byCategory} onChange={(e) => setByCategory(e.target.checked)} />
          {t('views.byCategory')}
        </label>
        {view && (
          <label className={styles.check}>
            <input type="checkbox" checked={takeCurrent} onChange={(e) => setTakeCurrent(e.target.checked)} />
            {t('views.takeCurrent')}
          </label>
        )}
        <div className={styles.buttons}>
          {view?.id && (
            <Button type="button" variant="secondary" disabled={busy} onClick={() => remove({ id: view.id! })}>
              {t('views.delete')}
            </Button>
          )}
          <Button type="submit" variant="primary" disabled={busy || !name.trim()}>
            {t('views.save')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
