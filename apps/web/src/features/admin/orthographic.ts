/** Where the globe faces: the longitude and latitude at its centre, in degrees. */
export interface Facing {
  lon: number;
  lat: number;
}

const RAD = Math.PI / 180;
// Two decimals is finer than a pixel at any size the globe is drawn; `+ 0` turns -0 into 0.
const round = (value: number) => Math.round(value * 100) / 100 + 0;

/**
 * The orthographic projection of a point on a globe of radius `r` centred on (0, 0), facing
 * `facing` (#573): x grows to the east, y to the south, as in SVG. A point on the far side is
 * not visible and is pinned to the rim, so an outline crossing the horizon follows the edge.
 */
export function project(
  [lon, lat]: readonly [number, number],
  facing: Facing,
  r: number
) {
  const lambda = (lon - facing.lon) * RAD;
  const phi = lat * RAD;
  const phi0 = facing.lat * RAD;
  let x = r * Math.cos(phi) * Math.sin(lambda);
  let y =
    -r *
    (Math.cos(phi0) * Math.sin(phi) -
      Math.sin(phi0) * Math.cos(phi) * Math.cos(lambda));
  const visible =
    Math.sin(phi0) * Math.sin(phi) +
      Math.cos(phi0) * Math.cos(phi) * Math.cos(lambda) >=
    0;
  if (!visible) {
    const length = Math.hypot(x, y) || 1;
    x = (x / length) * r;
    y = (y / length) * r;
  }
  return { x: round(x), y: round(y), visible };
}
