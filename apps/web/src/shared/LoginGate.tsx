import type { ReactNode } from 'react';
import { useApiAuth } from './api';
import { Button } from './ui/primitives';
import { Icon } from './ui/Icon';

/** Affiche le contenu si l'utilisateur est connecté, sinon une invite de connexion. */
export function LoginGate({ children, prompt }: { children: ReactNode; prompt?: string }) {
  const { authed, loading, login } = useApiAuth();

  if (loading) {
    return <p style={{ color: 'var(--muted)', fontSize: 13, padding: '40px 0', textAlign: 'center' }}>Chargement…</p>;
  }
  if (!authed) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 16px', color: 'var(--muted)' }}>
        <Icon name="lock" size={40} />
        <p style={{ fontSize: 13.5, lineHeight: 1.5, margin: '12px 0 18px' }}>
          {prompt ?? 'Connecte-toi pour accéder à cette section.'}
        </p>
        <Button variant="primary" onClick={login}>
          <Icon name="login" size={20} fill color="#fff" />
          Se connecter
        </Button>
      </div>
    );
  }
  return <>{children}</>;
}
