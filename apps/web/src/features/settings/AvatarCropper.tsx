import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
} from "react";
import { useTranslation } from "react-i18next";
import { Button } from "../../shared/ui/primitives";
import { Modal } from "../../shared/ui/Modal";
import { CENTRED, cropSquare, type AvatarCrop } from "./avatarImage";
import styles from "./ProfileSection.module.css";

/** The preview's side, in CSS pixels. */
const VIEW = 240;
/** How far one arrow key moves the frame, as a share of the room there is to move it. */
const STEP = 0.1;

const clamp = (value: number) =>
  Math.min(1, Math.max(-1, Math.round(value * 100) / 100));

/**
 * Framing a new profile picture before it leaves (#571): a round preview to drag, or to move
 * with the arrow keys, and a zoom slider. What the preview shows is the square sent.
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
  const [url, setUrl] = useState<string | null>(null);
  const [size, setSize] = useState<{ width: number; height: number } | null>(
    null
  );
  const [crop, setCrop] = useState<AvatarCrop>(CENTRED);
  const drag = useRef<{ x: number; y: number; crop: AvatarCrop } | null>(null);

  // Made and revoked by the same effect, so React's double mount in development never leaves
  // the preview pointing at a revoked address.
  useEffect(() => {
    const address = URL.createObjectURL(file);
    setUrl(address);
    return () => URL.revokeObjectURL(address);
  }, [file]);

  // How many preview pixels one unit of `x` or `y` spans, at the current zoom.
  const span = (axis: "width" | "height") => {
    if (!size) return 1;
    const { side } = cropSquare(size.width, size.height, crop);
    return Math.max(1, ((size[axis] - side) / 2) * (VIEW / side));
  };

  const square = size ? cropSquare(size.width, size.height, crop) : null;
  const scale = square ? VIEW / square.side : 1;

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
      x: clamp(start.crop.x - (event.clientX - start.x) / span("width")),
      y: clamp(start.crop.y - (event.clientY - start.y) / span("height")),
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
    setCrop((c) => ({
      ...c,
      x: clamp(c.x + move[0]),
      y: clamp(c.y + move[1]),
    }));
  };

  return (
    <Modal
      title={t("settings.avatar.cropTitle")}
      description={t("settings.avatar.cropHint")}
      onClose={onCancel}
    >
      <div
        role="group"
        aria-label={t("settings.avatar.cropFrame")}
        tabIndex={0}
        className={styles.cropFrame}
        style={{ width: VIEW, height: VIEW }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={() => (drag.current = null)}
        onPointerCancel={() => (drag.current = null)}
        onKeyDown={onKeyDown}
      >
        {url && (
          <img
            src={url}
            // eslint-disable-next-line no-restricted-syntax -- empty alt: the frame names the picture
            alt=""
            draggable={false}
            className={styles.cropImage}
            onLoad={(event) =>
              setSize({
                width: event.currentTarget.naturalWidth,
                height: event.currentTarget.naturalHeight,
              })
            }
            style={
              size && square
                ? {
                    width: size.width * scale,
                    height: size.height * scale,
                    transform: `translate(${-square.sx * scale}px, ${
                      -square.sy * scale
                    }px)`,
                  }
                : { width: "100%", height: "100%", objectFit: "cover" }
            }
          />
        )}
      </div>
      <label className={styles.cropZoom}>
        <span>{t("settings.avatar.zoom")}</span>
        <input
          type="range"
          min={1}
          max={3}
          step={0.05}
          value={crop.zoom}
          aria-label={t("settings.avatar.zoom")}
          onChange={(event) =>
            setCrop((c) => ({ ...c, zoom: Number(event.target.value) }))
          }
        />
      </label>
      <div className={styles.cropActions}>
        <Button variant="secondary" size="compact" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button
          variant="primary"
          size="compact"
          onClick={() => onConfirm(crop)}
        >
          {t("settings.avatar.cropSave")}
        </Button>
      </div>
    </Modal>
  );
}
