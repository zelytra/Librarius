import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Grid } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { moveKey } from './views';
import styles from './ReorderGrid.module.css';

/**
 * The shelf in "arrange" mode (#451): each entry gets a handle to drag it — with a finger or
 * a mouse, pointer events serving both — and two buttons that move it one place either way,
 * for the keyboard. The new order is handed back once the drag ends.
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
  const [order, setOrder] = useState(keys);
  const [dragging, setDragging] = useState<string | null>(null);
  // The order being dragged, read by the pointer handlers without waiting for a render.
  const live = useRef(order);
  live.current = order;

  // A page that loads while arranging brings new entries: they join at the end.
  useEffect(() => {
    setOrder((previous) => [...previous.filter((k) => keys.includes(k)), ...keys.filter((k) => !previous.includes(k))]);
  }, [keys]);

  const commit = (next: string[]) => {
    setOrder(next);
    onCommit(next);
  };

  const onMove = (event: React.PointerEvent) => {
    if (!dragging) return;
    const target = document
      .elementFromPoint(event.clientX, event.clientY)
      ?.closest<HTMLElement>('[data-reorder-key]')?.dataset.reorderKey;
    if (!target || target === dragging) return;
    const current = live.current;
    setOrder(moveKey(current, current.indexOf(dragging), current.indexOf(target)));
  };

  const onEnd = () => {
    if (!dragging) return;
    setDragging(null);
    onCommit(live.current);
  };

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
              onClick={() => commit(moveKey(order, index, index - 1))}
            >
              <Icon name="arrow_back" size={16} color="var(--ink)" />
            </button>
            <button
              type="button"
              className={styles.handle}
              aria-label={t('views.drag')}
              onPointerDown={(event) => {
                event.preventDefault();
                (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
                setDragging(key);
              }}
              onPointerMove={onMove}
              onPointerUp={onEnd}
              onPointerCancel={onEnd}
            >
              <Icon name="drag_indicator" size={18} color="var(--ink)" />
            </button>
            <button
              type="button"
              className={styles.step}
              aria-label={t('views.later')}
              disabled={index === order.length - 1}
              onClick={() => commit(moveKey(order, index, index + 1))}
            >
              <Icon name="arrow_forward" size={16} color="var(--ink)" />
            </button>
          </div>
        </div>
      ))}
    </Grid>
  );
}
