# Data model — Librarius

Source of truth: `apps/api/src/main/resources/db/migration/`.
Hibernate runs in `validate` — the Flyway schema **is** the model.

## 1. Current schema (V1 + V2 + V3 + V4 + V5 + V6 + V7 + V8 + V9 + V10 + V11 + V12 + V13 + V14 + V15 + V16)

```text
app_user ──┬─< library_item >── edition >── work >── series >── upcoming_release
           ├─< wishlist_item >──┘             │        ▲
           ├─< reading_goal                   ├─< work_genre >── genre >─< genre_alias
           ├─< series_follow >────────────────┼────────┘
           ├─< author_follow >── author >─────┴─< work_author
           ├─< rank_category (custom)          library_item ──1:1─ reading_progress
           │        ▲                                │
           │  rank_category (built-ins, user_id NULL)┘
           └─< dashboard_layout
```

### Tables

| Table | Key | Notable columns | Constraints |
|---|---|---|---|
| `app_user` | `id VARCHAR(255)` = Keycloak `sub` | `email`, `display_name`, `locale` (defaults to `fr`), `time_zone` **nullable** (V14), `trusted BOOLEAN NOT NULL DEFAULT false` + `trusted_at TIMESTAMPTZ` **nullable** (V16) | No credential stored. `time_zone` is an IANA id (`Europe/Paris`); NULL falls back to the client zone. `trusted` is **server-computed** (V16, #180), never client input |
| `work` | `id UUID` | `kind` (BOOK\|MANGA\|COMIC\|GRAPHIC_NOVEL\|AUDIOBOOK, the last three V15/#178), `title`, `authors` (raw credit line, normalised into `work_author` in V13), `series_title`, `series_id` FK **nullable** (V4), `volume_number`, `synopsis`, `genres` (raw wording, normalised into `work_genre` in V6), `original_year`, `provider`, `provider_ref` (V12) | idx on `kind` and on `lower(title)`, `lower(authors)`, `lower(genres)` (V3), `(series_id, volume_number)` (V4). `CHECK ((provider IS NULL) = (provider_ref IS NULL))` (V12) |
| `series` | `id UUID` | `kind` (BOOK\|MANGA\|COMIC\|GRAPHIC_NOVEL\|AUDIOBOOK, the last three V15/#178), `title`, `original_title`, `total_volumes`, `status` (ONGOING\|COMPLETED\|HIATUS), `cover_url`, `synopsis`, `provider`, `provider_ref` | `UNIQUE(kind, lower(title))` — the key the import path attaches a new volume by |
| `series_follow` | `(user_id, series_id)` | `created_at` | No surrogate key: the pair is the identity, and doubles as the index |
| `edition` | `id UUID` | `work_id` FK, `isbn13`, `isbn10`, `publisher`, `language`, `page_count`, `cover_url`, `format`, `release_date`, `provider`, `provider_ref` | idx on `work_id`, `isbn13`. `CHECK ((provider IS NULL) = (provider_ref IS NULL))` (V12) |
| `library_item` | `id UUID` | `user_id` FK, `edition_id` FK, `status` (OWNED\|READING\|READ\|ABANDONED, V11), `rating`, `review TEXT` (V7), `acquired_at`, `rank_category_id` FK | `UNIQUE(user_id, edition_id)`, idx `(user_id, status)` and `(user_id, created_at DESC)` (V3), `(user_id, rating DESC NULLS LAST)` (V7) |
| `reading_progress` | `id UUID` | `library_item_id` **UNIQUE** FK, `current_page`, `percent`, `started_at`, `finished_at` | 1:1 with `library_item` |
| `wishlist_item` | `id UUID` | `user_id` FK, `edition_id` FK, `priority` (PRIORITY\|SOON\|SOMEDAY), `estimated_price NUMERIC(8,2)`, `note` | `UNIQUE(user_id, edition_id)`, idx `(user_id, priority)` and `(user_id, created_at DESC)` (V3) |

The `priority` is stored as its name, so it is **not** what the wishlist is ordered by: the
default ordering maps it to an urgency rank in the query, otherwise `SOMEDAY` sorts ahead of
`SOON` ([#114](https://github.com/zelytra/Librarius/issues/114)). The `(user_id, priority)`
index therefore serves the `priority=` filter and the budget grouping, not the sort — which
is fine: it runs on one user's wishes, a set small enough for the sort to be free. Storing
the rank in a column of its own would buy an index the ordering does not need, at the price
of a denormalised field to keep in step on every write.
| `reading_goal` | `id UUID` | `user_id` FK, `year`, `target_count`, `unit` (BOOKS\|VOLUMES\|PAGES) | `UNIQUE(user_id, year)` |
| `rank_category` | `id UUID` | `user_id` FK **nullable**, `code`, `label`, `color`, `sort_order`, `is_builtin` | `user_id NULL` = shared built-in. `UNIQUE(user_id, code)` (V9) |
| `dashboard_layout` | `user_id VARCHAR(255)` | `sections JSONB` — `[{"code": "...", "hidden": false}, …]` | No surrogate key: one layout per user, so `user_id` is the identity. No row until the first `PUT` (V10) |
| `catalog_cache` | `(provider, query_hash)` | `payload JSONB`, `fetched_at`, `expires_at` | Owned by no user: it caches provider answers, not data. idx on `expires_at` for the purge |
| `genre` | `id UUID` | `code` **UNIQUE**, `label` | `code` is the identity, `label` only what a screen shows (V6) |
| `genre_alias` | `alias VARCHAR(64)` | `code` FK → `genre(code)` | Provider wording → canonical genre. Seed data, never written at runtime (V6) |
| `work_genre` | `(work_id, genre_id)` | — | Both FKs `ON DELETE CASCADE`. idx `(genre_id, work_id)` for the reverse walk (V6) |
| `author` | `id UUID` | `name`, `name_key` **UNIQUE**, `photo_url`, `provider`, `provider_ref`, `created_at` | `name_key` (the fold of `name`) is the identity, `name` only what a screen shows. No `kind`. idx on `lower(name)` for the search of [#196](https://github.com/zelytra/Librarius/issues/196). `CHECK ((provider IS NULL) = (provider_ref IS NULL))` (V13) |
| `work_author` | `(work_id, author_id)` | — | Both FKs `ON DELETE CASCADE`. idx `(author_id, work_id)` — the bibliography walks it that way (V13) |
| `author_follow` | `(user_id, author_id)` | `created_at` | No surrogate key: the pair is the identity, exactly like `series_follow` (V13) |
| `upcoming_release` | `id UUID` | `series_id` FK, `volume_number`, `title`, `release_date`, `date_precision` (DAY\|MONTH\|QUARTER\|YEAR), `region` (FR\|JP\|EN), `publisher`, `source` (`manual`\|`catalog`\|provider name), `confidence` (CONFIRMED\|ESTIMATED) | `UNIQUE(series_id, coalesce(volume_number, -1), region)`. No `user_id`: catalog data, like `series` (V8) |

Built-ins inserted in V1: `or` (#d9b94e), `argent` (#b3b7bf), `bronze` (#c08a5a); `abandon`
(#8f8579) joins them in V11.

`V3__pagination_indexes.sql` adds no column: it only backs the server-side pagination of
`/api/library` and `/api/wishlist` with the indexes their default ordering (newest first,
per user) and their `kind` / `title` / `author` / `genre` orderings need. The free-text
search deliberately has no trigram index — it runs on a set already narrowed down to one
user's items; installing `pg_trgm` would need privileges the API role may not have.

`V4__series.sql` turns the series into a first-class object and backfills it: one `series`
row per distinct (`kind`, `series_title`) already in the catalog — folded
case-insensitively, mirroring the `toLowerCase()` deduplication the statistics used to do
— then every matching `work` is attached. `work.series_title` **stays and stays
populated**: `BookView` still exposes it and the deployed front end reads it. It is now the
denormalised label of `series.title`, and is dropped once the front end goes through the
series identifier.

`V5__catalog_cache.sql` adds the second level of the catalog cache. Caffeine stays in front
of it, but it dies with the pod and there is a deployment on every merge to `main`, so the
searches went back out to Open Library and AniList several times a day on quotas the whole
instance shares. A row holds one provider's answer to one canonical request, hashed;
`expires_at` carries the time-to-live — six hours for a search, twelve for the upcoming
releases — because the request type is inside the hash and cannot be read back from the
row. It belongs to no user and holds nothing private: it is a copy of a public catalog.

`V6__normalized_genres.sql` turns the genres into rows and backfills them from the
free-text `work.genres`. It also creates two SQL functions, `genre_parts()` and
`genre_code()`, which define what "the same genre" means:

- a value is **split** on `,` `;` `/` `|` and line breaks — never on a space, "Science
  fiction" being one genre and not two;
- each wording is **folded** into a code: ligatures expanded, accents and macrons mapped
  onto ASCII through an explicit `translate()` table, lower case, every other run of
  characters collapsed into a single hyphen, truncated to the 64 characters of the column.
  `Science-Fiction`, `science fiction` and `SCIENCE FICTION` therefore share the code
  `science-fiction`, and `Poésie` becomes `poesie`;
- what differs by more than spelling — another language, a plural, an abbreviation, a
  publishing category — goes through `genre_alias` (`shounen` → `shonen`,
  `juvenile-fiction` → `jeunesse`, `polar` → `policier`, …);
- a wording holding nothing usable once folded yields no code and is dropped, rather than
  filed under a meaningless one every such wording would share.

`unaccent` would read better than the `translate()` table, but it lives in an extension the
API role may not be allowed to install — the same reason V3 does without `pg_trgm`. The
explicit table also makes the fold independent of the database collation, where `lower()`
alone leaves accented letters untouched under a C locale.

The functions are ported verbatim into `GenreNormalizer`, which every row written after the
migration goes through, and `GenreNormalizerSqlParityTest` runs both over the same wordings
to keep them in step. The two backfill statements are **re-runnable** — both end on
`ON CONFLICT DO NOTHING` — and `GenreBackfillTest` replays them, twice, on a database seeded
with the values the providers actually return.

`work.genres` **stays and stays populated**: `BookView` exposes it and the deployed front
end shows it. It is now the denormalised label list of `work_genre`, and is dropped once the
front end reads the codes. `sort=genre` still orders on it: a work carries several genres, so
there is no such thing as "its" genre to order on.

### How a work comes to hold several editions

No migration is involved, but the rule that fills these two tables changed with
[#49](https://github.com/zelytra/Librarius/issues/49) and it is what makes the 1→N usable.
`CatalogEntryService` used to create a brand-new `work` for every entry, so a work never
held more than one `edition` and two readers of the same novel never shared a catalog row —
the opposite of what [PRODUCT](PRODUCT.md) § 3 promises. It now **matches the work** on
(`kind`, `lower(title)`, `lower(authors)`, `volume_number`) — the key the import path already
deduplicated on — and **always creates the edition**, since the publisher, the ISBN, the page
count and the format are precisely what tells two editions apart.

The lookup rides on `idx_work_title_lower` (V3), which is why it folds `lower(title)` and not
`lower(trim(title))`: an expression the index does not carry would turn every add into a
sequential scan over the catalog. A matched work is only ever **completed** with the columns
it left null (`synopsis`, `original_year`, `genres`, `series_id`, and since V12 the
`provider` / `provider_ref` pair), never overwritten: the row belongs to everyone owning the
title.

Rows created before the change keep the work they founded, duplicates included; nothing
back-fills them. The lookup returns the **oldest** match, so the editions entered from now on
gather on the first of them rather than scattering.

`V7__library_item_review.sql` adds `library_item.review`. It sits on the ownership row
rather than on the shared `work` on purpose: an opinion belongs to one user's copy of a
book, and on `work` it would have been readable by everyone owning the title — the one
thing [PRODUCT](PRODUCT.md) § 6 rule 6 forbids. The index it ships,
`(user_id, rating DESC NULLS LAST)`, backs the "my favourites" filter (`rating >= 4`) and
the ordering by rating; `NULLS LAST` matches the ordering the API applies, so unrated
titles sort after the rated ones rather than ahead of them.

`V8__upcoming_release.sql` adds the table personalised upcoming releases read from
([#57](https://github.com/zelytra/Librarius/issues/57)). It is catalog data — one row per
series and per market, never per user — filled off the request path by
`UpcomingReleaseRefresher` from two feeds (the providers behind their existing 12 h cache,
and the editions of the catalog already dated ahead) and by curated rows (`source =
manual`), which the refresher never overwrites. `release_date` is always stored on the
first day of the window `date_precision` opens, so a provider that only knows the month
never invents a day; `region` says which edition the date belongs to, the one label
`GET /api/releases/upcoming` never drops. The unique index folds a `NULL` volume number
through `coalesce(..., -1)`, since an announcement naming no volume must still collide with
a second refresh of itself, where `NULL` never equals `NULL`.

This migration was implemented ahead of the two placeholders `§ 3` had reserved for it —
the personalisation half of the planned V9 below — and takes **V8** rather than either of
those numbers, being the first version free on `main` at the time it landed. The two
planned entries were renumbered down to keep the roadmap contiguous; neither had been
implemented yet, so nothing shipped needed to move.

`V9__rank_category_unique_code.sql` makes a category name unique per user. `code` is derived
from the label and is what `/api/library?rank=` filters on, so two categories sharing one
code are two shelves a filter cannot tell apart — and nothing stopped `POST /api/categories`
from creating them, since it never looked at what the user already had. PostgreSQL treats
NULLs as distinct, so the constraint does not reach the built-ins (`user_id NULL`): those are
seed data written once by V1. What it cannot express either is a custom code shadowing a
built-in one, the two rows differing by `user_id`; `CategoryService` covers that by refusing
a label whose code is already visible to the user, built-ins included.

Rows that were already duplicated are **renamed, not deleted** — a category carries the rank
of every title filed under it, and dropping the row would unrank them for the sake of a
constraint. The rewrite suffixes the code with `~n`, a separator no generated code can
contain (they are `[a-z0-9-]`), so it can collide neither with an existing code nor with
another rewrite. `idx_rank_category_user` goes away with the same migration: the constraint's
index leads on `user_id` and covers what it was for.

`V10__dashboard_layout.sql` adds `dashboard_layout` ([#54](https://github.com/zelytra/Librarius/issues/54)): the sections the Home screen
shows, and in which order. `sections` is a JSONB array of `{"code": "...", "hidden": false}`
rather than one column per section, because the set is meant to grow — a section added
later is missing from a layout saved before it existed, and `DashboardLayoutService.get`
fills the gap in rather than the client needing to know the defaults itself. No JPA entity
maps the table: `DashboardLayoutService` reads and writes the JSONB column through plain
JDBC and a Jackson `ObjectMapper`, the same choice `V5__catalog_cache.sql` made and for the
same reason — mapping a JSONB column through Hibernate buys nothing for a table nothing
else joins against. No row exists until the first `PUT`: `GET` computes the default order
in memory when it finds none, so an account that never touches the feature costs this
table exactly one indexed lookup that finds nothing.

`V11__abandoned_status.sql` opens the fourth reading status,
[#163](https://github.com/zelytra/Librarius/issues/163), and **changes no structure**.
`library_item.status` is a bare `VARCHAR(16)` holding the name of the enum — no database
enum type, no `CHECK` constraint — so `ABANDONED` was storable before the migration existed,
and `idx_library_user_status` already covers it. What the migration does is say so: a
`COMMENT ON COLUMN` carrying the four values, because the inline comment `V1__init.sql`
wrote next to the column names three and cannot be corrected — Flyway checksums the whole
file, comments included, and that migration has run on every database.

The second half is data: a fourth built-in `rank_category`, `abandon` / *Abandon*, the shelf
a title given up on is filed under and the one the post-abandon screen
([#165](https://github.com/zelytra/Librarius/issues/165)) pre-selects. Built-in, i.e.
`user_id NULL`, for the same reason the three metals are: one shared row every account sees,
that `CategoryService` refuses to rename or delete, and that `/api/library?rank=abandon`
turns into a shelf. The identifier continues V1's series, which also makes the insert
re-runnable through its primary key — `UNIQUE(user_id, code)` from V9 never fires on these
rows, PostgreSQL treating NULLs as distinct.

**What the status does to `reading_progress` is the part worth knowing.** Abandoning stamps
`finished_at` — the day tracking stopped is as worth keeping as a finish date — and touches
neither `percent` nor `current_page`: a book put down at page 120 was read up to page 120,
and completing the position the way `READ` does would erase the one thing the status has to
record. The consequence is that `finished_at` alone **no longer means "read to the end"**,
so the four aggregations counted from it — the annual goal, the timeline buckets, the
average days per book and the author/publisher/language/rank breakdowns, all in
`ReadingProgressRepository` — filter the abandoned out through a single shared clause. Left
out, a book given up on would have advanced the goal and inflated every figure on the Stats
screen. See [API](API.md#reading-progress).

`V12__work_provider_reference.sql` gives `work` the `provider` / `provider_ref` pair
`edition` has carried since V1 and `series` since V4
([#184](https://github.com/zelytra/Librarius/issues/184)). The work is the level the
question is asked at: "the other editions of *this* title" is about the work, not about the
copy somebody owns, so without it no provider could be queried and
[API](API.md) gap A12 could not be closed.

**The two columns are one value**, and a `CHECK` on each table says so —
`(provider IS NULL) = (provider_ref IS NULL)`, the same shape as `ck_upcoming_release_dated`
(V8). A provider name with no reference resolves to nothing and a reference with no provider
belongs to no catalog, so half a pair is refused rather than stored: `provider IS NOT NULL`
can then be read as "there is a record to ask about", with no second column to re-test, and
a half-filled row cannot sit there blocking `CatalogEntryService.complete` against a later
entry that knows the whole thing.

The same migration **clears `edition.provider`** wherever it has no reference next to it.
Every edition row held `'manual'`: `CatalogEntryService` stamped it unconditionally, on
entries typed into the form *and* on titles picked straight off a live Open Library or
AniList hit, and never wrote `provider_ref` at all. The value therefore marked nothing — it
did not say the entry was manual, and it could not be resolved. It is written as "clear
every half-reference", mirroring the constraint, so it is re-runnable and true whatever a
database holds.

**Nothing is backfilled, and nothing can be.** An entry recorded which fields the user saw,
never which search result they came from; re-matching a stored title against a provider
would be a guess dressed up as data. Every work and every edition that exists today
therefore reads as "typed by hand", and only entries added from Discover after this
migration carry a reference — so an absent reference is the normal case for a long while
yet, not an exception, and [#197](https://github.com/zelytra/Librarius/issues/197) has to
treat it as such.

A second reason it stays empty longer than one would like: `OpenLibraryProvider` puts
`"openlibrary"` on its results and **no reference**, so a book added from Discover still
records neither half. The gap is in the provider — its search does not request the work key
— and closing it is a provider change, not a schema one.

Neither pair is indexed. Nothing looks a work up *by* its reference: the work is matched on
(`kind`, `lower(title)`, `lower(authors)`, `volume_number`) as it always was, and whoever
reads the reference already holds the row.

`V13__author_entities.sql` turns the authors into rows
([#182](https://github.com/zelytra/Librarius/issues/182)): `author`, `work_author` and
`author_follow`, the same three shapes the series already had. It is the third time the
project makes this move, after the series (V4) and the genres (V6), and it follows the same
plan — add the structure, backfill it from the free-text column, keep that column populated.

**What it reuses from V6, and what it deliberately does not.** Reused: the split of a
free-text list and a fold of each part into a key, through `author_parts()` and
`author_key()`, ported verbatim into `AuthorNormalizer` and kept in step by
`AuthorNormalizerSqlParityTest`. The fold is V6's — ligatures expanded, diacritics mapped
onto ASCII through an explicit `translate()` table, lower case, every other run of characters
collapsed into a single hyphen — at the width of `author.name` rather than of `genre.code`,
and with a longer character table: a genre list is written in French or English, a credit
line in every language that has a writer, and without the Latin Extended-A rows
"Stanisław Lem" and "Stanislaw Lem" would be two people. Collapsing punctuation is what earns
the fold its keep here: the catalogues disagree on the spacing of initials, and
`J.R.R. Tolkien` and `J. R. R. Tolkien` have to land on one row.

**Not reused: `genre_alias`.** That table works because the genres are a closed vocabulary of
a few dozen entries somebody can curate; the authors are not, its author counterpart would
have to hold the world's writers, and a wrong row there does not mis-file a tag — it merges
two people's bibliographies. Two spellings are therefore related **only** when they fold
alike, never by curated fiat. Which is worth stating as a list of what this cannot do, rather
than leaving it to be discovered from the data:

- `Damasio, Alain` splits on the comma into two authors — the accepted cost of splitting on
  the character the providers join with, the same trade-off V6 made for the genres.
- `A. Damasio` and `Alain Damasio` are two rows: nothing expands an initial.
- `Neil Gaiman and Terry Pratchett` is one row: `,` `;` `/` `|` `&` and line breaks separate
  names, words do not, and a rule cutting on " and " would also cut "Bell and Sons".
- Two writers sharing a name are one row. `author.provider` / `author.provider_ref` is where
  the eventual answer lands — a catalogue identifier tells namesakes apart, a spelling never
  will — and both columns are **empty today**: a name folded out of free text names no
  record, and `work.provider_ref` (V12) identifies the work, not the person who wrote it.
  Filling them is a provider change, like the rest of V12's story.

One difference from `genre_code()` in the fold itself: a name the table cannot read at all —
a script it does not cover — keeps its own trimmed text as the key instead of being dropped.
An unreadable genre is noise the statistics are better off without; this is somebody's name,
and it is the only credit that work carries. Such a key can collide with no folded one, those
being `[a-z0-9-]` and never empty by construction. It is not lower-cased either: `lower()` on
a script the table ignores is the database's collation talking, which V6 went out of its way
not to depend on — so two cases of one Cyrillic name are two rows.

The backfill is **re-runnable**, both statements ending on `ON CONFLICT DO NOTHING`, and
`AuthorBackfillTest` replays them twice on a database seeded with the values the providers
actually return. `AuthorService` is its runtime counterpart, called by `CatalogEntryService`
on every entry recorded: without it the backfill would cover the catalog as it stood on
migration day and `work_author` would stop growing the day it was created.

`work.authors` **stays and stays populated**: `BookView` exposes it, the deployed front end
and both export formats read it, and — unlike the two other denormalised labels —
`WorkRepository.findMatch` still deduplicates works on it. It is now the denormalised credit
line of `work_author`, and is dropped once the front end goes through the author identifiers
([#199](https://github.com/zelytra/Librarius/issues/199)). `sort=author` still orders on it:
a work carries several authors, so there is no such thing as "its" author to order on — the
same reasoning as `sort=genre`.

`V14__user_time_zone.sql` adds one nullable column, `app_user.time_zone`
([#75](https://github.com/zelytra/Librarius/issues/75)). It carries an IANA identifier
(`Europe/Paris`), which `PATCH /api/me` validates as a `java.time.ZoneId` before storing, and
is left NULL for everyone who never sets one — a null zone means "use the device's", the
right default, so there is nothing to backfill. It joins the `display_name` and `locale` the
row already held, completing the three editable profile fields the Settings screen now owns.

`V16__app_user_trust_flag.sql` adds the server-computed trust flag
([#180](https://github.com/zelytra/Librarius/issues/180)): `app_user.trusted BOOLEAN NOT NULL
DEFAULT false` and `app_user.trusted_at TIMESTAMPTZ` (NULL until the flag is first earned).
Trust is a private signal — "this account's catalog contributions can be trusted" — and it is
**never** set by a user, on themselves or on anyone else: only the application decides, from the
account's own activity. So it lives entirely off the request path. `TrustEvaluator` is the one
writer, run from a daily `@Scheduled` job, and **no endpoint accepts `trusted` as input** —
`PATCH /api/me` ignores a body that carries it, which a test pins down. The default is `false`
and there is nothing to backfill: an account earns the flag the first time an evaluation finds
it over the criteria, and `trusted_at` records that day.

**Why the criteria are a single method, not a column.** What counts as trustworthy is an open
product question — a blend of tenure, of how much the account has finished reading, and of a
record clean of upheld reports — meant to be retuned. It lives in `TrustEvaluator.qualifies`
behind thresholds read from `application.properties`
(`librarius.trust.min-account-months`, `librarius.trust.min-read-titles`), so retuning it moves
a number rather than touching an endpoint or the schema. The evaluation **only ever grants**:
an account that already earned the flag is left alone. Showing the flag as a badge
([#186](https://github.com/zelytra/Librarius/issues/186)) and revoking it on an upheld report
([#195](https://github.com/zelytra/Librarius/issues/195)) — which is what would feed a non-zero
`upheldReports` signal — are their own issues; the second is why the `MeDto` is deliberately
**not** extended here.

### Cascades

Every FK pointing at `app_user` is `ON DELETE CASCADE`: deleting an `app_user` wipes all of
their data — handy for GDPR account deletion.
`library_item.rank_category_id` is `ON DELETE SET NULL`. **Deleting a category detaches the
titles, it never deletes them**: a rank is a label stuck on a book, so dropping the label
cannot drop the book. `CategoryService.delete` clears the column itself before removing the
row, so the intent is readable from the code and not only from a DDL clause.

## 2. Known limits of the current model

| # | Limit | Impact |
|---|---|---|
| L1 | ✅ Lifted by V4 — `series` table, `work.series_id`, `series_follow` | — |
| L2 | ✅ Lifted by V6 — `genre`, `genre_alias`, `work_genre` | — |
| L3 | ✅ Lifted by V13 — `author`, `work_author`, `author_follow` ([#182](https://github.com/zelytra/Librarius/issues/182)) | — |
| L4 | **No reading history** | A re-read overwrites `started_at`/`finished_at` |
| L5 | ✅ Lifted by V5 — `catalog_cache` behind Caffeine | — |
| L6 | ✅ Lifted by V10 — `dashboard_layout` ([#54](https://github.com/zelytra/Librarius/issues/54)) | — |
| L7 | **No `notification_pref`** and no notification channel | No alerting possible |
| L8 | ✅ Lifted by V8 — `upcoming_release`, read through `GET /api/releases/upcoming` | — |
| L9 | ✅ Lifted by V4 — `series_follow` | — |
| L10 | **`work.series_title` still duplicates `series.title`** | Two sources of truth for one label until the front end reads `series_id`; dropped in a later migration |
| L11 | **`work.genres` still duplicates `work_genre`** | Same, for the genres: the raw wording stays until the front end reads the codes |
| L12 | **`work.authors` still duplicates `work_author`** | Same, for the authors — and one step further behind the two others: `WorkRepository.findMatch` deduplicates works on the raw credit line, so it cannot be dropped on the front end's say-so alone |
| L13 | **No author is disambiguated beyond their spelling** | Two writers of one name share a row, and an initial never resolves to the name it stands for. `author.provider_ref` is the way out and nothing fills it — see V13 above |

## 3. Planned changes

> Numbering: V8 to V16 are taken — the upcoming releases, the category constraint, the
> dashboard layout, the abandoned status, the provider reference, the author entities, the
> user time zone, the medium taxonomy (V15, #178) and the trust flag (V16, #180). The plan
> below therefore starts at **V17**. Both entries were renumbered up by one again when V16 was
> taken, as they had been at V15, V14, V13, V12 and V11; neither has been implemented, so
> nothing that shipped had to move.

### V17 — Drop the denormalised labels & reading history

`work.series_title` and `work.genres` go away as soon as the front end reads `series_id`
(#45, #46) and the genre codes:

```sql
ALTER TABLE work DROP COLUMN series_title;
ALTER TABLE work DROP COLUMN genres;
DROP INDEX idx_work_genres_lower;             -- V3, on the dropped column
```

`work.authors` is a third denormalised label and is **not** on that list yet: the front end
reading author identifiers (#199) is only half of what holds it up, `WorkRepository.findMatch`
deduplicating works on it being the other half. Dropping it means giving the work match
another key first.

A re-read stops overwriting the previous one:

```sql
CREATE TABLE reading_session (
    id              UUID PRIMARY KEY,
    library_item_id UUID NOT NULL REFERENCES library_item (id) ON DELETE CASCADE,
    started_at      DATE,
    finished_at     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### V18 — Notifications

`notification_pref (user_id PK, prefs JSONB)`, the last table this slot still reserves.
The two others it used to hold have shipped ahead of it: `upcoming_release` as V8 (#57) and
`dashboard_layout` as V10 (#54) — see § 1.

## 4. Rules for writing migrations

1. Naming `V<n>__snake_case_description.sql`, strictly increasing numbering.
2. **Never modify a migration already merged into `main`** — Flyway fails on a diverging
   checksum. Fix it with a new migration instead.
3. The data migration goes in the same migration file as the structural change.
4. Add the column as **nullable**, backfill it, then constrain it — in three steps if the
   table is large.
5. Update the Panache entity **and** this document in the same PR.
6. Check it locally: `pnpm infra:up && cd apps/api && ./mvnw quarkus:dev` must start with no
   Hibernate validation error.

> The comments inside the already-shipped migrations (`V1__init.sql`,
> `V2__progress_and_ranks.sql`) are in French and stay that way: Flyway checksums the whole
> file, comments included. New migrations are written in English.
