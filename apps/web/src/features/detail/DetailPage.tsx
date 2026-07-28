import { useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import { RANK_COLORS } from '../collection/mockData';
import {
  getGetApiLibraryIdQueryKey,
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  useGetApiCategories,
  useGetApiLibraryId,
  usePutApiLibraryIdProgress,
  usePutApiLibraryIdRank,

} from '../../api/generated/librarius';

const PALETTE = ['#bccab2', '#cabdd6', '#ddb9b3', '#b6c6d6', '#dccfae', '#aec8c0'];
function colorFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

function DetailContent({ id }: { id: string }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // One request for one title. The collection is paginated, so the item is no longer
  // guaranteed to be in a cached page — and a deep link never had it. Its own cache
  // entry also means the screen keeps working when the user reloads on this URL.
  const { data: item = null, isPending: loading } = useGetApiLibraryId(id);
  const { data: cats = [] } = useGetApiCategories();

  const invalidateLibrary = () => {
    // The item has its own cache entry, whose key is not a prefix of the collection's:
    // invalidating the list alone would leave this very screen showing a stale rank.
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryIdQueryKey(id) });
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
  };

  const { mutate: mutateRank } = usePutApiLibraryIdRank({
    mutation: { onSuccess: invalidateLibrary },
  });
  const { mutate: mutateProgress } = usePutApiLibraryIdProgress({
    mutation: { onSuccess: invalidateLibrary },
  });

  function assignRank(categoryId?: string) {
    mutateRank({ id, data: { categoryId } });
  }

  function setStatus(status: 'READING' | 'READ') {
    mutateProgress({ id, data: { status, percent: status === 'READ' ? 100 : undefined } });
  }

  if (loading) return <p style={{ padding: '40px 22px', color: 'var(--muted)' }}>Chargement…</p>;
  if (!item) {
    return (
      <div style={{ padding: '40px 22px', textAlign: 'center', color: 'var(--muted)' }}>
        <p>Titre introuvable.</p>
        <Button variant="secondary" onClick={() => navigate(-1)}>Retour</Button>
      </div>
    );
  }

  const b = item.book!;
  const title = b.title ?? '—';
  const color = colorFor(title);
  const ranks = cats.filter((c) => ['or', 'argent', 'bronze'].includes(c.code ?? ''));

  return (
    <div style={{ position: 'relative' }}>
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 300, background: `linear-gradient(180deg, ${color}aa, ${color}00)` }} />
      <div style={{ position: 'relative', padding: '0 22px 40px' }}>
        <div style={{ padding: '14px 0 18px' }}>
          <button onClick={() => navigate(-1)} aria-label="Retour" style={{ width: 40, height: 40, border: 'none', borderRadius: '50%', background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
            <Icon name="arrow_back" size={24} color="#3a342c" />
          </button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 18 }}>
          <div style={{ width: 150, height: 222, borderRadius: 12, background: b.coverUrl ? `center/cover no-repeat url(${b.coverUrl})` : color, borderLeft: '4px solid rgba(0,0,0,0.08)', boxShadow: '0 18px 36px -14px rgba(74,64,52,0.55)', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', padding: b.coverUrl ? 0 : '16px 14px', overflow: 'hidden' }}>
            {!b.coverUrl && <div style={{ fontFamily: "'Newsreader',serif", fontSize: 19, fontWeight: 600, color: '#352f28' }}>{title}</div>}
          </div>
        </div>

        <div style={{ textAlign: 'center', marginBottom: 18 }}>
          <h2 style={{ fontSize: 24, lineHeight: 1.15 }}>{title}</h2>
          <div style={{ fontSize: 14, color: 'var(--muted)', marginTop: 4 }}>{b.authors}</div>
          <div style={{ fontSize: 12.5, color: 'var(--accent-deep)', fontWeight: 600, marginTop: 8 }}>
            {b.genres || (b.kind === 'MANGA' ? 'Manga' : 'Roman')}
          </div>
        </div>

        <div style={{ display: 'flex', background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 16, padding: '16px 0', marginBottom: 22 }}>
          <Stat value={b.pageCount != null ? String(b.pageCount) : '—'} label="pages" />
          <Stat value={b.seriesTitle || 'Standalone'} label="série" grow />
          <Stat value={b.originalYear != null ? String(b.originalYear) : '—'} label="sorti" last />
        </div>

        {b.synopsis && (
          <>
            <h3 style={{ fontSize: 17, margin: '0 0 8px' }}>Résumé</h3>
            <p style={{ fontSize: 13.5, lineHeight: 1.6, color: 'var(--ink-soft)', margin: '0 0 22px' }}>{b.synopsis}</p>
          </>
        )}

        <h3 style={{ fontSize: 17, margin: '0 0 12px' }}>Classement</h3>
        <div style={{ display: 'flex', gap: 9, marginBottom: 26 }}>
          {ranks.map((r) => {
            const on = item.rankCode === r.code;
            const rc = RANK_COLORS[r.code as 'or' | 'argent' | 'bronze'];
            return (
              <button key={r.id} onClick={() => assignRank(on ? undefined : r.id)} style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: 10, borderRadius: 12, cursor: 'pointer', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', color: 'var(--ink-soft)', border: on ? `1.5px solid ${rc}` : '1.5px solid var(--line)', background: on ? `${rc}22` : 'var(--surface)' }}>
                <span style={{ width: 11, height: 11, borderRadius: '50%', background: rc }} />
                {r.label}
              </button>
            );
          })}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {item.status !== 'READ' && (
            <Button variant="primary" style={{ padding: 15 }} onClick={() => setStatus('READING')}>
              <Icon name="auto_stories" size={20} fill color="#fff" />
              {item.status === 'READING' ? 'Lecture en cours' : 'Commencer la lecture'}
            </Button>
          )}
          <Button variant="secondary" style={{ padding: 14 }} onClick={() => setStatus('READ')}>
            {item.status === 'READ' ? '✓ Lu' : 'Marquer comme lu'}
          </Button>
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

export function DetailPage() {
  const { id = '' } = useParams();
  return (
    <LoginGate prompt="Connecte-toi pour voir ce titre.">
      <DetailContent id={id} />
    </LoginGate>
  );
}
