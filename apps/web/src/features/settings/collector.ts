/**
 * The library collector: a bookmarklet the reader runs on Booknode or Babelio, in their own
 * browser, on their own open session.
 *
 * <p>Why it exists: Babelio shows a member's library only to that member, signed in, and
 * Booknode turns servers away — no server can read either reliably. The reader's browser can:
 * it is already allowed in. The collector walks every page of the library the way the reader
 * would by clicking "next", keeps the rows of each page, and downloads them as one file the
 * reader then imports in Settings. Nothing leaves the browser except that download.
 *
 * <p>{@link collectLibrary} is serialized with `Function.prototype.toString` into the
 * bookmarklet, so it must stay self-contained: every helper is declared inside it, and it reads
 * nothing from this module. The `env` parameter only exists for the tests.
 */

/** What the collector saves, and what `POST /api/import/collected` takes (plus a format tag). */
export interface CollectedFile {
  format: 'librarius-collect';
  /** {@link COLLECTOR_VERSION} for a file of the current bookmark; older bookmarks wrote 1. */
  version: number;
  source: 'booknode' | 'babelio' | 'mangacollec';
  collectedAt: string;
  pages: string[];
  /**
   * Babelio only: the filters its library page offers (label and address), kept so that a
   * file a reader sends says what the live site calls them (#502). Ignored by the import.
   */
  filters?: { text: string; url: string }[];
}

/** The browser surface the collector touches, injectable for the tests. */
export interface CollectorEnv {
  location: { hostname: string; pathname: string; href: string };
  document: Document;
  fetch: (url: string, headers?: Record<string, string>) => Promise<Response>;
  /** The page's own storage, where Mangacollec keeps the reader's session. */
  storage?: Storage[];
  alert: (message: string) => void;
  /** Saves the collected file; the bookmarklet downloads it. */
  save: (file: CollectedFile) => void;
  /** Pause between two pages, so a 100-page library is read at a reader's pace. */
  pauseMs?: number;
  /** The page's global object, whose requests the Mangacollec collector listens to (#503). */
  global?: typeof globalThis;
}

/**
 * The version the current bookmark writes in its files (#507). Raised when the collector
 * changes in a way an older bookmark misses, so the import can tell the reader to replace it.
 * The literal inside `collectLibrary` must match: the bookmark is serialized on its own.
 */
export const COLLECTOR_VERSION = 2;

/**
 * @param save replaces the download when the default environment is used: the in-app window
 *   (#570) posts the file to the app instead. Ignored with `env`, which carries its own.
 */
