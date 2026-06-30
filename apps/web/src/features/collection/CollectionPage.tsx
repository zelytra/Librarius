import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Chip, Segmented } from '../../shared/ui/primitives';
import {
  BOOKS,
  MANGAS,
  RANK_COLORS,
  RANK_ICONS,
  type MockBook,
  type Rank,
} from './mockData';

type CollType = 'livres' | 'mangas';
type RankFilter = 'all' | 'or' | 'argent' | 'bronze';
type SortBy = 'ajout' | 'titre' | 'auteur' | 'genre';

const SORTS: { id: SortBy; label: string }[] = [
  { id: 'ajout', label: 'Ajout' },
  { id: 'titre', label: 'Titre' },
  { id: 'auteur', label: 'Auteur' },
  { id: 'genre', label: 'Genre' },
];

function RankBadge({ rank }: { rank: Exclude<Rank, null> }) {
  return (
    <span
      style={{
        position: 'absolute',
        top: -6,
        right: -6,
        width: 24,
        height: 24,
        borderRadius: '50%',
        background: RANK_COLORS[rank],
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: '0 2px 6px rgba(74,64,52,0.3)',
      }}
    >
      <Icon name={RANK_ICONS[rank]} size={14} fill color="#fff" />
    </span>
  );
}

function CoverTile({ book, onClick, width }: { book: MockBook; onClick: () => void; width?: number }) {
  return (
    <div onClick={onClick} style={{ cursor: 'pointer', width }}>
      <div
        style={{
          position: 'relative',
          width: width ?? '100%',
          aspectRatio: width ? undefined : '0.68',
          height: width ? width / 0.68 : undefined,
          borderRadius: 9,
          background: book.color,
          borderLeft: '3px solid rgba(0,0,0,0.07)',
          boxShadow: '0 7px 16px -8px rgba(74,64,52,0.4)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: '10px 9px',
        }}
      >
        <span style={{ fontSize: 8, fontWeight: 700, letterSpacing: '0.07em', color: 'rgba(58,52,44,0.5)' }}>
          {book.tag}
        </span>
        <div style={{ fontFamily: "'Newsreader',serif", fontSize: 12.5, fontWeight: 600, lineHeight: 1.08, color: '#352f28' }}>
          {book.title}
        </div>
        {book.rank && <RankBadge rank={book.rank} />}
      </div>
      <div
        style={{
          fontSize: 10,
          color: 'var(--muted)',
          marginTop: 6,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {book.author}
      </div>
    </div>
  );
}

export function CollectionPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [collType, setCollType] = useState<CollType>('livres');
  const [rankFilter, setRankFilter] = useState<RankFilter>('all');
  const [sortBy, setSortBy] = useState<SortBy>('ajout');
  const [grouped, setGrouped] = useState(false);

  const base = collType === 'mangas' ? MANGAS : BOOKS;

  const filtered = useMemo(() => {
    const list = rankFilter === 'all' ? base : base.filter((b) => b.rank === rankFilter);
    if (sortBy === 'ajout') return list;
    const key = sortBy === 'titre' ? 'title' : sortBy === 'auteur' ? 'author' : 'genre';
    return [...list].sort((a, b) => a[key].localeCompare(b[key], 'fr'));
  }, [base, rankFilter, sortBy]);

  const groups = useMemo(() => {
    const map = new Map<string, MockBook[]>();
    filtered.forEach((b) => {
      if (!map.has(b.series)) map.set(b.series, []);
      map.get(b.series)!.push(b);
    });
    return [...map.entries()];
  }, [filtered]);

  const open = (id: string) => navigate(`/detail/${id}`);

  const cats: { id: RankFilter; name: string; dot?: string }[] = [
    { id: 'all', name: 'Tous' },
    { id: 'or', name: 'Or', dot: RANK_COLORS.or },
    { id: 'argent', name: 'Argent', dot: RANK_COLORS.argent },
    { id: 'bronze', name: 'Bronze', dot: RANK_COLORS.bronze },
  ];

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 16px' }}>{t('collection.title')}</h2>

      <div style={{ marginBottom: 18 }}>
        <Segmented<CollType>
          value={collType}
          onChange={setCollType}
          options={[
            { id: 'livres', label: t('common.books') },
            { id: 'mangas', label: t('common.mangas') },
          ]}
        />
      </div>

      <div className="scroll-x" style={{ display: 'flex', gap: 9, overflowX: 'auto', margin: '0 -22px 16px', padding: '2px 22px 6px' }}>
        {cats.map((c) => (
          <Chip key={c.id} selected={rankFilter === c.id} dotColor={c.dot} onClick={() => setRankFilter(c.id)}>
            {c.name}
          </Chip>
        ))}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <span style={{ fontSize: 13, color: 'var(--muted)', fontWeight: 500 }}>
          {filtered.length} titres
        </span>
        <div style={{ width: 160 }}>
          <Segmented<'flat' | 'grouped'>
            value={grouped ? 'grouped' : 'flat'}
            onChange={(v) => setGrouped(v === 'grouped')}
            options={[
              { id: 'flat', label: t('collection.list') },
              { id: 'grouped', label: t('collection.series') },
            ]}
          />
        </div>
      </div>

      <div className="scroll-x" style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '0 -22px 18px', padding: '0 22px 2px', overflowX: 'auto' }}>
        <span style={{ fontSize: 12, color: 'var(--faint)', fontWeight: 600, flex: '0 0 auto' }}>
          {t('collection.sortBy')}
        </span>
        {SORTS.map((s) => (
          <Chip key={s.id} selected={sortBy === s.id} onClick={() => setSortBy(s.id)}>
            {s.label}
          </Chip>
        ))}
      </div>

      {filtered.length === 0 && (
        <div style={{ textAlign: 'center', padding: '50px 24px', color: 'var(--faint)' }}>
          <Icon name="bookmark_add" size={42} />
          <p style={{ fontSize: 13.5, lineHeight: 1.5, marginTop: 12 }}>
            Aucun titre dans cette catégorie pour l'instant.
          </p>
        </div>
      )}

      {!grouped && filtered.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: '14px 12px' }}>
          {filtered.map((b) => (
            <CoverTile key={b.id} book={b} onClick={() => open(b.id)} />
          ))}
        </div>
      )}

      {grouped && filtered.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {groups.map(([series, items]) => (
            <div key={series} style={{ background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 18, padding: '15px 0 14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px 12px' }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600, lineHeight: 1.1 }}>{series}</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 2 }}>{items[0].author}</div>
                </div>
                <span style={{ flex: '0 0 auto', marginLeft: 12, fontSize: 11, fontWeight: 600, color: 'var(--accent-deep)', background: 'var(--accent-soft)', padding: '5px 11px', borderRadius: 20, whiteSpace: 'nowrap' }}>
                  {items.length > 1 ? `${items.length} tomes` : items[0].genre}
                </span>
              </div>
              <div className="scroll-x" style={{ display: 'flex', gap: 11, overflowX: 'auto', padding: '2px 16px 4px' }}>
                {items.map((b) => (
                  <CoverTile key={b.id} book={b} onClick={() => open(b.id)} width={84} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
