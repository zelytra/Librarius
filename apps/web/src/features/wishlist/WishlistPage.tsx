import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';

interface WishItem {
  id: string;
  title: string;
  author: string;
  color: string;
  price: string;
  prio: 'high' | 'mid' | 'low';
}

const PRIO: Record<WishItem['prio'], { label: string; color: string; bg: string }> = {
  high: { label: 'Priorité', color: '#b0857f', bg: '#f4e4e1' },
  mid: { label: 'Bientôt', color: '#7d8f73', bg: '#e6ece0' },
  low: { label: 'Un jour', color: '#9a8fa6', bg: '#ece6f0' },
};

const WISHLIST: WishItem[] = [
  { id: 'w1', title: 'Onyx Storm', author: 'Rebecca Yarros', color: '#bccab2', price: '24,90 €', prio: 'high' },
  { id: 'w2', title: 'A Court of Mist and Fury', author: 'Sarah J. Maas', color: '#cabdd6', price: '9,90 €', prio: 'high' },
  { id: 'w3', title: 'Blue Lock T.26', author: 'M. Kaneshiro', color: '#b6c6d6', price: '7,20 €', prio: 'mid' },
  { id: 'w4', title: 'Twisted Games', author: 'Ana Huang', color: '#ddb9b3', price: '12,90 €', prio: 'mid' },
  { id: 'w5', title: 'Berserk T.42', author: 'Kentaro Miura', color: '#cab5ad', price: '8,50 €', prio: 'low' },
];

export function WishlistPage() {
  const { t } = useTranslation();
  const total = WISHLIST.reduce((sum, w) => sum + parseFloat(w.price.replace(',', '.')), 0);

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 6px' }}>{t('wishlist.title')}</h2>
      <p style={{ margin: '0 0 22px', fontSize: 13, color: 'var(--muted)' }}>
        {WISHLIST.length} titres · estimé {total.toFixed(2).replace('.', ',')} €
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {WISHLIST.map((w) => {
          const p = PRIO[w.prio];
          return (
            <div
              key={w.id}
              style={{ display: 'flex', gap: 14, alignItems: 'center', padding: 12, background: 'var(--surface)', borderRadius: 16, border: '1px solid var(--line)' }}
            >
              <div style={{ flex: '0 0 auto', width: 52, height: 76, borderRadius: 8, background: w.color, borderLeft: '3px solid rgba(0,0,0,0.07)', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', padding: 7, boxShadow: '0 3px 8px -3px rgba(0,0,0,0.25)' }}>
                <span style={{ fontFamily: "'Newsreader',serif", fontSize: 10, fontWeight: 600, lineHeight: 1.05, color: '#352f28' }}>{w.title}</span>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 15, fontWeight: 600, lineHeight: 1.1 }}>{w.title}</div>
                <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 1 }}>{w.author}</div>
                <span style={{ display: 'inline-block', marginTop: 6, fontSize: 10, fontWeight: 600, color: p.color, background: p.bg, padding: '3px 8px', borderRadius: 20 }}>{p.label}</span>
              </div>
              <div style={{ flex: '0 0 auto', textAlign: 'right' }}>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 16, fontWeight: 600 }}>{w.price}</div>
                <Icon name="add_shopping_cart" size={24} fill color="var(--accent)" style={{ marginTop: 4 }} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
