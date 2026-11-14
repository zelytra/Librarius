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
  only way into Babelio, which shows a library to its signed-in owner only.
- **Mangacollec** (`POST /api/import/mangacollec`, the body is the file as text, #448):
  Mangacollec has no export, but its site loads a signed-in member's whole collection as one
  JSON document. The reader saves the response of the `collection` request from the
  browser's network panel (Settings explains how) and imports it; the file has no extension,
  so Settings recognises it by its content (`features/settings/mangacollec.ts`) and the
  picker filters nothing out on that source.

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
