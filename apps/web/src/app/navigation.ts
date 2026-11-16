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
 * The six destinations of the application proper. Both navigations carry all six, in
 * this order.
 */
export const NAV_DESTINATIONS: readonly Destination[] = [
  { to: '/', icon: 'cottage', labelKey: 'nav.home' },
  { to: '/collection', icon: 'auto_stories', labelKey: 'nav.collection' },
  // The pile of books owned and not opened yet (#391).
  { to: '/to-read', icon: 'library_books', labelKey: 'nav.toRead' },
  { to: '/discover', icon: 'search', labelKey: 'nav.discover' },
  { to: '/wishlist', icon: 'favorite', labelKey: 'nav.wishlist' },
  { to: '/stats', icon: 'insights', labelKey: 'nav.stats' },
];

/**
 * The readers (#210, #497): the feed, finding people, profiles — it used to hide in Settings.
 * The side navigation lists it with the others; on a phone it is an icon in Home's header,
 * like Settings, because a seventh tab would cut « Bibliothèque » short in a 360px bar.
 */
export const PEOPLE_DESTINATION: Destination = { to: '/people', icon: 'group', labelKey: 'nav.people' };

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

/**
 * The administration (#496), a destination of the navigation rather than a link buried in
 * Settings — and only for the admin role: `useNavDestinations` appends it when `/api/me`
 * says so. The API checks the role on every call; hiding the entry only spares everyone
 * else a screen they cannot use.
 */
export const ADMIN_DESTINATION: Destination = {
  to: '/admin',
  icon: 'admin_panel_settings',
  labelKey: 'nav.admin',
};

/**
 * The phone's bar (#504): the four pages a reader goes back to all day. Every other
 * destination sits in the bar's « Plus » sheet, so the bar stays compact and nothing is out of
 * reach.
 */
export const PHONE_PRIMARY: readonly string[] = ['/', '/collection', '/discover', '/wishlist'];

/** The notifications (#516): the bell's page, with its unread count drawn by each navigation. */
export const NOTIFICATIONS_DESTINATION: Destination = {
  to: '/notifications',
  icon: 'notifications',
  labelKey: 'nav.notifications',
};
