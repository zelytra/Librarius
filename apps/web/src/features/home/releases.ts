import type { TFunction } from 'i18next';
import type { UpcomingReleaseDto } from '../../api/generated/librarius';

/**
 * Pure reading of what `/api/releases/upcoming` returns. Kept out of the component so the
 * date, region and source labelling — the part the issue asks to make unambiguous — is
 * testable on its own, the way `detail/DetailPage.tsx` keeps `releaseLabel` free-standing.
 */

/**
 * Volume label of an announcement: its own title when it carries one, the plain "Tome N"
 * otherwise — the same wording the Series screen uses for a volume with no title of its
 * own. `undefined` when the announcement names no volume at all (a series start, a
 * one-shot), which is the one case with nothing to print on this line.
 */
export function releaseVolumeLabel(release: UpcomingReleaseDto, t: TFunction): string | undefined {
  if (release.title) return release.title;
  if (release.volumeNumber != null) return t('series.volume', { number: release.volumeNumber });
  return undefined;
}

/**
 * The date of a release, at the precision the announcement actually carries. `DAY`,
 * `MONTH` and `YEAR` read through `Intl.DateTimeFormat`, exactly like `releaseLabel` on the
 * Detail screen; `QUARTER` has no `Intl` equivalent and goes through a translation key
 * instead. A release with no date, or a precision the front end does not know, says so
 * rather than guessing a day nobody committed to.
 */
export function formatReleaseDate(release: UpcomingReleaseDto, t: TFunction): string {
  const { releaseDate, datePrecision } = release;
  if (!releaseDate || !datePrecision) return t('home.upcomingReleases.unknownDate');

  const parsed = new Date(releaseDate);
  if (Number.isNaN(parsed.getTime())) return t('home.upcomingReleases.unknownDate');

  switch (datePrecision) {
    case 'DAY':
      return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })
        .format(parsed);
    case 'MONTH':
      return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(parsed);
    case 'QUARTER':
      return t('home.upcomingReleases.quarter', {
        quarter: Math.floor(parsed.getMonth() / 3) + 1,
        year: parsed.getFullYear(),
      });
    case 'YEAR':
      return String(parsed.getFullYear());
    default:
      return t('home.upcomingReleases.unknownDate');
  }
}

/**
 * The market a date belongs to. Never optional in the data the API sends, but a raw code
 * the interface could not label would be worse than an explicit "unknown edition" — the
 * same choice `ReleaseRegion` makes server-side by dropping a release whose market cannot
 * be established.
 */
export function regionLabel(region: string | undefined, t: TFunction): string {
  if (region === 'FR' || region === 'JP' || region === 'EN') {
    return t(`home.upcomingReleases.region.${region}`);
  }
  return t('home.upcomingReleases.region.unknown');
}

/**
 * Where a date comes from, and how firm it is. Confidence is checked first: a provider can
 * be precise to the day about a date nobody has committed to, and showing "estimated"
 * matters more there than naming the provider.
 */
export function sourceLabel(release: UpcomingReleaseDto, t: TFunction): string {
  if (release.confidence === 'ESTIMATED') return t('home.upcomingReleases.source.estimated');
  if (release.source === 'manual') return t('home.upcomingReleases.source.manual');
  if (release.source === 'catalog') return t('home.upcomingReleases.source.catalog');
  return t('home.upcomingReleases.source.provider', { provider: release.source ?? '' });
}
