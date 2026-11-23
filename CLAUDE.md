# Librarius — project instructions

Personal library for **books & manga**: collection, reading tracking, wishlist, catalog
discovery, statistics. React PWA + Quarkus API + PostgreSQL + Keycloak.

> 📚 **Full working documentation for agents: [`.claude/docs/`](.claude/docs/README.md).**
> Read at least [INVENTORY](.claude/docs/INVENTORY.md),
> [CONVENTIONS](.claude/docs/CONVENTIONS.md) and [WORKFLOW](.claude/docs/WORKFLOW.md) before
> changing anything.

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
pnpm install            # frontend dependencies (pnpm 9, Node 24)
pnpm infra:up           # postgres :5432 · keycloak :8081 · prometheus :9090 · grafana :3000
pnpm api:dev            # Quarkus API :8080 (mvnw quarkus:dev)
pnpm web:dev            # Vite PWA :5173 (proxies /api → 8080)
pnpm mobile:build       # builds the web bundle, then `cap sync` into the native shell
```

Quality gate — **run before every push** (the e2e journeys no longer run in CI — the workflow was
removed while red on every merge, #439; run `pnpm e2e` locally when a change touches a journey):

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
| `apps/web/` | React 19 + Vite 8 + TS PWA. `features/<screen>/`, `shared/` (ui, theme, styles), `api/generated/` (orval — **never edit by hand**) |
| `apps/api/` | Quarkus 3 / Java 21. `domain/` (entities + Panache repositories), `web/` (JAX-RS resources + DTOs), `catalog/` (external providers), `imports/`, `security/` |
| `apps/mobile/` | Capacitor 7 native shell (Android/iOS). **No application code**: `webDir` points at the `apps/web` build — see [MOBILE](.claude/docs/MOBILE.md) |
| `openapi/` | **Contract** between the api and the web app: schema produced by the api build, consumed by orval. Belongs to neither application |
| `packages/` | Shared libraries — empty to date, the workspace glob expects it |
| `infra/` | dev & prod docker-compose, Keycloak realm, Prometheus, Grafana, Helm chart |
| `infra/helm/librarius/` | k3s deployment chart (web, api, postgres, keycloak, ingress) |
| `docs/` | public documentation (ARCHITECTURE, DEPLOYMENT) |
| `docs/wiki/` | **developer wiki** — published to the GitHub wiki on every merge to `main` (`wiki.yml`) |
| `.claude/docs/` | detailed working documentation for agents |

## Git flow — non-negotiable

`main` ← `feature/*`. **There is no `develop` branch.**

- **Never commit directly** on `main`.
- One branch per change, cut from an up-to-date `main`.
- Conventional commits **in English**: `feat(web): …`, `fix(api): …`, `docs: …`, `ci: …`.
- Commit identity for agent sessions: `Claude` / `noreply@anthropic.com` (what the session hook
  verifies). Pull requests are squashed, so `main` carries the merger's identity.
- Every change goes through a pull request against `main`. Merge commits are refused:
  squash or rebase only.
- **Follow the pull request and merge it on green**: the agent watches its CI, fixes what goes
  red, and squash-merges it itself once every check is green and it has no conflict (#509).
- **Never merge on a red CI.** Merging into `main` deploys to **staging**; production
  will be deployed by tagging `main`, and does not exist yet ([#103](https://github.com/zelytra/Librarius/issues/103)).

## Developer wiki — keep it current

The GitHub wiki is the developers' technical documentation: architecture, API, data model,
catalog, imports, authentication, running locally, contributing, deployment. Its source is
**`docs/wiki/`** in this repository; the `wiki` workflow publishes it on every merge to `main`
(never edit the wiki directly — the next merge overwrites it).

**Any change that alters one of those subjects updates the matching `docs/wiki/` page in the
same pull request** — a new endpoint, a DTO, an import rule, a migration, a workflow, a
command. A pull request that changes behaviour a page describes and leaves the page stale is
not finished.

## Unclear specs: ask first

When a request is not clear enough, lacks precision, or could be improved (an ambiguity, a
missing rule, a choice with real trade-offs), **ask the person you are working with** before
starting — a few targeted questions, each with the options and a recommended default. When
nothing needs asking, carry out the task normally; never ask for what the code, the docs or a
sensible default already settle. The answers go into the issue. See
[WORKFLOW § 1](.claude/docs/WORKFLOW.md#1-feedback-goes-into-issues-first).

## Feedback goes into issues first

Every fix or request the user reports is written into a GitHub issue (split when it covers
several things) **before** any work on it starts, so a session cut short leaves a trace. The
pull request that fixes it closes it (`Closes #n`).

## Development rules — every agent, every change

These five rules come first; [CONVENTIONS § 0](.claude/docs/CONVENTIONS.md#0-engineering-rules)
details each one with what is checked.

1. **No over-engineering.** The smallest change that solves the request, with the standard
   tool already in the repository. No speculative abstraction, option or dependency.
2. **Good practices.** Readable names, one responsibility per unit, one source of truth, no
   dead code, errors handled where they can be acted on, security rules below always on.
3. **TDD, always.** Write the failing test first, see it fail for the right reason, make it
   pass with the least code, then refactor. A bug is reproduced by a test before it is fixed.
4. **As many tests as it takes to stop regressions.** Every behaviour, edge case, error path
   and cross-user case a change touches is locked by a test. A test is never deleted, skipped
   or loosened to get a green build.
5. **UI/UX good practices on every screen change.** Reuse the design tokens and existing
   patterns, meet WCAG 2.1 AA (labels, keyboard, focus, contrast, 44 px targets, reduced
   motion), give every action feedback (loading, empty, error, success), and check the
   result in a real browser at phone width before calling it done.

## Code rules

- **No over-engineering.** Reach for the simple, standard solution first.
- **Security**: every resource is `@Authenticated` and **scoped by `CurrentUser.id()`** —
  never read an entity without filtering on `user_id`. See `security/CurrentUser.java`.
- **Database**: Flyway owns the schema (`hibernate-orm.database.generation=validate`).
  Any model change goes through a `V<n>__description.sql` migration, never through an
  edit to a migration that has already shipped.
- **Frontend**: no secret nor key in the bundle; user-facing text goes through `i18n`
  (`useTranslation`), no hard-coded string in new screens.
- **Tests**: written first (TDD); every fixed or added behaviour is locked down by a test
  (`vitest` on the web side, `@QuarkusTest` on the api side).

## Environments

| Env | URL | Notes |
|---|---|---|
| Local | http://localhost:5173 | test accounts `alice/alice`, `bob/bob` |
| Staging | https://librarius.zelytra.fr | k3s, single ingress; `/auth` → Keycloak, `/api` → api, `/` → web. `/q` is **not** routed |
| Production | — | does not exist yet; to be opened at the v1.0 milestone |

⚠️ `librarius.zelytra.fr` is a **staging** environment, not production: an outage during
a release is acceptable, and the data there is considered disposable. That assumption
goes away the day production opens — see [ROADMAP](.claude/docs/ROADMAP.md).
