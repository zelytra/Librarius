import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { LoginGate } from '../../shared/LoginGate';
import { useApiAuth } from '../../shared/api';
import { getApiStats, type StatsDto } from '../../api/generated/librarius';

const GENRE_GRADIENTS = [
  'linear-gradient(90deg,#9aab92,#bccab2)',
  'linear-gradient(90deg,#b0857f,#ddb9b3)',
  'linear-gradient(90deg,#8c7da0,#cabdd6)',
  'linear-gradient(90deg,#c08a5a,#dccfae)',
];

function StatsContent() {
  const { opts } = useApiAuth();
  const [stats, setStats] = useState<StatsDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      try {
        const res = await getApiStats(opts);
        if (res.status === 200) setStats(res.data);
      } finally {
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <p style={{ color: 'var(--muted)', fontSize: 13 }}>Chargement…</p>;
  if (!stats) return <p style={{ color: 'var(--rose)', fontSize: 13 }}>Statistiques indisponibles.</p>;

  const read = stats.read ?? 0;
  const reading = stats.reading ?? 0;
  const pagesRead = stats.pagesRead ?? 0;
  const seriesCount = stats.seriesCount ?? 0;
  const goalCurrent = stats.goalCurrent ?? 0;
  const byGenre = stats.byGenre ?? [];
  const target = stats.goalTarget ?? 0;
  const pct = target > 0 ? Math.min(100, Math.round((goalCurrent / target) * 100)) : 0;
  const remaining = target > 0 ? Math.max(0, target - goalCurrent) : 0;

  const bigStats = [
    { value: String(read), label: 'Livres lus', icon: 'menu_book', ic: '#7d8f73', bg: '#e6ece0' },
    { value: pagesRead.toLocaleString('fr-FR'), label: 'Pages lues', icon: 'auto_stories', ic: '#b0857f', bg: '#f3e6e3' },
    { value: String(seriesCount), label: 'Séries suivies', icon: 'collections_bookmark', ic: '#8c7da0', bg: '#ece6f0' },
    { value: String(reading), label: 'En cours', icon: 'local_fire_department', ic: '#c08a5a', bg: '#f1ebe0' },
  ];

  const maxGenre = Math.max(1, ...byGenre.map((g) => g.count ?? 0));

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 20, background: 'var(--surface)', borderRadius: 20, padding: 20, border: '1px solid var(--line)', marginBottom: 18 }}>
        <div style={{ flex: '0 0 auto', width: 96, height: 96, borderRadius: '50%', background: `conic-gradient(var(--accent) 0% ${pct}%, var(--chip) ${pct}% 100%)`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ width: 72, height: 72, borderRadius: '50%', background: 'var(--surface)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <span style={{ fontFamily: "'Newsreader',serif", fontSize: 22, fontWeight: 600, lineHeight: 1 }}>{goalCurrent}</span>
            <span style={{ fontSize: 10, color: 'var(--muted)' }}>/ {target || '—'}</span>
          </div>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontFamily: "'Newsreader',serif", fontSize: 17, fontWeight: 600 }}>Objectif {new Date().getFullYear()}</div>
          <div style={{ fontSize: 12.5, color: 'var(--ink-soft)', marginTop: 4, lineHeight: 1.4 }}>
            {target > 0 ? `Plus que ${remaining} titre(s) pour atteindre ton objectif.` : 'Définis un objectif de lecture annuel dans tes réglages.'}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 22 }}>
        {bigStats.map((s) => (
          <div key={s.label} style={{ background: s.bg, borderRadius: 18, padding: '18px 16px' }}>
            <Icon name={s.icon} size={22} color={s.ic} />
            <div style={{ fontFamily: "'Newsreader',serif", fontSize: 26, fontWeight: 600, lineHeight: 1, marginTop: 10 }}>{s.value}</div>
            <div style={{ fontSize: 12, color: 'var(--ink-soft)', marginTop: 4, fontWeight: 500 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: 'var(--surface)', borderRadius: 20, padding: 20, border: '1px solid var(--line)' }}>
        <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600, marginBottom: 16 }}>Genres favoris</div>
        {byGenre.length === 0 ? (
          <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>Pas encore de données — ajoute des titres à ta collection.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {byGenre.map((g, i) => (
              <div key={g.genre}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
                  <span style={{ color: 'var(--ink-soft)', fontWeight: 500 }}>{g.genre}</span>
                  <span style={{ color: 'var(--muted)' }}>{g.count ?? 0}</span>
                </div>
                <div style={{ height: 8, borderRadius: 6, background: 'var(--chip)', overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${Math.round(((g.count ?? 0) / maxGenre) * 100)}%`, borderRadius: 6, background: GENRE_GRADIENTS[i % GENRE_GRADIENTS.length] }} />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

export function StatsPage() {
  const { t } = useTranslation();
  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 20px' }}>{t('stats.title')}</h2>
      <LoginGate prompt="Connecte-toi pour voir tes statistiques.">
        <StatsContent />
      </LoginGate>
    </div>
  );
}
