# My Library (Librarius)

Personal library manager — **books & manga**. Keep track of your collection, your
reading and your wishlist; search for works (title / author / date) with covers and
**multiple editions**; browse **upcoming releases** and your reading statistics.

> 🇫🇷 The user interface is in French — `fr` is the only locale so far — but the
> architecture is ready for more.

**Try it**: a staging build runs at [librarius.zelytra.fr](https://librarius.zelytra.fr),
open sign-up, no invitation needed.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React + Vite + TypeScript, responsive **PWA** (desktop + mobile) |
| Backend | Java + **Quarkus** (REST, Hibernate Panache, Flyway) |
| Database | PostgreSQL |
| Auth | **Keycloak** (OIDC), end to end |
| Monitoring | Prometheus + Grafana (Micrometer), plus Alertmanager and alerting rules with runbooks |
| Mobile | **Capacitor** shell around the same web build (`apps/mobile`) |
| Monorepo | pnpm workspaces (web, mobile) + Maven (api) |

## Monorepo layout

```text
apps/
  web/        # React + Vite + TS PWA
  api/        # Quarkus API (Maven, mvnw wrapper)
  mobile/     # Capacitor shell (Android/iOS): runs the web build, holds no code of its own
openapi/      # Contract between api and web: schema produced by the api, consumed by orval
packages/     # Shared libraries — empty to date, the workspace glob expects it
e2e/          # Playwright suite: the key journeys against the whole stack
infra/        # docker-compose (dev & prod), Keycloak realm, Prometheus, Grafana, Helm chart
docs/         # ARCHITECTURE.md, DEPLOYMENT.md
.claude/docs/ # Detailed working documentation, for whoever picks up a task next
.github/      # CI/CD workflows
```

## Requirements

- **Node.js 24** + **pnpm 9** (`corepack enable` or `npm i -g pnpm`)
- **JDK 21+**
- **Docker**, to run PostgreSQL, Keycloak, Prometheus and Grafana locally

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
| `pnpm mobile:build` | Builds the web bundle, then syncs it into the native shell |
| `pnpm infra:up` / `infra:down` | Local Docker stack |
| `pnpm e2e:install` then `pnpm e2e` | End-to-end journeys (starts and stops its own stack) |

## Quality gate

Run before every push — it is also exactly what CI runs:

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
```

```bash
cd apps/api && ./mvnw -B verify
```

The full checklist — OpenAPI client regeneration, bundle size budget, end-to-end suite —
is in [`.claude/docs/CONVENTIONS.md`](.claude/docs/CONVENTIONS.md).

## Contribution & git flow

`main` ← `feature/*`, no `develop` branch: every change is a pull request against `main`,
squash-merged once CI is green — merge commits are refused, and a squashed pull request
title becomes the commit subject, so write it with care. Merging deploys to staging;
production will be deployed by tagging `main`. Everything in this repository is written in
English: code, comments, documentation, commit messages and pull requests alike.

## Documentation

- **[Developer wiki](docs/wiki/README.md)** — start here if you are new to the project:
  architecture, catalog search, data model, running it locally, contributing, deployment.
- **[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)** — technical choices and the pull
  request roadmap, kept current as the code changes.
- **[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)** — the full deployment guide: releases,
  secrets, monitoring, backups, rollback.
- **[`.claude/docs/`](.claude/docs/README.md)** — detailed working documentation for
  whoever picks up a task next, human or agent.

## Deployment

Docker images (JVM api + nginx web) are pushed to GHCR by `cd.yml` on every merge into
`main` (tags `latest` and `<sha>`, then deployed to **staging**,
[librarius.zelytra.fr](https://librarius.zelytra.fr)) and by `release.yml` on a
`vX.Y.Z` tag (tags `X.Y.Z`, `X.Y`, `X` and `<sha>`). **Production does not exist yet** — it
opens at the v1.0 milestone. Releases are listed in [`CHANGELOG.md`](CHANGELOG.md); the full
guide, including the rollback procedure, is in
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).
