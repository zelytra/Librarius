import {
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
} from "react";
import { useTranslation } from "react-i18next";
import { useGetApiAdminCountries } from "../../api/generated/librarius";
import { activeLanguage } from "../../i18n/languages";
import { countryName } from "../../shared/countries";
import { ErrorState, Loading } from "../../shared/ui/states";
import { CENTROIDS, LAND } from "./globeData";
import { project, type Facing } from "./orthographic";
import styles from "./AdminPage.module.css";

/** The globe's radius in SVG units; the drawing scales to the panel's width. */
const R = 100;
/** How far one arrow key turns the globe, in degrees. */
const STEP = 15;
/** Degrees turned per pixel dragged. */
const DRAG = 0.5;

const clampLat = (lat: number) => Math.max(-80, Math.min(80, lat));
const wrapLon = (lon: number) => ((((lon + 180) % 360) + 360) % 360) - 180;

/** The land outlines as one SVG path, at the current facing. */
function landPath(facing: Facing): string {
  let d = "";
  for (const ring of LAND) {
    let points = "";
    let anyVisible = false;
    for (let i = 0; i < ring.length; i += 2) {
      const p = project([ring[i], ring[i + 1]], facing, R);
      anyVisible ||= p.visible;
      points += `${i === 0 ? "M" : "L"}${p.x} ${p.y}`;
    }
    if (anyVisible) d += `${points}Z`;
  }
  return d;
}

/**
 * Where the members live (#573): a count per country — only from the country each member chose
 * to give in their profile, never who — on a globe that turns with a drag or the arrow keys, and
 * the same counts as a list, which is what a screen reader reads.
 */
export function AdminGlobe() {
  const { t } = useTranslation();
  const language = activeLanguage();
  const { data, isPending, isError, refetch } = useGetApiAdminCountries();
  const rows = useMemo(() => data ?? [], [data]);
  const largest = rows.find((row) => row.country && CENTROIDS[row.country]);
  const start = largest?.country
    ? CENTROIDS[largest.country]
    : ([10, 30] as const);
  const [turned, setTurned] = useState<Facing | null>(null);
  const facing = turned ?? { lon: start[0], lat: clampLat(start[1]) };
  const drag = useRef<{ x: number; y: number; facing: Facing } | null>(null);
  const land = useMemo(() => landPath({ lon: facing.lon, lat: facing.lat }), [facing.lon, facing.lat]);

  if (isPending) return <Loading />;
  if (isError)
    return (
      <ErrorState
        message={t("admin.globe.error")}
        onRetry={() => void refetch()}
      />
    );
  if (rows.length === 0)
    return <p className={styles.note}>{t("admin.globe.empty")}</p>;

  const max = Math.max(...rows.map((row) => row.members ?? 0));
  const total = rows.reduce((sum, row) => sum + (row.members ?? 0), 0);

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-STEP, 0],
      ArrowRight: [STEP, 0],
      ArrowUp: [0, STEP],
      ArrowDown: [0, -STEP],
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    setTurned({
      lon: wrapLon(facing.lon + move[0]),
      lat: clampLat(facing.lat + move[1]),
    });
  };
  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture?.(event.pointerId);
    drag.current = { x: event.clientX, y: event.clientY, facing };
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const from = drag.current;
    if (!from) return;
    // Dragging the globe right brings the west into view: the facing moves the other way.
    setTurned({
      lon: wrapLon(from.facing.lon - (event.clientX - from.x) * DRAG),
      lat: clampLat(from.facing.lat + (event.clientY - from.y) * DRAG),
    });
  };
  const stopDrag = () => (drag.current = null);

  return (
    <div className={styles.globeLayout}>
      <div
        role="group"
        aria-label={t("admin.globe.label")}
        tabIndex={0}
        data-facing={`${facing.lon},${facing.lat}`}
        className={styles.globe}
        onKeyDown={onKeyDown}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={stopDrag}
        onPointerCancel={stopDrag}
      >
        <svg
          viewBox={`${-R - 2} ${-R - 2} ${2 * R + 4} ${2 * R + 4}`}
          aria-hidden="true"
          focusable="false"
        >
          <circle r={R} className={styles.globeSea} />
          <path d={land} className={styles.globeLand} />
          {rows.map((row) => {
            const at = row.country ? CENTROIDS[row.country] : undefined;
            if (!at) return null;
            const p = project(at, facing, R);
            if (!p.visible) return null;
            // Area, not radius, follows the count, so a country twice as large looks twice as large.
            const radius = 3 + 9 * Math.sqrt((row.members ?? 0) / max);
            return (
              <circle
                key={row.country}
                data-country={row.country}
                cx={p.x}
                cy={p.y}
                r={radius}
                className={styles.globeDot}
              />
            );
          })}
        </svg>
      </div>
      <div>
        <p className={styles.note}>
          {t("admin.globe.summary", {
            count: total,
            members: total,
            countries: rows.length,
          })}
        </p>
        <ul
          className={styles.globeList}
          aria-label={t("admin.globe.listLabel")}
        >
          {rows.map((row) => (
            <li key={row.country} className={styles.globeRow}>
              <span>{countryName(row.country ?? "", language)}</span>
              <span className={styles.globeCount}>{row.members}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
