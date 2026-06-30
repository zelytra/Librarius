import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';

const BIG_STATS = [
  { value: '47', label: 'Livres lus', icon: 'menu_book', ic: '#7d8f73', bg: '#e6ece0' },
  { value: '12 480', label: 'Pages lues', icon: 'auto_stories', ic: '#b0857f', bg: '#f3e6e3' },
  { value: '9', label: 'Séries suivies', icon: 'collections_bookmark', ic: '#8c7da0', bg: '#ece6f0' },
  { value: '18 j', label: 'Série de lecture', icon: 'local_fire_department', ic: '#c08a5a', bg: '#f1ebe0' },
];

const MONTHS = [
  { label: 'Jan', value: 6 },
  { label: 'Fév', value: 9 },
  { label: 'Mar', value: 5 },
  { label: 'Avr', value: 11 },
  { label: 'Mai', value: 8 },
  { label: 'Juin', value: 8 },
];
const MONTH_MAX = 12;

const GENRES = [
  { name: 'Romantasy', pct: 42, bg: 'linear-gradient(90deg,#9aab92,#bccab2)' },
  { name: 'Shōnen', pct: 28, bg: 'linear-gradient(90deg,#b0857f,#ddb9b3)' },
  { name: 'Romance', pct: 18, bg: 'linear-gradient(90deg,#8c7da0,#cabdd6)' },
  { name: 'Fantasy', pct: 12, bg: 'linear-gradient(90deg,#c08a5a,#dccfae)' },
];

export function StatsPage() {
  const { t } = useTranslation();

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 20px' }}>{t('stats.title')}</h2>

      {/* Objectif annuel */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 20, background: 'var(--surface)', borderRadius: 20, padding: 20, border: '1px solid var(--line)', marginBottom: 18 }}>
        <div style={{ flex: '0 0 auto', width: 96, height: 96, borderRadius: '50%', background: 'conic-gradient(var(--accent) 0% 78%, var(--chip) 78% 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ width: 72, height: 72, borderRadius: '50%', background: 'var(--surface)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <span style={{ fontFamily: "'Newsreader',serif", fontSize: 22, fontWeight: 600, lineHeight: 1 }}>47</span>
            <span style={{ fontSize: 10, color: 'var(--muted)' }}>/ 60</span>
          </div>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontFamily: "'Newsreader',serif", fontSize: 17, fontWeight: 600 }}>Objectif 2026</div>
          <div style={{ fontSize: 12.5, color: 'var(--ink-soft)', marginTop: 4, lineHeight: 1.4 }}>
            Plus que 13 titres pour atteindre votre objectif. Vous êtes en avance de 3 livres !
          </div>
        </div>
      </div>

      {/* Gros chiffres */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 22 }}>
        {BIG_STATS.map((s) => (
          <div key={s.label} style={{ background: s.bg, borderRadius: 18, padding: '18px 16px' }}>
            <Icon name={s.icon} size={22} color={s.ic} />
            <div style={{ fontFamily: "'Newsreader',serif", fontSize: 26, fontWeight: 600, lineHeight: 1, marginTop: 10 }}>{s.value}</div>
            <div style={{ fontSize: 12, color: 'var(--ink-soft)', marginTop: 4, fontWeight: 500 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Lectures par mois */}
      <div style={{ background: 'var(--surface)', borderRadius: 20, padding: 20, border: '1px solid var(--line)', marginBottom: 22 }}>
        <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600, marginBottom: 18 }}>Lectures par mois</div>
        <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', height: 120, gap: 10 }}>
          {MONTHS.map((m, i) => (
            <div key={m.label} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, height: '100%', justifyContent: 'flex-end' }}>
              <span style={{ fontSize: 10, color: 'var(--accent-deep)', fontWeight: 600 }}>{m.value}</span>
              <div style={{ width: '100%', maxWidth: 26, height: `${Math.round((m.value / MONTH_MAX) * 100)}%`, borderRadius: '7px 7px 4px 4px', background: i === MONTHS.length - 1 ? 'linear-gradient(180deg,#b0857f,#ddb9b3)' : 'linear-gradient(180deg,#9aab92,#bccab2)' }} />
              <span style={{ fontSize: 10, color: 'var(--muted)', fontWeight: 500 }}>{m.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Genres favoris */}
      <div style={{ background: 'var(--surface)', borderRadius: 20, padding: 20, border: '1px solid var(--line)' }}>
        <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600, marginBottom: 16 }}>Genres favoris</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {GENRES.map((g) => (
            <div key={g.name}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
                <span style={{ color: 'var(--ink-soft)', fontWeight: 500 }}>{g.name}</span>
                <span style={{ color: 'var(--muted)' }}>{g.pct}%</span>
              </div>
              <div style={{ height: 8, borderRadius: 6, background: 'var(--chip)', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${g.pct}%`, borderRadius: 6, background: g.bg }} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
