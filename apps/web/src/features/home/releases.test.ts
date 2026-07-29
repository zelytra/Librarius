import { describe, expect, test } from 'vitest';
import i18n from '../../i18n';
import { upcomingRelease } from '../../test/fixtures';
import { formatReleaseDate, regionLabel, releaseVolumeLabel, sourceLabel } from './releases';

// The real dictionary rather than a stub: what is asserted here is the French wording the
// issue asks for, not that some placeholder function got called.
const t = i18n.t.bind(i18n);

describe('releaseVolumeLabel', () => {
  test('prefers the title the announcement carries', () => {
    const release = upcomingRelease({ title: 'Deluxe Edition', volumeNumber: 3 });
    expect(releaseVolumeLabel(release, t)).toBe('Deluxe Edition');
  });

  test('falls back to the volume number', () => {
    const release = upcomingRelease({ title: undefined, volumeNumber: 12 });
    expect(releaseVolumeLabel(release, t)).toBe('Tome 12');
  });

  test('names neither when the announcement names no volume', () => {
    const release = upcomingRelease({ title: undefined, volumeNumber: undefined });
    expect(releaseVolumeLabel(release, t)).toBeUndefined();
  });
});

describe('formatReleaseDate', () => {
  test('DAY prints the full date', () => {
    const release = upcomingRelease({ releaseDate: '2027-03-12', datePrecision: 'DAY' });
    expect(formatReleaseDate(release, t)).toBe('12 mars 2027');
  });

  test('MONTH omits the day nobody announced', () => {
    const release = upcomingRelease({ releaseDate: '2027-03-01', datePrecision: 'MONTH' });
    expect(formatReleaseDate(release, t)).toBe('mars 2027');
  });

  test('QUARTER reads the month into a quarter number', () => {
    const release = upcomingRelease({ releaseDate: '2027-07-01', datePrecision: 'QUARTER' });
    expect(formatReleaseDate(release, t)).toBe('T3 2027');
  });

  test('YEAR keeps only the year', () => {
    const release = upcomingRelease({ releaseDate: '2027-01-01', datePrecision: 'YEAR' });
    expect(formatReleaseDate(release, t)).toBe('2027');
  });

  test('a release with no date at all says so rather than guessing one', () => {
    const release = upcomingRelease({ releaseDate: undefined, datePrecision: undefined });
    expect(formatReleaseDate(release, t)).toBe('Date à confirmer');
  });
});

describe('regionLabel', () => {
  test('names the three markets the interface knows', () => {
    expect(regionLabel('FR', t)).toBe('Édition française');
    expect(regionLabel('JP', t)).toBe('Édition originale');
    expect(regionLabel('EN', t)).toBe('Édition anglaise');
  });

  test('a region the front end does not know is never shown unlabelled', () => {
    expect(regionLabel(undefined, t)).toBe('Édition inconnue');
  });
});

describe('sourceLabel', () => {
  test('an estimate says so before naming where it came from', () => {
    const release = upcomingRelease({ source: 'manual', confidence: 'ESTIMATED' });
    expect(sourceLabel(release, t)).toBe('Date estimée');
  });

  test('a curated row says it was confirmed by the publisher', () => {
    const release = upcomingRelease({ source: 'manual', confidence: 'CONFIRMED' });
    expect(sourceLabel(release, t)).toBe("Confirmé par l'éditeur");
  });

  test('a date read off the catalog names the catalog', () => {
    const release = upcomingRelease({ source: 'catalog', confidence: 'CONFIRMED' });
    expect(sourceLabel(release, t)).toBe("D'après notre catalogue");
  });

  test('any other confirmed source falls back to naming the provider', () => {
    const release = upcomingRelease({ source: 'anilist', confidence: 'CONFIRMED' });
    expect(sourceLabel(release, t)).toBe('Source : anilist');
  });
});
