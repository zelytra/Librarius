# REST API — Librarius

Base path: `/api`. Every resource is `@Authenticated` (Keycloak JWT in
`Authorization: Bearer …`).
Reference contract: `openapi/openapi.yaml` (generated at build time).

> ⚠️ Any change to a resource or a DTO requires regenerating the schema **and** the TS
> client, otherwise the `openapi-sync` CI job fails:
> `cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api`

## Inventory

| Method | Path | Role |
|---|---|---|
| GET | `/api/me` | Current profile (`MeDto`) — creates the `app_user` on the fly |
| GET | `/api/catalog/search?q=&kind=&limit=` | External catalog search. `kind` defaults to `BOOK`, `limit` clamped to 1–40 |
| GET | `/api/catalog/upcoming?kind=&limit=` | Upcoming releases. `kind` defaults to `MANGA`, `limit` clamped to 1–50 |
| GET | `/api/library?page=&size=&sort=&kind=&status=&rank=&genre=&minRating=&q=` | One page of the owned titles (`LibraryPageDto`) — see [Pagination](#pagination) |
| GET | `/api/library/{id}` | A single owned title (`LibraryItemDto`), 404 if it is not the caller's |
| POST | `/api/library` | Adds a title (`LibraryCreateDto`) — creates `work`+`edition` if needed |
| PUT | `/api/library/{id}/rank` | Assigns/removes a rank (`RankAssignDto`, a null `categoryId` removes it) |
| PUT | `/api/library/{id}/progress` | Status and reading progress (`ProgressDto`) — 204, see [Reading progress](#reading-progress) |
| PUT | `/api/library/{id}/review` | Private rating and review (`ReviewDto`) — returns the updated `LibraryItemDto` |
| DELETE | `/api/library/{id}` | Removes a title from the collection |
| GET | `/api/wishlist?page=&size=&sort=&kind=&priority=&q=` | One page of the wishlist, with the budget of the whole filtered set (`WishlistPageDto`) |
| POST | `/api/wishlist` | Adds a wish (`WishlistCreateDto`) |
| PUT | `/api/wishlist/{id}` | Replaces the priority, the estimated price and the note (`WishlistUpdateDto`) |
| POST | `/api/wishlist/{id}/acquire` | "I bought it": creates the owned title and drops the wish, in one transaction (`WishlistAcquireDto`, body optional) |
| DELETE | `/api/wishlist/{id}` | Removes a wish |
| GET | `/api/series` | Series the user owns a volume of or follows (`SeriesSummaryDto`) |
| GET | `/api/series/{id}` | A series and the state of each of its volumes (`SeriesDetailDto`) |
| GET | `/api/series/{id}/missing` | Holes in the owned run (`SeriesMissingDto`) |
| PUT | `/api/series/{id}/follow` | Starts following the series — 204, idempotent |
| DELETE | `/api/series/{id}/follow` | Stops following the series — 204, idempotent |
| GET | `/api/genres` | Genres present in the caller's collection, most frequent first (`GenreCount`) — see [Genres](#genres) |
| GET | `/api/categories` | Built-in ranks + the user's own categories |
| POST | `/api/categories` | Creates a custom category (`CategoryCreateDto`) |
| GET | `/api/goals` | Reading goals |
| PUT | `/api/goals/{year}` | Creates or updates a year's goal (`GoalUpsertDto`) |
| GET | `/api/stats` | Aggregated statistics (`StatsDto`) |
| POST | `/api/import/{source}` | Import by handle (`booknode`, `babelio`) — `{ "handle": "…" }` |
| POST | `/api/import/csv` | CSV import (the body is the raw content) |

Outside `/api`: `/q/health` and `/q/metrics`, **cluster-internal only** — the ingress does not route `/q`. Swagger UI is served in dev and test, and absent from the production build.

## Main DTOs

```java
MeDto(String id, String email, String displayName, String locale)

BookView(/* read projection of an edition and its work */)

ManualBookDto(Kind kind, String title, String authors, String seriesTitle,
              Integer volumeNumber, String isbn13, String publisher, String language,
              Integer pageCount, String coverUrl, String format, LocalDate releaseDate,
              Integer originalYear, String synopsis, String genres)

LibraryCreateDto(ManualBookDto book, LibraryStatus status, Integer rating, LocalDate acquiredAt)
LibraryItemDto(UUID id, String status, Integer rating, String review, LocalDate acquiredAt,
               String rankCode, ProgressView progress, BookView book)
LibraryPageDto(List<LibraryItemDto> items, int page, int size, long total)

ProgressView(Integer currentPage, Integer percent, LocalDate startedAt, LocalDate finishedAt)
ProgressDto(Integer currentPage, Integer percent, LibraryStatus status,
            LocalDate startedAt, LocalDate finishedAt)
ReviewDto(Integer rating, String review)
RankAssignDto(UUID categoryId)

WishlistCreateDto(ManualBookDto book, WishPriority priority, BigDecimal estimatedPrice, String note)
WishlistUpdateDto(WishPriority priority, BigDecimal estimatedPrice, String note)
WishlistAcquireDto(LibraryStatus status, Integer rating, LocalDate acquiredAt)
WishlistItemDto(UUID id, String priority, BigDecimal estimatedPrice, String note, BookView book)
WishlistPageDto(List<WishlistItemDto> items, int page, int size, long total,
                WishlistBudgetDto budget)
WishlistBudgetDto(BigDecimal total, long pricedCount, List<WishlistBudgetLineDto> byPriority)
WishlistBudgetLineDto(String priority, long count, long pricedCount, BigDecimal total)

CategoryDto(UUID id, String code, String label, String color, boolean builtin)
CategoryCreateDto(String label, String color)

GoalDto(UUID id, int year, int targetCount, String unit)
GoalUpsertDto(Integer targetCount, GoalUnit unit)

SeriesSummaryDto(UUID id, String kind, String title, String coverUrl, Integer totalVolumes,
                 String status, long ownedCount, long readCount, boolean followed)
SeriesVolumeDto(Integer volumeNumber, String title, UUID workId, UUID libraryItemId,
                boolean owned, boolean read, boolean missing, boolean upcoming)
SeriesDetailDto(UUID id, String kind, String title, String originalTitle, String coverUrl,
                String synopsis, Integer totalVolumes, String status, long ownedCount,
                long readCount, boolean followed, List<SeriesVolumeDto> volumes)
SeriesMissingDto(UUID seriesId, String title, List<Integer> volumes)

StatsDto(long read, long reading, long toRead, long pagesRead, long seriesCount,
         Integer goalTarget, long goalCurrent, List<GenreCount> byGenre)
GenreCount(String code, String genre, long count)
```

Enums: `Kind {BOOK, MANGA}` · `LibraryStatus {OWNED, READING, READ}` ·
`WishPriority {PRIORITY, SOON, SOMEDAY}` · `GoalUnit {BOOKS, VOLUMES, PAGES}` ·
`SeriesStatus {ONGOING, COMPLETED, HIATUS}`.

## Reading progress

`PUT /api/library/{id}/progress` replaces the whole position — it is a PUT, not a patch, so
a field left out is cleared. A client that only flips the status therefore hands the
position back untouched, which is what the detail screen does.

**Page and percentage are two views of the same thing.** Only one needs sending: when the
edition carries a `page_count`, the server derives the other and stores both. Page 120 of a
300-page book is 40 % on every screen, in the collection listing and in the detail
endpoint alike, because nobody but the server ever computes it. An edition with no page
count keeps whichever side was supplied and leaves the other null — an unknown total makes
the ratio meaningless, not zero.

**The status transitions fill in what the user should not have to type:**

| Transition | Effect |
|---|---|
| → `READING` | `started_at` set to today **when it is empty** |
| → `READ` | `percent` 100, `current_page` set to the page count when known, `finished_at` set to today unless one was supplied |

A date sent explicitly always wins: marking a book read on the day it was actually finished
is a normal thing to want.

`ProgressView` rides on `LibraryItemDto`, so a client never needs a second request to draw
a progress bar — the "resume reading" carousel on Home reads it off the paginated
collection.

## Rating and review

`PUT /api/library/{id}/review` takes a rating from 1 to 5 and free text, and replaces both:
sending a null rating removes it. A blank review is stored as nothing rather than as an
empty string.

Both are **strictly private**. They live on the caller's own `library_item`, are returned to
nobody else, and are never aggregated into a shared score — there is no public average and
no plan for one. Another user's identifier answers 404 like an unknown one.

## Series

A `series` row is shared catalog data, but `/api/series` is not a catalog browser: a series
is visible to a caller only once they **own a volume of it or follow it**. Anything else
answers 404, the same as an unknown identifier — a 403 would tell the caller that a series
exists in someone else's collection.

The volume list of `GET /api/series/{id}` runs from volume 1 to the furthest volume anyone
knows about: the announced `total_volumes`, the last volume present in the shared catalog,
or the last one the caller owns. Each entry carries four non-exclusive flags — a read
volume is also an owned one:

| Flag | Meaning |
|---|---|
| `owned` | the caller has this volume in their collection |
| `read` | …and it is marked `READ` |
| `missing` | not owned, and **below** the highest volume they own — a hole in the run |
| `upcoming` | not owned, and **above** it — what is still ahead of them |

`GET /api/series/{id}/missing` returns exactly the `missing` volume numbers: owning 1, 2
and 5 reports `[3, 4]`. Volumes carrying no number (a series entry recorded without one)
are appended after the numbered ones with a null `volumeNumber`; they count towards
`ownedCount` but are never reported as missing.

## Genres

A genre is a row of the shared catalog, identified by a **code** — `science-fiction`,
`shonen`, `jeunesse` — and carrying a label only for display. The code is what
`/api/library?genre=` filters on and what `GenreCount.code` returns; how a free-text wording
becomes one is described in [DATA-MODEL](DATA-MODEL.md) § 1.

`GET /api/genres` lists the genres of the **caller's own** collection with their counts,
most frequent first: it is what a filter is built from. Listing the whole `genre` table
would offer genres the user owns nothing of, and would say what other people collect.
`GET /api/stats` returns the same figures capped at the six the breakdown shows.

An item is counted once per genre it carries, so the counts add up to more than the size of
the collection. That is the point: a title tagged "Fantasy, Aventure" counts towards both,
where it used to form a third genre of its own
([#56](https://github.com/zelytra/Librarius/issues/56)).

`BookView.genres` still returns the raw wording, and no per-title list of codes is exposed:
reading them while rendering a page of the collection would cost one query per item. The
column goes away, and the codes take its place, once the front end reads them.

## Wishlist

**Ordering.** `WishPriority` carries an explicit `rank` (`PRIORITY` 0, `SOON` 1, `SOMEDAY`
2) and the default ordering maps the column to it through a `case`. Ordering on the column
itself sorted the stored *name* — `PRIORITY, SOMEDAY, SOON` — and showed the wishes with no
date attached ahead of the ones the user meant to buy next
([#114](https://github.com/zelytra/Librarius/issues/114)). A new priority is one line in the
enum: the `case` is generated from it, so the query and the enum cannot disagree.

**Budget.** `GET /api/wishlist` carries a `budget` alongside the items rather than exposing
it behind an endpoint of its own, so the figure a client shows can never contradict the
rows underneath it: one request, one set of criteria, one answer. Like `total`, it covers
the whole filtered set and is identical on every page — a client sums nothing client-side,
which would only ever describe the pages it happens to have loaded. Priorities no wish
carries are absent from `byPriority` rather than reported as zero.

## Pagination

`GET /api/library` and `GET /api/wishlist` return an envelope, never a bare array:

```json
{ "items": [ … ], "page": 0, "size": 50, "total": 412 }
```

`total` is the number of items matching the filter, all pages taken together — that is what
lets a client display a count, and decide whether there is more to fetch, without
downloading the collection.

| Parameter | Default | Applies to | Notes |
|---|---|---|---|
| `page` | `0` | both | Zero-based. Clamped to 0; a page past the end is empty with the right `total` |
| `size` | `50` | both | Clamped to 1–200. The envelope echoes the size actually applied |
| `sort` | `added` / `priority` | both | Collection: `added`, `title`, `author`, `genre`, `rating` (best first, unrated last). Wishlist: `priority` (by urgency, see [Wishlist](#wishlist)), `added`, `title`, `author`, `price`. Case-insensitive; an unknown value is a **400** |
| `kind` | — | both | `BOOK` \| `MANGA`, carried by the `work` |
| `status` | — | collection | `OWNED` \| `READING` \| `READ` |
| `rank` | — | collection | Rank category code (`or`, `argent`, `bronze` or a custom one) |
| `genre` | — | collection | Genre code, as `/api/genres` returns it. A wording is folded the same way, so `genre=Science Fiction` behaves like `genre=science-fiction`; an unknown genre matches nothing rather than being ignored |
| `minRating` | — | collection | Keeps the titles rated at least that much; "my favourites" is `4`. Outside 1–5 is a **400**, and unrated titles never match |
| `priority` | — | wishlist | `PRIORITY` \| `SOON` \| `SOMEDAY` |
| `q` | — | both | Free text, case-insensitive, matched against the title, the authors and the series. `%` and `_` typed by the user are searched literally |

Every ordering ends on the identifier, so an item never swaps pages between two requests.
Filters combine with an `and`, and all of them narrow a set already scoped to
`CurrentUser.id()`.

## Conventions

- **Adding a title**: the client sends a complete `ManualBookDto`; the server matches or
  creates the `work` + `edition`, then creates the `library_item`. The front end never has
  to handle a catalog identifier.
- **Isolation**: every resource resolves `CurrentUser.id()` and filters on it. An `id`
  belonging to another user must answer **404**, never 403 (no leaking of existence).
- **Validation**: Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Min`) on the input DTOs;
  violations surface as 400.
- **Import errors**: `ImportException` → `ImportExceptionMapper`.

## Identified gaps

| # | Gap | Target milestone |
|---|---|---|
| A1 | ✅ Pagination on `/api/library` and `/api/wishlist` (#38) | Foundations |
| A2 | No `PATCH /api/me` (display name, language) | Public product |
| A3 | No `GET /api/export` (CSV/JSON) and no `DELETE /api/me` — **GDPR requirements** | Public product |
| A4 | ✅ `/api/series` resource — details, volumes, follow (#44) | Core product |
| A5 | No `DELETE`/`PUT` on `/api/categories/{id}` | Core product |
| A6 | ✅ `PUT /api/wishlist/{id}` (edit priority/price/note) (#52) | Core product |
| A7 | ✅ One-call conversion from wish to collection (#52) | Core product |
| A8 | No `/api/dashboard/layout` | Core product |
| A9 | ✅ Server-side search and filters on the collection (#38) | Foundations |
| A10 | No rate limiting on `/api/catalog/*` | Operations |
| A11 | No time-based statistics (`/api/stats/timeline`) | Core product |
