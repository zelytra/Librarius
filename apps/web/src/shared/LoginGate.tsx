import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useApiAuth } from './api';
import { Button } from './ui/primitives';
import { Icon } from './ui/Icon';
import { Loading } from './ui/states';
import styles from './LoginGate.module.css';

/**
 * The frame both non-nominal branches of the gate share: the app names itself before it
 * says anything else, so a cold start and a signed-out screen read as this application
 * waiting rather than as a page that failed to load. It matters most on the mobile shell,
 * which opens straight onto whatever route the router lands on.
 */
function Welcome({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className={styles.gate}>
      {/* An open book rather than a padlock: this is a welcome, not a refusal. */}
      <Icon name="auto_stories" size={40} color="var(--accent-deep)" />
      <p className={styles.name}>{t('app.name')}</p>
      <p className={styles.tagline}>{t('app.tagline')}</p>
      {children}
    </div>
  );
}

/** Renders the content when the user is signed in, otherwise a sign-in prompt. */
export function LoginGate({ children, prompt }: { children: ReactNode; prompt?: string }) {
  const { t } = useTranslation();
  const { authed, loading, login } = useApiAuth();

  // The session is still being resolved. Nothing is known yet — not even whether there
  // is one — so the screen says the app is opening rather than that a session is missing.
  if (loading) {
    return (
      <Welcome>
        <Loading label={t('auth.welcome.opening')} />
      </Welcome>
    );
  }
  if (!authed) {
    return (
      <Welcome>
        <p className={styles.prompt}>{prompt ?? t('auth.defaultPrompt')}</p>
        <Button variant="primary" onClick={login}>
          <Icon name="login" size={20} fill color="var(--on-accent)" />
          {t('auth.signIn')}
        </Button>
        {/* The root sends a signed-out visitor to the landing page on its own (see
            App.tsx), but a shared link to a title does not: it prompts here, so that
            signing in lands on that title. Without this way out, the visitor who
            arrived that way would have no way of finding out what any of it is. */}
        <Link to="/welcome" className={styles.discover}>
          {t('auth.discover')}
        </Link>
      </Welcome>
    );
  }
  return <>{children}</>;
}
