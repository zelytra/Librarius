import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { deleteApiMeAvatar, getGetApiMeQueryKey, putApiMeAvatar, useGetApiMe } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';
import { apiErrorStatus } from '../../shared/apiClient';
import { Avatar } from '../../shared/ui/Avatar';
import { Button } from '../../shared/ui/primitives';
import { toast } from '../../shared/ui/toast';
import { prepareAvatar } from './avatarImage';
import styles from './ProfileSection.module.css';

/** What is worth reading in the browser: anything bigger is no profile picture. */
const MAX_FILE_BYTES = 20 * 1024 * 1024;

/**
 * The reader's own profile picture (#500): choose one, replace it, remove it. The picture is
 * cropped and re-encoded here before it leaves, and checked and re-encoded again by the API.
 */
export function AvatarPicker() {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const queryClient = useQueryClient();
  const { data: me } = useGetApiMe({ query: { enabled: authed } });
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () => void queryClient.invalidateQueries({ queryKey: getGetApiMeQueryKey() });

  async function choose(file: File | undefined) {
    if (!file) return;
    if (input.current) input.current.value = '';
    if (!file.type.startsWith('image/') || file.type === 'image/svg+xml') {
      toast.error(t('settings.avatar.format'));
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      toast.error(t('settings.avatar.tooBig'));
      return;
    }
    setBusy(true);
    try {
      let prepared: Blob;
      try {
        prepared = await prepareAvatar(file);
      } catch {
        toast.error(t('settings.avatar.format'));
        return;
      }
      await putApiMeAvatar(prepared);
      toast.success(t('settings.avatar.saved'));
      refresh();
    } catch (error) {
      const status = apiErrorStatus(error);
      toast.error(t(status === 415 ? 'settings.avatar.format' : status === 413 ? 'settings.avatar.tooBig' : 'settings.avatar.failed'));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    setBusy(true);
    try {
      await deleteApiMeAvatar();
      toast.success(t('settings.avatar.removed'));
      refresh();
    } catch {
      toast.error(t('settings.avatar.failed'));
    } finally {
      setBusy(false);
    }
  }

  if (!me) return null;
  const name = me.displayName ?? '';
  return (
    <div className={styles.avatarRow}>
      <Avatar id={me.id} name={name} version={me.avatarVersion} size={64} decorative />
      <div className={styles.avatarActions}>
        <label className={styles.avatarChoose}>
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
            className={styles.avatarInput}
            aria-label={t('settings.avatar.choose')}
            disabled={busy}
            onChange={(event) => void choose(event.target.files?.[0])}
          />
          <span aria-hidden="true">{t(me.avatarVersion != null ? 'settings.avatar.replace' : 'settings.avatar.choose')}</span>
        </label>
        {me.avatarVersion != null && (
          <Button variant="secondary" size="compact" disabled={busy} onClick={() => void remove()}>
            {t('settings.avatar.remove')}
          </Button>
        )}
      </div>
    </div>
  );
}
