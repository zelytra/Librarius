import { Link } from 'react-router';
import { useGetApiSeries } from '../../api/generated/librarius';
import styles from '../author/AuthorNames.module.css';

/**
 * A series named by a catalog result, as a link (#443): to its Series screen when the reader
 * has a stake in it (a volume owned, or the series followed), else to a search on the series,
 * which the catalog answers with its volumes in order.
 */
export function SeriesLink({ title, kind, children }: { title: string; kind?: string; children?: React.ReactNode }) {
  const { data: known = [] } = useGetApiSeries();
  const needle = title.trim().toLowerCase();
  const mine = known.find((s) => (!kind || s.kind === kind) && s.title?.trim().toLowerCase() === needle);
  const to = mine?.id ? `/series/${mine.id}` : `/discover?q=${encodeURIComponent(title)}`;
  return (
    <Link to={to} className={styles.link} onClick={(e) => e.stopPropagation()}>
      {children ?? title}
    </Link>
  );
}
