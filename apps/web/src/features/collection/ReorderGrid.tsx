import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Grid } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { moveKey } from './views';
import styles from './ReorderGrid.module.css';

/**
 * The shelf of a view (#451, #482): each entry keeps opening on a tap, and gets a handle to
 * drag it — with a finger or a mouse, pointer events serving both — and two buttons that move
 * it one place either way, for the keyboard. The new order is handed back once the drag ends;
 * the parent shows it by passing it back as `keys`.
 */
export function ReorderGrid({
  keys,
  render,
  onCommit,
}: {
  keys: string[];
  render: (key: string) => ReactNode;
  onCommit: (keys: string[]) => void;
}) {
  const { t } = useTranslation();
  // The order being dragged, or null: outside a drag the shelf is exactly `keys`, so a new
  // sort, filter or page shows at once and nothing arranged earlier lingers (#486).
  const [drag, setDrag] = useState<{ key: string; order: string[] } | null>(null);
  const order = drag?.order ?? keys;
  const dragging = drag?.key ?? null;
  const live = useRef(drag);
  live.current = drag;

  // The gesture is followed on the window, not the handle: the handle moves in the page as
  // the entries swap and may lose the pointer, and a release must end the drag wherever it
  // lands (#486).
  useEffect(() => {
    if (!dragging) return;
    const onMove = (event: PointerEvent) => {
      const current = live.current;
      if (!current) return;
      const target = document
        .elementFromPoint(event.clientX, event.clientY)
        ?.closest<HTMLElement>('[data-reorder-key]')?.dataset.reorderKey;
      if (!target || target === current.key) return;
      const next = moveKey(current.order, current.order.indexOf(current.key), current.order.indexOf(target));
      setDrag({ key: current.key, order: next });
    };
    const onUp = () => {
      const current = live.current;
      setDrag(null);
      if (current && current.order.some((key, i) => key !== keys[i])) onCommit(current.order);
    };
    const onCancel = () => setDrag(null);
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onCancel);
    window.addEventListener('blur', onCancel);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onCancel);
      window.removeEventListener('blur', onCancel);
    };
  }, [dragging, keys, onCommit]);

  return (
    <Grid>
      {order.map((key, index) => (
        <div
          key={key}
          data-reorder-key={key}
          className={`${styles.cell} ${dragging === key ? styles.dragging : ''}`}
        >
          {render(key)}
          <div className={styles.controls}>
            <button
              type="button"
              className={styles.step}
              aria-label={t('views.earlier')}
              disabled={index === 0}
              onClick={() => onCommit(moveKey(order, index, index - 1))}
            >
              <Icon name="arrow_back" size={16} color="var(--ink)" />
            </button>
            <button
              type="button"
              className={styles.handle}
              aria-label={t('views.drag')}
              onPointerDown={(event) => {
                event.preventDefault();
                setDrag({ key, order: keys });
              }}
            >
              <Icon name="drag_indicator" size={18} color="var(--ink)" />
            </button>
            <button
              type="button"
              className={styles.step}
              aria-label={t('views.later')}
              disabled={index === order.length - 1}
              onClick={() => onCommit(moveKey(order, index, index + 1))}
            >
              <Icon name="arrow_forward" size={16} color="var(--ink)" />
            </button>
          </div>
        </div>
      ))}
    </Grid>
  );
}
