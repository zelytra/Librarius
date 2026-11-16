# Imports

A reader brings their library from Booknode, Babelio, Mangacollec, a CSV (Goodreads…) or a
Librarius export. Every import is a **job**: `POST` starts it and answers an id, the screen polls
`GET /api/import/jobs/{id}` for `total`, `imported`, `skipped` and the outcome.

## Getting the library out of the source site

- **Booknode by pseudo** (`POST /api/import/booknode`): the server reads
  `booknode.com/profil/<pseudo>/biblio-table`, every page of it. A 404 means no such
  profile; a 403/429/503 means Booknode turned the server away, and the message says so.
- **The collector** (Settings → "Librarius : collecter"): a bookmarklet the reader runs on
  Booknode or on Babelio's "Mes livres", in their own signed-in browser. It walks every page,
  keeps only the library rows (Babelio's `li.lib-book` items, never the site's menus) and
  downloads `librarius-<source>.json`, imported with `POST /api/import/collected`. It is the
  only way into Babelio, which shows a library to its signed-in owner only. On Babelio it also
  follows the « Pense-bête » filter (#477) — only a filter **of the library page itself**
  (same path, another query, a link or an htmx `hx-get`), never another page whose label
  merely contains « pense » (#502: a readers' selection once brought 100 strangers' books in).
  The file records the filters the page offers (`filters`), so a file sent with a report says
  what the live site calls them. On the server, once a file holds the `li.lib-book` list, a
  page without it is skipped, and Babelio's `list-item`/selection rows are never read; a
  `lib-status--3` row (« Pense-bête ») is filed on the wishlist.
- **Mangacollec** (#448, #456, #503): Mangacollec has no export, but its site loads a member's
  whole collection as one JSON document from `api.mangacollec.com/v2/users/me/collection`.
  The **collector** is the only way in — the pseudo was removed (#503): it only ever worked
  for a public profile, and `POST /api/import/mangacollec` now answers with the way to the
  collector. On mangacollec.com the collector listens to the site's own requests to its API
  (`fetch` and `XMLHttpRequest`, the hook kept between two clicks) and replays the
  `Authorization` header the site sent; then it tries a token found in the page's storage or a
  readable cookie, then the site's cookies alone. It saves a body only if it is a collection
  (`volumes`, `editions`, `possessions`/`reads`) and, when nothing works, its alert names each
  attempt and its HTTP status. The first click on a freshly loaded page usually has heard
  nothing yet: it asks the reader to open their collection from the site's menu and click
  again. The file's single page is the document itself, imported with
  `POST /api/import/collected` like the others.
  A raw collection document saved by hand is still accepted by the file picker, recognised by
  its content (`features/settings/mangacollec.ts`).

## What a row becomes

| Source | Read from it |
|---|---|
| Booknode | title, author, cover, status from the badge's class; tier (diamant, or, argent, bronze) → the built-in Or / Argent / Bronze category (diamant also rated 5); the list ("manga or", "BD", "romance") → kind and genre; "envies" → wishlist; the date added, which is the reading date of a read title |
| Babelio | title, author, cover, status ("Lu", "En cours", "À lire", "Abandonné"), the reader's own rating, the reading date; "Pense-bête" → wishlist |
| CSV | title, author, status, rating, dates, ISBN, length — French and English headers |
| Mangacollec | `MangacollecImporter`: every volume in `possessions` or `reads` becomes "Série [Édition] Tome n" (a one-shot keeps the bare title), with its ISBN, its cover and the day it was added; a volume read is READ on the day of its `reads` row, one held and unread is to read, one **read but not held is `NOT_OWNED`**; a held box set (`box_possessions`) is a `BOX_SET`; the edition name gives the format (Deluxe/Perfect/Intégrale → `DELUXE`, reliée → `HARDCOVER`, Grand format → `LARGE`, poche → `POCKET`); type "Roman" → a book, otherwise a manga. Mangacollec names no author: the enrichment that follows finds them from the ISBN. A named edition ("Berserk Edition Prestige") is a series of its own, so its volume numbers do not collide with the standard edition's |

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

The importing reader's own editions do not wait for the schedule (#425): once the job is
done, `ImportFollowUp` runs, on the import worker, consecutive enrichment batches restricted
to that reader's library and wishlist, then consecutive BnF price batches (`PriceRefresher`)
for the editions that now carry an ISBN, then the page-count estimates — at most 20 batches
of each, the rest left to the scheduled passes. `librarius.imports.follow-up.enabled` turns
it off (it is off in the test profile).
