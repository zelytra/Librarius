import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useApiAuth } from './api';
import { Button } from './ui/primitives';
import { Icon } from './ui/Icon';
import styles from './LoginGate.module.css';

/** Renders the content when the user is signed in, otherwise a sign-in prompt. */
export function LoginGate({ children, prompt }: { children: ReactNode; prompt?: string }) {
  const { t } = useTranslation();
  const { authed, loading, login } = useApiAuth();

  if (loading) {
    return <p className={styles.loading}>{t('common.loading')}</p>;
  }
  if (!authed) {
    return (
      <div className={styles.gate}>
        <Icon name="lock" size={40} />
        <p className={styles.prompt}>{prompt ?? t('auth.defaultPrompt')}</p>
        <Button variant="primary" onClick={login}>
          <Icon name="login" size={20} fill color="var(--on-accent)" />
          {t('auth.signIn')}
        </Button>
      </div>
    );
  }
  return <>{children}</>;
}
