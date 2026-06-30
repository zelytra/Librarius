# Architecture — Ma Bibliothèque (Librarius)

Document vivant décrivant les choix techniques et la roadmap. Mis à jour au fil des PR.

## Vision produit

Bibliothèque personnelle multi-utilisateurs pour **livres et mangas** : collection,
suivi de lecture, souhaits, recherche (titre/auteur/date) avec couvertures et
**éditions multiples**, **prochaines sorties**, statistiques. FR par défaut, i18n-ready.
La maquette de référence (Claude Design) définit une UI mobile-first « papier »
(serif *Newsreader*, *DM Sans*, accent sauge `#9aab92` sur crème `#f3ede3`).

## Choix structurants

| Domaine | Choix | Justification courte |
|---|---|---|
| Front | React + Vite + TS, PWA | Un seul code responsive PC + mobile ; installable ; prêt Capacitor |
| Back | Java + Quarkus | Démarrage rapide, Micrometer/Prometheus natif, `quarkus-oidc` |
| DB | PostgreSQL + Hibernate Panache + Flyway | Migrations versionnées, scoping user au niveau repository |
| Auth | Keycloak (OIDC) | N'implémente pas soi-même register/reset/refresh ; SSO Grafana partagé |
| Client API | orval (OpenAPI → hooks typés) | Types front/back synchronisés, gate CI sur le diff |
| Catalogue livres | Open Library + Google Books | Graphe works↔editions↔ISBN↔cover |
| Catalogue mangas | AniList + Jikan + MangaDex | Meilleures données manga gratuites |
| Monorepo | pnpm (web) + Maven (api) | Coexistence sans friction, séparés en CI par path filters |

## Modèle de données (cible)

Principe clé : **séparer le catalogue partagé** (`series` / `work` / `edition`) de la
**possession par utilisateur** (`library_item`). Deux utilisateurs partagent la même
`edition` ; chacun a sa `library_item`.

- `app_user` (id = `sub` Keycloak, display_name, email, locale) — JIT, **aucun credential stocké**
- `series` (titre, kind BOOK|MANGA, total_volumes?, status)
- `work` (titre, titre_original, auteurs, kind, series_id?, volume_number?, synopsis, genres[], année)
- `edition` (work_id, isbn13/10, éditeur, langue, pages, cover_url, format, release_date, provider, provider_ref) — **1 work → N éditions**
- `library_item` (user_id, edition_id, acquired_at, rating, status OWNED|READING|READ, UNIQUE(user,edition))
- `reading_progress` (library_item_id, current_page, percent, started_at, finished_at)
- `rank_category` (user_id NULL pour built-ins Or/Argent/Bronze, code, label, color, is_builtin) + `library_item_rank`
- `wishlist_item` (user_id, work/edition, priority, estimated_price, note)
- `reading_goal` (user_id, year, target_count, unit)
- `dashboard_layout` (user_id, sections JSONB) — accueil réordonnable/masquable
- `notification_pref` (user_id, JSONB)
- `catalog_cache` (provider, query_hash, payload JSONB, fetched_at)

## Catalogue externe

Abstraction `CatalogProvider` (search / getWork / getEditions / upcomingReleases) +
`CatalogAggregator` qui fan-out par type, normalise vers `work`/`edition` et dédoublonne
par ISBN13 puis titre+auteur flou. Cache persistant (`catalog_cache`, TTL) + Caffeine.

> ⚠️ **Prochaines sorties manga VF** : aucune API gratuite fiable ne couvre les
> calendriers des éditeurs français (Glénat, Ki-oon, Kana, Pika). Les API (AniList/MAL/
> MangaDex) donnent surtout des dates JP/EN. MVP = dates providers **étiquetées** + table
> `upcoming_release` curée manuellement ; scraper FR best-effort = phase ultérieure.

## CI/CD & git flow

Branches : `main` (prod) ← `develop` (intégration) ← `feature/*` ; `release/*`, `hotfix/*`.
Workflows GitHub Actions (path-filtered) :

- **web** : pnpm `--frozen-lockfile` → eslint → `tsc` → vitest → `vite build`
- **api** : JDK 21 + cache Maven → `./mvnw -B verify`
- **openapi-sync** *(PR #3)* : régénère le client, échoue sur diff
- **release** *(PR #10)* : build + push images Docker GHCR (web nginx, api JVM ; natif optionnel)

Chaque PR doit être verte avant merge.

## Monitoring *(PR #9)*

`quarkus-micrometer-registry-prometheus` → `/q/metrics` (JVM, HTTP, Hibernate, HikariCP)
+ métriques métier (`catalog_search_total`, `library_item_added_total`, …). Grafana
provisionné as-code (datasource Prometheus, dashboards JVM/HTTP/Business), SSO via Keycloak.

## Roadmap des PR

1. **Fondation** — monorepo, skeletons web/api, compose postgres, CI minimale ✅
2. Backend core + auth (Flyway, entités Panache, Keycloak OIDC)
3. OpenAPI + client TS (orval)
4. Providers catalogue (recherche / éditions / sorties)
5. Design system + i18n (thèmes, polices, PWA, app shell)
6. Écrans A — Collection, Détail
7. Écrans B — Découvrir, Souhaits, Stats
8. Accueil personnalisable + Réglages
9. Monitoring Grafana
10. Déploiement (images GHCR, compose prod)
