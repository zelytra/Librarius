# Data model — Librarius

Source of truth: `apps/api/src/main/resources/db/migration/`.
Hibernate runs in `validate` — the Flyway schema **is** the model.

## 1. Current schema (V1 + V2 + V3 + V4 + V5 + V6 + V7)

```text
app_user ──┬─< library_item >── edition >── work >── series
           ├─< wishlist_item >──┘             │        ▲
           ├─< reading_goal                   └─< work_genre >── genre >─< genre_alias
           ├─< series_follow >─────────────────────────┘
           └─< rank_category (custom)          library_item ──1:1─ reading_progress
                    ▲                                │
              rank_category (built-ins, user_id NULL)┘
```

### Tables

| Table | Key | Notable columns | Constraints |
|---|---|---|---|
| `app_user` | `id VARCHAR(255)` = Keycloak `sub` | `email`, `display_name`, `locale` (defaults to `fr`) | No credential stored |
| `work` | `id UUID` | `kind` (BOOK\|MANGA), `title`, `authors`, `series_title`, `series_id` FK **nullable** (V4), `volume_number`, `synopsis`, `genres` (raw wording, normalised into `work_genre` in V6), `original_year` | idx on `kind` and on `lower(title)`, `lower(authors)`, `lower(genres)` (V3), `(series_id, volume_number)` (V4) |
| `series` | `id UUID` | `kind` (BOOK\|MANGA), `title`, `original_title`, `total_volumes`, `status` (ONGOING\|COMPLETED\|HIATUS), `cover_url`, `synopsis`, `provider`, `provider_ref` | `UNIQUE(kind, lower(title))` — the key the import path attaches a new volume by |
| `series_follow` | `(user_id, series_id)` | `created_at` | No surrogate key: the pair is the identity, and doubles as the index |
| `edition` | `id UUID` | `work_id` FK, `isbn13`, `isbn10`, `publisher`, `language`, `page_count`, `cover_url`, `format`, `release_date`, `provider`, `provider_ref` | idx on `work_id`, `isbn13` |
| `library_item` | `id UUID` | `user_id` FK, `edition_id` FK, `status` (OWNED\|READING\|READ), `rating`, `review TEXT` (V7), `acquired_at`, `rank_category_id` FK | `UNIQUE(user_id, edition_id)`, idx `(user_id, status)` and `(user_id, created_at DESC)` (V3), `(user_id, rating DESC NULLS LAST)` (V7) |
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
| `rank_category` | `id UUID` | `user_id` FK **nullable**, `code`, `label`, `color`, `sort_order`, `is_builtin` | `user_id NULL` = shared built-in |
| `catalog_cache` | `(provider, query_hash)` | `payload JSONB`, `fetched_at`, `expires_at` | Owned by no user: it caches provider answers, not data. idx on `expires_at` for the purge |
| `genre` | `id UUID` | `code` **UNIQUE**, `label` | `code` is the identity, `label` only what a screen shows (V6) |
| `genre_alias` | `alias VARCHAR(64)` | `code` FK → `genre(code)` | Provider wording → canonical genre. Seed data, never written at runtime (V6) |
| `work_genre` | `(work_id, genre_id)` | — | Both FKs `ON DELETE CASCADE`. idx `(genre_id, work_id)` for the reverse walk (V6) |

Built-ins inserted in V1: `or` (#d9b94e), `argent` (#b3b7bf), `bronze` (#c08a5a).

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
it left null (`synopsis`, `original_year`, `genres`, `series_id`), never overwritten: the row
belongs to everyone owning the title.

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

### Cascades

Every FK pointing at `app_user` is `ON DELETE CASCADE`: deleting an `app_user` wipes all of
their data — handy for GDPR account deletion.
`library_item.rank_category_id` is `ON DELETE SET NULL`.

## 2. Known limits of the current model

| # | Limit | Impact |
|---|---|---|
| L1 | ✅ Lifted by V4 — `series` table, `work.series_id`, `series_follow` | — |
| L2 | ✅ Lifted by V6 — `genre`, `genre_alias`, `work_genre` | — |
| L3 | **`authors` is a string** | No author page, no grouping, no exact search by author |
| L4 | **No reading history** | A re-read overwrites `started_at`/`finished_at` |
| L5 | ✅ Lifted by V5 — `catalog_cache` behind Caffeine | — |
| L6 | **No `dashboard_layout`** | The Home sections are hardcoded |
| L7 | **No `notification_pref`** and no notification channel | No alerting possible |
| L8 | **No curated `upcoming_release`** | Impossible to offer French release dates |
| L9 | ✅ Lifted by V4 — `series_follow` | — |
| L10 | **`work.series_title` still duplicates `series.title`** | Two sources of truth for one label until the front end reads `series_id`; dropped in a later migration |
| L11 | **`work.genres` still duplicates `work_genre`** | Same, for the genres: the raw wording stays until the front end reads the codes |

## 3. Planned changes

### V8 — Drop the denormalised labels & reading history

`work.series_title` and `work.genres` go away as soon as the front end reads `series_id`
(#45, #46) and the genre codes:

```sql
ALTER TABLE work DROP COLUMN series_title;
ALTER TABLE work DROP COLUMN genres;
DROP INDEX idx_work_genres_lower;             -- V3, on the dropped column
```

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

### V9 — Personalisation & notifications

`dashboard_layout (user_id PK, sections JSONB)`,
`notification_pref (user_id PK, prefs JSONB)`,
`upcoming_release (id, series_id, volume_number, release_date, region, publisher, source)`.

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
