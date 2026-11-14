import { describe, expect, test, vi } from 'vitest';
import { bookmarkletHref, collectLibrary, isCollectedFile, type CollectedFile, type CollectorEnv } from './collector';

/** A Booknode library page: rows, and the pagination naming the last page. */
function booknodePage(titles: string[], last: number): string {
  const rows = titles.map((t) => `<div class="book-item"><span class="book-name">${t}</span></div>`).join('');
  const links = Array.from({ length: last }, (_, i) => `<a href="/profil/alice/biblio-table?page=${i + 1}">${i + 1}</a>`).join('');
  return `<html><body>${rows}<ul class="pagination">${links}</ul></body></html>`;
}

function response(html: string, contentType = 'text/html; charset=utf-8', bytes?: Uint8Array): Response {
  return new Response(bytes ?? new TextEncoder().encode(html), { status: 200, headers: { 'content-type': contentType } });
}

function env(overrides: Partial<CollectorEnv>): { env: CollectorEnv; saved: CollectedFile[]; alerts: string[] } {
  const saved: CollectedFile[] = [];
  const alerts: string[] = [];
  return {
    saved,
    alerts,
    env: {
      location: { hostname: 'booknode.com', pathname: '/profil/alice', href: 'https://booknode.com/profil/alice' },
      document,
      fetch: vi.fn(),
      alert: (m) => alerts.push(m),
      save: (f) => saved.push(f),
      pauseMs: 0,
      ...overrides,
    },
  };
}

describe('collectLibrary', () => {
  test('walks every page of a Booknode library and keeps its rows', async () => {
    const fetch = vi.fn((url: string) =>
      Promise.resolve(response(url.endsWith('page=1') ? booknodePage(['Dune'], 2) : booknodePage(['Fondation'], 2))),
    );
    const { env: e, saved } = env({ fetch });

    await collectLibrary(e);

    expect(fetch).toHaveBeenCalledWith('https://booknode.com/profil/alice/biblio-table?page=1');
    expect(fetch).toHaveBeenCalledWith('https://booknode.com/profil/alice/biblio-table?page=2');
    expect(saved).toHaveLength(1);
    expect(saved[0].source).toBe('booknode');
    expect(saved[0].pages).toHaveLength(2);
    expect(saved[0].pages[0]).toContain('Dune');
    expect(saved[0].pages[1]).toContain('Fondation');
  });

  test('reads a Babelio page in the charset it declares, rows wrapped as a table', async () => {
    // "Mes livres" with an accented title, served in ISO-8859-1 as Babelio does.
    const html =
      '<html><body><table><tr><td><a href="/livres/X-Ete/1">Été</a></td>' +
      '<td><a href="/auteur/Y/2">Auteur</a></td><td>Lu</td></tr></table></body></html>';
    const latin1 = Uint8Array.from(html, (c) => c.charCodeAt(0));
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch: vi.fn(() => Promise.resolve(response('', 'text/html; charset=ISO-8859-1', latin1))),
    });

    await collectLibrary(e);

    expect(saved[0].source).toBe('babelio');
    expect(saved[0].pages[0]).toContain('Été');
    expect(saved[0].pages[0]).toMatch(/^<table><tbody><tr>/);
  });

  test("keeps Babelio's list items and leaves its menus' promoted books out", async () => {
    const html =
      '<html><body><nav><div class="megamenu-body"><a href="/livres/Promo/1" class="megamenu-hero">' +
      '<span>Accroche</span><span>Promo</span><span>Auteur promu</span></a></div></nav>' +
      '<ul><li class="lib-book" id="b1"><h3 class="lib-book-title"><a href="/livres/Mien/2">Mon livre</a></h3></li></ul>' +
      '</body></html>';
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch: vi.fn(() => Promise.resolve(response(html))),
    });

    await collectLibrary(e);

    expect(saved[0].pages[0]).toContain('Mon livre');
    expect(saved[0].pages[0]).not.toContain('Promo');
  });

  test('follows the numbered page links of the page the reader is on', async () => {
    const page = (n: number) =>
      `<html><body><table><tr><td><a href="/livres/T${n}/${n}">Titre ${n}</a></td></tr></table>` +
      '<a href="/mabibliotheque.php?pageN=2">2</a><a href="/mabibliotheque.php?pageN=3">3</a></body></html>';
    const fetch = vi.fn((url: string) => Promise.resolve(response(page(Number(/pageN=(\d)/.exec(url)?.[1] ?? 1)))));
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch,
    });

    await collectLibrary(e);

    expect(fetch).toHaveBeenCalledTimes(3);
    expect(saved[0].pages.map((p) => /Titre \d/.exec(p)?.[0])).toEqual(['Titre 1', 'Titre 2', 'Titre 3']);
  });

  test('says where to go when run anywhere else', async () => {
    const { env: e, saved, alerts } = env({
      location: { hostname: 'example.org', pathname: '/', href: 'https://example.org/' },
    });

    await collectLibrary(e);

    expect(saved).toHaveLength(0);
    expect(alerts[0]).toContain('Booknode, Babelio ou Mangacollec');
  });

  test('saves nothing for a Babelio page holding no book, signed out for instance', async () => {
    const { env: e, saved, alerts } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch: vi.fn(() => Promise.resolve(response('<html><body><form>Connexion</form></body></html>'))),
    });

    await collectLibrary(e);

    expect(saved).toHaveLength(0);
    expect(alerts[0]).toContain('Mes livres');
  });
});

