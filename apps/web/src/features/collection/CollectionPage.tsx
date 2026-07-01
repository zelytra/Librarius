import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Chip, Segmented } from '../../shared/ui/primitives';
import { LoginGate } from '../../shared/LoginGate';
import { useApiAuth } from '../../shared/api';
import {
  deleteApiLibraryId,
  getApiLibrary,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import { RANK_COLORS, RANK_ICONS } from './mockData';

type Kind = 'BOOK' | 'MANGA';
type RankFilter = 'all' | 'or' | 'argent' | 'bronze';
type SortBy = 'ajout' | 'titre' | 'auteur' | 'genre';

const PALETTE = ['#bccab2', '#cabdd6', '#ddb9b3', '#b6c6d6', '#dccfae', '#aec8c0', '#d8b6a6', '#bcc9d8', '#c2caa6', '#b9b3c9'];
function colorFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

const SORTS: { id: SortBy; label: string }[] = [
  { id: 'ajout', label: 'Ajout' },
  { id: 'titre', label: 'Titre' },
  { id: 'auteur', label: 'Auteur' },
  { id: 'genre', label: 'Genre' },
];

function CoverTile({ item, onDelete, width }: { item: LibraryItemDto; onDelete: () => void; width?: number }) {
  const b = item.book!;
  const title = b.title ?? '—';
  const color = colorFor(title);
  const rank = item.rankCode as 'or' | 'argent' | 'bronze' | null | undefined;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', width }}>
      <div
        style={{
          position: 'relative',
          width: width ?? '100%',
          aspectRatio: width ? undefined : '0.68',
          height: width ? width / 0.68 : undefined,
          borderRadius: 9,
          background: b.coverUrl ? `center / cover no-repeat url(${b.coverUrl})` : color,
          borderLeft: '3px solid rgba(0,0,0,0.07)',
          boxShadow: '0 7px 16px -8px rgba(74,64,52,0.4)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: b.coverUrl ? 0 : '10px 9px',
          overflow: 'hidden',
        }}
      >
        {!b.coverUrl && (
          <>
            <span style={{ fontSize: 8, fontWeight: 700, letterSpacing: '0.07em', color: 'rgba(58,52,44,0.5)' }}>
              {b.volumeNumber ? `T.${b.volumeNumber}` : (b.kind === 'MANGA' ? 'MANGA' : 'LIVRE')}
            </span>
            <div style={{ fontFamily: "'Newsreader',serif", fontSize: 12.5, fontWeight: 600, lineHeight: 1.08, color: '#352f28' }}>{title}</div>
          </>
        )}
        {rank && (
          <span style={{ position: 'absolute', top: -6, right: -6, width: 24, height: 24, borderRadius: '50%', background: RANK_COLORS[rank], display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 2px 6px rgba(74,64,52,0.3)' }}>
            <Icon name={RANK_ICONS[rank]} size={14} fill color="#fff" />
          </span>
        )}
        <button
          onClick={onDelete}
          aria-label="Retirer"
          style={{ position: 'absolute', bottom: 6, right: 6, width: 24, height: 24, borderRadius: '50%', background: 'rgba(255,255,255,0.85)', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        >
          <Icon name="delete" size={14} color="#b0857f" />
        </button>
      </div>
      <div style={{ fontSize: 10, color: 'var(--muted)', marginTop: 6, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
        {b.authors}
      </div>
    </div>
  );
}

function CollectionContent() {
  const { t } = useTranslation();
  const { opts } = useApiAuth();
  const [items, setItems] = useState<LibraryItemDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [collType, setCollType] = useState<Kind>('BOOK');
  const [rankFilter, setRankFilter] = useState<RankFilter>('all');
  const [sortBy, setSortBy] = useState<SortBy>('ajout');
  const [grouped, setGrouped] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getApiLibrary(undefined, opts);
      if (res.status === 200) setItems(res.data);
    } finally {
      setLoading(false);
    }
    // opts identity changes each render; fetch once on mount is enough here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function remove(id: string) {
    await deleteApiLibraryId(id, opts);
    setItems((cur) => cur.filter((it) => it.id !== id));
  }

  const filtered = useMemo(() => {
    let list = items.filter((it) => it.book?.kind === collType);
    if (rankFilter !== 'all') list = list.filter((it) => it.rankCode === rankFilter);
    if (sortBy !== 'ajout') {
      const key = sortBy === 'titre' ? 'title' : sortBy === 'auteur' ? 'authors' : 'genres';
      list = [...list].sort((a, b) => (a.book?.[key] ?? '').localeCompare(b.book?.[key] ?? '', 'fr'));
    }
    return list;
  }, [items, collType, rankFilter, sortBy]);

  const groups = useMemo(() => {
    const map = new Map<string, LibraryItemDto[]>();
    filtered.forEach((it) => {
      const s = it.book?.seriesTitle || it.book?.title || '—';
      if (!map.has(s)) map.set(s, []);
      map.get(s)!.push(it);
    });
    return [...map.entries()];
  }, [filtered]);

  const cats: { id: RankFilter; name: string; dot?: string }[] = [
    { id: 'all', name: 'Tous' },
    { id: 'or', name: 'Or', dot: RANK_COLORS.or },
    { id: 'argent', name: 'Argent', dot: RANK_COLORS.argent },
    { id: 'bronze', name: 'Bronze', dot: RANK_COLORS.bronze },
  ];

  return (
    <>
      <div style={{ marginBottom: 18 }}>
        <Segmented<Kind>
          value={collType}
          onChange={setCollType}
          options={[
            { id: 'BOOK', label: t('common.books') },
            { id: 'MANGA', label: t('common.mangas') },
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
        <span style={{ fontSize: 13, color: 'var(--muted)', fontWeight: 500 }}>{filtered.length} titres</span>
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
        <span style={{ fontSize: 12, color: 'var(--faint)', fontWeight: 600, flex: '0 0 auto' }}>{t('collection.sortBy')}</span>
        {SORTS.map((s) => (
          <Chip key={s.id} selected={sortBy === s.id} onClick={() => setSortBy(s.id)}>{s.label}</Chip>
        ))}
      </div>

      {loading && <p style={{ color: 'var(--muted)', fontSize: 13 }}>{t('common.loading')}</p>}

      {!loading && filtered.length === 0 && (
        <div style={{ textAlign: 'center', padding: '50px 24px', color: 'var(--faint)' }}>
          <Icon name="bookmark_add" size={42} />
          <p style={{ fontSize: 13.5, lineHeight: 1.5, marginTop: 12 }}>
            Aucun titre ici. Ajoute des livres depuis l'onglet <strong>Découvrir</strong>.
          </p>
        </div>
      )}

      {!loading && !grouped && filtered.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: '14px 12px' }}>
          {filtered.map((it) => (
            <CoverTile key={it.id} item={it} onDelete={() => void remove(it.id!)} />
          ))}
        </div>
      )}

      {!loading && grouped && filtered.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {groups.map(([series, list]) => (
            <div key={series} style={{ background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 18, padding: '15px 0 14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px 12px' }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600, lineHeight: 1.1 }}>{series}</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 2 }}>{list[0]?.book?.authors}</div>
                </div>
                <span style={{ flex: '0 0 auto', marginLeft: 12, fontSize: 11, fontWeight: 600, color: 'var(--accent-deep)', background: 'var(--accent-soft)', padding: '5px 11px', borderRadius: 20, whiteSpace: 'nowrap' }}>
                  {list.length > 1 ? `${list.length} tomes` : (list[0]?.book?.genres ?? '')}
                </span>
              </div>
              <div className="scroll-x" style={{ display: 'flex', gap: 11, overflowX: 'auto', padding: '2px 16px 4px' }}>
                {list.map((it) => (
                  <CoverTile key={it.id} item={it} onDelete={() => void remove(it.id!)} width={84} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

export function CollectionPage() {
  const { t } = useTranslation();
  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 16px' }}>{t('collection.title')}</h2>
      <LoginGate prompt="Connecte-toi pour voir ta collection.">
        <CollectionContent />
      </LoginGate>
    </div>
  );
}
