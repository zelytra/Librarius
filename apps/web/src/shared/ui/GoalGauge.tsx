import styles from './GoalGauge.module.css';

/** Geometry of the ring, in the coordinates of the viewBox. */
const RADIUS = 42;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

interface GoalGaugeProps {
  /** Share of the target already reached, 0 to 100. */
  percent: number;
  /** What is read so far, shown at the centre. */
  value: number;
  /** The target it is measured against, already worded — "/ 30". */
  targetLabel: string;
  /** Unit the two figures are counted in. */
  unitLabel: string;
  /** Read out instead of the figures, which mean nothing on their own. */
  label: string;
}

/**
 * Circular progress towards the annual reading goal.
 *
 * <p>Two SVG circles rather than a charting library: an arc is a stroke with a gap in it,
 * and the ring has to follow the theme through the same tokens as everything around it.
 * The dash offset is the only value the component computes.
 *
 * <p>It is a {@code progressbar} and not an image: a screen reader has to announce how far
 * along the user is, which means the value has to be exposed as a value. The label says
 * the same thing in words, for the figures inside the ring mean nothing read on their own.
 */
export function GoalGauge({ percent, value, targetLabel, unitLabel, label }: GoalGaugeProps) {
  const filled = Math.min(100, Math.max(0, percent));

  return (
    <div
      className={styles.gauge}
      role="progressbar"
      aria-label={label}
      aria-valuenow={filled}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <svg viewBox="0 0 100 100" className={styles.ring} aria-hidden="true" focusable="false">
        <circle className={styles.track} cx="50" cy="50" r={RADIUS} />
        <circle
          className={styles.arc}
          cx="50"
          cy="50"
          r={RADIUS}
          // The gap in the stroke is the progress; the rotation puts its start at noon.
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={CIRCUMFERENCE * (1 - filled / 100)}
          transform="rotate(-90 50 50)"
        />
      </svg>
      <div className={styles.core}>
        <span className={styles.value}>{value}</span>
        <span className={styles.target}>{targetLabel}</span>
        <span className={styles.unit}>{unitLabel}</span>
      </div>
    </div>
  );
}
