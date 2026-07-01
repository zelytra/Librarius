import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { LoginGate } from '../../shared/LoginGate';
import { useApiAuth } from '../../shared/api';
import {
  deleteApiWishlistId,
  getApiWishlist,
  type WishlistItemDto,
} from '../../api/generated/librarius';

const PRIO: Record<string, { label: string; color: string; bg: string }> = {
  PRIORITY: { label: 'Priorité', color: '#b0857f', bg: '#f4e4e1' },
  SOON: { label: 'Bientôt', color: '#7d8f73', bg: '#e6ece0' },
  SOMEDAY: { label: 'Un jour', color: '#9a8fa6', bg: '#ece6f0' },
};

const PALETTE = ['#bccab2', '#cabdd6', '#ddb9b3', '#b6c6d6', '#dccfae', '#cab5ad'];
function colorFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

function WishlistContent() {
  const { opts } = useApiAuth();
  const [items, setItems] = useState<WishlistItemDto[]>([]);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getApiWishlist(opts);
      if (res.status === 200) setItems(res.data);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function remove(id: string) {
    await deleteApiWishlistId(id, opts);
    setItems((cur) => cur.filter((it) => it.id !== id));
  }

  const total = items.reduce((s, w) => s + (w.estimatedPrice ?? 0), 0);

  if (loading) return <p style={{ color: 'var(--muted)', fontSize: 13 }}>Chargement…</p>;

  if (items.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 24px', color: 'var(--faint)' }}>
        <Icon name="favorite" size={40} />
        <p style={{ fontSize: 13.5, lineHeight: 1.5, marginTop: 12 }}>
          Ta liste de souhaits est vide. Ajoute des titres depuis <strong>Découvrir</strong>.
        </p>
      </div>
    );
  }

  return (
    <>
      <p style={{ margin: '0 0 22px', fontSize: 13, color: 'var(--muted)' }}>
        {items.length} titres · estimé {total.toFixed(2).replace('.', ',')} €
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {items.map((w) => {
          const p = PRIO[w.priority ?? 'SOON'] ?? PRIO.SOON;
          const title = w.book?.title ?? '—';
          return (
            <div key={w.id} style={{ display: 'flex', gap: 14, alignItems: 'center', padding: 12, background: 'var(--surface)', borderRadius: 16, border: '1px solid var(--line)' }}>
              <div style={{ flex: '0 0 auto', width: 52, height: 76, borderRadius: 8, background: w.book?.coverUrl ? `center/cover no-repeat url(${w.book.coverUrl})` : colorFor(title), borderLeft: '3px solid rgba(0,0,0,0.07)', boxShadow: '0 3px 8px -3px rgba(0,0,0,0.25)' }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 15, fontWeight: 600, lineHeight: 1.1 }}>{title}</div>
                <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 1 }}>{w.book?.authors}</div>
                <span style={{ display: 'inline-block', marginTop: 6, fontSize: 10, fontWeight: 600, color: p.color, background: p.bg, padding: '3px 8px', borderRadius: 20 }}>{p.label}</span>
              </div>
              <div style={{ flex: '0 0 auto', textAlign: 'right' }}>
                {w.estimatedPrice != null && (
                  <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600 }}>{w.estimatedPrice.toFixed(2).replace('.', ',')} €</div>
                )}
                <button onClick={() => void remove(w.id!)} aria-label="Retirer" style={{ background: 'none', border: 'none', cursor: 'pointer', marginTop: 4 }}>
                  <Icon name="delete" size={22} color="var(--faint)" />
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}

export function WishlistPage() {
  const { t } = useTranslation();
  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 12px' }}>{t('wishlist.title')}</h2>
      <LoginGate prompt="Connecte-toi pour voir tes souhaits.">
        <WishlistContent />
      </LoginGate>
    </div>
  );
}
