# Data model — Librarius

Source of truth: `apps/api/src/main/resources/db/migration/`.
Hibernate runs in `validate` — the Flyway schema **is** the model.

## 1. Current schema (V1 + V2 + V3)

```text
app_user ──┬─< library_item >── edition >── work
           ├─< wishlist_item >──┘
           ├─< reading_goal
           └─< rank_category (custom)          library_item ──1:1─ reading_progress
                    ▲                                │
              rank_category (built-ins, user_id NULL)┘
```

### Tables

| Table | Key | Notable columns | Constraints |
|---|---|---|---|
| `app_user` | `id VARCHAR(255)` = Keycloak `sub` | `email`, `display_name`, `locale` (defaults to `fr`) | No credential stored |
| `work` | `id UUID` | `kind` (BOOK\|MANGA), `title`, `authors`, `series_title`, `volume_number`, `synopsis`, `genres`, `original_year` | idx on `kind` and on `lower(title)`, `lower(authors)`, `lower(genres)` (V3) |
| `edition` | `id UUID` | `work_id` FK, `isbn13`, `isbn10`, `publisher`, `language`, `page_count`, `cover_url`, `format`, `release_date`, `provider`, `provider_ref` | idx on `work_id`, `isbn13` |
| `library_item` | `id UUID` | `user_id` FK, `edition_id` FK, `status` (OWNED\|READING\|READ), `rating`, `acquired_at`, `rank_category_id` FK | `UNIQUE(user_id, edition_id)`, idx `(user_id, status)` and `(user_id, created_at DESC)` (V3) |
| `reading_progress` | `id UUID` | `library_item_id` **UNIQUE** FK, `current_page`, `percent`, `started_at`, `finished_at` | 1:1 with `library_item` |
| `wishlist_item` | `id UUID` | `user_id` FK, `edition_id` FK, `priority` (PRIORITY\|SOON\|SOMEDAY), `estimated_price NUMERIC(8,2)`, `note` | `UNIQUE(user_id, edition_id)`, idx `(user_id, priority)` and `(user_id, created_at DESC)` (V3) |
| `reading_goal` | `id UUID` | `user_id` FK, `year`, `target_count`, `unit` (BOOKS\|VOLUMES\|PAGES) | `UNIQUE(user_id, year)` |
| `rank_category` | `id UUID` | `user_id` FK **nullable**, `code`, `label`, `color`, `sort_order`, `is_builtin` | `user_id NULL` = shared built-in |

Built-ins inserted in V1: `or` (#d9b94e), `argent` (#b3b7bf), `bronze` (#c08a5a).

`V3__pagination_indexes.sql` adds no column: it only backs the server-side pagination of
`/api/library` and `/api/wishlist` with the indexes their default ordering (newest first,
per user) and their `kind` / `title` / `author` / `genre` orderings need. The free-text
search deliberately has no trigram index — it runs on a set already narrowed down to one
user's items; installing `pg_trgm` would need privileges the API role may not have.

### Cascades

Every FK pointing at `app_user` is `ON DELETE CASCADE`: deleting an `app_user` wipes all of
their data — handy for GDPR account deletion.
`library_item.rank_category_id` is `ON DELETE SET NULL`.

## 2. Known limits of the current model

| # | Limit | Impact |
|---|---|---|
| L1 | **No `series` table** — a series is a free-text `work.series_title` | Impossible to count the volumes of a series, follow a series, or detect missing volumes. Deduplicated with `toLowerCase()` in `StatsResource` |
| L2 | **`genres` is a free-text `VARCHAR(512)`**, treated as atomic | "Fantasy, Aventure" ≠ "Fantasy" in the stats; no reliable genre filter |
| L3 | **`authors` is a string** | No author page, no grouping, no exact search by author |
| L4 | **No reading history** | A re-read overwrites `started_at`/`finished_at` |
| L5 | **No `catalog_cache`** | The Caffeine cache dies on restart → pressure on the provider quotas |
| L6 | **No `dashboard_layout`** | The Home sections are hardcoded |
| L7 | **No `notification_pref`** and no notification channel | No alerting possible |
| L8 | **No curated `upcoming_release`** | Impossible to offer French release dates |
| L9 | No `series_followed` | Upcoming releases cannot be personalised |

## 3. Planned changes

### V4 — Series (milestone "Core product")

```sql
CREATE TABLE series (
    id            UUID PRIMARY KEY,
    kind          VARCHAR(16)  NOT NULL,          -- BOOK | MANGA
    title         VARCHAR(512) NOT NULL,
    original_title VARCHAR(512),
    total_volumes INT,                            -- NULL if unknown / ongoing
    status        VARCHAR(16),                    -- ONGOING | COMPLETED | HIATUS
    cover_url     VARCHAR(1024),
    synopsis      TEXT,
    provider      VARCHAR(32),
    provider_ref  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE work ADD COLUMN series_id UUID REFERENCES series (id) ON DELETE SET NULL;
CREATE INDEX idx_work_series ON work (series_id, volume_number);

CREATE TABLE series_follow (
    user_id   VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    series_id UUID         NOT NULL REFERENCES series (id)   ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, series_id)
);
```

Data migration: create one `series` per distinct `work.series_title` (per `kind`), attach
the `work` rows, **keep `series_title`** read-only for one release so the front end does not
break, then drop it in V4.

### V5 — Normalised genres & history

```sql
CREATE TABLE genre (id UUID PRIMARY KEY, code VARCHAR(64) UNIQUE NOT NULL, label VARCHAR(64) NOT NULL);
CREATE TABLE work_genre (work_id UUID REFERENCES work(id) ON DELETE CASCADE,
                         genre_id UUID REFERENCES genre(id) ON DELETE CASCADE,
                         PRIMARY KEY (work_id, genre_id));

CREATE TABLE reading_session (
    id              UUID PRIMARY KEY,
    library_item_id UUID NOT NULL REFERENCES library_item (id) ON DELETE CASCADE,
    started_at      DATE,
    finished_at     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### V6 — Personalisation & notifications

`dashboard_layout (user_id PK, sections JSONB)`,
`notification_pref (user_id PK, prefs JSONB)`,
`upcoming_release (id, series_id, volume_number, release_date, region, publisher, source)`.

### V7 — Persistent catalog cache

`catalog_cache (provider, query_hash, payload JSONB, fetched_at, PRIMARY KEY (provider, query_hash))`.

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
