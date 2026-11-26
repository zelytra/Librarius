import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { bookmarkletHref, collectLibrary, COLLECTOR_VERSION, inAppCollectorScript, isCollectedFile, postToApp, type CollectedFile, type CollectorEnv } from './collector';

/** A Booknode library page: rows, and the pagination naming the last page. */
function booknodePage(titles: string[], last: number): string {
  const rows = titles.map((t) => `<div class="book-item"><span class="book-name">${t}</span></div>`).join('');
  const links = Array.from({ length: last }, (_, i) => `<a href="/profil/alice/biblio-table?page=${i + 1}">${i + 1}</a>`).join('');
  return `<html><body>${rows}<ul class="pagination">${links}</ul></body></html>`;
}

function response(html: string, contentType = 'text/html; charset=utf-8', bytes?: Uint8Array<ArrayBuffer>): Response {
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

  /**
   * #651: run on Booknode's reading history, the collector opens every year the way the reader
   * would — the site's script draws a year's months when its heading is clicked — and saves them.
   */
  test('opens every year of the Booknode reading history and keeps its months', async () => {
    const month = (label: string, title: string) =>
      `<div class="one_month"><h3>${label}</h3><div class="item"><div class="panel-heading"><a href="/x">${title}</a></div></div></div>`;
    document.body.innerHTML =
      '<div class="history_header"><h3 class="button_and_year"><button><i class="fa fa-chevron-circle-down"></i></button> 2026</h3></div>' +
      month('novembre 2026', 'Dune') +
      '<div class="history_header" id="y2019"><h3 class="button_and_year"><button id="open2019"><i class="fa fa-chevron-circle-right"></i></button> 2019</h3></div>';
    // The site's script: the months of 2019 arrive a moment after its heading is clicked.
    document.getElementById('open2019')!.addEventListener('click', () =>
      setTimeout(() => document.getElementById('y2019')!.insertAdjacentHTML('afterend', month('août 2019', 'Fondation')), 5));
    const fetch = vi.fn();
    const { env: e, saved } = env({
      fetch,
      location: { hostname: 'booknode.com', pathname: '/profil/alice/historique', href: 'https://booknode.com/profil/alice/historique' },
    });

    await collectLibrary(e);

    expect(fetch).not.toHaveBeenCalled();
    expect(saved).toHaveLength(1);
    expect(saved[0].source).toBe('booknode');
    expect(saved[0].pages.join('')).toContain('novembre 2026');
    expect(saved[0].pages.join('')).toContain('Fondation');
    document.body.innerHTML = '';
  });

  test('points to the reading history once the library is collected', async () => {
    const fetch = vi.fn(() => Promise.resolve(response(booknodePage(['Dune'], 1))));
    const { env: e } = env({ fetch });

    await collectLibrary(e);

    expect(document.body.textContent).toContain('historique');
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

  /**
   * #477: Babelio files the wished books under their own « Pense-bête » filter, which the
   * list the reader stands on may leave out. The collector walks that filter too, its pages
   * included, so the wishes reach the import and the wishlist.
   */
  test('walks the « Pense-bête » filter of a Babelio library too', async () => {
    const library = (n: number) =>
      `<html><body><ul><li class="lib-book"><a href="/livres/L${n}/${n}">Lu ${n}</a></li></ul>` +
      '<a href="/mabibliotheque.php?statut=pense">Pense-bête (2)</a></body></html>';
    const wishes = (n: number) =>
      `<html><body><ul><li class="lib-book"><a href="/livres/W${n}/${n}">Voulu ${n}</a>` +
      '<span class="lib-status">Pense-bête</span></li></ul>' +
      '<a href="/mabibliotheque.php?statut=pense&pageN=2">2</a></body></html>';
    const fetch = vi.fn((url: string) => {
      const n = Number(/pageN=(\d)/.exec(url)?.[1] ?? 1);
      return Promise.resolve(response(url.includes('statut=pense') ? wishes(n) : library(n)));
    });
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch,
    });

    await collectLibrary(e);

    const titles = saved[0].pages.map((p) => /(Lu|Voulu) \d/.exec(p)?.[0]);
    expect(titles).toEqual(['Lu 1', 'Voulu 1', 'Voulu 2']);
  });

  /**
   * #502, measured on the owner's real file: a link whose text merely contained « pense »
   * led to a readers' selection, and its hundred books were imported as the reader's own.
   */
  test('follows only the wish filter of the library page, never another page that says « pense »', async () => {
    const library =
      '<html><body><ul><li class="lib-book"><a href="/livres/L1/1">Lu 1</a></li></ul>' +
      '<a href="/selection/top">Qu\u2019en pensez-vous ?</a>' +
      '<button hx-get="/mabibliotheque.php?s1=3">Pense-bêtes</button></body></html>';
    const wishes = '<html><body><ul><li class="lib-book"><a href="/livres/W1/2">Voulu 1</a></li></ul></body></html>';
    const selection = '<html><body><ul><li class="list-item"><a href="/livres/P/9">Promu</a></li></ul></body></html>';
    const fetch = vi.fn((url: string) =>
      Promise.resolve(response(url.includes('selection') ? selection : url.includes('s1=3') ? wishes : library)),
    );
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch,
    });

    await collectLibrary(e);

    expect(fetch.mock.calls.map(([url]) => url).some((url) => url.includes('selection'))).toBe(false);
    const text = saved[0].pages.join(' ');
    expect(text).toContain('Voulu 1');
    expect(text).not.toContain('Promu');
  });

  /** #506: Babelio's live filters, as the owner's file recorded them — the wishes are « Wishlist ». */
  test('walks the « Wishlist » filter of the live library page', async () => {
    const filters =
      '<a href="/mabibliotheque.php?s1=0">En cours 1</a><a href="/mabibliotheque.php?s1=1">Lus 113</a>' +
      '<a href="/mabibliotheque.php?s1=2">À lire 15</a><a href="/mabibliotheque.php?s1=3">Wishlist 5</a>' +
      '<a href="/mabibliotheque.php?s1=4">Abandonnés 4</a>';
    const library = `<html><body><ul><li class="lib-book"><a href="/livres/L1/1">Lu 1</a></li></ul>${filters}</body></html>`;
    const wishes =
      '<html><body><ul><li class="lib-book"><a href="/livres/W1/2">Voulu 1</a>' +
      '<span class="lib-status lib-status--3">Wishlist</span></li></ul></body></html>';
    const fetch = vi.fn((url: string) => Promise.resolve(response(url.endsWith('s1=3') ? wishes : library)));
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch,
    });

    await collectLibrary(e);

    expect(fetch.mock.calls.map(([url]) => url)).toContain('https://www.babelio.com/mabibliotheque.php?s1=3');
    // Only the wish filter: the other statuses are already in the default view.
    expect(fetch.mock.calls.map(([url]) => url)).not.toContain('https://www.babelio.com/mabibliotheque.php?s1=1');
    expect(saved[0].pages.join(' ')).toContain('Voulu 1');
  });

  test('keeps nothing from a page that is not the library list once the library has one', async () => {
    const library =
      '<html><body><ul><li class="lib-book"><a href="/livres/L1/1">Lu 1</a></li></ul>' +
      '<a href="/mabibliotheque.php?statut=pense">Pense-bête</a></body></html>';
    const notTheList = '<html><body><aside><a href="/livres/P/9">Suggestion</a></aside></body></html>';
    const fetch = vi.fn((url: string) => Promise.resolve(response(url.includes('statut') ? notTheList : library)));
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch,
    });

    await collectLibrary(e);

    expect(saved[0].pages.join(' ')).not.toContain('Suggestion');
  });

  /** So that a file the owner sends says what the live filters are called and where they go. */
  test('records the filters the library page offers', async () => {
    const library =
      '<html><body><ul><li class="lib-book"><a href="/livres/L1/1">Lu 1</a></li></ul>' +
      '<a href="/mabibliotheque.php?s1=1">Lus</a><a hx-get="/mabibliotheque.php?s1=2">À lire</a>' +
      '<a href="/auteur/X/1">Un auteur</a></body></html>';
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch: vi.fn(() => Promise.resolve(response(library))),
    });

    await collectLibrary(e);

    expect(saved[0].filters).toEqual([
      { text: 'Lus', url: 'https://www.babelio.com/mabibliotheque.php?s1=1' },
      { text: 'À lire', url: 'https://www.babelio.com/mabibliotheque.php?s1=2' },
    ]);
  });

  /** #507: the serialized bookmark writes the version the import compares against. */
  test('stamps its files with the current collector version', async () => {
    const { env: e, saved } = env({
      location: { hostname: 'www.babelio.com', pathname: '/mabibliotheque.php', href: 'https://www.babelio.com/mabibliotheque.php' },
      fetch: vi.fn(() => Promise.resolve(response('<html><body><ul><li class="lib-book"><a href="/livres/L/1">L</a></li></ul></body></html>'))),
    });

    await collectLibrary(e);

    expect(saved[0].version).toBe(COLLECTOR_VERSION);
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
      global: { fetch: vi.fn() } as unknown as typeof globalThis,
    });

    await collectLibrary(e);

    expect(fetch).toHaveBeenCalledWith('https://api.mangacollec.com/v2/users/me/collection',
      { Authorization: `Bearer ${session}` });
    expect(saved).toEqual([expect.objectContaining({ source: 'mangacollec', pages: [collection] })]);
  });

  /** #478: a private profile still reads, with a session kept in a cookie or by the browser. */
  test('reads a private Mangacollec collection with the site cookies, no public profile needed', async () => {
    const collection = '{"editions":[],"volumes":[],"possessions":[]}';
    const fetch = vi.fn((url: string, headers?: Record<string, string>) =>
      Promise.resolve(url.endsWith('users/me/collection') && !headers
        ? response(collection, 'application/json')
        : new Response('', { status: 401 })));
    const { env: e, saved } = env({
      location: { hostname: 'www.mangacollec.com', pathname: '/collection', href: 'https://www.mangacollec.com/collection' },
      fetch,
      storage: [],
      global: { fetch: vi.fn() } as unknown as typeof globalThis,
    });

    await collectLibrary(e);

    expect(fetch).toHaveBeenCalledWith('https://api.mangacollec.com/v2/users/me/collection', undefined);
    expect(saved).toEqual([expect.objectContaining({ source: 'mangacollec', pages: [collection] })]);
  });

  /**
   * #503: wherever the site keeps its session, it sends it to its own API. The collector
   * listens to the site's requests — `fetch` and `XMLHttpRequest` alike — and reads the
   * collection with the header the site itself used. Nothing heard yet on the first click: it
   * asks the reader to open their collection, and the second click reads it.
   */
  test("reads the collection with the header the site's own requests carry", async () => {
    const collection = '{"editions":[],"volumes":[],"possessions":[]}';
    const opened: string[] = [];
    class FakeXhr {
      open(_method: string, url: string) {
        opened.push(url);
      }
      readonly headers: [string, string][] = [];
      setRequestHeader(name: string, value: string) {
        this.headers.push([name, value]);
      }
    }
    const site = { fetch: vi.fn(() => Promise.resolve(new Response('{}'))), XMLHttpRequest: FakeXhr } as unknown as typeof globalThis;
    const fetch = vi.fn((_url: string, headers?: Record<string, string>) =>
      Promise.resolve(headers?.Authorization === 'Bearer from-the-site'
        ? response(collection, 'application/json')
        : new Response('', { status: 401 })));
    const first = env({
      location: { hostname: 'www.mangacollec.com', pathname: '/', href: 'https://www.mangacollec.com/' },
      fetch,
      storage: [],
      global: site,
    });

    await collectLibrary(first.env);
    expect(first.saved).toHaveLength(0);
    expect(first.alerts[0]).toContain('clique à nouveau');

    // The site, going on with its life: an axios-style request, then a fetch.
    const xhr = new (site.XMLHttpRequest as unknown as typeof FakeXhr)();
    xhr.open('GET', 'https://api.mangacollec.com/v2/users/me');
    xhr.setRequestHeader('Authorization', 'Bearer from-the-site');

    const second = env({
      location: first.env.location,
      fetch,
      storage: [],
      global: site,
    });
    await collectLibrary(second.env);

    expect(opened).toEqual(['https://api.mangacollec.com/v2/users/me']);
    expect(second.saved).toEqual([expect.objectContaining({ source: 'mangacollec', pages: [collection] })]);
  });

  test("hears the header on the site's fetch calls too", async () => {
    const collection = '{"editions":[],"volumes":[],"reads":[]}';
    const site = { fetch: vi.fn(() => Promise.resolve(new Response('{}'))) } as unknown as typeof globalThis;
    const fetch = vi.fn((_url: string, headers?: Record<string, string>) =>
      Promise.resolve(headers?.authorization === 'Bearer via-fetch'
        ? response(collection, 'application/json')
        : new Response('', { status: 401 })));
    const run = () => {
      const { env: e, saved } = env({
        location: { hostname: 'www.mangacollec.com', pathname: '/', href: 'https://www.mangacollec.com/' },
        fetch,
        storage: [],
        global: site,
      });
      return collectLibrary(e).then(() => saved);
    };

    await run();
    await site.fetch('https://api.mangacollec.com/v2/users/me/collection', { headers: { authorization: 'Bearer via-fetch' } });

    expect(await run()).toEqual([expect.objectContaining({ pages: [collection] })]);
  });

  /** Only the API's own host counts: a look-alike address must not lend its header (CodeQL). */
  test('ignores a header sent to a host that only contains the API name', async () => {
    const site = { fetch: vi.fn(() => Promise.resolve(new Response('{}'))) } as unknown as typeof globalThis;
    const fetch = vi.fn<(url: string, headers?: Record<string, string>) => Promise<Response>>(() =>
      Promise.resolve(new Response('', { status: 401 })));
    const run = () =>
      collectLibrary(env({
        location: { hostname: 'www.mangacollec.com', pathname: '/', href: 'https://www.mangacollec.com/' },
        fetch,
        storage: [],
        global: site,
      }).env);

    await run();
    await site.fetch('https://api.mangacollec.com.evil.example/x', { headers: { authorization: 'Bearer stolen' } });
    await site.fetch('https://evil.example/?api.mangacollec.com', { headers: { authorization: 'Bearer stolen' } });
    fetch.mockClear();
    await run();

    expect(fetch.mock.calls.some(([, headers]) => headers?.authorization === 'Bearer stolen')).toBe(false);
  });

  /** An error page answered with 200 used to be saved, and the import then failed on it. */
  test('saves only a collection, and says which reads failed and how', async () => {
    const fetch = vi.fn((_url: string, headers?: Record<string, string>) =>
      Promise.resolve(headers ? response('{"error":"expired"}', 'application/json') : new Response('', { status: 403 })));
    const session = 'y'.repeat(24);
    const store = new Map([['token', session]]);
    const storage = {
      length: store.size,
      key: (i: number) => [...store.keys()][i] ?? null,
      getItem: (k: string) => store.get(k) ?? null,
    } as unknown as Storage;
    const { env: e, saved, alerts } = env({
      location: { hostname: 'www.mangacollec.com', pathname: '/collection', href: 'https://www.mangacollec.com/collection' },
      fetch,
      storage: [storage],
      global: { fetch: vi.fn() } as unknown as typeof globalThis,
    });

    await collectLibrary(e);

    expect(saved).toHaveLength(0);
    expect(alerts[0]).toContain('pas une collection');
    expect(alerts[0]).toContain('403');
  });

  /** #503: the pseudo is gone — a collection is the reader's own, read with their session. */
  test("never reads another member's public profile", async () => {
    const fetch = vi.fn<(url: string) => Promise<Response>>(() => Promise.resolve(new Response('', { status: 401 })));
    const { env: e, saved } = env({
      location: { hostname: 'www.mangacollec.com', pathname: '/user/alice/collection', href: 'https://www.mangacollec.com/user/alice/collection' },
      fetch,
      storage: [],
      global: { fetch: vi.fn() } as unknown as typeof globalThis,
    });

    await collectLibrary(e);

    expect(fetch.mock.calls.map(([url]) => url)).not.toContain('https://api.mangacollec.com/v2/user/alice/collection');
    expect(saved).toHaveLength(0);
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

  test('is a bookmark address with no line break in it', () => {
    // The scheme is checked by the test above; this one is about the line breaks only.
    expect(decodeURIComponent(bookmarkletHref())).not.toMatch(/[\r\n]/);
  });
});

