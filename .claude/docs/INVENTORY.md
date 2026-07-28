# Inventory — repository audit

> Audit carried out on **2026-07-28** against `af55df2`. It describes the code
> **as it is**, not as it ought to be. To be revised at the end of every milestone.

## Overview

The project is a **complete and deployed** skeleton: all 7 screens exist, the API exposes
9 resources, OIDC auth works end to end, and CI/CD ships to k3s. What is missing is
**functional depth** (series, fine-grained progress, French release dates), **quality**
(incomplete i18n) and **operations** (credentials still readable in the git history;
backups and alert rules now exist but neither has been proven on the cluster).

> **Environment**: `librarius.zelytra.fr` is a **staging** environment, not production.
> No production environment is open to date. That lowers the immediate criticality of the
> operational items below — without cancelling them: the repository is public and the
> instance is reachable from the Internet with open sign-up. Every one of those items
> becomes blocking again when production opens (v1.0 milestone).

## What works ✅

| Area | State |
|---|---|
| Monorepo | pnpm workspaces (`apps/web`) + Maven (`apps/api`), Node 20 / pnpm 9.15.9 / JDK 21 |
| Auth | Keycloak OIDC end to end — Dev Services in tests, realm imported in dev, `/auth` ingress in staging |
| Persistence | PostgreSQL + Panache + Flyway (2 migrations), Hibernate in `validate` mode |
| Catalog | `CatalogService` aggregates Open Library (books) and AniList (manga), two-level cache Caffeine → `catalog_cache` (6 h / 12 h). Search on text, author, year, language, publisher or ISBN, each provider honouring what it indexes ([API](API.md#catalog-search)) |
| Import | Booknode (scraping) + CSV, exposed in Settings |
| API contract | OpenAPI generated at build time → orval TS client, `openapi-sync` CI gate |
| Screens | Home, Collection, Series, Detail, Discover, Wishlist, Stats, Settings — **all wired to the live API** |
| PWA | `vite-plugin-pwa`, icons, `/auth` `/api` `/q` excluded from the navigation fallback |
| Monitoring | Micrometer → `/q/metrics`, Prometheus + Grafana provisioned, "Overview" dashboard, 10 alert rules with runbooks (`infra/prometheus/rules/`) — **not evaluated on the cluster**, see debt #15 |
| Backups | Helm CronJob: daily `pg_dump` → gzip → AES-256 → S3-compatible bucket, 7/4/6 retention. **Off by default**, restore procedure documented but never run, see debt #14 |
| CI/CD | Path-filtered workflows (lint, tests, images, docs) plus CodeQL and a dependency audit; push to `main` → build GHCR images + `helm upgrade` |

## Technical debt identified 🔧

### Front-end — critical

1. ~~**Inline styles everywhere.**~~ ✅ **Cleared on 2026-07-28**
   ([#32](https://github.com/zelytra/Librarius/issues/32)): one `.module.css` per screen and
   per shared component, all backed by `shared/styles/tokens.css`, which gained a type
   scale, radii, shadows, screen padding and the decorative tints. No hex colour, font or
   font size is hardcoded under `features/` any more; `style={{…}}` is left only where the
   value is computed at render time. The dark theme followed
   ([#42](https://github.com/zelytra/Librarius/issues/42), below).
2. **No shared server state.** Every page redoes its own `fetch` inside a `useEffect` with
   `// eslint-disable-next-line react-hooks/exhaustive-deps`, with no cache, no
   invalidation, no retry. Opening Detail from Home reloads the whole library
   (`getApiLibrary` then a client-side `.find()`).
3. **Inconsistent error and loading handling.** `DiscoverPage` handles both errors and the
   empty state; `HomePage`, `CollectionPage` and `StatsPage` swallow failures silently.
4. **Incomplete i18n.** A single file (`fr.json`, 66 lines) while half the labels are
   hardcoded in the JSX ("Reprendre la lecture", "Classement", "Marquer comme lu",
   "Titre introuvable"…). No other language.
5. ~~**Tests almost non-existent.**~~ ✅ **Cleared on 2026-07-28** ([#36](https://github.com/zelytra/Librarius/issues/36)):
   74 tests across 9 files cover the seven application screens through MSW — nominal render,
   empty state, error state, missing session and the main interactions.
   Completed on 2026-07-28 by the Playwright suite
   ([#37](https://github.com/zelytra/Librarius/issues/37)): five journeys — Discover,
   Detail, wishlist, statistics, CSV import — run in `e2e/` against the full stack
   (PostgreSQL, Keycloak, API, web image) on every pull request.
6. ~~**`mockData.ts` still imported**~~ ✅ **Cleared on 2026-07-28**
   ([#34](https://github.com/zelytra/Librarius/issues/34)): `RANK_COLORS`/`RANK_ICONS` moved
   to `shared/ui/ranks.ts` and the file was deleted. The cover palette now has one
   definition (`shared/ui/coverPalette.ts`), so a title keeps the same colour on every
   screen.

### Back-end — moderate

7. ~~**No `series` table**~~ ✅ **Resolved on 2026-07-28**
   ([#43](https://github.com/zelytra/Librarius/issues/43),
   [#44](https://github.com/zelytra/Librarius/issues/44)): `V4__series.sql` adds `series`,
   `series_follow` and `work.series_id`, backfilled from the existing `series_title` values,
   and `/api/series` exposes the counters, the missing volumes and the follow.
   Left behind: `StatsResource.seriesCount` still counts distinct lower-cased titles rather
   than joining `series`; `work.series_title` still duplicates `series.title`; and
   `BookView` exposes `seriesTitle` without a `seriesId`, so the Detail screen resolves the
   link to a series by matching kind and title against `/api/series`
   ([#45](https://github.com/zelytra/Librarius/issues/45)).
8. ~~**`genres` is a `VARCHAR(512)`** treated as an atomic value in the stats~~
   ✅ **Resolved on 2026-07-28** ([#56](https://github.com/zelytra/Librarius/issues/56)):
   `V6__normalized_genres.sql` adds `genre`, `genre_alias` and `work_genre`, backfilled by
   splitting and folding the existing free-text values; the breakdown groups on the codes
   and `/api/library?genre=` filters on them. Left behind: `work.genres` still carries the
   raw wording for the front end, which shows it and does not yet offer the filter, and
   `sort=genre` still orders on that raw value.
9. ~~**Statistics computed in memory**~~ ✅ **Resolved on 2026-07-28**
   ([#40](https://github.com/zelytra/Librarius/issues/40)): three aggregate queries in
   `LibraryItemRepository` replace the in-memory fold, and the query count no longer
   depends on the size of the collection. One behaviour change: genres with equal counts
   are now ordered alphabetically rather than by insertion order, the old tie-break
   following a listing order that SQL cannot reproduce.
10. **No pagination** on `GET /api/library` or `GET /api/wishlist`.
11. **Tables planned but never created**: `dashboard_layout`,
    `notification_pref`, `upcoming_release`, `library_item_rank` (the rank is a column, not
    a table — an acceptable simplification, worth documenting).
12. ~~**`HelloResource` is unauthenticated**~~ ✅ **Resolved on 2026-07-28** ([#41](https://github.com/zelytra/Librarius/issues/41)): the demo endpoint is gone, every resource is now authenticated.

### Defects fixed since the audit ✅

- **Dark theme unreadable, no system preference**
  ([#42](https://github.com/zelytra/Librarius/issues/42), 2026-07-28): the palette blocks
  described the light themes only, so `nuit` inherited pale tints and invisible shadows.
  The dark palette now redeclares the whole set, the secondary text tokens were retuned to
  clear 4.5:1 on every palette, a **Système** theme follows `prefers-color-scheme`, and a
  boot script in `index.html` applies the palette before the first paint. Two carry-overs
  from #32 went with it: the Collection rank medal was clipped by its cover, and Detail's
  back button sat on a fixed white panel.
- **Data isolation untested** ([#39](https://github.com/zelytra/Librarius/issues/39),
  2026-07-28): every user-scoped resource is now exercised with two accounts, including
  the fact that someone else's identifier must answer 404 and not 403.
- **Reading goal creation broken** ([#88](https://github.com/zelytra/Librarius/issues/88),
  2026-07-28): `GoalResource.upsert` persisted the entity before setting `target_count`, a
  NOT NULL column — `PUT /api/goals/{year}` returned 500 for a year with no goal yet. Never
  caught, for lack of a test and for lack of a screen exposing the feature. Surfaced by the
  isolation tests.
- **Wishlist sorted alphabetically instead of by urgency**
  ([#114](https://github.com/zelytra/Librarius/issues/114), 2026-07-28): the priority is
  stored as its name, so `order by priority` yielded `PRIORITY, SOMEDAY, SOON` and put the
  wishes with no date attached ahead of the next purchases. The ordering now maps the
  column to `WishPriority.rank`. Spotted while adding pagination (#38) and kept as its own
  issue rather than changed silently under an unrelated title.

### Operations

*Criticality assessed for a staging environment; every line below becomes blocking when
production opens.*

13. **Database and Keycloak credentials exposed in the git history**
    (`infra/helm/librarius/values.yaml`, a **public repository**). The chart now reads
    them from Kubernetes Secrets, ships no default value, and a `gitleaks` job blocks any
    comeback — but the old values remain readable in the history and the instance is
    reachable from the Internet: the exposure only closes once they are **rotated on the
    cluster**. Procedure in `docs/DEPLOYMENT.md`.
14. **PostgreSQL backups: mechanism shipped, restore never exercised.** The chart carries a
    daily CronJob (`pg_dump` → gzip → AES-256 → S3-compatible bucket, 7/4/6 retention),
    **off by default** until a bucket and its credentials exist. The chain is tested end to
    end against a throwaway PostgreSQL and MinIO (`infra/backup/verify.sh`), and the restore
    procedure is written down in `docs/DEPLOYMENT.md` — but it has never been run against
    the cluster, and Keycloak's own database is not in the dump.
    [#59](https://github.com/zelytra/Librarius/issues/59) stays open until a real restore is
    done.
15. **Alerting: rules written, nothing evaluates them.** Ten rules with runbooks in
    `infra/prometheus/rules/librarius.rules.yml` (API down, 5xx, p95, PVC, TLS, backups,
    CrashLoopBackOff), loaded by the compose stack and `promtool`-clean. **The Helm chart
    deploys no Prometheus**, there is no kube-state-metrics and no Alertmanager, so on the
    cluster nobody is still being told anything.
    [#60](https://github.com/zelytra/Librarius/issues/60) stays open.
16. ~~**Zero-downtime rollout shipped, never exercised.**~~ ✅ **Exercised on 2026-07-28**
    ([#64](https://github.com/zelytra/Librarius/issues/64)). Both deployments roll
    (`maxSurge: 1`, `maxUnavailable: 0`) on requests cut to measured usage, with a
    `startupProbe`, `preStop` pauses and disruption budgets. A forced restart of `web` and
    `api` together completed in **21 s**, `kubectl` reporting `1 old replicas are pending
    termination` — the replacement was serving before the outgoing pod left. An external
    probe polling `/` and `/api/me` every ~3 s over the whole window recorded **124
    samples, 124× `200` and 124× `401`, no 5xx and no connection error**. The `Recreate`
    outage it replaces measured 11 s (web) and 31 s (api).
17. **Rollback never exercised.** A `vX.Y.Z` tag now publishes `X.Y.Z` / `X.Y` / `X`
    images, aligns the chart and generates the changelog (`release.yml`), and the
    `helm rollback` procedure is written down in `docs/DEPLOYMENT.md` — but it has never
    been run against the cluster, so the procedure is documented, not proven. See
    [#63](https://github.com/zelytra/Librarius/issues/63).
18. ~~🔴 **Continuous deployment has been broken since 1 July 2026**~~ ✅ **Fixed on
    2026-07-28** ([#85](https://github.com/zelytra/Librarius/issues/85)): `cd.yml` failed on
    rejected Kubernetes credentials (401 on the first `kubectl`); moving every Librarius
    resource into its own `librarius` namespace settled it. **Eight consecutive green
    deployments** since. The workflow now opens an issue by itself when a deployment fails,
    and serialises runs (`concurrency: cd`).
    What this episode says about debt #15 stands: the breakage lasted four weeks and was
    found by triggering a release, not by an alert.

## Functional gaps vs the vision 📋

| Expected (docs/ARCHITECTURE.md) | Reality |
|---|---|
| Multiple editions per work | Schema ready (`work` 1→N `edition`), **no screen** lets you pick or compare an edition |
| Upcoming **French** releases | `GET /api/catalog/upcoming` returns the **provider** dates (JP/EN), shown as "indicative dates". No French publisher data |
| Reorderable/hideable Home | Sections hardcoded in `HomePage.tsx` |
| Reading progress | ✅ Current page or percentage, each derived from the other, start and finish dates, progress bar on the detail screen and on the "resume reading" carousel |
| Reading goals | `GET/PUT /api/goals` works, **no screen** exposes it |
| Custom categories | `POST /api/categories` works, the UI only shows Gold/Silver/Bronze |
| Notifications | Nothing (no preferences, no push, no email) |
| Series / volumes | ✅ `/series/:id` and the Series view of the collection. Missing: a `wished` flag on a volume (the marker is session-local), volume covers, and ordering the series by most recently added — none of the three exists in the API payloads |
| Export / account deletion | Nothing — **blocking for a public product (GDPR)** |
| Multilingual | i18n plumbing in place, a single locale |
| Native mobile | No Capacitor project |

## Security — items to address

- Credentials exposed in the history, awaiting rotation (see debt #13).
- `quarkus.http.cors.origins=http://localhost:5173` hardcoded: check the configuration of
  the deployed environment (the web app is served by the same host, so same-origin — to be
  confirmed).
- ~~Swagger UI publicly exposed~~ ✅ **Resolved on 2026-07-28** ([#62](https://github.com/zelytra/Librarius/issues/62)): Swagger UI is back to its default (dev and test only), and `/q` is no longer routed by the ingress.
- Keycloak sign-up is **open** on the imported realm: anyone can create an account on
  `librarius.zelytra.fr`. Deliberate or not? To be decided.
- The Booknode import is scraping of a third-party site: a fragile and legally grey
  dependency, to be documented (terms of use) and isolated behind a feature flag.
- No rate limiting on `/api/catalog/search` → a single user can burn the instance's Open
  Library / AniList quota.

## Repository metrics

| Indicator | Value |
|---|---|
| Java classes (main) | 64 |
| Java tests | 22 files |
| Front-end files (src) | 49 |
| Front-end tests | 8 files, 63 tests |
| Flyway migrations | 7 |
| REST endpoints exposed | 28 (10 resources) |
| Locales | 1 (fr) |
| CI workflows | 5 |