export async function collectLibrary(env?: CollectorEnv, save?: (file: CollectedFile) => void): Promise<void> {
  const e: CollectorEnv = env ?? {
    location: window.location,
    document: window.document,
    fetch: (url: string, headers?: Record<string, string>) =>
      window.fetch(url, headers ? { headers } : { credentials: 'include' }),
    storage: [window.localStorage, window.sessionStorage],
    alert: (message: string) => window.alert(message),
    save: save ?? ((file: CollectedFile) => {
      const blob = new Blob([JSON.stringify(file)], { type: 'application/json' });
      const link = window.document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = 'librarius-' + file.source + '.json';
      window.document.body.appendChild(link);
      link.click();
      link.remove();
    }),
    pauseMs: 400,
    global: window,
  };

  const host = e.location.hostname;
  const source = host.includes('babelio')
    ? 'babelio'
    : host.includes('booknode')
      ? 'booknode'
      : host.includes('mangacollec')
        ? 'mangacollec'
        : null;
  if (!source) {
    e.alert(
      'Librarius : ouvre ta bibliothèque sur Booknode, Babelio ou Mangacollec (connecté à ton compte), puis clique à nouveau sur ce favori.',
    );
    return;
  }

  /** A status line pinned over the page, so a long collection shows it is working. */
  const banner = e.document.createElement('div');
  banner.setAttribute(
    'style',
    'position:fixed;z-index:2147483647;top:12px;right:12px;padding:10px 14px;border-radius:10px;' +
      'background:#332e26;color:#fff;font:600 14px sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.3)',
  );
  e.document.body.appendChild(banner);
  const say = (text: string) => {
    banner.textContent = 'Librarius — ' + text;
  };

  /** A page of the site, decoded in the charset it declares (Babelio serves ISO-8859-1). */
  async function load(url: string): Promise<Document> {
    const response = await e.fetch(url);
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const bytes = await response.arrayBuffer();
    const declared = /charset=([\w-]+)/i.exec(response.headers.get('content-type') ?? '');
    const charset = declared ? declared[1] : e.document.characterSet || 'utf-8';
    let text: string;
    try {
      text = new TextDecoder(charset).decode(bytes);
    } catch {
      text = new TextDecoder('utf-8').decode(bytes);
    }
    return new DOMParser().parseFromString(text, 'text/html');
  }

  /** Whether the library is Babelio's current list (`li.lib-book`): then nothing else is read. */
  let libraryList = false;

  /** The rows describing one book each, as HTML the server parses. */
  function rowsOf(page: Document): string {
    if (source === 'booknode') {
      return Array.from(page.querySelectorAll('.book-item'))
        .map((row) => row.outerHTML)
        .join(' ');
    }
    /* Babelio's current list: one `li.lib-book` per title. Taken as is when the page has it. */
    const items = Array.from(page.querySelectorAll('li.lib-book'));
    if (items.length > 0) return items.map((item) => item.outerHTML).join(' ');
    /* A page without the list, in a library that has one, is not the library (#502). */
    if (libraryList) return '';
    const rows: Element[] = [];
    page.querySelectorAll('a[href*="/livres/"]').forEach((link) => {
      /* The site's menus, header and footer link to promoted books on every page, and its */
      /* selections list other people's books (`list-item`): not the reader's library. */
      if (link.closest('[class*="megamenu"], nav, header, footer, .list-item, [class*="selection"]')) return;
      const row = link.closest('tr') ?? link.closest('li') ?? link.parentElement;
      if (row && !rows.includes(row)) rows.push(row);
    });
    const tableRows = rows.filter((row) => row.tagName === 'TR').map((row) => row.outerHTML);
    const others = rows.filter((row) => row.tagName !== 'TR').map((row) => row.outerHTML);
    return (tableRows.length ? '<table><tbody>' + tableRows.join('') + '</tbody></table>' : '') + others.join(' ');
  }

  /**
   * The URLs of every page of the library, in order. Booknode's library is
   * `/profil/<pseudo>/biblio-table?page=N`; Babelio's is the page the reader is on, paged by a
   * numeric query parameter its "next" links carry — read off those links rather than assumed.
   */
  function pageUrls(first: Document, firstUrl: string): string[] {
    const base = new URL(firstUrl);
    let parameter: string | null = null;
    let last = 1;
    first.querySelectorAll('a[href]').forEach((link) => {
      let target: URL;
      try {
        target = new URL(link.getAttribute('href') ?? '', base);
      } catch {
        return;
      }
      if (target.pathname !== base.pathname) return;
      target.searchParams.forEach((value, key) => {
        if (/page/i.test(key) && /^\d+$/.test(value) && Number(value) >= last) {
          parameter = key;
          last = Number(value);
        }
      });
    });
    const urls = [firstUrl];
    if (parameter) {
      for (let n = 2; n <= Math.min(last, 1000); n++) {
        const next = new URL(firstUrl);
        next.searchParams.set(parameter, String(n));
        urls.push(next.toString());
      }
    }
    return urls;
  }

  /**
   * Mangacollec (#456, #503): the site loads the whole collection as one JSON document from
   * its API. Where it keeps its session has changed under us before, so the collector does not
   * depend on it: it listens to the site's own requests to its API — `fetch` and
   * `XMLHttpRequest`, the hook staying installed between two clicks — and replays the
   * `Authorization` header the site sent. A token found in the page's storage or cookies, and
   * the site's cookies alone, are tried next. A body is kept only if it is a collection;
   * otherwise `notes` says what each attempt answered, for the reader and for a report.
   */
  type HookedGlobal = typeof globalThis & {
    __librariusHooked?: boolean;
    __librariusAuth?: [string, string];
  };
  const page = (e.global ?? window) as HookedGlobal;
  const notes: string[] = [];

  function listenToTheSite(): void {
    if (page.__librariusHooked) return;
    page.__librariusHooked = true;
    const keep = (url: string, name: string, value: string) => {
      let host = '';
      try {
        host = new URL(url, 'https://www.mangacollec.com/').hostname;
      } catch {
        return;
      }
      /* The API's own host, exactly: a look-alike address must never lend its header. */
      if (host === 'api.mangacollec.com' && /^authorization$/i.test(name) && value) {
        page.__librariusAuth = [name, value];
      }
    };
    const siteFetch = page.fetch;
    if (typeof siteFetch === 'function') {
      page.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        try {
          const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
          const headers = new Headers(init?.headers ?? (typeof input === 'object' && 'headers' in input ? input.headers : undefined));
          headers.forEach((value, name) => keep(url, name, value));
        } catch {
          /* A request we cannot read is none of ours. */
        }
        return siteFetch.call(page, input, init);
      }) as typeof fetch;
    }
    const xhr = page.XMLHttpRequest?.prototype as
      | (XMLHttpRequest & { __librariusUrl?: string })
      | undefined;
    if (xhr && typeof xhr.open === 'function' && typeof xhr.setRequestHeader === 'function') {
      const open = xhr.open;
      const setHeader = xhr.setRequestHeader;
      xhr.open = function (this: XMLHttpRequest & { __librariusUrl?: string }, ...args: unknown[]) {
        this.__librariusUrl = String(args[1]);
        return (open as (...a: unknown[]) => void).apply(this, args);
      } as typeof xhr.open;
      xhr.setRequestHeader = function (this: XMLHttpRequest & { __librariusUrl?: string }, name: string, value: string) {
        keep(this.__librariusUrl ?? '', name, value);
        return setHeader.call(this, name, value);
      };
    }
  }

  function isCollection(text: string): boolean {
    try {
      const doc = JSON.parse(text) as Record<string, unknown>;
      return Array.isArray(doc.volumes) && Array.isArray(doc.editions)
        && (Array.isArray(doc.possessions) || Array.isArray(doc.reads));
    } catch {
      return false;
    }
  }

  async function mangacollec(): Promise<string | null> {
    listenToTheSite();
    const url = 'https://api.mangacollec.com/v2/users/me/collection';
    const tokens: string[] = [];
    const visit = (value: unknown, key: string) => {
      if (typeof value === 'string') {
        if (/token/i.test(key) && /^[\w.~+/=-]{20,}$/.test(value)) tokens.push(value);
        else if (value.startsWith('{') || value.startsWith('[')) {
          try {
            visit(JSON.parse(value), key);
          } catch {
            /* Not JSON: nothing to look into. */
          }
        }
      } else if (value && typeof value === 'object') {
        for (const [k, v] of Object.entries(value as Record<string, unknown>)) visit(v, k);
      }
    };
    for (const store of e.storage ?? []) {
      for (let i = 0; i < store.length; i++) {
        const key = store.key(i) ?? '';
        visit(store.getItem(key), key);
      }
    }
    for (const pair of (e.document.cookie ?? '').split(';')) {
      const at = pair.indexOf('=');
      if (at > 0) visit(decodeURIComponent(pair.slice(at + 1).trim()), pair.slice(0, at).trim());
    }
    const attempts: [string, Record<string, string> | undefined][] = [];
    const heard = page.__librariusAuth;
    if (heard) attempts.push(['session du site', { [heard[0]]: heard[1] }]);
    for (const token of tokens) attempts.push(['jeton enregistré', { Authorization: 'Bearer ' + token }]);
    attempts.push(['cookies', undefined]);
    for (const [label, headers] of attempts) {
      try {
        const response = await e.fetch(url, headers);
        if (!response.ok) {
          notes.push(label + ' : ' + response.status);
          continue;
        }
        const text = await response.text();
        if (isCollection(text)) return text;
        notes.push(label + ' : ' + response.status + ' mais pas une collection');
      } catch (error) {
        notes.push(label + ' : ' + String(error));
      }
    }
    return null;
  }

  try {
    if (source === 'mangacollec') {
      say('lecture de ta collection…');
      const collection = await mangacollec();
      if (!collection) {
        e.alert(
          'Librarius : impossible de lire ta collection (' + notes.join(', ') + '). ' +
            'Sur mangacollec.com, connecté à ton compte, ouvre ta collection depuis le menu du site, puis clique à nouveau sur ce favori.',
        );
        return;
      }
      e.save({ format: 'librarius-collect', version: 2, source, collectedAt: new Date().toISOString(), pages: [collection] });
      say('collection récupérée : importe le fichier dans Librarius (Réglages → Importer).');
      return;
    }
    let firstUrl = e.location.href;
    if (source === 'booknode') {
      const handle = /\/profil\/([^/?#]+)/.exec(e.location.pathname);
      if (!handle) {
        e.alert('Librarius : ouvre ta page de profil Booknode (booknode.com/profil/ton-pseudo), puis clique à nouveau sur ce favori.');
        return;
      }
      firstUrl = 'https://' + host + '/profil/' + handle[1] + '/biblio-table?page=1';
    }
    say('lecture de la page 1…');
    const first = await load(firstUrl);
    libraryList = source === 'babelio' && first.querySelectorAll('li.lib-book').length > 0;
    const urls = pageUrls(first, firstUrl);
    const pages = [rowsOf(first)];
    for (let i = 1; i < urls.length; i++) {
      say('lecture de la page ' + (i + 1) + ' sur ' + urls.length + '…');
      await new Promise((resolve) => setTimeout(resolve, e.pauseMs ?? 0));
      pages.push(rowsOf(await load(urls[i])));
    }
    /* Babelio files the wished books under their own filter — « Wishlist » today (#506), */
    /* « Pense-bête » before — which the list the */
    /* reader stands on leaves out (#477). Only a filter of the library page itself is followed — */
    /* same address, another query, as a link or an htmx `hx-get` — never another page whose */
    /* label merely contains « pense » (#502: a readers' selection came in that way). */
    let filters: { text: string; url: string }[] | undefined;
    if (source === 'babelio') {
      const seen = new Set(urls);
      const libraryPath = new URL(firstUrl).pathname;
      const found = new Map<string, string>();
      first.querySelectorAll('a[href], [hx-get]').forEach((element) => {
        const raw = element.getAttribute('hx-get') ?? element.getAttribute('href') ?? '';
        let target: URL;
        try {
          target = new URL(raw, firstUrl);
        } catch {
          return;
        }
        if (target.pathname !== libraryPath || !target.search) return;
        if (Array.from(target.searchParams.keys()).some((key) => /page|part/i.test(key))) return;
        const text = (element.textContent ?? '').replace(/\s+/g, ' ').trim();
        if (text && !found.has(target.toString())) found.set(target.toString(), text);
      });
      filters = Array.from(found, ([url, text]) => ({ text, url })).slice(0, 40);
      const wishUrls = filters
        .filter((filter) => {
          const folded = filter.text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
          return /wishlist|pense.?bete|envie de lire/.test(folded);
        })
        .map((filter) => filter.url)
        .filter((url) => !seen.has(url));
      for (const wishUrl of wishUrls) {
        say('lecture des pense-bêtes…');
        const wishFirst = await load(wishUrl);
        const wishPages = pageUrls(wishFirst, wishUrl);
        pages.push(rowsOf(wishFirst));
        for (let i = 1; i < wishPages.length; i++) {
          if (seen.has(wishPages[i])) continue;
          seen.add(wishPages[i]);
          await new Promise((resolve) => setTimeout(resolve, e.pauseMs ?? 0));
          pages.push(rowsOf(await load(wishPages[i])));
        }
      }
    }
    const kept = pages.filter((page) => page.trim().length > 0);
    if (kept.length === 0) {
      e.alert(
        source === 'babelio'
          ? 'Librarius : aucun livre trouvé. Ouvre « Mes livres » sur Babelio, connecté à ton compte, puis relance la collecte.'
          : 'Librarius : aucun livre trouvé sur ce profil Booknode.',
      );
      return;
    }
    e.save({
      format: 'librarius-collect',
      version: 2,
      source,
      collectedAt: new Date().toISOString(),
      pages: kept,
      ...(filters ? { filters } : {}),
    });
    say(urls.length + ' pages collectées : importe le fichier dans Librarius (Réglages → Importer).');
  } catch (error) {
    e.alert('Librarius : la collecte s’est interrompue (' + String(error) + '). Recharge la page et réessaie.');
  } finally {
    setTimeout(() => banner.remove(), 8000);
  }
}

/** The bookmarklet: the collector, serialized and run on the page it is clicked on. */
export function bookmarkletHref(): string {
  // One line: some browsers drop or refuse a bookmark whose address holds a line break (#487).
  const source = collectLibrary.toString().replace(/\s*[\r\n]+\s*/g, ' ');
  return 'javascript:' + encodeURIComponent('(' + source + ')()');
}

/**
 * Hands a collected file to the app (#570), from inside the window it opened on the source site:
 * `window.mobileApp` is what `@capgo/inappbrowser` injects there. Serialized into
 * {@link inAppCollectorScript}, so it reads nothing from this module.
 */
export function postToApp(file: CollectedFile): void {
  (window as unknown as { mobileApp: { postMessage: (message: { detail: CollectedFile }) => void } }).mobileApp.postMessage({
    detail: file,
  });
}

/**
 * The script the app injects into every page of the in-app window (#570): it pins one
 * « Importer dans Librarius » button over the page — once, however often it is injected — and
 * the button starts the bookmarklet's own collector, saving through {@link postToApp}. The
 * label is French, like the collector's own messages: it is drawn inside a French site's page,
 * out of reach of the app's translations.
 */
export function inAppCollectorScript(): string {
  return `(function () {
  if (document.getElementById('librarius-collect')) return;
  var button = document.createElement('button');
  button.id = 'librarius-collect';
  button.type = 'button';
  button.textContent = 'Importer dans Librarius';
  button.setAttribute('style', 'position:fixed;z-index:2147483647;left:50%;bottom:24px;transform:translateX(-50%);' +
    'min-height:48px;padding:12px 20px;border:none;border-radius:24px;background:#5f6f52;color:#fff;' +
    'font:600 16px sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.3)');
  button.addEventListener('click', function () {
    button.remove();
    (${collectLibrary.toString()})(undefined, (${postToApp.toString()}));
  });
  document.body.appendChild(button);
})();`;
}

/** Whether a parsed file is one the collector saved. */
export function isCollectedFile(value: unknown): value is CollectedFile {
  const file = value as Partial<CollectedFile> | null;
  return (
    file != null &&
    file.format === 'librarius-collect' &&
    (file.source === 'booknode' || file.source === 'babelio' || file.source === 'mangacollec') &&
    Array.isArray(file.pages)
  );
}
