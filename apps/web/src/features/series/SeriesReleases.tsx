import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiReleasesUpcomingQueryKey,
  useGetApiMe,
  useGetApiReleasesUpcoming,
  usePostApiAdminReleases,
} from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';
import { Button } from '../../shared/ui/primitives';
import { toast } from '../../shared/ui/toast';
import { ReleaseList } from '../home/UpcomingReleases';
import styles from './SeriesReleases.module.css';

/**
 * What is announced for this run (#614): nothing at all when nothing is, for a reader — an
 * empty section would only say "no news". An admin also gets the form that enters a checked
 * French date, the source no free API offers.
 */
export function SeriesReleases({ seriesId }: { seriesId: string }) {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: authed } });
  const { data: releases = [], isPending, isError } = useGetApiReleasesUpcoming({ seriesId, limit: 50 });
  const admin = me?.admin === true;

  if (isPending || isError) return null;
  if (releases.length === 0 && !admin) return null;

  return (
    <section className={styles.section} aria-labelledby="series-releases">
      <h3 className={styles.title} id="series-releases">
        {t('releases.title')}
      </h3>
      {releases.length > 0 && <ReleaseList releases={releases} />}
      {admin && <CurateRelease seriesId={seriesId} />}
    </section>
  );
}

function CurateRelease({ seriesId }: { seriesId: string }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [volume, setVolume] = useState('');
  const [date, setDate] = useState('');
  const [publisher, setPublisher] = useState('');
  const { mutate, isPending } = usePostApiAdminReleases({
    mutation: {
      onSuccess: () => {
        toast.success(t('releases.curate.done'), 'event_available');
        setVolume('');
        setDate('');
        setPublisher('');
        void queryClient.invalidateQueries({ queryKey: getGetApiReleasesUpcomingQueryKey() });
      },
      onError: () => toast.error(t('releases.curate.failed')),
    },
  });
  const volumeNumber = Number(volume);
  const complete = Number.isInteger(volumeNumber) && volumeNumber >= 1 && date !== '';

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!complete) return;
    mutate({
      data: { seriesId, volumeNumber, releaseDate: date, ...(publisher.trim() ? { publisher: publisher.trim() } : {}) },
    });
  };

  return (
    <form className={styles.curate} aria-label={t('releases.curate.title')} onSubmit={submit}>
      <p className={styles.curateTitle}>{t('releases.curate.title')}</p>
      <p className={styles.hint}>{t('releases.curate.hint')}</p>
      <div className={styles.fields}>
        <label className={`${styles.field} ${styles.fieldNarrow}`}>
          <span className={styles.label}>{t('releases.curate.volume')}</span>
          <input
            type="number"
            inputMode="numeric"
            min={1}
            required
            value={volume}
            onChange={(e) => setVolume(e.target.value)}
            className={styles.input}
          />
        </label>
        <label className={styles.field}>
          <span className={styles.label}>{t('releases.curate.date')}</span>
          <input type="date" required value={date} onChange={(e) => setDate(e.target.value)} className={styles.input} />
        </label>
        <label className={styles.field}>
          <span className={styles.label}>{t('releases.curate.publisher')}</span>
          <input
            maxLength={255}
            value={publisher}
            onChange={(e) => setPublisher(e.target.value)}
            className={styles.input}
          />
        </label>
      </div>
      <Button type="submit" size="compact" disabled={!complete || isPending}>
        {t(isPending ? 'common.working' : 'releases.curate.submit')}
      </Button>
    </form>
  );
}
