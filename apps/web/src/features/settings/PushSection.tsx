import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiMePushQueryKey,
  useGetApiMePush,
  usePutApiMePush,
  type PushPrefsDto,
} from '../../api/generated/librarius';
import { enablePushOnDevice, pushSupported } from '../../shared/push';
import { toast } from '../../shared/ui/toast';
import styles from './SettingsPage.module.css';
import own from './PushSection.module.css';

const CATEGORIES = ['releases', 'wishlist', 'goal'] as const;
type Category = (typeof CATEGORIES)[number];

/**
 * Push notifications (#69): three categories, each off until the reader switches it on — and
 * switching one on asks the system's permission first. On the web, where nothing is pushed,
 * the block says where it lives instead.
 */
export function PushSection() {
  const { t } = useTranslation();
  const supported = pushSupported();
  return (
    <>
      <h3 className={styles.sectionTitle} id="settings-push">
        {t('settings.push.title')}
      </h3>
      <p className={styles.sectionIntro}>{t(supported ? 'settings.push.description' : 'settings.push.webOnly')}</p>
      {supported && <PushChoices />}
    </>
  );
}

function PushChoices() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: prefs } = useGetApiMePush();
  const [denied, setDenied] = useState(false);
  const [asking, setAsking] = useState(false);
  const { mutate: save, isPending } = usePutApiMePush({
    mutation: {
      onSuccess: (next) => {
        queryClient.setQueryData(getGetApiMePushQueryKey(), next);
        toast.success(t('settings.push.saved'));
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });

  if (!prefs) return null;

  const toggle = async (category: Category, on: boolean) => {
    setDenied(false);
    if (on) {
      setAsking(true);
      const allowed = await enablePushOnDevice().catch(() => false);
      setAsking(false);
      if (!allowed) {
        setDenied(true);
        return;
      }
    }
    const next: PushPrefsDto = { ...prefs, [category]: on };
    save({ data: next });
  };

  return (
    <div role="group" aria-labelledby="settings-push">
      {CATEGORIES.map((category) => (
        <label key={category} className={styles.checkRow}>
          <input
            type="checkbox"
            checked={prefs[category]}
            disabled={isPending || asking}
            onChange={(e) => void toggle(category, e.target.checked)}
          />
          {t(`settings.push.${category}`)}
        </label>
      ))}
      {denied && (
        <p className={own.denied} role="alert">
          {t('settings.push.denied')}
        </p>
      )}
    </div>
  );
}
