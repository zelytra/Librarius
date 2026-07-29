# Changelog

Every release of Librarius is recorded here. The sections below are generated from the
conventional commits of each release by `.github/scripts/changelog.sh`, run by the
`release` workflow when a `vX.Y.Z` tag is pushed on `main` — do not write them by hand,
write the commit subject properly instead.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), the
versioning [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Releasing, image
tags and the rollback procedure are documented in
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

Anything predating the first tag lives in the commit log and in the pull requests: the
versioning pipeline lands before the first release is cut.

<!-- releases:start -->

## [0.4.0] - 2026-07-29

### Features

- **api**: persistence, OIDC auth and core CRUD (PR #2)
- **api,web**: typed OpenAPI client with CI drift gate (PR #3)
- **api**: external catalog search & upcoming releases (PR #4)
- **web**: design system, theming, i18n & app shell (PR #5)
- **web**: Collection & Detail screens (PR #6)
- **web,infra**: web OIDC login + live catalog search (PR #7)
- **monitoring**: Prometheus metrics + provisioned Grafana (PR #9)
- **web**: Wishlist & Stats screens (PR #10)
- **web**: full Home dashboard (PR #11)
- **infra**: Docker images, release pipeline & prod compose (PR #12)
- **deploy**: Helm chart + CD to Kubernetes (book.zelytra.fr)
- import de bibliothèque (Booknode scraping + CSV) dans les Réglages
- **api**: statistiques, progression, rangs & catégories (features maquette)
- **deploy**: full-stack Helm chart (web+api+postgres+keycloak) — librarius.zelytra.fr
- **web**: wire Collection/Discover/Wishlist/Stats to live API
- **web**: wire Home dashboard and Detail actions to live API
- **api**: make the series a first-class object and expose /api/series (#128)
- **api**: keep the catalog cache across restarts (#131)
- **web**: real dark theme driven by tokens (#135)
- **infra**: back up PostgreSQL off-site and alert on what breaks (#130)
- **api**: order the wishlist by urgency, and let a wish be edited or bought (#137)
- **web**: share the non-nominal states and move every string into i18n (#134)
- **api**: normalise the genres and count the statistics on them (#139)
- **web**: add the series screen and the Series view of the collection (#141)
- **infra**: roll web and api without taking the site down (#144)
- **api,web**: track reading progress and keep a private rating (#140)
- **web**: make the wishlist a purchase-decision tool (#142)
- advanced search and manual entry in Discover (#146)
- **infra**: alert somebody when Librarius breaks (#153)
- **api,web**: let a title switch to another edition of its work (#152)
- **mobile**: add the Capacitor shell over the web bundle (#154)
- expose the annual reading goal and chart reading over time (#145)
- **api,web**: add library export and account deletion (GDPR) (#204)
- **api,web**: personalized upcoming releases and upcoming_release table (#212)
- **api,web**: let a user build and manage their own ranking categories (#160)
- **api,web**: reorder and hide the Home dashboard sections (#218)

### Fixes

- **api**: Open Library book provider + AniList NSFW filter
- **deploy**: Keycloak issuer & readiness behind path-based ingress
- **web**: exclude /auth /api /q from PWA navigation fallback
- **deploy**: Recreate strategy for web+api (CPU-constrained node)
- **api**: ne persister un objectif de lecture qu'une fois complet
- **api**: allow the deployed origin so writes stop returning 403 (#127)
- **deploy**: pull the images without a credential that expires (#149)
- **infra**: back up the accounts, not only the books (#156)
- **ci**: stop the release notes aborting on the initial commit (#159)
- **api**: stop a burst of cold catalog searches from emptying the pool (#167)

### Performance

- **web**: shrink the first payload and lock it down in CI (#151)
- **web**: subset Material Symbols instead of the full font (#216)

### Refactoring

- **api**: remove Google Books provider (Open Library only)
- normaliser l'arborescence du monorepo (#96)
- **web**: extract inline styles into CSS Modules and factor out the cover palette (#126)

### Documentation

- cadrage projet et documentation pour agents
- **roadmap**: rattacher la roadmap aux issues GitHub
- librarius.zelytra.fr est une qualification, pas une production
- consigner le blocage du déploiement continu
- retirer la mention d'outillage de la doc publique
- normer la langue du code, des issues et des milestones en anglais
- actualiser l'état des lieux et documenter les tests hors JDK
- **roadmap**: refléter les titres anglais des issues et l'avancement (#92)
- record that two API tests cannot run in the container (#138)
- warn that verify rewrites the OpenAPI schema (#147)
- record what the cluster actually does now (#148)
- the test profile adds to the schema as well as removing (#150)
- say when a tag is cut, not only what it does (#158)
- Node 24 removed the reason Capacitor is pinned to 7 (#162)
- refresh the README and stage a developer wiki (#213)
- point at the published wiki and drop the staging copy (#214)
- bring the inventory and the roadmap back to what the code says (#217)

### Tests

- **api**: verrouiller l'isolation des données entre utilisateurs
- **web**: couvrir les sept écrans avec MSW
- **e2e**: cover the key journeys with Playwright (#116)

### CI

- let pnpm action read version from packageManager field
- contrôle des modifications documentaires
- rationaliser les workflows et alerter sur les échecs de déploiement (#94)
- bump the github-actions group with 12 updates (#123)
- **mobile**: build and publish a debug Android APK (#215)

### Chores

- scaffold monorepo foundation (PR #1)
- **web**: stop tracking tsbuildinfo build artifact
- exclure les worktrees temporaires du suivi git
- **web**: passer les commentaires et noms de tests en anglais (#93)
- **api**: passer les commentaires et la javadoc en anglais (#95)
- **deps-dev**: bump vitest from 2.1.9 to 3.2.6 (#121)
- **deps**: bump the web-runtime group across 1 directory with 4 updates (#122)
- **deps**: bump Quarkus to 3.37.4 and realign the OpenAPI contract (#129)
- **deps**: bump the images group across 2 directories with 3 updates (#118)
- **web**: clear the react-router advisory and stage the toolchain upgrade (#157)

### Other

- Initial commit
- Release — English codebase, React Query, pagination, hardened operations (#117)
