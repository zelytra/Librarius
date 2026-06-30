import { Outlet, useLocation } from 'react-router-dom';
import { BottomNav } from './BottomNav';

// La barre du bas est masquée sur les écrans « plein écran » (détail, réglages).
const HIDDEN_NAV_PREFIXES = ['/detail', '/settings'];

export function AppShell() {
  const location = useLocation();
  const showNav = !HIDDEN_NAV_PREFIXES.some((p) => location.pathname.startsWith(p));

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg)',
      }}
    >
      <div
        style={{
          position: 'relative',
          width: '100%',
          maxWidth: 440,
          height: '100dvh',
          maxHeight: 900,
          background: 'var(--bg)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          boxShadow: '0 30px 80px -20px rgba(74,64,52,0.35)',
        }}
      >
        <main className="scroll-x" style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden' }}>
          <Outlet />
        </main>
        {showNav && <BottomNav />}
      </div>
    </div>
  );
}
