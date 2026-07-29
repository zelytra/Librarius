# Librarius — project instructions

Personal library for **books & manga**: collection, reading tracking, wishlist, catalog
discovery, statistics. React PWA + Quarkus API + PostgreSQL + Keycloak.

> 📚 **Full working documentation for agents: [`.claude/docs/`](.claude/docs/README.md).**
> Read at least [INVENTORY](.claude/docs/INVENTORY.md) and
> [CONVENTIONS](.claude/docs/CONVENTIONS.md) before changing anything.

## Language

**Everything in this repository is written in English**: code, comments and javadoc,
documentation, commit messages, pull requests, issues and milestones. Where the existing
content is still in French, convert what you touch. A pull request title becomes the
commit subject when it is squashed, so it is English too.

Two things stay in French:

- **The application itself**: `fr` is the only locale, and user-facing copy is written
  in French in `i18n/locales/fr.json`.
- **Flyway migrations that have already shipped**, comments included: the Flyway
  checksum covers the whole file, so rewording a comment breaks validation on databases
  where the migration has already run.

## Commands

```bash
pnpm install            # frontend dependencies (pnpm 9, Node 20)
pnpm infra:up           # postgres :5432 · keycloak :8081 · prometheus :9090 · grafana :3000
pnpm api:dev            # Quarkus API :8080 (mvnw quarkus:dev)
pnpm web:dev            # Vite PWA :5173 (proxies /api → 8080)
pnpm mobile:build       # builds the web bundle, then `cap sync` into the native shell
```

Quality gate — **run before every push**:

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
```

```bash
cd apps/api && ./mvnw -B verify
```

After any change to a JAX-RS resource or a DTO, regenerate the TS client — otherwise the
`openapi-sync` CI job fails:

```bash
cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api
```

## Layout

| Path | Contents |
|---|---|
| `apps/web/` | React 19 + Vite 6 + TS PWA. `features/<screen>/`, `shared/` (ui, theme, styles), `api/generated/` (orval — **never edit by hand**) |
| `apps/api/` | Quarkus 3 / Java 21. `domain/` (entities + Panache repositories), `web/` (JAX-RS resources + DTOs), `catalog/` (external providers), `imports/`, `security/` |
| `apps/mobile/` | Capacitor 7 native shell (Android/iOS). **No application code**: `webDir` points at the `apps/web` build — see [MOBILE](.claude/docs/MOBILE.md) |
| `openapi/` | **Contract** between the api and the web app: schema produced by the api build, consumed by orval. Belongs to neither application |
| `packages/` | Shared libraries — empty to date, the workspace glob expects it |
| `infra/` | dev & prod docker-compose, Keycloak realm, Prometheus, Grafana, Helm chart |
| `infra/helm/librarius/` | k3s deployment chart (web, api, postgres, keycloak, ingress) |
| `docs/` | public documentation (ARCHITECTURE, DEPLOYMENT) |
| `.claude/docs/` | detailed working documentation for agents |

## Git flow — non-negotiable

`main` ← `feature/*`. **There is no `develop` branch.**

- **Never commit directly** on `main`.
- One branch per change, cut from an up-to-date `main`.
- Conventional commits **in English**: `feat(web): …`, `fix(api): …`, `docs: …`, `ci: …`.
- Commit identity: `zelytra` / `contact@zelytra.fr`.
- Every change goes through a pull request against `main`. Merge commits are refused:
  squash or rebase only.
- **Never merge on a red CI.** Merging into `main` deploys to **staging**; production
  will be deployed by tagging `main`, and does not exist yet ([#103](https://github.com/zelytra/Librarius/issues/103)).

## Code rules

- **No over-engineering.** Reach for the simple, standard solution first.
- **Security**: every resource is `@Authenticated` and **scoped by `CurrentUser.id()`** —
  never read an entity without filtering on `user_id`. See `security/CurrentUser.java`.
- **Database**: Flyway owns the schema (`hibernate-orm.database.generation=validate`).
  Any model change goes through a `V<n>__description.sql` migration, never through an
  edit to a migration that has already shipped.
- **Frontend**: no secret nor key in the bundle; user-facing text goes through `i18n`
  (`useTranslation`), no hard-coded string in new screens.
- **Tests**: every fixed or added behaviour is locked down by a test (`vitest` on the
  web side, `@QuarkusTest` on the api side).

## Environments

| Env | URL | Notes |
|---|---|---|
| Local | http://localhost:5173 | test accounts `alice/alice`, `bob/bob` |
| Staging | https://librarius.zelytra.fr | k3s, single ingress; `/auth` → Keycloak, `/api` → api, `/` → web. `/q` is **not** routed |
| Production | — | does not exist yet; to be opened at the v1.0 milestone |

⚠️ `librarius.zelytra.fr` is a **staging** environment, not production: an outage during
a release is acceptable, and the data there is considered disposable. That assumption
goes away the day production opens — see [ROADMAP](.claude/docs/ROADMAP.md).
