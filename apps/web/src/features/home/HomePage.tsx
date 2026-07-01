import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { SectionHeader } from '../../shared/ui/primitives';
import { LoginGate } from '../../shared/LoginGate';
import { useApiAuth } from '../../shared/api';
import {
  getApiCatalogUpcoming,
  getApiLibrary,
  getApiStats,
  type CatalogResult,
  type LibraryItemDto,
  type StatsDto,
} from '../../api/generated/librarius';

const PALETTE = ['#bccab2', '#cabdd6', '#ddb9b3', '#b6c6d6', '#dccfae', '#aec8c0', '#d8b6a6', '#bcc9d8'];
function colorFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

function Cover({ item, onClick, w = 104 }: { item: LibraryItemDto; onClick: () => void; w?: number }) {
  const b = item.book!;
  const title = b.title ?? '—';
  return (
    <div onClick={onClick} style={{ flex: '0 0 auto', width: w, cursor: 'pointer' }}>
      <div style={{ width: w, height: w * 1.46, borderRadius: 10, background: b.coverUrl ? `center/cover no-repeat url(${b.coverUrl})` : colorFor(title), borderLeft: '3px solid rgba(0,0,0,0.07)', boxShadow: '0 8px 18px -8px rgba(74,64,52,0.4)', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', padding: b.coverUrl ? 0 : '12px 11px', overflow: 'hidden' }}>
        {!b.coverUrl && <div style={{ fontFamily: "'Newsreader',serif", fontSize: 14, fontWeight: 600, lineHeight: 1.1, color: '#352f28' }}>{title}</div>}
      </div>
      <div style={{ fontSize: 10, color: 'var(--muted)', marginTop: 6, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{b.authors}</div>
    </div>
  );
}

function Dashboard() {
  const navigate = useNavigate();
  const { opts } = useApiAuth();
  const [items, setItems] = useState<LibraryItemDto[]>([]);
  const [stats, setStats] = useState<StatsDto | null>(null);
  const [upcoming, setUpcoming] = useState<CatalogResult[]>([]);

  useEffect(() => {
    void (async () => {
      const [lib, st, up] = await Promise.all([
        getApiLibrary(undefined, opts),
        getApiStats(opts),
        getApiCatalogUpcoming({ kind: 'MANGA', limit: 5 }, opts),
      ]);
      if (lib.status === 200) setItems(lib.data);
      if (st.status === 200) setStats(st.data);
      if (up.status === 200) setUpcoming(up.data);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });
  const reading = items.filter((it) => it.status === 'READING');
  const read = items.filter((it) => it.status === 'READ').slice(0, 8);

  const mini = [
    { value: String(stats?.read ?? 0), label: 'lus', bg: '#e6ece0' },
    { value: String(stats?.reading ?? 0), label: 'en cours', bg: '#f3e6e3' },
    { value: String(stats?.toRead ?? 0), label: 'à lire', bg: '#efe9e0' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
      {reading.length > 0 && (
        <section>
          <SectionHeader title="Reprendre la lecture" action={`${reading.length} en cours`} />
          <div className="scroll-x" style={{ display: 'flex', gap: 14, overflowX: 'auto', margin: '0 -22px', padding: '2px 22px 6px' }}>
            {reading.map((it) => <Cover key={it.id} item={it} onClick={() => open(it)} />)}
          </div>
        </section>
      )}

      <section>
        <div style={{ display: 'flex', gap: 10 }}>
          {mini.map((s) => (
            <div key={s.label} style={{ flex: 1, background: s.bg, borderRadius: 16, padding: '14px 12px', textAlign: 'center' }}>
              <div style={{ fontFamily: "'Newsreader',serif", fontSize: 24, fontWeight: 600, lineHeight: 1 }}>{s.value}</div>
              <div style={{ fontSize: 11, color: 'var(--ink-soft)', marginTop: 5, fontWeight: 500 }}>{s.label}</div>
            </div>
          ))}
        </div>
      </section>

      {upcoming.length > 0 && (
        <section>
          <SectionHeader title="Prochaines sorties" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {upcoming.map((u, i) => (
              <div key={`${u.providerRef ?? i}`} style={{ display: 'flex', alignItems: 'center', gap: 13, padding: '11px 14px', background: 'var(--surface)', borderRadius: 16, border: '1px solid var(--line)' }}>
                <div style={{ flex: '0 0 auto', width: 40, height: 54, borderRadius: 8, background: u.coverUrl ? `center/cover no-repeat url(${u.coverUrl})` : 'var(--surface-2)' }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontFamily: "'Newsreader',serif", fontSize: 15, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{u.title}</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 1 }}>{u.authors}</div>
                </div>
                {u.releaseDate && <span style={{ flex: '0 0 auto', fontSize: 10.5, fontWeight: 600, color: '#b0857f', background: '#f4e4e1', padding: '4px 9px', borderRadius: 20 }}>{u.releaseDate}</span>}
              </div>
            ))}
          </div>
          <p style={{ fontSize: 11, color: 'var(--faint)', marginTop: 8 }}>Dates indicatives (édition d'origine).</p>
        </section>
      )}

      {read.length > 0 && (
        <section>
          <SectionHeader title="Derniers lus" />
          <div className="scroll-x" style={{ display: 'flex', gap: 14, overflowX: 'auto', margin: '0 -22px', padding: '2px 22px 6px' }}>
            {read.map((it) => <Cover key={it.id} item={it} onClick={() => open(it)} />)}
          </div>
        </section>
      )}

      {items.length === 0 && (
        <div style={{ textAlign: 'center', padding: '40px 24px', color: 'var(--faint)' }}>
          <Icon name="auto_stories" size={42} />
          <p style={{ fontSize: 13.5, lineHeight: 1.5, marginTop: 12 }}>
            Ta bibliothèque est vide. Va sur <strong>Découvrir</strong> pour ajouter tes premiers titres.
          </p>
        </div>
      )}
    </div>
  );
}

export function HomePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const today = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '6px 0 22px' }}>
        <div>
          <div style={{ fontSize: 13, color: 'var(--faint)', fontWeight: 500, textTransform: 'capitalize' }}>{today}</div>
          <div style={{ fontFamily: "'Newsreader', serif", fontSize: 27, fontWeight: 500, marginTop: 2 }}>{t('home.greeting')}</div>
        </div>
        <button onClick={() => navigate('/settings')} aria-label={t('settings.title')} style={{ width: 46, height: 46, border: 'none', borderRadius: '50%', background: 'linear-gradient(135deg,#c9b8d4,#9aab92)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 4px 12px -3px rgba(122,143,115,0.5)' }}>
          <Icon name="settings" size={22} color="#fff" />
        </button>
      </div>
      <LoginGate prompt="Connecte-toi pour retrouver ta bibliothèque et tes lectures.">
        <Dashboard />
      </LoginGate>
    </div>
  );
}
