import { useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useGetApiFeedEventId } from '../../api/generated/librarius';
import { apiErrorStatus } from '../../shared/apiClient';
import { LoginGate } from '../../shared/LoginGate';
import { Icon } from '../../shared/ui/Icon';
import { Screen } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { FeedEvent } from '../people/Feed';
import styles from './Notifications.module.css';

/** One activity of the feed, with its reactions and comments: the screen a notification opens (#516). */
export function EventPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Screen>
      <div className={`sticky-header ${styles.header}`}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <h2 className={styles.title}>{t('notifications.event')}</h2>
      </div>
      <LoginGate prompt={t('notifications.prompt')}>
        <Event />
      </LoginGate>
    </Screen>
  );
}

function Event() {
  const { t } = useTranslation();
  const { eventId = '' } = useParams();
  const { data, isPending, isError, error, refetch } = useGetApiFeedEventId(eventId);
  if (isPending) return <Loading />;
  if (isError) {
    return apiErrorStatus(error) === 404 ? (
      <EmptyState icon="search_off" title={t('notifications.gone')} description={t('notifications.goneHint')} />
    ) : (
      <ErrorState message={t('notifications.error')} onRetry={() => void refetch()} />
    );
  }
  return (
    <div className={styles.event}>
      <FeedEvent item={data} />
    </div>
  );
}
