# Imports

A reader brings their library from Booknode, Babelio, Mangacollec, a CSV (Goodreads…) or a
Librarius export. Every import is a **job**: `POST` starts it and answers an id, the screen polls
`GET /api/import/jobs/{id}` for `total`, `imported`, `skipped` and the outcome.

## Getting the library out of the source site

- **Booknode by pseudo** (`POST /api/import/booknode`): the server reads
  `booknode.com/profil/<pseudo>/biblio-table`, every page of it. A 404 means no such
  profile; a 403/429/503 means Booknode turned the server away, and the message says so.
- **The collector** (Settings → "Librarius : collecter"): **one** bookmarklet for Booknode,
  Babelio and Mangacollec, shown once above the source choice (#507), that the reader runs in
  their own signed-in browser. Its files carry `version` (`COLLECTOR_VERSION`, 2 since #507);
  a file from an older bookmark still imports and Settings says to replace the bookmark. It walks every page,
  keeps only the library rows (Babelio's `li.lib-book` items, never the site's menus) and
  downloads `librarius-<source>.json`, imported with `POST /api/import/collected`. It is the
  only way into Babelio, which shows a library to its signed-in owner only. On Babelio it also
  follows the wish filter — « Wishlist » (`?s1=3`) on the live site (#506), « Pense-bête » on
  older layouts (#477) — only a filter **of the library page itself**
  (same path, another query, a link or an htmx `hx-get`), never another page whose label
  merely contains « pense » (#502: a readers' selection once brought 100 strangers' books in).
  The file records the filters the page offers (`filters`), so a file sent with a report says
  what the live site calls them. On the server, once a file holds the `li.lib-book` list, a
  page without it is skipped, and Babelio's `list-item`/selection rows are never read; a
  `lib-status--3` row, or one labelled « Wishlist » / « Pense-bête », is filed on the wishlist.
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

## In the mobile app: signing in inside the app (#570)

The collector asks a reader to install a bookmarklet, which phone browsers make awkward and
the native app cannot run at all. In the app, Settings offers **« Se connecter à … et
importer »** instead: the source site opens in a window the app owns, the reader signs in as
usual, opens their library, and taps the **« Importer dans Librarius »** button pinned over
the page. The same collector then runs in that page and hands the file straight back to the
app, which imports it with `POST /api/import/collected` — no bookmark, no download, no file
picker.

How the options compared:

| Option | Reads the signed-in page | Sends the file back | Verdict |
|---|---|---|---|
| `@capacitor/browser` (Custom Tabs, `SFSafariViewController`) — already a dependency | no: the system browser runs no script of ours | no | ✗ |
| A popup or iframe from the web app | no: the third-party origin is cross-origin | no | ✗ |
| A browser extension | yes | yes | heavier than the bookmarklet it would replace, one per browser, a store review each |
| A native plugin of our own around a `WebView` | yes | yes | native code on two platforms to write and keep |
| **`@capgo/inappbrowser`** (MIT, Capacitor ≥ 8) | **yes**: `executeScript` in the page | **yes**: `window.mobileApp.postMessage` → `messageFromWebview` | **chosen** |

What the code does (`features/settings/inAppImport.ts`):

- `openWebView` on the source's sign-in page (`IN_APP_START`), and on every
  `browserPageLoaded` an `executeScript` of `inAppCollectorScript()`
  (`features/settings/collector.ts`): it pins the button — once per page — and its click runs
  `collectLibrary` with `postToApp` as the save, so the file is posted to the app instead of
  downloaded. The collector is the bookmarklet's, unchanged: one code path for both.
- The first `messageFromWebview` that holds a collected file closes the window and resolves
  with it; closing the window first resolves with nothing, and the screen stays as it was.
- Nothing about the session leaves the window: the collector reads the pages, the app only
  receives the rows it would have downloaded. The sites' terms of use apply as they do to
  the bookmarklet — the import reads the reader's own library, at a reader's pace.
- On the web the bookmarklet stays: no browser lets a page script another origin, and an
  extension was judged not worth it (table above).

Not yet run on a device: the flow is locked by tests with the plugin faked, and needs a try on
Android and iOS against the live sites — in particular the size of a large Mangacollec
collection through the bridge.

## What a row becomes

| Source | Read from it |
|---|---|
| Booknode | title, author, cover, status from the badge's class; tier (diamant, or, argent, bronze) → the built-in Platine / Or / Argent / Bronze category (diamant also rated 5; before V46 it filed under Or); the list ("manga or", "BD", "romance") → kind and genre; "envies" → wishlist; the date added → the acquisition date only. A read title is imported **undated** (#645): the date is the day the title went on the shelf, and a title moved there from « Envies » keeps the day of the wish |
| Babelio | title, author, cover, status ("Lu", "En cours", "À lire", "Abandonné"), the reader's own rating, the reading date; "Pense-bête" → wishlist |
| CSV | title, author, status, rating, dates, ISBN, length — French and English headers |
| Mangacollec | `MangacollecImporter`: every volume in `possessions` or `reads` becomes "Série [Édition] Tome n" (a one-shot keeps the bare title), with its ISBN, its cover and the day it was added; a volume read is READ on the day of its `reads` row, one held and unread is to read, one **read but not held is `NOT_OWNED`**; a held box set (`box_possessions`) is a `BOX_SET`; the edition name gives the format (Deluxe/Perfect/Intégrale → `DELUXE`, reliée → `HARDCOVER`, Grand format → `LARGE`, poche → `POCKET`); type "Roman" → a book, otherwise a manga. Mangacollec names no author: the enrichment that follows finds them from the ISBN. A named edition ("Berserk Edition Prestige") is a series of its own, so its volume numbers do not collide with the standard edition's |

## How it is written

`ImportService.persist` writes **100 titles per transaction** and publishes its progress after
each batch: one transaction for a 2,400-title library ran past the 60 s transaction timeout
and was rolled back whole. A volume joins its series (`ImportTitle`); an entry with nothing to
tell about its edition shares the work's bare edition instead of founding one more.

How `ImportTitle` finds the series (#642):

- the catalog's markers (`VolumeParser`): "Série, Tome 3", "Série Vol. 2", "Série T5", "Série #12";
- after them, the other words Booknode numbers a run with, only after the series' comma and
  ending the title or opening its subtitle: "La Passe-miroir, Livre 1 : …", "Série, Saison 2",
  "Série, Partie 1", "Série, Épisode 3" ("Le Livre des heures" stays standalone);
- a decimal volume ("Série, Tome 1.5") is a novella: it joins the series **without a number**,
  so it never takes the place of volume 1;
- a bracket in the series is kept whole ("U4 (BD), Tome 1" → "U4 (BD)").

`SeriesRepository.findByKindAndTitle` matches the title case-blind, then accent- and
punctuation-blind (`genre_code`, V6), so "Le Vaisseau d'Amelia" and "Le Vaisseau d'Amélia" are
one series. A title an earlier import left standalone joins its series when the library is
imported again.

**Importing again completes** what is already there instead of skipping it: the tier, the
kind (the title is moved to an entry of the right kind), the rating, the reading date and the
genre a title lacks — never overriding what the reader set. It also takes back a reading date
an earlier import invented (#645): a finish with no start that falls on the very day the
source says the title was added is cleared when the source tells no reading date. A title already held, or listed
twice in the file, is not imported twice.

After the import, `ImportEnricher` fills lengths, ISBNs and publishers in from the catalog, in
the background — see [Catalog & book search](https://github.com/zelytra/Librarius/wiki/Catalog-and-Book-Search#filling-in-imported-titles).

The importing reader's own editions do not wait for the schedule (#425): once the job is
done, `ImportFollowUp` runs, on the import worker, consecutive enrichment batches restricted
to that reader's library and wishlist, then consecutive BnF price batches (`PriceRefresher`)
for the editions that now carry an ISBN, then the page-count estimates — at most 20 batches
of each, the rest left to the scheduled passes. `librarius.imports.follow-up.enabled` turns
it off (it is off in the test profile).
