/**
 * Where the application can be navigated to, declared once for both navigations.
 *
 * The bottom bar (`BottomNav`) and the side navigation (`SideNav`) draw the same
 * destinations in two different shapes, and only one of them is mounted at a time —
 * `AppShell` picks by viewport width. Keeping the list here is what makes "add a
 * destination" a one-line change instead of two tables to remember to keep in step.
 *
 * `icon` is a Material Symbols name from `shared/ui/iconSubset.ts`; `labelKey` an i18n
 * key, so no copy is hardcoded here.
 */
export interface Destination {
  to: string;
  icon: string;
  labelKey: string;
}

/**
 * The five destinations of the application proper. Both navigations carry all five, in
 * this order.
 */
export const NAV_DESTINATIONS: readonly Destination[] = [
  { to: '/', icon: 'cottage', labelKey: 'nav.home' },
  { to: '/collection', icon: 'auto_stories', labelKey: 'nav.collection' },
  { to: '/discover', icon: 'search', labelKey: 'nav.discover' },
  { to: '/wishlist', icon: 'favorite', labelKey: 'nav.wishlist' },
  { to: '/stats', icon: 'insights', labelKey: 'nav.stats' },
];

/**
 * Settings, which is not one of the five: it configures the application rather than
 * being a part of it, and the side navigation draws it apart from the others at the
 * bottom of the rail.
 *
 * Only the side navigation offers it. On a phone it stays where it is today — the icon
 * in Home's header — because a sixth tab in a bar already holding five would shrink all
 * of them, and the bar is the one thing #172 does not touch.
 *
 * It reuses the Settings screen's own title rather than declaring a `nav.settings`
 * duplicate: the entry and the screen it opens are the same word in both locales.
 */
export const SETTINGS_DESTINATION: Destination = {
  to: '/settings',
  icon: 'settings',
  labelKey: 'settings.title',
};
