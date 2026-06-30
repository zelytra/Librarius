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

# 2. Infra : postgres + keycloak + prometheus + grafana
pnpm infra:up           # keycloak :8081 · prometheus :9090 · grafana :3000

# 3. Backend (port 8080)
pnpm api:dev            # cd apps/api && ./mvnw quarkus:dev

# 4. Frontend (port 5173, proxy /api → 8080)
pnpm web:dev
```

Ouvrez http://localhost:5173. Pour la recherche catalogue (écran **Découvrir**),
connectez-vous via Keycloak avec un utilisateur de test : **alice / alice** ou
**bob / bob**. La console d'admin Keycloak est sur http://localhost:8081 (admin / admin).

**Monitoring** : l'API expose ses métriques Prometheus sur `/q/metrics`. Prometheus
tourne sur http://localhost:9090 et Grafana sur http://localhost:3000 (admin / admin),
avec un dashboard « Librarius — Vue d'ensemble » provisionné automatiquement.

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

## Déploiement

Images Docker (api JVM + web nginx) construites et poussées vers GHCR par
`release.yml`, stack de production via `infra/compose.prod.yml`. Guide complet :
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).
