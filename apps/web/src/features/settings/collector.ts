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
  version: 1;
  source: 'booknode' | 'babelio';
  collectedAt: string;
  pages: string[];
}

/** The browser surface the collector touches, injectable for the tests. */
export interface CollectorEnv {
  location: { hostname: string; pathname: string; href: string };
  document: Document;
  fetch: (url: string) => Promise<Response>;
  alert: (message: string) => void;
  /** Saves the collected file; the bookmarklet downloads it. */
  save: (file: CollectedFile) => void;
  /** Pause between two pages, so a 100-page library is read at a reader's pace. */
  pauseMs?: number;
}

export async function collectLibrary(env?: CollectorEnv): Promise<void> {
  const e: CollectorEnv = env ?? {
    location: window.location,
    document: window.document,
    fetch: (url: string) => window.fetch(url, { credentials: 'include' }),
    alert: (message: string) => window.alert(message),
    save: (file: CollectedFile) => {
      const blob = new Blob([JSON.stringify(file)], { type: 'application/json' });
      const link = window.document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = 'librarius-' + file.source + '.json';
      window.document.body.appendChild(link);
      link.click();
      link.remove();
    },
    pauseMs: 400,
  };

  const host = e.location.hostname;
  const source = host.includes('babelio') ? 'babelio' : host.includes('booknode') ? 'booknode' : null;
  if (!source) {
    e.alert(
      'Librarius : ouvre ta bibliothèque sur Booknode ou sur Babelio (connecté à ton compte), puis clique à nouveau sur ce favori.',
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

  /** The rows describing one book each, as HTML the server parses. */
  function rowsOf(page: Document): string {
    if (source === 'booknode') {
      return Array.from(page.querySelectorAll('.book-item'))
        .map((row) => row.outerHTML)
        .join('\n');
    }
    const rows: Element[] = [];
    page.querySelectorAll('a[href*="/livres/"]').forEach((link) => {
      const row = link.closest('tr') ?? link.closest('li') ?? link.parentElement;
      if (row && !rows.includes(row)) rows.push(row);
    });
    const tableRows = rows.filter((row) => row.tagName === 'TR').map((row) => row.outerHTML);
    const others = rows.filter((row) => row.tagName !== 'TR').map((row) => row.outerHTML);
    return (tableRows.length ? '<table><tbody>' + tableRows.join('') + '</tbody></table>' : '') + others.join('\n');
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

  try {
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
    const urls = pageUrls(first, firstUrl);
    const pages = [rowsOf(first)];
    for (let i = 1; i < urls.length; i++) {
      say('lecture de la page ' + (i + 1) + ' sur ' + urls.length + '…');
      await new Promise((resolve) => setTimeout(resolve, e.pauseMs ?? 0));
      pages.push(rowsOf(await load(urls[i])));
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
      version: 1,
      source,
      collectedAt: new Date().toISOString(),
      pages: kept,
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
  return 'javascript:' + encodeURIComponent('(' + collectLibrary.toString() + ')()');
}

/** Whether a parsed file is one the collector saved. */
export function isCollectedFile(value: unknown): value is CollectedFile {
  const file = value as Partial<CollectedFile> | null;
  return (
    file != null &&
    file.format === 'librarius-collect' &&
    (file.source === 'booknode' || file.source === 'babelio') &&
    Array.isArray(file.pages)
  );
}
