# My Library (Librarius)

Personal library manager — **books & manga**. Keep track of your collection, your
reading and your wishlist; search for works (title / author / date) with covers and
**multiple editions**; browse **upcoming releases** and your reading statistics.

> 🇫🇷 The user interface is in French — `fr` is the only locale so far — but the
> architecture is ready for more.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React + Vite + TypeScript, responsive **PWA** (desktop + mobile) |
| Backend | Java + **Quarkus** (REST, Hibernate Panache, Flyway) |
| Database | PostgreSQL |
| Auth | Keycloak (OIDC) — *coming in PR #2* |
| Monitoring | Prometheus + Grafana (Micrometer) — *coming in PR #9* |
| Monorepo | pnpm workspaces (web) + Maven (api) |

## Monorepo layout

```text
apps/
  web/        # React + Vite + TS PWA
  api/        # Quarkus API (Maven, mvnw wrapper)
packages/     # TS client generated from the OpenAPI schema (coming)
infra/        # docker-compose (postgres, …)
docs/         # ARCHITECTURE.md
.github/      # CI/CD workflows
```

## Requirements

- **Node.js ≥ 20** + **pnpm 9** (`corepack enable` or `npm i -g pnpm`)
- **JDK 21+**
- **Docker** (for PostgreSQL and, later on, Keycloak / Dev Services)

## Quick start

```bash
# 1. Frontend dependencies
pnpm install

# 2. Infra: postgres + keycloak + prometheus + grafana
pnpm infra:up           # keycloak :8081 · prometheus :9090 · grafana :3000

# 3. Backend (port 8080)
pnpm api:dev            # cd apps/api && ./mvnw quarkus:dev

# 4. Frontend (port 5173, proxies /api → 8080)
pnpm web:dev
```

Open http://localhost:5173. Catalog search (the **Discover** screen) requires a
Keycloak login: use one of the test accounts, **alice / alice** or **bob / bob**. The
Keycloak admin console lives at http://localhost:8081 (admin / admin).

**Monitoring**: the API exposes its Prometheus metrics on `/q/metrics`. Prometheus runs
on http://localhost:9090 and Grafana on http://localhost:3000 (admin / admin), with the
dashboard "Librarius — Vue d'ensemble" provisioned automatically.

## Handy scripts

| Command | Effect |
|---|---|
| `pnpm web:dev` / `web:build` / `web:test` / `web:lint` | Frontend |
| `pnpm api:dev` / `api:test` | Backend (through `mvnw`) |
| `pnpm infra:up` / `infra:down` | Local Docker stack |

## Contribution & git flow

`main` (release) ← `develop` (integration) ← `feature/*`. Urgent fixes: `hotfix/*`.

- **Everything here is written in English**: code, comments, documentation, commit
  messages and pull requests alike.
- Conventional commits: `feat(web): …`, `fix(api): …`, `docs: …`, `ci: …`.
- **The repository refuses merge commits** — pull requests land by squash or rebase
  only. A squashed pull request title becomes the commit subject, so write it with care.
- Every pull request must pass CI (lint, typecheck, tests, build) before it is merged.

Architecture and roadmap details in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Deployment

Docker images (JVM api + nginx web) are built and pushed to GHCR by `release.yml`, and
the production stack is described by `infra/compose.prod.yml`. Full guide:
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).
