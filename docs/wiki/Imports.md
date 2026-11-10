# Imports

A reader brings their library from Booknode, Babelio, a CSV (Goodreads…) or a Librarius
export. Every import is a **job**: `POST` starts it and answers an id, the screen polls
`GET /api/import/jobs/{id}` for `total`, `imported`, `skipped` and the outcome.

## Getting the library out of the source site

- **Booknode by pseudo** (`POST /api/import/booknode`): the server reads
  `booknode.com/profil/<pseudo>/biblio-table`, every page of it. A 404 means no such
  profile; a 403/429/503 means Booknode turned the server away, and the message says so.
- **The collector** (Settings → "Librarius : collecter"): a bookmarklet the reader runs on
  Booknode or on Babelio's "Mes livres", in their own signed-in browser. It walks every page,
  keeps only the library rows (Babelio's `li.lib-book` items, never the site's menus) and
  downloads `librarius-<source>.json`, imported with `POST /api/import/collected`. It is the
  only way into Babelio, which shows a library to its signed-in owner only.

## What a row becomes

| Source | Read from it |
|---|---|
| Booknode | title, author, cover, status from the badge's class; tier (diamant, or, argent, bronze) → the built-in Or / Argent / Bronze category (diamant also rated 5); the list ("manga or", "BD", "romance") → kind and genre; "envies" → wishlist; the date added, which is the reading date of a read title |
| Babelio | title, author, cover, status ("Lu", "En cours", "À lire", "Abandonné"), the reader's own rating, the reading date; "Pense-bête" → wishlist |
| CSV | title, author, status, rating, dates, ISBN, length — French and English headers |

## How it is written

`ImportService.persist` writes **100 titles per transaction** and publishes its progress after
each batch: one transaction for a 2,400-title library ran past the 60 s transaction timeout
and was rolled back whole. A volume joins its series (`ImportTitle`); an entry with nothing to
tell about its edition shares the work's bare edition instead of founding one more.

**Importing again completes** what is already there instead of skipping it: the tier, the
kind (the title is moved to an entry of the right kind), the rating, the reading date and the
genre a title lacks — never overriding what the reader set. A title already held, or listed
twice in the file, is not imported twice.

After the import, `ImportEnricher` fills lengths, ISBNs and publishers in from the catalog, in
the background — see [Catalog & book search](https://github.com/zelytra/Librarius/wiki/Catalog-and-Book-Search#filling-in-imported-titles).
