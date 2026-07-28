import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { Button, Screen, ScreenTitle } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import { apiErrorStatus } from '../../shared/apiClient';
import {
  getGetApiCategoriesQueryKey,
  getGetApiLibraryQueryKey,
  useDeleteApiCategoriesId,
  useGetApiCategories,
  usePostApiCategories,
  usePutApiCategoriesId,
  type CategoryDto,
} from '../../api/generated/librarius';
import styles from './CategoriesPage.module.css';

/**
 * A name already in use comes back as a 409 — the only failure the user can do anything
 * about, so it is the only one worth a message of its own.
 */
function failureMessage(t: TFunction, error: unknown, fallbackKey: string): string {
  return apiErrorStatus(error) === 409
    ? t('categories.errors.duplicate')
    : t(fallbackKey);
}

/** What the row is currently doing: showing itself, being renamed, or being confirmed away. */
type RowMode = 'idle' | 'rename' | 'delete';

/**
 * The rename editor, mounted only while the row is being renamed: its draft therefore
 * starts from the name the category carries now, and not from the one it had when the
 * screen was first drawn.
 */
function RenameForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial: string;
  onSubmit: (label: string) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(initial);

  return (
    <form
      className={styles.editForm}
      onSubmit={(e) => {
        e.preventDefault();
        if (draft.trim()) onSubmit(draft.trim());
      }}
    >
      <input
        value={draft}
        autoFocus
        onChange={(e) => setDraft(e.target.value)}
        aria-label={t('categories.newName')}
        className={styles.input}
      />
      <Button type="submit" size="compact" disabled={!draft.trim()}>
        {t('categories.save')}
      </Button>
      <Button type="button" variant="ghost" size="compact" onClick={onCancel}>
        {t('categories.cancel')}
      </Button>
    </form>
  );
}

function CategoryRow({
  category,
  mode,
  onMode,
  onRename,
  onDelete,
}: {
  category: CategoryDto;
  mode: RowMode;
  onMode: (mode: RowMode) => void;
  onRename: (label: string) => void;
  onDelete: () => void;
}) {
  const { t } = useTranslation();

  if (mode === 'rename') {
    return (
      <li className={styles.row}>
        <RenameForm
          initial={category.label ?? ''}
          onSubmit={onRename}
          onCancel={() => onMode('idle')}
        />
      </li>
    );
  }

  return (
    <li className={styles.row}>
      <div className={styles.identity}>
        {/* The colour belongs to the category, so it cannot come from a stylesheet. */}
        <span className={styles.dot} style={{ background: category.color ?? 'var(--chip)' }} />
        <span className={styles.label}>{category.label}</span>
        {category.builtin && <span className={styles.builtin}>{t('categories.builtin')}</span>}
      </div>

      {!category.builtin && mode === 'idle' && (
        <div className={styles.actions}>
          <button
            type="button"
            onClick={() => onMode('rename')}
            aria-label={t('categories.renameOf', { name: category.label })}
            className={styles.action}
          >
            <Icon name="edit" size={18} color="var(--accent-deep)" />
          </button>
          <button
            type="button"
            onClick={() => onMode('delete')}
            aria-label={t('categories.deleteOf', { name: category.label })}
            className={styles.action}
          >
            <Icon name="delete" size={18} color="var(--rose)" />
          </button>
        </div>
      )}

      {mode === 'delete' && (
        // Deleting unranks titles, which is not something to discover after the fact:
        // the row says what it costs before asking for the confirmation.
        <div className={styles.confirm} role="alert">
          <p className={styles.confirmText}>{t('categories.deleteExplain')}</p>
          <div className={styles.confirmActions}>
            <Button size="compact" onClick={onDelete}>
              {t('categories.confirmDelete')}
            </Button>
            <Button variant="ghost" size="compact" onClick={() => onMode('idle')}>
              {t('categories.cancel')}
            </Button>
          </div>
        </div>
      )}
    </li>
  );
}

function CategoriesContent() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);
  /** Identifier of the row being renamed or confirmed, and what it is doing. */
  const [active, setActive] = useState<{ id: string; mode: RowMode } | null>(null);

  const { data: categories = [], isPending, isError, refetch } = useGetApiCategories();
  const { mutateAsync: create, isPending: creating } = usePostApiCategories();
  const { mutateAsync: rename } = usePutApiCategoriesId();
  const { mutateAsync: remove } = useDeleteApiCategoriesId();

  /**
   * A category is the rank shown on a title, so the collection has to be re-read after any
   * change: a rename moves every title to a new code, a deletion unranks them.
   */
  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: getGetApiCategoriesQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
  }

  async function submitCreate() {
    const label = draft.trim();
    if (!label) return;
    setError(null);
    try {
      await create({ data: { label } });
      setDraft('');
      await refresh();
    } catch (e) {
      setError(failureMessage(t, e, 'categories.errors.createFailed'));
    }
  }

  async function submitRename(id: string, label: string) {
    setError(null);
    try {
      await rename({ id, data: { label } });
      setActive(null);
      await refresh();
    } catch (e) {
      setError(failureMessage(t, e, 'categories.errors.renameFailed'));
    }
  }

  async function submitDelete(id: string) {
    setError(null);
    try {
      await remove({ id });
      setActive(null);
      await refresh();
    } catch (e) {
      setError(failureMessage(t, e, 'categories.errors.deleteFailed'));
    }
  }

  if (isPending) return <Loading />;
  if (isError) {
    return <ErrorState message={t('categories.errors.load')} onRetry={() => void refetch()} />;
  }

  const own = categories.filter((c) => !c.builtin);

  return (
    <>
      <p className={styles.intro}>{t('categories.intro')}</p>

      <form
        className={styles.createForm}
        onSubmit={(e) => {
          e.preventDefault();
          void submitCreate();
        }}
      >
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={t('categories.namePlaceholder')}
          aria-label={t('categories.namePlaceholder')}
          className={styles.input}
        />
        <Button type="submit" size="compact" disabled={!draft.trim() || creating}>
          {t(creating ? 'common.working' : 'categories.create')}
        </Button>
      </form>

      {error && <p className={styles.failure}>{error}</p>}

      <ul className={styles.list}>
        {categories.map((c) => (
          <CategoryRow
            key={c.id}
            category={c}
            mode={active && active.id === c.id ? active.mode : 'idle'}
            onMode={(mode) => setActive(mode === 'idle' ? null : { id: c.id!, mode })}
            onRename={(label) => void submitRename(c.id!, label)}
            onDelete={() => void submitDelete(c.id!)}
          />
        ))}
      </ul>

      {own.length === 0 && <p className={styles.hint}>{t('categories.none')}</p>}
    </>
  );
}

/**
 * Managing the ranking categories: create, rename, delete.
 *
 * <p>Reached from the Collection, whose rank filter is built from exactly this list. The
 * built-ins are listed too, greyed out rather than hidden: they take part in the ranking,
 * and hiding them would make the screen contradict the filter it feeds.
 */
export function CategoriesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <Screen>
      <div className={styles.backRow}>
        <button onClick={() => navigate('/collection')} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={20} color="var(--ink)" />
        </button>
      </div>
      <ScreenTitle className={styles.title}>{t('categories.title')}</ScreenTitle>
      <LoginGate prompt={t('auth.prompts.categories')}>
        <CategoriesContent />
      </LoginGate>
    </Screen>
  );
}
