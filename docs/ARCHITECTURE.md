# Architecture — My Library (Librarius)

A living document describing the technical choices and the roadmap. Kept up to date as
pull requests land.

## Product vision

Multi-user personal library for **books and manga**: collection, reading tracking,
wishlist, search (title/author/date) with covers and **multiple editions**, **upcoming
releases**, statistics. French user interface, i18n-ready. The reference mockup defines
a mobile-first "paper" look (serif *Newsreader*, *DM Sans*, sage accent `#9aab92` on
cream `#f3ede3`).

## Structuring choices

| Area | Choice | Short rationale |
|---|---|---|
| Frontend | React + Vite + TS, PWA | One responsive codebase for desktop and mobile; installable; Capacitor-ready |
| Backend | Java + Quarkus | Fast startup, built-in Micrometer/Prometheus, `quarkus-oidc` |
| DB | PostgreSQL + Hibernate Panache + Flyway | Versioned migrations, per-user scoping at the repository level |
| Auth | Keycloak (OIDC) | No home-grown register/reset/refresh; SSO shared with Grafana |
| API client | orval (OpenAPI → typed hooks) | Frontend and backend types stay in sync, CI gate on the diff |
| Book catalog | Open Library (no API key) | Title/author search, covers, ISBN |
| Manga catalog | AniList + Jikan + MangaDex | Best free manga data available |
| Monorepo | pnpm (web) + Maven (api) | Coexist without friction, kept apart in CI by path filters |

## Data model (target)

Key principle: **keep the shared catalog** (`series` / `work` / `edition`) **separate
from per-user ownership** (`library_item`). Two users share the same `edition`; each has
their own `library_item`.

- `app_user` (id = Keycloak `sub`, display_name, email, locale) — provisioned JIT,
  **no credential stored**
- `series` (title, kind BOOK|MANGA, total_volumes?, status)
- `work` (title, original_title, authors, kind, series_id?, volume_number?, synopsis,
  genres[], year)
- `edition` (work_id, isbn13/10, publisher, language, pages, cover_url, format,
  release_date, provider, provider_ref) — **1 work → N editions**
- `library_item` (user_id, edition_id, acquired_at, rating, status OWNED|READING|READ,
  UNIQUE(user, edition))
- `reading_progress` (library_item_id, current_page, percent, started_at, finished_at)
- `rank_category` (user_id NULL for the built-in Gold/Silver/Bronze ranks, code, label,
  color, is_builtin) + `library_item_rank`
- `wishlist_item` (user_id, work/edition, priority, estimated_price, note)
- `reading_goal` (user_id, year, target_count, unit)
- `dashboard_layout` (user_id, sections JSONB) — reorderable/hideable home screen
- `notification_pref` (user_id, JSONB)
- `catalog_cache` (provider, query_hash, payload JSONB, fetched_at)

## External catalog

A `CatalogProvider` abstraction (search / getWork / getEditions / upcomingReleases) plus
a `CatalogAggregator` that fans out per kind, normalises results into `work`/`edition`
and de-duplicates by ISBN13, then by fuzzy title+author. Persistent cache
(`catalog_cache`, with a TTL) backed by Caffeine.

> ⚠️ **Upcoming French-language manga releases**: no reliable free API covers the
> release calendars of the French publishers (Glénat, Ki-oon, Kana, Pika). The available
> APIs (AniList/MAL/MangaDex) mostly return JP/EN dates. The MVP therefore combines
> **labelled** provider dates with a manually curated `upcoming_release` table; a
> best-effort French scraper is left for a later phase.

## CI/CD & git flow

Branches: `main` ← `feature/*`. There is no `develop` branch — pull requests target
`main`, merging deploys to staging, and production is deployed by tagging.
GitHub Actions workflows (path-filtered):

- **web**: pnpm `--frozen-lockfile` → eslint → `tsc` → vitest → `vite build`
- **api**: JDK 21 + Maven cache → `./mvnw -B verify`
- **openapi-sync** *(PR #3)*: regenerates the client, fails on a diff
- **release** *(PR #10)*: builds and pushes Docker images to GHCR (nginx web, JVM api;
  native optional)
- **codeql**: static analysis of the TypeScript and Java sources, weekly and on every
  pull request
- **audit**: `pnpm audit` on the runtime dependency tree, fails on a high or critical
  advisory

Actions are pinned by commit SHA (the tag is kept in a trailing comment): a moved tag must
not be able to change what runs. Dependency updates come from Dependabot, grouped and
weekly — see `.github/dependabot.yml`.

Every pull request must be green before it is merged.

## Monitoring ✅

`quarkus-micrometer-registry-prometheus` → `/q/metrics` (JVM, HTTP, system) plus the
business metric `librarius_catalog_search_total{kind}`. Prometheus (`:9090`) scrapes the
API; Grafana (`:3000`) is provisioned as code (Prometheus datasource and the dashboard
"Librarius — Vue d'ensemble": HTTP throughput and latency, JVM memory, catalog
searches). In dev, Prometheus scrapes the host (`host.docker.internal:8080`); in prod,
the API container.

## Pull request roadmap

1. **Foundation** — monorepo, web/api skeletons, postgres compose, minimal CI ✅
2. Backend core + auth (Flyway, Panache entities, Keycloak OIDC)
3. OpenAPI + TS client (orval)
4. Catalog providers (search / editions / releases)
5. Design system + i18n (themes, fonts, PWA, app shell)
6. Screens A — Collection, Detail
7. Screens B — Discover, Wishlist, Stats
8. Customisable home screen + Settings
9. Grafana monitoring
10. Deployment (GHCR images, prod compose)
