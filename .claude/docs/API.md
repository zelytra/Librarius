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
| DELETE | `/api/me` | Deletes the account and everything in it (`AccountDeletionDto`) — see [Account deletion](#account-deletion) |
| GET | `/api/export?format=csv\|json` | Everything the caller entered — see [Export](#export) |
| GET | `/api/export/{jobId}` | A deferred export: the file, or 202 while it is being built |
| GET | `/api/catalog/search?q=&author=&year=&language=&publisher=&isbn=&kind=&limit=` | External catalog search — see [Catalog search](#catalog-search). `kind` defaults to `BOOK`, `limit` clamped to 1–40 |
| GET | `/api/catalog/upcoming?kind=&limit=` | Generic provider trends, same answer to every caller. `kind` defaults to `MANGA`, `limit` clamped to 1–50. No longer read by the Home screen — see `/api/releases/upcoming` |
| GET | `/api/releases/upcoming?kind=&limit=` | Personalised upcoming releases — see [Upcoming releases](#upcoming-releases). `kind` unrestricted by default, `limit` clamped to 1–50 |
| GET | `/api/library?page=&size=&sort=&kind=&status=&rank=&genre=&minRating=&q=` | One page of the owned titles (`LibraryPageDto`) — see [Pagination](#pagination) |
| GET | `/api/library/{id}` | A single owned title (`LibraryItemDto`), 404 if it is not the caller's |
| POST | `/api/library` | Adds a title (`LibraryCreateDto`) — creates `work`+`edition` if needed |
| PUT | `/api/library/{id}/rank` | Assigns/removes a rank (`RankAssignDto`, a null `categoryId` removes it) |
| PUT | `/api/library/{id}/progress` | Status and reading progress (`ProgressDto`) — 204, see [Reading progress](#reading-progress) |
| PUT | `/api/library/{id}/review` | Private rating and review (`ReviewDto`) — returns the updated `LibraryItemDto` |
| PUT | `/api/library/{id}/edition` | "This is the edition I own" (`EditionSwitchDto`) — see [Editions](#editions) |
| DELETE | `/api/library/{id}` | Removes a title from the collection |
| GET | `/api/works/{id}/editions` | Known editions of a work (`EditionDto`), the caller's own flagged `owned` |
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
| GET | `/api/stats/timeline?from=&to=&granularity=` | Reading over time (`TimelineDto`) — see [Timeline](#timeline) |
| POST | `/api/import/{source}` | Import by handle (`booknode`, `babelio`) — `{ "handle": "…" }` |
| POST | `/api/import/csv` | CSV import (the body is the raw content) |
| POST | `/api/import/json` | Restores a JSON export (`ExportDto`) — see [Export](#export) |

Outside `/api`: `/q/health` and `/q/metrics`, **cluster-internal only** — the ingress does not route `/q`. Swagger UI is served in dev and test, and absent from the production build.

## Main DTOs

```java
MeDto(String id, String email, String displayName, String locale)

BookView(/* read projection of an edition and its work; carries editionId and workId */)

EditionDto(UUID id, String isbn13, String publisher, String language, Integer pageCount,
           String format, LocalDate releaseDate, String coverUrl, boolean owned)
EditionSwitchDto(UUID editionId)

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

ExportDto(int schemaVersion, OffsetDateTime exportedAt, ExportUserDto user,
          List<ExportCategoryDto> categories, List<ExportGoalDto> goals,
          List<ExportCollectionItemDto> collection, List<ExportWishDto> wishlist,
          List<ExportSeriesFollowDto> followedSeries)
ExportCollectionItemDto(ManualBookDto book, LibraryStatus status, Integer rating,
                        String review, LocalDate acquiredAt, String rankCode,
                        ExportProgressDto progress)
ExportWishDto(ManualBookDto book, WishPriority priority, BigDecimal estimatedPrice, String note)
ExportProgressDto(Integer currentPage, Integer percent, LocalDate startedAt, LocalDate finishedAt)
ExportCategoryDto(String code, String label, String color, int sortOrder)
ExportGoalDto(int year, int targetCount, GoalUnit unit)
ExportSeriesFollowDto(Kind kind, String title)
ExportJobDto(UUID id, String status, String format, int rows, OffsetDateTime createdAt)

AccountDeletionDto(int libraryItems, int wishlistItems, int goals, int categories,
                   int seriesFollows)

SeriesSummaryDto(UUID id, String kind, String title, String coverUrl, Integer totalVolumes,
                 String status, long ownedCount, long readCount, boolean followed)
SeriesVolumeDto(Integer volumeNumber, String title, UUID workId, UUID libraryItemId,
                boolean owned, boolean read, boolean missing, boolean upcoming)
SeriesDetailDto(UUID id, String kind, String title, String originalTitle, String coverUrl,
                String synopsis, Integer totalVolumes, String status, long ownedCount,
                long readCount, boolean followed, List<SeriesVolumeDto> volumes)
SeriesMissingDto(UUID seriesId, String title, List<Integer> volumes)

UpcomingReleaseDto(UUID id, UUID seriesId, String seriesTitle, String kind, String coverUrl,
                   Integer volumeNumber, String title, LocalDate releaseDate,
                   String datePrecision, String region, String publisher, String source,
                   String confidence)

StatsDto(long read, long reading, long toRead, long pagesRead, long seriesCount,
         Integer goalTarget, String goalUnit, long goalCurrent, List<GenreCount> byGenre)
GenreCount(String code, String genre, long count)

TimelineDto(LocalDate from, LocalDate to, String granularity, List<TimelinePointDto> points,
            long books, long pages, double pagesPerDay, Double daysPerBook,
            String bestPeriod, long bestPeriodBooks,
            List<BreakdownCountDto> byAuthor, byPublisher, byLanguage, byRank)
TimelinePointDto(String period, long books, long pages)
BreakdownCountDto(String label, long count)
```

Enums: `Kind {BOOK, MANGA}` · `LibraryStatus {OWNED, READING, READ}` ·
`WishPriority {PRIORITY, SOON, SOMEDAY}` · `GoalUnit {BOOKS, VOLUMES, PAGES}` ·
`SeriesStatus {ONGOING, COMPLETED, HIATUS}` · `DatePrecision {DAY, MONTH, QUARTER, YEAR}` ·
`ReleaseRegion {FR, JP, EN}` · `ReleaseConfidence {CONFIRMED, ESTIMATED}`.

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

## Editions

A `work` is the intellectual content, an `edition` one materialisation of it — publisher,
ISBN, language, page count, format. Both are shared catalog data; the `library_item`
pointing at an edition is not.

**A work only holds several editions because entries are deduplicated.** `POST /api/library`
and `POST /api/wishlist` match the work before creating it — kind, title, authors and volume
number, folded to lower case, the key the import path already used — and always create the
edition, since the publisher and the ISBN are exactly what tells two editions apart. Without
that matching every entry founded a work of its own, and no work ever held more than one
edition. A matched work is only ever **completed** with the fields it lacks: it is shared, so
a thin entry never overwrites a synopsis somebody else supplied.

**Visibility.** `GET /api/works/{id}/editions` opens on a work the caller owns an edition of,
and answers 404 on anything else — the same answer an unknown identifier gets, exactly like
[Series](#series). Each entry carries `owned`, which describes the caller's own collection
and nobody else's: two readers of the same title see the same editions and different flags.

**Switching.** `PUT /api/library/{id}/edition` moves the ownership row onto another edition
of the same work.

| Case | Answer |
|---|---|
| The edition already in force | 200, unchanged — a double click is not an error |
| Another edition of the same work | 200 with the updated `LibraryItemDto` |
| Someone else's item, or an unknown one | **404** |
| Unknown edition, or an edition of another work | **400** |
| An edition already in the caller's collection | **409** with a `message` — `UNIQUE(user_id, edition_id)` |

**What the switch keeps.** Status, rating, review, rank, acquisition date and the reading
dates are left untouched: they describe the reader, not the object. Buying the hardcover
does not un-read a book.

**What it recomputes.** The reading position, and only it. A percentage means the same thing
in every printing; a page number does not — page 150 of a 300-page paperback is nowhere near
page 150 of a 600-page hardcover. The position is therefore carried over as its **percentage**
and the page is re-derived from the page count of the new edition, the same conversion
[Reading progress](#reading-progress) applies on every save. An edition with no page count
leaves the page empty rather than keeping a page number that now means nothing. The one
exception: when no percentage was ever recorded — a raw page typed on an edition that carried
no page count either — the page is left alone, being the only thing the reader ever supplied.

## Catalog search

`GET /api/catalog/search` takes six criteria, all optional and all combining: `q` (free
text), `author`, `year`, `language`, `publisher` and `isbn`. A call with none of them
answers an empty list without charging the rate limit — an empty field must not cost a
provider call.

Each provider honours what its own API indexes, and ignores the rest rather than answering
nothing:

| Criterion | Open Library (`BOOK`) | AniList (`MANGA`) |
|---|---|---|
| `q` | ✅ free text | ✅ `media(search:)` |
| `author` | ✅ `author:` | ✅ resolved through `Staff(search:)`, then that person's works |
| `year` | ✅ `first_publish_year:` | ✅ `startDate` window |
| `publisher` | ✅ `publisher:` | ❌ AniList describes works, not editions |
| `language` | ✅ `language:`, ISO 639-1 mapped to the MARC code (`fr` → `fre`) | ❌ |
| `isbn` | ✅ `isbn:` | ❌ |

A manga search carrying only criteria AniList cannot honour returns nothing, rather than
the most popular mangas — an answer that looks like a result and is one by accident.

`language` is given as an **ISO 639-1** code, which is what a client holds; the mapping to
whatever a provider indexes belongs to the provider.

Every criterion takes part in the cache key, so two searches differing by a single field
are never served the same answer.

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

## Upcoming releases

`GET /api/releases/upcoming` is the personalised counterpart to `GET /api/catalog/upcoming`:
where that one answers the same provider trends to every caller, this one reads
`upcoming_release` ([DATA-MODEL](DATA-MODEL.md) § 1) through the caller's own **stake** —
the series they own a volume of, have a wish on, or follow — and returns nothing about any
other run. A caller with no stake in anything gets an empty list, the ordinary answer for a
new account rather than a failure.

An announcement already behind the caller — a volume they already own — is dropped; one
still ahead but with no date at all is always kept, being exactly a volume known to be
coming and not known to be out yet. No provider is called on the request path: the table is
filled once a day by `UpcomingReleaseRefresher`, off the request path, so displaying the
section costs one query rather than the Open Library / AniList quota the whole instance
shares.

**Never show a date without what qualifies it.** `region` (`FR`\|`JP`\|`EN`) says which
edition it belongs to — a JP date shown unlabelled is the exact confusion this endpoint
exists to remove — `datePrecision` says how much of `releaseDate` is real (a provider that
only knows the month must not be shown a day it never announced), and `source`/`confidence`
say where the date came from and how firm it is. A curated row (`source = manual`) always
wins over a provider guess, and the refresher never touches one.

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

## Reading goal

`GET /api/stats` carries the goal set for the **current calendar year**: `goalTarget`,
`goalUnit` and `goalCurrent`, the last one counted in that unit. Both `goalTarget` and
`goalUnit` are null when no goal is set for the year — the client shows an invitation
rather than a gauge at zero.

| Unit | What `goalCurrent` counts |
|---|---|
| `BOOKS` | every title finished during the year |
| `VOLUMES` | the same thing — see below |
| `PAGES` | the pages of the titles finished, editions with no page count contributing nothing |

`BOOKS` and `VOLUMES` **measure the same figure** and differ in wording only: a volume of a
manga is a `work` of its own in this model, so a title read is a title read either way.
Counting only the titles carrying a `volume_number` under `VOLUMES` was tried and dropped —
it made a "50 volumes" goal quietly ignore every novel read towards it, which is the kind of
silent subtraction a user has no way to notice.

**"Finished" means `reading_progress.finished_at`**, which
`PUT /api/library/{id}/progress` stamps when the status becomes `READ` — the business rule
[PRODUCT](PRODUCT.md) § 6.2 describes. Moving to `READING` stamps `started_at` if it is
empty and **clears** `finished_at`: a title being read again is not a finished one, and it
must stop counting towards the year it was first finished in.

A title added straight in the `READ` state — an import, or a manual add — carries no reading
date: the application does not know when it was read. It counts in the all-time counters and
not towards the year's goal, which is the honest answer and keeps an import from spiking the
day it ran.

## Timeline

`GET /api/stats/timeline` reads the same `finished_at` the goal is counted from, over a
window the caller chooses.

| Parameter | Default | Notes |
|---|---|---|
| `from` | 1 January of the current year | ISO `yyyy-MM-dd`; an unparseable value is a **400** |
| `to` | 31 December of the current year | Inclusive. A window ending before it starts is a **400** |
| `granularity` | `month` | `month` \| `year`, case-insensitive; anything else is a **400** |

**Only the buckets holding something come back.** A month with no reading is an absent
point, not a zero: the answer then follows the data rather than the range asked for, and a
client charting a full year pads the gaps itself. `points[].period` is `2026-03` at month
granularity and `2026` at year granularity.

The derived figures ride along rather than living behind endpoints of their own, so a
screen can never show a pace that contradicts the buckets above it: `pagesPerDay` divides
the pages by the **elapsed** part of the window (a year in progress is not twelve months of
reading), `daysPerBook` averages `finished_at - started_at` over the titles carrying both
dates and is `null` when none does, and `bestPeriod` is the bucket with the most titles.

`byAuthor`, `byPublisher`, `byLanguage` and `byRank` rank the six most represented labels
of the window, ties broken alphabetically — same shape as the all-time `byGenre` of
`/api/stats`. The rank breakdown only covers the titles filed under one.

Every aggregation is a `group by` in the database. The endpoint costs six queries whatever
the length of the reading history behind it, which is what
[#40](https://github.com/zelytra/Librarius/issues/40) bought and what
`ReadingTimelineTest` keeps.

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

## Export

`GET /api/export?format=csv|json` hands the caller everything they entered — right to
portability, GDPR art. 20. `format` defaults to `json`; anything else than the two values is
a **400**. Both answers carry a `Content-Disposition: attachment` and a dated filename.

**JSON** is the complete archive and the only one that round-trips. `ExportDto` carries the
profile, the custom categories, the goals, the collection (status, **rating and private
review**, acquisition date, rank, reading position with its start and finish dates), the
wishlist (priority, estimate, note) and the followed series — every table holding a
`user_id`, plus `reading_progress`, which hangs off `library_item`. What it does **not**
carry is the row-creation timestamps: they are server metadata rather than something the
user provided, and an import cannot restore them.
It holds **no database identifier**: a book is described exactly as a user would type it in,
which is what makes `POST /api/import/json` able to recreate it in an account — or an
instance — where none of those rows exist. Ordering is by title rather than by date added,
for the same reason: an export carries no `created_at`, so ordering on it would make the
same library serialise differently after a round trip. Two exports of an unchanged account
are therefore identical but for `exportedAt`, which is what `ExportRoundTripTest` asserts.

The restore is **additive**: nothing is ever deleted, and an entry already present is
skipped rather than duplicated, so importing the same file twice is harmless. What counts
as "already present" identifies an **edition**, not a work — title, authors, volume number,
ISBN, publisher and format. A work can be owned in several editions (#152), and a key
stopping at the title would bring one of them back and swallow the rest. That is also why
the export ordering carries on into the ISBN and the publisher: two rows tied on the title
would otherwise be separated by a generated identifier, which no restore can reproduce.

**CSV** is the flat book list, collection and wishlist together, in the vocabulary of the
other reading trackers: `Title`, `Author`, `ISBN13`, `My Rating`, `My Review`,
`Exclusive Shelf` (`read` / `currently-reading` / `to-read`), `Bookshelves`
(`collection` / `wishlist`), and what has no counterpart elsewhere under a `Librarius`
prefix. The progress percentage is derived from the page number when the reader entered
one, since a receiving tool cannot work it out from a page alone. UTF-8 **with a byte-order
mark**, `;` separator, CRLF, RFC 4180 quoting — the combination Excel opens correctly under
a French locale, and the separator this API's own CSV importer already prefers. Goals and
categories are **not** in the CSV: they are not properties of a book, and a flat file that
changes shape halfway down stops being openable in a spreadsheet.

**Deferred generation.** Past `librarius.export.async-threshold` titles (collection plus
wishlist, 2000 by default) the request answers **202** with an `ExportJobDto`
(`id`, `status`, `format`, `rows`) and a `Location`; the file is built on a small pool of its
own and fetched from `GET /api/export/{jobId}`, which answers 202 while it is pending, the
file once it is ready, and **404** for a job belonging to somebody else or one that never
existed. Jobs live in memory, expire after fifteen minutes and die with the pod: an export
is a copy of data still in the database, so losing one costs a click — where persisting it
would keep a second copy of a library outside the tables the account deletion promises to
erase.

## Account deletion

`DELETE /api/me` erases the caller and everything they own — right to erasure, GDPR art. 17.
No identifier is passed and none is accepted: a caller can only ever delete themselves,
which is the one shape of this endpoint that cannot be pointed at anybody else.

Two systems have to forget the user, and **the order is the point**:

1. **Keycloak first**, through its admin API. If it refuses, or is not configured, the call
   answers **503** with a reason and **nothing is erased**. The other ordering loses data on
   the more likely failure: a library erased while the login survives gives the user a fresh
   empty account on their next sign-in, indistinguishable from having lost everything.
2. **The database second**, as a single `DELETE` on `app_user`; the schema cascades from
   there ([DATA-MODEL](DATA-MODEL.md) § Cascades), `reading_progress` included.

The **shared catalog is untouched** — `work`, `edition`, `series`, `genre` describe books,
not people, and every other collection is built on the same rows.

The deletion is logged at `INFO` with the technical subject, the instant and the counters,
and **no personal data**: no email, no display name, no title. The configuration the
maintainer has to provide, and how long the encrypted backups keep the data afterwards, are
in [docs/DEPLOYMENT.md](../../docs/DEPLOYMENT.md) § "Account deletion".

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
| A3 | ✅ `GET /api/export` (CSV/JSON) and `DELETE /api/me` (#72, #73) | Public product |
| A4 | ✅ `/api/series` resource — details, volumes, follow (#44) | Core product |
| A5 | No `DELETE`/`PUT` on `/api/categories/{id}` | Core product |
| A6 | ✅ `PUT /api/wishlist/{id}` (edit priority/price/note) (#52) | Core product |
| A7 | ✅ One-call conversion from wish to collection (#52) | Core product |
| A8 | No `/api/dashboard/layout` | Core product |
| A9 | ✅ Server-side search and filters on the collection (#38) | Foundations |
| A10 | No rate limiting on `/api/catalog/*` | Operations |
| A11 | ✅ Time-based statistics (`/api/stats/timeline`) (#55) | Core product |
| A12 | No **provider enrichment** of the editions of a work: the list holds what users entered. `work` carries no `provider_ref`, so no provider can be asked "the other editions of *this* work" | Core product |
