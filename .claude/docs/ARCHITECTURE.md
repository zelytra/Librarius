# Technical architecture — Librarius

> Complements `docs/ARCHITECTURE.md` (the public documentation) with the detail you
> actually need to work in the code. Describes what exists, then the changes already
> decided on.

## 1. Overview

```text
Browser (React 19 PWA)
   │  OIDC Authorization Code + PKCE (react-oidc-context)
   ├──────────────► Keycloak  (librarius realm)
   │                    ▲ JWT validation (JWKS)
   │  Bearer JWT        │
   └──────────────► Quarkus 3 / Java 21 API ──► PostgreSQL 16 (Flyway + Panache)
                          │
                          ├──► Open Library  (REST, books)
                          └──► AniList       (GraphQL, manga)
```

On the deployed environment, **a single host** `librarius.zelytra.fr` sits behind Traefik:
`/auth` → Keycloak · `/api` + `/q` → API · `/` → nginx (the PWA).
The front end and the API are therefore *same-origin*: no CORS preflight.

> `librarius.zelytra.fr` is a **staging** environment. No production environment is open to
> date; it is targeted for the v1.0 milestone.

## 2. Frontend — `apps/web`

### Layout

```text
src/
  api/generated/librarius.ts   # orval client — GENERATED, do not edit
  app/AppShell.tsx             # layout + <Outlet/>, BottomNav.tsx
  auth/oidc.ts                 # OIDC configuration
  features/<screen>/           # one page per screen, self-contained
  shared/
    api.ts                     # useApiAuth() → { authed, loading, login, opts }
    LoginGate.tsx              # per-screen authentication guard
    theme/                     # ThemeProvider, themes.ts, context.ts
    styles/                    # tokens.css (CSS variables), global.css
    ui/                        # Icon, BookCover, primitives (Button, Chip, Segmented…)
  i18n/                        # i18next + locales/fr.json
```

### Current conventions

- **Routing**: `react-router-dom` v7, routes declared in `App.tsx`, all children of
  `AppShell`. The `*` fallback goes to Home.
- **Authentication**: every screen wraps its content in `<LoginGate>`; the token is injected
  by hand through `opts` on each generated call.
- **API calls**: typed orval functions (`getApiLibrary`, `postApiWishlist`, …) called inside
  a `useEffect`. Responses carry `{ status, data }` — **always check `status === 200`**
  before using `data`.
- **Theme**: CSS variables (`--ink`, `--surface`, `--accent`, `--line`, `--muted`,
  `--faint`) defined in `tokens.css` and switched by `ThemeProvider`.

### Decided changes

1. **TanStack Query** for server state: cache, invalidation, retry, consistent loading
   states. Orval can generate the hooks (`client: 'react-query'`) — the switch happens in
   `orval.config.ts`, not by hand.
2. **Move inline styles out** into CSS Modules backed by the tokens. Target: no `style={{…}}`
   carrying durable presentation; inline stays acceptable for genuinely dynamic values (a
   colour derived from a title, a computed width).
3. **A single colour factory**: `colorFor()` and `PALETTE` are duplicated in `HomePage`,
   `CollectionPage` and `DetailPage` → extract into `shared/ui/cover.ts`.
4. **Authentication interceptor**: an orval `mutator` that sets the `Authorization` header
   and refreshes the token, instead of threading `opts` everywhere.
5. **Error boundary** plus shared `<Loading>`, `<ErrorState>` and `<EmptyState>` components.

## 3. Backend — `apps/api`

### Layout

```text
zelytra/librarius/
  domain/               # Panache entities + enums (Kind, LibraryStatus, WishPriority, GoalUnit)
    repository/         # PanacheRepositoryBase, every query scoped to the user
  web/                  # JAX-RS resources + ApiDtos (records) + exception mappers
  catalog/              # CatalogProvider (SPI), CatalogService (aggregation + cache)
    provider/           # OpenLibraryProvider/Client, AniListProvider/Client
  imports/              # LibraryImporter (SPI), Booknode, Babelio, CSV, ImportService
  security/CurrentUser  # resolves the Keycloak "sub" → AppUser (JIT creation)
```

### Structural points

- **User scoping**: `CurrentUser.id()` returns the `sub` from the JWT; the `AppUser` is
  created *just in time* on the first call. **Every ownership query filters on `user_id`** —
  that is the only isolation barrier, there is no PostgreSQL RLS.
