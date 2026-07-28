import type { ReactNode } from 'react';
import { useApiAuth } from './api';
import { Button } from './ui/primitives';
import { Icon } from './ui/Icon';
import styles from './LoginGate.module.css';

/** Renders the content when the user is signed in, otherwise a sign-in prompt. */
export function LoginGate({ children, prompt }: { children: ReactNode; prompt?: string }) {
  const { authed, loading, login } = useApiAuth();

  if (loading) {
    return <p className={styles.loading}>Chargement…</p>;
  }
  if (!authed) {
    return (
      <div className={styles.gate}>
        <Icon name="lock" size={40} />
        <p className={styles.prompt}>
          {prompt ?? 'Connecte-toi pour accéder à cette section.'}
        </p>
        <Button variant="primary" onClick={login}>
          <Icon name="login" size={20} fill color="var(--on-accent)" />
          Se connecter
        </Button>
      </div>
    );
  }
  return <>{children}</>;
}
