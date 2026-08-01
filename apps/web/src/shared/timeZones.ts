/**
 * The IANA time zones offered in the profile (#75).
 *
 * `Intl.supportedValuesOf('timeZone')` is the runtime's own list — every zone it can format
 * a date in — so the picker never offers one the browser would reject, and it stays current
 * without a hand-maintained table. It is a recent API: where it is missing (an old engine, or
 * jsdom under the tests), the caller's own zone is offered alone, which is enough to save a
 * profile and always a valid identifier.
 */
export function supportedTimeZones(): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] };
  if (typeof intl.supportedValuesOf === 'function') {
    try {
      return intl.supportedValuesOf('timeZone');
    } catch {
      // Fall through to the single-zone fallback below.
    }
  }
  const local = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return local ? [local] : [];
}
