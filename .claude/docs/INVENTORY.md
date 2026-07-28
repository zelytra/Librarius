# Inventory — repository audit

> Audit carried out on **2026-07-28** against `af55df2`. It describes the code
> **as it is**, not as it ought to be. To be revised at the end of every milestone.

## Overview

The project is a **complete and deployed** skeleton: all 7 screens exist, the API exposes
9 resources, OIDC auth works end to end, and CI/CD ships to k3s. What is missing is
**functional depth** (series, fine-grained progress, French release dates), **quality**
(incomplete i18n) and **operations**
(plaintext secrets, no backups, no alerting).

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
| Catalog | `CatalogService` aggregates Open Library (books) and AniList (manga), Caffeine cache 6 h / 12 h |
| Import | Booknode (scraping) + CSV, exposed in Settings |
| API contract | OpenAPI generated at build time → orval TS client, `openapi-sync` CI gate |
| Screens | Home, Collection, Detail, Discover, Wishlist, Stats, Settings — **all wired to the live API** |
| PWA | `vite-plugin-pwa`, icons, `/auth` `/api` `/q` excluded from the navigation fallback |
| Monitoring | Micrometer → `/q/metrics`, Prometheus + Grafana provisioned, "Overview" dashboard |
| CI/CD | Path-filtered workflows (lint, tests, images, docs) plus CodeQL and a dependency audit; push to `main` → build GHCR images + `helm upgrade` |

## Technical debt identified 🔧

### Front-end — critical

1. ~~**Inline styles everywhere.**~~ ✅ **Cleared on 2026-07-28**
   ([#32](https://github.com/zelytra/Librarius/issues/32)): one `.module.css` per screen and
   per shared component, all backed by `shared/styles/tokens.css`, which gained a type
   scale, radii, shadows, screen padding and the decorative tints. No hex colour, font or
   font size is hardcoded under `features/` any more; `style={{…}}` is left only where the
   value is computed at render time. A real dark theme is now a matter of overriding tokens
   ([#42](https://github.com/zelytra/Librarius/issues/42)).
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
   41 tests across 7 files cover the six application screens through MSW — nominal render,
   empty state, error state, missing session and the main interactions.
   Still to do: the Playwright e2e suite ([#37](https://github.com/zelytra/Librarius/issues/37)).
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
   Left behind: `work.series_title` still duplicates `series.title` until the front end
   reads the identifier (#45, #46), and `StatsResource.seriesCount` still counts distinct
   lower-cased titles rather than joining `series`.
8. **`genres` is a `VARCHAR(512)`** treated as an atomic value in the stats (a book tagged
   "Fantasy, Aventure" counts as a genre distinct from "Fantasy").
9. ~~**Statistics computed in memory**~~ ✅ **Resolved on 2026-07-28**
   ([#40](https://github.com/zelytra/Librarius/issues/40)): three aggregate queries in
   `LibraryItemRepository` replace the in-memory fold, and the query count no longer
   depends on the size of the collection. One behaviour change: genres with equal counts
   are now ordered alphabetically rather than by insertion order, the old tie-break
   following a listing order that SQL cannot reproduce.
10. **No pagination** on `GET /api/library` or `GET /api/wishlist`.
11. **Tables planned but never created**: `catalog_cache`, `dashboard_layout`,
    `notification_pref`, `upcoming_release`, `library_item_rank` (the rank is a column, not
    a table — an acceptable simplification, worth documenting).
12. ~~**`HelloResource` is unauthenticated**~~ ✅ **Resolved on 2026-07-28** ([#41](https://github.com/zelytra/Librarius/issues/41)): the demo endpoint is gone, every resource is now authenticated.

### Defects fixed since the audit ✅

- **Data isolation untested** ([#39](https://github.com/zelytra/Librarius/issues/39),
  2026-07-28): every user-scoped resource is now exercised with two accounts, including
  the fact that someone else's identifier must answer 404 and not 403.
- **Reading goal creation broken** ([#88](https://github.com/zelytra/Librarius/issues/88),
  2026-07-28): `GoalResource.upsert` persisted the entity before setting `target_count`, a
  NOT NULL column — `PUT /api/goals/{year}` returned 500 for a year with no goal yet. Never
  caught, for lack of a test and for lack of a screen exposing the feature. Surfaced by the
  isolation tests.

### Operations

*Criticality assessed for a staging environment; every line below becomes blocking when
production opens.*

13. **Database and Keycloak credentials exposed in the git history**
    (`infra/helm/librarius/values.yaml`, a **public repository**). The chart now reads
    them from Kubernetes Secrets, ships no default value, and a `gitleaks` job blocks any
    comeback — but the old values remain readable in the history and the instance is
    reachable from the Internet: the exposure only closes once they are **rotated on the
    cluster**. Procedure in `docs/DEPLOYMENT.md`.
14. **No PostgreSQL backup.** `local-path` PVC on a single node. Tolerable as long as the
    staging data is disposable — to be handled before hosting any real data.
15. **No alerting.** Grafana displays, nobody gets told.
16. **`Recreate` strategy** (node CPU constraint) → downtime on every deployment. Accepted
    in staging.
17. **Rollback never exercised.** A `vX.Y.Z` tag now publishes `X.Y.Z` / `X.Y` / `X`
    images, aligns the chart and generates the changelog (`release.yml`), and the
    `helm rollback` procedure is written down in `docs/DEPLOYMENT.md` — but it has never
    been run against the cluster, so the procedure is documented, not proven. See
    [#63](https://github.com/zelytra/Librarius/issues/63).
18. 🔴 **Continuous deployment has been broken since 1 July 2026**: `cd.yml` fails on
    rejected Kubernetes credentials (401 on the first `kubectl`). Images are still built
    and pushed to GHCR, but nothing is deployed any more. Discovered on 2026-07-28 by
    triggering a release — **no alert existed to report it**, a direct illustration of debt
    #15. See [#85](https://github.com/zelytra/Librarius/issues/85).

## Functional gaps vs the vision 📋

| Expected (docs/ARCHITECTURE.md) | Reality |
|---|---|
| Multiple editions per work | Schema ready (`work` 1→N `edition`), **no screen** lets you pick or compare an edition |
| Upcoming **French** releases | `GET /api/catalog/upcoming` returns the **provider** dates (JP/EN), shown as "indicative dates". No French publisher data |
| Reorderable/hideable Home | Sections hardcoded in `HomePage.tsx` |
| Reading progress | The `reading_progress` table exists; the UI only offers READING / READ (no current page, no %) |
| Reading goals | `GET/PUT /api/goals` works, **no screen** exposes it |
| Custom categories | `POST /api/categories` works, the UI only shows Gold/Silver/Bronze |
| Notifications | Nothing (no preferences, no push, no email) |
| Series / volumes | Neither a series screen nor "missing volume" tracking |
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
| Java classes (main) | 46 |
| Java tests | 10 files |
| Front-end files (src) | 30 |
| Front-end tests | 7 files, 41 tests |
| Flyway migrations | 2 |
| REST endpoints exposed | 19 (9 resources) |
| Locales | 1 (fr) |
| CI workflows | 5 |
