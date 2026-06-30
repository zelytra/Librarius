# Ma Bibliothèque (Librarius)

Application de gestion de bibliothèque personnelle — **livres & mangas**. Suivez votre
collection, vos lectures, vos souhaits ; recherchez des ouvrages (titre / auteur / date)
avec couvertures et **éditions multiples** ; consultez les **prochaines sorties** et vos
statistiques de lecture.

> 🇫🇷 Interface en français par défaut, architecture prête pour le multilingue.

## Stack

| Couche | Technologie |
|---|---|
| Frontend | React + Vite + TypeScript, **PWA** responsive (PC + mobile) |
| Backend | Java + **Quarkus** (REST, Hibernate Panache, Flyway) |
| Base de données | PostgreSQL |
| Auth | Keycloak (OIDC) — *à venir PR #2* |
| Monitoring | Prometheus + Grafana (Micrometer) — *à venir PR #9* |
| Monorepo | pnpm workspaces (web) + Maven (api) |

## Structure du monorepo

```
apps/
  web/        # PWA React + Vite + TS
  api/        # API Quarkus (Maven, wrapper mvnw)
packages/     # client TS généré depuis l'OpenAPI (à venir)
infra/        # docker-compose (postgres, …)
docs/         # ARCHITECTURE.md
.github/      # workflows CI/CD
```

## Prérequis

- **Node.js ≥ 20** + **pnpm 9** (`corepack enable` ou `npm i -g pnpm`)
- **JDK 21+**
- **Docker** (pour PostgreSQL et, plus tard, Keycloak / Dev Services)

## Démarrage rapide

```bash
# 1. Dépendances front
pnpm install

# 2. Base de données
pnpm infra:up           # docker compose up postgres

# 3. Backend (port 8080)
pnpm api:dev            # cd apps/api && ./mvnw quarkus:dev

# 4. Frontend (port 5173, proxy /api → 8080)
pnpm web:dev
```

Ouvrez http://localhost:5173 — l'écran d'amorçage confirme la connexion à l'API.

## Scripts utiles

| Commande | Effet |
|---|---|
| `pnpm web:dev` / `web:build` / `web:test` / `web:lint` | Front |
| `pnpm api:dev` / `api:test` | Back (via `mvnw`) |
| `pnpm infra:up` / `infra:down` | Stack Docker locale |

## Contribution & git flow

`main` (prod) ← `develop` (intégration) ← `feature/*`. Correctifs : `hotfix/*`.
Chaque PR doit passer la CI (lint, typecheck, tests, build) avant merge. Détails
d'architecture et de roadmap dans [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