- **DTOs**: `ApiDtos` groups Java `record` types with `of(entity)` factories. Entities are
  **never** serialised directly.
- **Catalog**: `CatalogProvider` is a CDI SPI (`kind()`, `search()`, `upcoming()`).
  `CatalogService` indexes the providers by `Kind`, fans out, deduplicates by key
  (`dedupKey`) and caches (`@CacheResult`, Caffeine, 6 h for search / 12 h for releases).
  **Adding a provider means writing one `@ApplicationScoped implements CatalogProvider`
  class** — nothing else needs to change.
- **Import**: the same SPI pattern through `LibraryImporter`, exposed by
  `POST /api/import/{source}` and `POST /api/import/csv`.
- **Migrations**: Flyway on startup, Hibernate in `validate`. The schema is the truth; an
  entity that does not match makes startup fail — that is the intended behaviour.
- **Tests**: `@QuarkusTest` with **Dev Services** (ephemeral PostgreSQL + Keycloak, users
  `alice`/`bob`). No external service is needed to run `mvnw verify`.

### Decided changes

1. **A `series` table** plus `work.series_id`, with a migration from `work.series_title`.
2. **SQL aggregations** for the statistics (computed in memory today).
3. **Pagination** (`page`, `size`) on `GET /api/library` and `GET /api/wishlist`.
4. **Rate limiting** on `/api/catalog/*` (per user).
5. **A persistent `catalog_cache`** alongside Caffeine, to survive restarts and spare the
   providers' quotas.
6. Removal of `HelloResource`.

## 4. Front ↔ back contract

```text
apps/api (JAX-RS annotations)
   └─ mvnw package → openapi/openapi.{json,yaml}   (generated at build time)
        └─ pnpm gen:api (orval) → apps/web/src/api/generated/librarius.ts
```

The **`openapi-sync`** workflow regenerates and fails on any diff: the schema and the client
are therefore **always** committed up to date. Any PR touching the API must include the
regeneration.

## 5. Security

| Aspect | State |
|---|---|
| Authentication | Keycloak OIDC, `quarkus.oidc.application-type=service`, JWT validated against JWKS |
| Authorisation | `@Authenticated` on every resource except `HelloResource` (to be removed) |
| Data isolation | Application-level, through `user_id` in every query — **to be covered by dedicated tests** |
| Secrets | ⚠️ In plaintext in `infra/helm/librarius/values.yaml` — to be moved to Kubernetes Secrets |
| Exposed surface | Swagger UI enabled on the deployed environment (`always-include=true`) — to be restricted before opening to the public |
| Rate limiting | None |

## 6. Deployment

The `infra/helm/librarius` chart: `web`, `api`, `postgres` (`local-path` PVC, two databases
`librarius` + `keycloak`), `keycloak`, `ingress` (Traefik + cert-manager).
Push to `main` → `cd.yml` → build/push GHCR images tagged `<sha>` → `helm upgrade`.

The `Recreate` deployment strategy (the node is CPU-constrained) means **downtime on every
release**. Acceptable while the environment is staging; blocking once production opens.

The OIDC authority is **baked into the web image at build time**
(`--build-arg VITE_OIDC_AUTHORITY`): changing domain requires a rebuild.

## 7. Architecture decisions

| # | Decision | Rationale | Status |
|---|---|---|---|
| 1 | Shared catalog / private ownership | Avoids duplication, enables global stats | ✅ Applied |
| 2 | Keycloak rather than home-grown auth | Do not reimplement register/reset/refresh | ✅ Applied |
| 3 | Flyway owns the schema | Versioned migrations, no Hibernate drift | ✅ Applied |
| 4 | Generated TS client (orval) + CI gate | Front/back contract always in sync | ✅ Applied |
| 5 | Rank as a column, not a join table | A title carries at most one rank | ✅ Applied (a simplification vs the initial vision) |
| 6 | Provider release dates, not French ones | No reliable free API for French publishers | ✅ Accepted, to be revisited |
| 7 | TanStack Query for server state | Removes hand-rolled cache/retry/invalidation | 🔜 Decided |
| 8 | CSS Modules + tokens, no more inline | Dark mode, consistency, reuse | 🔜 Decided |
| 9 | Capacitor for the native mobile app | ISBN scanning + push notifications, shared code | 🔜 Decided |
