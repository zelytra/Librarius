import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '../../shared/ui/primitives';
import { TrustBadge } from '../../shared/ui/TrustBadge';
import { useApiAuth } from '../../shared/api';
import {
  getGetApiMeBlockedQueryKey,
  useGetApiMeBlocked,
  useDeleteApiUsersIdBlock,
} from '../../api/generated/librarius';
import styles from './BlockedMembersSection.module.css';

/**
 * The members the caller blocks (#203), and the one action offered on them: unblock. Whether
 * an account is blocked is visible only to the blocker, so this list is the caller's own — the
 * block button that puts a member here lives on their profile (#202); this is where a caller
 * takes it back.
 *
 * <p>Unblocking invalidates the list so the row disappears at once, and the follow queries,
 * since a block overrides a follow: lifting it lets the two accounts see and follow each other
 * again.
 */
export function BlockedMembersSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const queryClient = useQueryClient();

  const { data: blocked } = useGetApiMeBlocked({ query: { enabled: auth.authed } });

  const { mutate: unblock, isPending } = useDeleteApiUsersIdBlock({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getGetApiMeBlockedQueryKey() });
      },
    },
  });

  return (
    <>
      <h3 className={styles.title}>{t('settings.blocked.title')}</h3>
      <p className={styles.intro}>{t('settings.blocked.description')}</p>

      {!auth.authed ? (
        <p className={styles.intro}>{t('settings.blocked.signIn')}</p>
      ) : !blocked || blocked.length === 0 ? (
        <p className={styles.empty}>{t('settings.blocked.empty')}</p>
      ) : (
        <ul className={styles.list}>
          {blocked.map((member) => (
            <li key={member.id} className={styles.row}>
              <span className={styles.name}>
                {member.displayName}
                {member.trusted && <TrustBadge className={styles.trustBadge} />}
              </span>
              <Button
                variant="secondary"
                size="compact"
                disabled={isPending}
                onClick={() => member.id && unblock({ id: member.id })}
              >
                {t('settings.blocked.unblock')}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
