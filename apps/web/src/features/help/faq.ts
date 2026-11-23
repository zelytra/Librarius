/**
 * The FAQ of Settings › "Un problème ?" (#630): its themes, its questions in order, and the
 * shortcut each answer offers. The words live in `faq.fr.json` / `faq.en.json`, an i18n
 * namespace of their own loaded with this chunk only — the French locale is bundled in the
 * first payload, whose budget is nearly full, and the FAQ is read rarely.
 */
export interface FaqAction {
  /** Key under `actions` in the FAQ locale. */
  label: string;
  /** In-app route the shortcut opens. */
  to: string;
}

export interface FaqQuestion {
  id: string;
  actions?: FaqAction[];
}

export interface FaqSection {
  id: string;
  questions: FaqQuestion[];
}

export const FAQ_SECTIONS: FaqSection[] = [
  {
    id: 'start',
    questions: [
      { id: 'add', actions: [{ label: 'search', to: '/discover' }, { label: 'scan', to: '/scan' }] },
      { id: 'notFound', actions: [{ label: 'search', to: '/discover' }] },
      { id: 'import', actions: [{ label: 'import', to: '/settings/data' }] },
      { id: 'scanCamera', actions: [{ label: 'scan', to: '/scan' }] },
    ],
  },
  {
    id: 'library',
    questions: [
      { id: 'statuses', actions: [{ label: 'library', to: '/collection' }] },
      { id: 'edition' },
      { id: 'pages' },
      { id: 'categories', actions: [{ label: 'categories', to: '/categories' }] },
      { id: 'series', actions: [{ label: 'library', to: '/collection' }] },
      { id: 'wishlist', actions: [{ label: 'wishlist', to: '/wishlist' }] },
      { id: 'goal', actions: [{ label: 'goal', to: '/settings/reading' }] },
    ],
  },
  {
    id: 'catalog',
    questions: [
      { id: 'wrongInfo' },
      { id: 'saleLinks' },
      { id: 'editPending' },
    ],
  },
  {
    id: 'trust',
    questions: [
      { id: 'trustHow' },
      { id: 'trustGives' },
      { id: 'trustLost' },
      { id: 'admin' },
    ],
  },
  {
    id: 'social',
    questions: [
      { id: 'visibility', actions: [{ label: 'profile', to: '/settings/profile' }] },
      { id: 'follow', actions: [{ label: 'people', to: '/people' }] },
      { id: 'block', actions: [{ label: 'community', to: '/settings/community' }] },
      { id: 'notifications', actions: [{ label: 'notifications', to: '/notifications' }] },
      { id: 'notificationsDelete', actions: [{ label: 'notifications', to: '/notifications' }] },
    ],
  },
  {
    id: 'account',
    questions: [
      { id: 'export', actions: [{ label: 'data', to: '/settings/data' }] },
      { id: 'deleteAccount', actions: [{ label: 'data', to: '/settings/data' }] },
      { id: 'language', actions: [{ label: 'profile', to: '/settings/profile' }] },
      { id: 'problem' },
    ],
  },
];
