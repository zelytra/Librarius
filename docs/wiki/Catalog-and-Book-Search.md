# Catalog & book search

The **Discover** screen searches external catalogs rather than a table Librarius owns. This
page explains how a search gets from the search box to a result, where the data comes from,
and how imported titles get their missing details later.

## Three providers, one shape

`CatalogService` fans a search out to every provider at once (virtual threads, one call per
provider, each bounded by `librarius.catalog.provider.call-timeout`), normalises the answers
into `CatalogResult`, de-duplicates them — ISBN first, then folded title and author — and
ranks the merged page before trimming it.

| Provider | Covers | Notes |
|---|---|---|
| **Open Library** | books, every language | works and editions; the work reference lets a book list its other editions |
| **BnF** (SRU, Dublin Core) | French books, BD | the French publishers' catalogue; authority headings put back in reading order |
| **AniList** (GraphQL) | manga | staff credited by role (story, art), long-vowel spellings folded |

None needs an API key. A criterion a provider cannot honour (publisher on AniList, say) is
dropped for that provider rather than faked.

## Relevance

The providers' own orders disagree, so the merged page is re-ranked on the free text
(`relevanceScorer`): an exact title first, then a title holding every word typed (volume
numbers compared whole — "one piece 1" does not match tome 113), then a work **by a person
whose name is the query** — the words must name one credited person, so an anthology
crediting a "Frank Andriat" and a "Philippe Herbet" does not rank as Frank Herbert — then
partial matches. Discover also shows the authors of the shared catalog the words name, above
the titles (`GET /api/authors?q=`).

## Quota and cache

A caller has a quota (`RateLimiter`, 30 searches a minute and 500 a day by default, chart
value `api.catalogQuota`): a scan or a search that reaches the providers spends it, one
answered from Librarius's own data does not. Results are cached twice: in process
(Caffeine) and in PostgreSQL (`catalog_cache`), so a deploy does not send every popular search
back out to the providers. Details: [`.claude/docs/DATA-MODEL.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/DATA-MODEL.md).

## Scanning a barcode

`GET /api/scan/{isbn}` (ISBN-13 or ISBN-10, check digit verified) answers whether the book
is the reader's already, on their wishlist, or new — see [API](https://github.com/zelytra/Librarius/wiki/API#scan). Imported
libraries carry no ISBN, so after the ISBN pass the catalog says what the book is and the
library is searched again by title and author, then series and volume, then a reworded
title with the same numbers.

A scanning session can file what it finds on its own (#168): the reader picks a destination
before scanning — to-read pile, read, reading, wishlist, or "check only" (the default) — and
each new title the catalog recognises is added there with no confirmation, while a title
already owned or wished for is only reported. The screen counts the session's additions and
offers an undo on each.

## Filling in imported titles

Booknode and Babelio give a title, an author and a cover — no ISBN, length or publisher.
`ImportEnricher` (scheduled, `librarius.imports.enrich.every`, 60 editions a run) looks each
such edition up in the catalog and fills only what is missing, when the candidate is
provably the same book. Until a library is filled in, the statistics estimate pages read from
the known lengths and say so ("≈").

## Upcoming releases

Release dates are the providers' own — mostly Japanese or English editions. There is no free
source of French publishers' calendars, so dates are shown as indicative.

The endpoint reference is on the [API](https://github.com/zelytra/Librarius/wiki/API) page.
