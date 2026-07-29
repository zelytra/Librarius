# Running the project locally

Condensed from the
[README quick start](https://github.com/zelytra/Librarius/blob/main/README.md#quick-start),
which is the version to copy commands from — this page is the narrative around it.

## Requirements

- **Node.js 24** and **pnpm 9** (`corepack enable`, or `npm i -g pnpm`)
- **JDK 21+**
- **Docker**, to run PostgreSQL, Keycloak, Prometheus and Grafana locally

## Four commands

```bash
pnpm install   # frontend dependencies
pnpm infra:up  # postgres, keycloak, prometheus, grafana, all through docker compose
pnpm api:dev   # Quarkus API on :8080 (mvnw quarkus:dev, live reload)
pnpm web:dev   # Vite dev server on :5173, proxies /api to :8080
```

Open <http://localhost:5173>. The **Discover** screen (catalog search) needs a session:
sign in with one of the seeded test accounts, `alice` / `alice` or `bob` / `bob`. The
Keycloak admin console is at <http://localhost:8081> (`admin` / `admin`).

Prometheus runs at <http://localhost:9090> and Grafana at <http://localhost:3000>
(`admin` / `admin`), with a dashboard provisioned automatically — the API exposes its own
metrics on `/q/metrics`.

## Running the backend tests

`cd apps/api && ./mvnw -B verify` starts its own PostgreSQL and Keycloak through
Testcontainers Dev Services, so nothing needs to be running beforehand. On a machine with no
local JDK or Docker (a locked-down Windows laptop, typically), the
[Contributing](https://github.com/zelytra/Librarius/wiki/Contributing) page links to a
WSL-based workaround that runs Maven inside a container instead.

## Before you push

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
cd apps/api && ./mvnw -B verify
```

Both are exactly what CI runs. See
[Contributing](https://github.com/zelytra/Librarius/wiki/Contributing) for the rest of the
checklist — bundle size budget, OpenAPI client regeneration, the end-to-end suite.