describe('isCollectedFile', () => {
  test('recognises a collected file and nothing else', () => {
    expect(isCollectedFile({ format: 'librarius-collect', version: 1, source: 'babelio', pages: [] })).toBe(true);
    expect(isCollectedFile({ source: 'babelio', pages: [] })).toBe(false);
    expect(isCollectedFile(null)).toBe(false);
  });
});

/**
 * #570: in the mobile app the collector runs in a window the app owns, not from a bookmark. The
 * script injected there pins one button over the page; the collector it starts posts its file
 * to the app rather than downloading it.
 */
describe('the in-app collector', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    delete (window as { mobileApp?: unknown }).mobileApp;
  });

  test('pins a single import button over the page, however often it is injected', () => {
    const run = new Function(inAppCollectorScript());
    run();
    run();
    const buttons = screen.getAllByRole('button', { name: 'Importer dans Librarius' });
    expect(buttons).toHaveLength(1);
  });

  test('starts the collector when the button is pressed', async () => {
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    new Function(inAppCollectorScript())();
    screen.getByRole('button', { name: 'Importer dans Librarius' }).click();
    // On a page that is none of the three sites, the collector says where to go.
    await waitFor(() => expect(alert).toHaveBeenCalledWith(expect.stringMatching(/Booknode, Babelio ou Mangacollec/)));
    alert.mockRestore();
  });

  test('posts the collected file to the app', () => {
    const postMessage = vi.fn();
    (window as { mobileApp?: unknown }).mobileApp = { postMessage };
    const file: CollectedFile = { format: 'librarius-collect', version: COLLECTOR_VERSION, source: 'babelio', collectedAt: '2026-11-21T10:00:00Z', pages: ['<ul></ul>'] };
    postToApp(file);
    expect(postMessage).toHaveBeenCalledWith({ detail: file });
  });
});
