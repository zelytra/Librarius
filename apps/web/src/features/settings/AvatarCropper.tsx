import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../shared/ui/primitives';
import { Modal } from '../../shared/ui/Modal';
import { CENTRED, cropSquare, type AvatarCrop } from './avatarImage';
import styles from './ProfileSection.module.css';

/** The preview's side, in CSS pixels. */
const VIEW = 240;
/** How far one arrow key moves the frame, as a share of the room there is to move it. */
const STEP = 0.1;

const clamp = (value: number) => Math.min(1, Math.max(-1, Math.round(value * 100) / 100));

/**
 * Framing a new profile picture before it leaves (#571): a round preview to drag, or to move
 * with the arrow keys, and a zoom slider. What the preview shows is the square sent.
 *
 * <p>The preview is drawn on a canvas from the decoded picture — the same `createImageBitmap`
 * the upload uses — rather than shown through a URL of the file: the chosen file never becomes
 * an address on the page (CodeQL js/xss-through-dom), and there is nothing to revoke.
 */
export function AvatarCropper({
  file,
  onCancel,
  onConfirm,
}: {
  file: File;
  onCancel: () => void;
  onConfirm: (crop: AvatarCrop) => void;
}) {
  const { t } = useTranslation();
  const [picture, setPicture] = useState<ImageBitmap | null>(null);
  const [crop, setCrop] = useState<AvatarCrop>(CENTRED);
  const drag = useRef<{ x: number; y: number; crop: AvatarCrop } | null>(null);
  const canvas = useRef<HTMLCanvasElement>(null);

  // Decoded once per file, and released when the dialog closes or the file changes. A runtime
  // without createImageBitmap (jsdom) shows an empty frame; the framing still works.
  useEffect(() => {
    if (typeof createImageBitmap !== 'function') return;
    let current: ImageBitmap | null = null;
    let cancelled = false;
    createImageBitmap(file)
      .then((decoded) => {
        if (cancelled) {
          decoded.close();
          return;
        }
        current = decoded;
        setPicture(decoded);
      })
      .catch(() => {
        // An undecodable file is refused by the upload itself, with its own message.
      });
    return () => {
      cancelled = true;
      current?.close();
    };
  }, [file]);

  // Redrawn on every move: the square the upload will cut, scaled to the frame.
  useEffect(() => {
    const target = canvas.current;
    const context = target?.getContext?.('2d');
    if (!target || !context || !picture) return;
    const ratio = window.devicePixelRatio || 1;
    target.width = Math.round(VIEW * ratio);
    target.height = Math.round(VIEW * ratio);
    const { sx, sy, side } = cropSquare(picture.width, picture.height, crop);
    context.clearRect(0, 0, target.width, target.height);
    context.drawImage(picture, sx, sy, side, side, 0, 0, target.width, target.height);
  }, [picture, crop]);

  // How many preview pixels one unit of `x` or `y` spans, at the current zoom.
  const span = (axis: 'width' | 'height') => {
    if (!picture) return 1;
    const { side } = cropSquare(picture.width, picture.height, crop);
    return Math.max(1, ((picture[axis] - side) / 2) * (VIEW / side));
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture?.(event.pointerId);
    drag.current = { x: event.clientX, y: event.clientY, crop };
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const start = drag.current;
    if (!start) return;
    // Dragging the picture right shows more of its left: the frame moves the other way.
    setCrop({
      ...start.crop,
      x: clamp(start.crop.x - (event.clientX - start.x) / span('width')),
      y: clamp(start.crop.y - (event.clientY - start.y) / span('height')),
    });
  };
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-STEP, 0],
      ArrowRight: [STEP, 0],
      ArrowUp: [0, -STEP],
      ArrowDown: [0, STEP],
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    setCrop((c) => ({ ...c, x: clamp(c.x + move[0]), y: clamp(c.y + move[1]) }));
  };

  return (
    <Modal title={t('settings.avatar.cropTitle')} description={t('settings.avatar.cropHint')} onClose={onCancel}>
      <div
        role="group"
        aria-label={t('settings.avatar.cropFrame')}
        tabIndex={0}
        className={styles.cropFrame}
        style={{ width: VIEW, height: VIEW }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={() => (drag.current = null)}
        onPointerCancel={() => (drag.current = null)}
        onKeyDown={onKeyDown}
      >
        {/* Decorative: the frame names the picture, and the slider and arrows act on it. */}
        <canvas ref={canvas} aria-hidden="true" className={styles.cropImage} />
      </div>
      <label className={styles.cropZoom}>
        <span>{t('settings.avatar.zoom')}</span>
        <input
          type="range"
          min={1}
          max={3}
          step={0.05}
          value={crop.zoom}
          aria-label={t('settings.avatar.zoom')}
          onChange={(event) => setCrop((c) => ({ ...c, zoom: Number(event.target.value) }))}
        />
      </label>
      <div className={styles.cropActions}>
        <Button variant="secondary" size="compact" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button variant="primary" size="compact" onClick={() => onConfirm(crop)}>
          {t('settings.avatar.cropSave')}
        </Button>
      </div>
    </Modal>
  );
}
