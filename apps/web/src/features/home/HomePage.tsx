import type { CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { SectionHeader } from '../../shared/ui/primitives';

const C = {
  sage: '#bccab2', lilac: '#cabdd6', rose: '#ddb9b3', blue: '#b6c6d6', sand: '#dccfae',
  clay: '#d8b6a6', moss: '#c2caa6', mauve: '#cab5ad', teal: '#aec8c0', sky: '#bcc9d8', dusk: '#b9b3c9',
};

const READING = [
  { id: 'b1', title: 'Fourth Wing', author: 'Rebecca Yarros', color: C.sage, tag: 'ROMANTASY', pct: 60, position: 'p. 312 / 517' },
  { id: 'm4', title: 'Jujutsu Kaisen T.24', author: 'Gege Akutami', color: C.dusk, tag: 'T.24', pct: 35, position: 'ch. 4 / 9' },
];

const MINI = [
  { value: '47', label: 'lus en 2026', bg: '#e6ece0' },
  { value: '2', label: 'en cours', bg: '#f3e6e3' },
  { value: '23', label: 'à lire', bg: '#efe9e0' },
];

const BOUGHT = [
  { id: 'b2', title: 'Iron Flame', author: 'R. Yarros', color: C.clay, tag: 'ROMANTASY' },
  { id: 'm6', title: 'Spy × Family', author: 'T. Endo', color: C.sage, tag: 'T.12' },
  { id: 'b7', title: 'Powerless', author: 'L. Roberts', color: C.moss, tag: 'FANTASY' },
  { id: 'm3', title: 'One Piece', author: 'E. Oda', color: C.sky, tag: 'T.107' },
];

const UPCOMING = [
  { day: '04', month: 'JUIL', title: 'Chainsaw Man T.17', author: 'Tatsuki Fujimoto', kind: 'Manga' },
  { day: '21', month: 'JUIL', title: 'Onyx Storm', author: 'Rebecca Yarros', kind: 'Roman' },
  { day: '02', month: 'AOÛT', title: 'Jujutsu Kaisen T.30', author: 'Gege Akutami', kind: 'Final' },
  { day: '18', month: 'AOÛT', title: 'Spy × Family T.14', author: 'Tatsuya Endo', kind: 'Manga' },
];

const RECENT = [
  { id: 'b5', title: 'The Love Hypothesis', author: 'A. Hazelwood', color: C.rose, tag: 'ROMANCE', stars: 5 },
  { id: 'm7', title: 'Demon Slayer T.23', author: 'K. Gotouge', color: C.lilac, tag: 'T.23', stars: 5 },
  { id: 'b6', title: 'It Ends with Us', author: 'C. Hoover', color: C.blue, tag: 'ROMANCE', stars: 4 },
  { id: 'm8', title: 'Blue Lock T.25', author: 'M. Kaneshiro', color: C.teal, tag: 'T.25', stars: 4 },
];

const SPINES = [
  { w: 150, h: 11, color: C.sage }, { w: 178, h: 15, color: C.rose }, { w: 132, h: 9, color: C.lilac },
  { w: 196, h: 16, color: C.sand }, { w: 160, h: 12, color: C.blue }, { w: 142, h: 10, color: C.clay },
  { w: 184, h: 14, color: C.moss }, { w: 168, h: 13, color: C.mauve }, { w: 154, h: 11, color: C.teal },
];

function stars(n: number) {
  return '★★★★★☆☆☆☆☆'.slice(5 - n, 10 - n);
}

function CoverCard({ title, author, color, tag, onClick, star }: { title: string; author: string; color: string; tag: string; onClick: () => void; star?: number }) {
  return (
    <div onClick={onClick} style={{ flex: '0 0 auto', width: 104, cursor: 'pointer' }}>
      <div style={{ width: 104, height: 152, borderRadius: 10, background: color, borderLeft: '3px solid rgba(0,0,0,0.07)', boxShadow: '0 8px 18px -8px rgba(74,64,52,0.4)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '12px 11px' }}>
        <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: '0.08em', color: 'rgba(58,52,44,0.5)' }}>{tag}</span>
        <div>
          <div style={{ fontFamily: "'Newsreader',serif", fontSize: 14, fontWeight: 600, lineHeight: 1.1, color: '#352f28' }}>{title}</div>
          <div style={{ fontSize: 9.5, color: 'rgba(58,52,44,0.6)', marginTop: 4, fontWeight: 500 }}>{author}</div>
        </div>
      </div>
      {star != null && <div style={{ display: 'flex', justifyContent: 'center', marginTop: 7, color: '#d9b94e', fontSize: 11 }}>{stars(star)}</div>}
    </div>
  );
}

export function HomePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const today = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  const open = (id: string) => navigate(`/detail/${id}`);
  const rowScroll: CSSProperties = { display: 'flex', gap: 14, overflowX: 'auto', margin: '0 -22px', padding: '2px 22px 6px' };

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      {/* En-tête */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '6px 0 22px' }}>
        <div>
          <div style={{ fontSize: 13, color: 'var(--faint)', fontWeight: 500, textTransform: 'capitalize' }}>{today}</div>
          <div style={{ fontFamily: "'Newsreader', serif", fontSize: 27, fontWeight: 500, marginTop: 2 }}>{t('home.greeting')}</div>
        </div>
        <button onClick={() => navigate('/settings')} aria-label={t('settings.title')} style={{ width: 46, height: 46, border: 'none', borderRadius: '50%', background: 'linear-gradient(135deg,#c9b8d4,#9aab92)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 4px 12px -3px rgba(122,143,115,0.5)' }}>
          <Icon name="settings" size={22} color="#fff" />
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        {/* Reprendre la lecture */}
        <section>
          <SectionHeader title={t('home.resumeReading')} action={`${READING.length} en cours`} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {READING.map((b) => (
              <div key={b.id} onClick={() => open(b.id)} style={{ display: 'flex', gap: 14, padding: 13, background: 'var(--surface)', borderRadius: 18, boxShadow: 'var(--shadow)', border: '1px solid var(--line)', cursor: 'pointer' }}>
                <div style={{ flex: '0 0 auto', width: 62, height: 90, borderRadius: 8, background: b.color, borderLeft: '3px solid rgba(0,0,0,0.07)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '8px 7px' }}>
                  <span style={{ fontSize: 8, fontWeight: 700, letterSpacing: '0.08em', color: 'rgba(58,52,44,0.55)' }}>{b.tag}</span>
                  <span style={{ fontFamily: "'Newsreader',serif", fontSize: 11, fontWeight: 600, lineHeight: 1.1, color: '#352f28' }}>{b.title}</span>
                </div>
                <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 7 }}>
                  <div>
                    <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600, lineHeight: 1.15, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{b.title}</div>
                    <div style={{ fontSize: 12.5, color: 'var(--muted)', marginTop: 1 }}>{b.author}</div>
                  </div>
                  <div>
                    <div style={{ height: 6, borderRadius: 6, background: 'var(--chip)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${b.pct}%`, borderRadius: 6, background: 'linear-gradient(90deg,#9aab92,#b6c4ad)' }} />
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 5, fontSize: 11, color: 'var(--muted)' }}>
                      <span>{b.position}</span>
                      <span style={{ color: 'var(--accent-deep)', fontWeight: 600 }}>{b.pct}%</span>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Votre pile 2026 */}
        <section>
          <SectionHeader title="Votre pile 2026" action="qui monte 📚" />
          <div style={{ background: 'linear-gradient(180deg,var(--surface),var(--surface-2))', borderRadius: 20, border: '1px solid var(--line)', padding: '20px 18px 18px', display: 'flex', gap: 18, alignItems: 'flex-end' }}>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-end', minHeight: 190 }}>
              <Icon name="workspace_premium" size={18} fill color="#c08a5a" style={{ marginBottom: 3 }} />
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3 }}>
                {SPINES.map((s, i) => (
                  <div key={i} style={{ width: s.w, height: s.h, background: s.color, borderRadius: 3, borderLeft: '2px solid rgba(0,0,0,0.06)', boxShadow: '0 1px 2px rgba(74,64,52,0.18)', animation: 'rise .5s ease both', animationDelay: `${(i * 0.06).toFixed(2)}s` }} />
                ))}
              </div>
              <div style={{ width: 210, maxWidth: '100%', height: 20, background: '#cdbfa6', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', marginTop: 3 }}>
                <span style={{ fontSize: 10, fontWeight: 700, color: '#fbf8f3', letterSpacing: '0.03em' }}>+ 38 livres de plus</span>
              </div>
              <div style={{ width: 230, maxWidth: '100%', height: 6, background: '#bcae93', borderRadius: '0 0 6px 6px' }} />
            </div>
            <div style={{ flex: '0 0 auto', width: 108, display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 26, fontWeight: 600, lineHeight: 1 }}>47</div>
                <div style={{ fontSize: 11, color: 'var(--ink-soft)', marginTop: 2 }}>livres lus</div>
              </div>
              <div>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 26, fontWeight: 600, lineHeight: 1 }}>12 480</div>
                <div style={{ fontSize: 11, color: 'var(--ink-soft)', marginTop: 2 }}>pages tournées</div>
              </div>
              <div style={{ background: 'var(--accent-soft)', borderRadius: 12, padding: '10px 11px' }}>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 18, fontWeight: 600, color: 'var(--accent-deep)', lineHeight: 1 }}>≈ 1,25 m</div>
                <div style={{ fontSize: 10.5, color: 'var(--ink-soft)', marginTop: 3, lineHeight: 1.25 }}>de hauteur de papier empilé</div>
              </div>
            </div>
          </div>
        </section>

        {/* Mini stats */}
        <section>
          <div style={{ display: 'flex', gap: 10 }}>
            {MINI.map((s) => (
              <div key={s.label} style={{ flex: 1, background: s.bg, borderRadius: 16, padding: '14px 12px', textAlign: 'center' }}>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 24, fontWeight: 600, lineHeight: 1 }}>{s.value}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-soft)', marginTop: 5, fontWeight: 500 }}>{s.label}</div>
              </div>
            ))}
          </div>
        </section>

        {/* Derniers achats */}
        <section>
          <SectionHeader title={t('home.recentlyBought')} action={t('common.seeAll')} />
          <div className="scroll-x" style={rowScroll}>
            {BOUGHT.map((b) => (
              <CoverCard key={b.id} {...b} onClick={() => open(b.id)} />
            ))}
          </div>
        </section>

        {/* Prochaines sorties */}
        <section>
          <SectionHeader title={t('home.upcoming')} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {UPCOMING.map((u, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 13, padding: '11px 14px', background: 'var(--surface)', borderRadius: 16, border: '1px solid var(--line)' }}>
                <div style={{ flex: '0 0 auto', width: 48, height: 54, borderRadius: 11, background: 'var(--surface-2)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontFamily: "'Newsreader',serif", fontSize: 20, fontWeight: 600, color: 'var(--accent-deep)', lineHeight: 1 }}>{u.day}</span>
                  <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: '0.06em', color: 'var(--muted)', marginTop: 2 }}>{u.month}</span>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontFamily: "'Newsreader',serif", fontSize: 15, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{u.title}</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 1 }}>{u.author}</div>
                </div>
                <span style={{ flex: '0 0 auto', fontSize: 10.5, fontWeight: 600, color: '#b0857f', background: '#f4e4e1', padding: '4px 9px', borderRadius: 20 }}>{u.kind}</span>
              </div>
            ))}
          </div>
        </section>

        {/* Derniers lus */}
        <section>
          <SectionHeader title={t('home.recentlyRead')} action={t('common.seeAll')} />
          <div className="scroll-x" style={rowScroll}>
            {RECENT.map((b) => (
              <CoverCard key={b.id} {...b} onClick={() => open(b.id)} star={b.stars} />
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
