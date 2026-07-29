# Catalog & book search

The **Discover** screen searches an external catalog rather than a table Librarius owns:
books come from [Open Library](https://openlibrary.org/), manga from
[AniList](https://anilist.co/). Neither needs an API key. This page explains how a search
gets from the search box to a result, and where the data underneath it actually comes from.

## Two providers, one shape

`CatalogAggregator` fans a search out to whichever provider matches the requested kind
(`BOOK` goes to Open Library, `MANGA` to AniList), then normalises whatever comes back into
this repository's own `work` / `edition` shape and de-duplicates it — first by ISBN13, then
by a fuzzy match on title and author. The two providers do not index the same fields, so a
search criterion one of them cannot honour is dropped for that provider rather than faked:

| Criterion | Open Library (books) | AniList (manga) |
|---|---|---|
| free text | yes | yes |
| author | yes | yes, resolved through the author's own works |
| year | yes | yes |
| publisher | yes | no — AniList describes works, not editions |
| language | yes | no |
| ISBN | yes | no |

A manga search that only fills in criteria AniList cannot use returns nothing, rather than a
list of the most popular manga — a result that would look correct and would not be one.

## Why there is a cache, and why it has two levels

A search first checks an in-process [Caffeine](https://github.com/ben-manes/caffeine) cache,
then a PostgreSQL table (`catalog_cache`) before it ever calls Open Library or AniList. The
second level exists because the first one does not survive a deploy: this project ships to
`main` often, every merge replaces the running pod, and without a persistent cache the same
popular searches would go back out to both providers' rate limits several times a day. A
cached row expires after six hours for a search and twelve for upcoming releases. Full detail
is in
[`.claude/docs/DATA-MODEL.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/DATA-MODEL.md).

## Where "upcoming releases" dates come from

`GET /api/catalog/upcoming` returns each provider's own release dates, which are
overwhelmingly Japanese or English — there is no free, reliable source for **French**
publisher release calendars (Glénat, Ki-oon, Kana, Pika…). Dates shown today are therefore
labelled as indicative rather than presented as French release dates. A curated table for
real French dates is a planned addition (see [Data model](https://github.com/zelytra/Librarius/wiki/Data-Model)),
not something the external providers can supply.

## Known gaps

- **No rate limiting** on the search endpoint yet — a single user could exhaust the
  instance's shared Open Library/AniList quota.
- **No provider enrichment of existing editions** — a work's list of editions only grows
  when a user adds one. There is no "ask the provider for other editions of this work" call
  yet, because a work does not keep a provider reference.

The full endpoint reference, including every query parameter, is in
[`.claude/docs/API.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/API.md#catalog-search).
