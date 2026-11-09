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
    expect(alerts[0]).toContain('Booknode ou sur Babelio');
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
  test('is the collector, self-contained, run on the page', () => {
    const href = bookmarkletHref();
    expect(href.startsWith('javascript:')).toBe(true);
    const code = decodeURIComponent(href.slice('javascript:'.length));
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
