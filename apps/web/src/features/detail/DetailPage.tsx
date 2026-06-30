import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import { findBook, RANK_COLORS } from '../collection/mockData';

function stars(n: number) {
  return '★★★★★☆☆☆☆☆'.slice(5 - n, 10 - n);
}

const RANK_OPTIONS = [
  { id: 'or', name: 'Or' },
  { id: 'argent', name: 'Argent' },
  { id: 'bronze', name: 'Bronze' },
] as const;

export function DetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id = '' } = useParams();
  const book = findBook(id);
  const [rank, setRank] = useState<string | null>(book?.rank ?? null);

  if (!book) {
    return (
      <div style={{ padding: '40px 22px', textAlign: 'center', color: 'var(--muted)' }}>
        <p>Titre introuvable.</p>
        <Button variant="secondary" onClick={() => navigate(-1)}>
          {t('common.back')}
        </Button>
      </div>
    );
  }

  // Éditions fictives pour illustrer « plusieurs éditions d'une même œuvre ».
  const editions = [
    { label: 'Broché', publisher: 'Hugo Roman', year: book.year, pages: book.pages },
    { label: 'Poche', publisher: 'Pocket', year: (book.year ?? 2023) + 1, pages: book.pages },
  ];

  return (
    <div style={{ position: 'relative' }}>
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 300, background: `linear-gradient(180deg, ${book.color}aa, ${book.color}00)` }} />
      <div style={{ position: 'relative', padding: '0 22px 40px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 0 18px' }}>
          <button
            onClick={() => navigate(-1)}
            aria-label={t('common.back')}
            style={{ width: 40, height: 40, border: 'none', borderRadius: '50%', background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}
          >
            <Icon name="arrow_back" size={24} color="#3a342c" />
          </button>
          <span style={{ width: 40, height: 40, borderRadius: '50%', background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="bookmark_border" size={22} color="#3a342c" />
          </span>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 18 }}>
          <div style={{ width: 150, height: 222, borderRadius: 12, background: book.color, borderLeft: '4px solid rgba(0,0,0,0.08)', boxShadow: '0 18px 36px -14px rgba(74,64,52,0.55)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '16px 14px' }}>
            <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.09em', color: 'rgba(58,52,44,0.55)' }}>{book.tag}</span>
            <div>
              <div style={{ fontFamily: "'Newsreader',serif", fontSize: 19, fontWeight: 600, lineHeight: 1.08, color: '#352f28' }}>{book.title}</div>
              <div style={{ fontSize: 11, color: 'rgba(58,52,44,0.6)', marginTop: 6, fontWeight: 500 }}>{book.author}</div>
            </div>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginBottom: 18 }}>
          <h2 style={{ fontSize: 24, lineHeight: 1.15 }}>{book.title}</h2>
          <div style={{ fontSize: 14, color: 'var(--muted)', marginTop: 4 }}>{book.author}</div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, marginTop: 10 }}>
            <span style={{ color: '#d9b94e', fontSize: 15, letterSpacing: 1 }}>{stars(book.rating ?? 4)}</span>
            <span style={{ width: 3, height: 3, borderRadius: '50%', background: 'var(--faint)' }} />
            <span style={{ fontSize: 12.5, color: 'var(--accent-deep)', fontWeight: 600 }}>{book.genre}</span>
          </div>
        </div>

        <div style={{ display: 'flex', background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 16, padding: '16px 0', marginBottom: 22 }}>
          <Stat value={String(book.pages ?? '—')} label={t('detail.pages')} />
          <Stat value={book.serieLabel ?? '—'} label={t('detail.series')} grow />
          <Stat value={String(book.year ?? '—')} label={t('detail.released')} last />
        </div>

        <h3 style={{ fontSize: 17, margin: '0 0 8px' }}>{t('detail.summary')}</h3>
        <p style={{ fontSize: 13.5, lineHeight: 1.6, color: 'var(--ink-soft)', margin: '0 0 22px' }}>{book.synopsis}</p>

        <h3 style={{ fontSize: 17, margin: '0 0 12px' }}>Éditions</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
          {editions.map((e) => (
            <div key={e.label} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 14 }}>
              <Icon name="menu_book" size={20} color="var(--accent-deep)" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13.5, fontWeight: 600 }}>{e.label} · {e.publisher}</div>
                <div style={{ fontSize: 11.5, color: 'var(--muted)', marginTop: 2 }}>{e.year} · {e.pages} pages</div>
              </div>
            </div>
          ))}
        </div>

        <h3 style={{ fontSize: 17, margin: '0 0 12px' }}>{t('detail.ranking')}</h3>
        <div style={{ display: 'flex', gap: 9, marginBottom: 26 }}>
          {RANK_OPTIONS.map((r) => {
            const on = rank === r.id;
            return (
              <button
                key={r.id}
                onClick={() => setRank(on ? null : r.id)}
                style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: 10, borderRadius: 12, cursor: 'pointer', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', color: 'var(--ink-soft)', border: on ? `1.5px solid ${RANK_COLORS[r.id]}` : '1.5px solid var(--line)', background: on ? `${RANK_COLORS[r.id]}22` : 'var(--surface)' }}
              >
                <span style={{ width: 11, height: 11, borderRadius: '50%', background: RANK_COLORS[r.id] }} />
                {r.name}
              </button>
            );
          })}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Button variant="primary" style={{ padding: 15 }}>
            <Icon name="auto_stories" size={20} fill color="#fff" />
            Commencer la lecture
          </Button>
          <Button variant="secondary" style={{ padding: 14 }}>Marquer comme lu</Button>
        </div>
      </div>
    </div>
  );
}

function Stat({ value, label, grow, last }: { value: string; label: string; grow?: boolean; last?: boolean }) {
  return (
    <div style={{ flex: grow ? 1.4 : 1, textAlign: 'center', borderRight: last ? 'none' : '1px solid var(--line)', padding: grow ? '0 8px' : 0 }}>
      <div style={{ fontFamily: "'Newsreader',serif", fontSize: grow ? 14 : 18, fontWeight: 600, lineHeight: 1.1 }}>{value}</div>
      <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 3 }}>{label}</div>
    </div>
  );
}
