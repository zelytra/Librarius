import type { CSSProperties } from 'react';
import { useQuery } from '@tanstack/react-query';
import { authorisedGet } from '../authorisedGet';
import { colorFor } from './coverPalette';
import styles from './Avatar.module.css';

/** The initials a member without a picture is drawn with. */
export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

/**
 * A member's profile picture (#500), or their initials on their colour when they have none or
 * it cannot be read. The picture is fetched with the session — an `<img>` cannot carry the
 * bearer token — and kept per `version`, which changes with each new picture.
 *
 * `decorative` when the name is written right beside it: a screen reader then hears it once.
 */
export function Avatar({
  id,
  name,
  version,
  size = 38,
  decorative = false,
}: {
  id?: string;
  name: string;
  version?: number | null;
  size?: number;
  decorative?: boolean;
}) {
  const { data: url } = useQuery({
    queryKey: ['avatar', id, version],
    enabled: Boolean(id) && version != null,
    staleTime: Infinity,
    queryFn: async () => {
      const response = await authorisedGet(`/api/users/${id}/avatar`);
      if (!response.ok) throw new Error(`avatar ${response.status}`);
      return URL.createObjectURL(await response.blob());
    },
  });
  const style: CSSProperties = { width: size, height: size, fontSize: Math.round(size * 0.37) };
  if (url && version != null) {
    return <img src={url} alt={decorative ? '' : name} className={styles.avatar} style={style} />;
  }
  return (
    <span
      className={styles.avatar}
      style={{ ...style, background: colorFor(name) }}
      {...(decorative ? { 'aria-hidden': true } : { role: 'img', 'aria-label': name })}
    >
      <span aria-hidden="true">{initialsOf(name)}</span>
    </span>
  );
}