describe('bookmarkletHref', () => {
  /** #456: on Mangacollec, the session in the page's storage reads the reader's collection. */
  test("reads a Mangacollec collection with the session the site keeps, and saves it as is", async () => {
    // A stand-in session, deliberately without entropy: a random-looking literal would read
    // as a leaked key to the secret scanner.
    const session = 'x'.repeat(24);
    const store = new Map([['persist:auth', JSON.stringify({ auth: JSON.stringify({ access_token: session }) })]]);
    const storage = {
      length: store.size,
      key: (i: number) => [...store.keys()][i] ?? null,
      getItem: (k: string) => store.get(k) ?? null,
    } as unknown as Storage;
    const collection = '{"editions":[],"volumes":[],"possessions":[]}';
    const fetch = vi.fn((_url: string, headers?: Record<string, string>) =>
      Promise.resolve(headers?.Authorization === `Bearer ${session}`
        ? response(collection, 'application/json')
        : new Response('', { status: 401 })));
    const { env: e, saved } = env({
      location: { hostname: 'www.mangacollec.com', pathname: '/collection', href: 'https://www.mangacollec.com/collection' },
      fetch,
      storage: [storage],
    });

    await collectLibrary(e);

    expect(fetch).toHaveBeenCalledWith('https://api.mangacollec.com/v2/users/me/collection',
      { Authorization: `Bearer ${session}` });
    expect(saved).toEqual([expect.objectContaining({ source: 'mangacollec', pages: [collection] })]);
  });

  test('falls back on the public profile the reader is on, and says so when nothing reads', async () => {
    const fetch = vi.fn(() => Promise.resolve(new Response('', { status: 403 })));
    const { env: e, saved, alerts } = env({
      location: { hostname: 'www.mangacollec.com', pathname: '/user/alice/collection', href: 'https://www.mangacollec.com/user/alice/collection' },
      fetch,
      storage: [],
    });

    await collectLibrary(e);

    expect(fetch).toHaveBeenCalledWith('https://api.mangacollec.com/v2/user/alice/collection', undefined);
    expect(saved).toHaveLength(0);
    expect(alerts[0]).toContain('mangacollec.com');
  });

  test('is the collector, self-contained, run on the page', () => {
    // The scheme and the code, split apart: what follows the colon is the collector itself.
    const [scheme, encoded] = [bookmarkletHref().split(':', 1)[0], bookmarkletHref().slice(bookmarkletHref().indexOf(':') + 1)];
    expect(scheme).toBe('javascript');
    const code = decodeURIComponent(encoded);
    // It must parse on its own: a reference to this module would break once serialized.
    expect(() => new Function(code)).not.toThrow();
    expect(code.endsWith(')()')).toBe(true);
  });
});

describe('isCollectedFile', () => {
  test('recognises a collected file and nothing else', () => {
    expect(isCollectedFile({ format: 'librarius-collect', version: 1, source: 'babelio', pages: [] })).toBe(true);
    expect(isCollectedFile({ source: 'babelio', pages: [] })).toBe(false);
    expect(isCollectedFile(null)).toBe(false);
  });
});
