# Architecture technique — Librarius

> Complète `docs/ARCHITECTURE.md` (doc publique) avec le détail utile pour intervenir
> dans le code. Décrit l'existant, puis les évolutions décidées.

## 1. Vue d'ensemble

```
Navigateur (PWA React 19)
   │  OIDC Authorization Code + PKCE (react-oidc-context)
   ├──────────────► Keycloak  (realm librarius)
   │                    ▲ validation JWT (JWKS)
   │  Bearer JWT        │
   └──────────────► API Quarkus 3 / Java 21 ──► PostgreSQL 16 (Flyway + Panache)
                          │
                          ├──► Open Library  (REST, livres)
                          └──► AniList       (GraphQL, mangas)
```

En production, **un seul hôte** `librarius.zelytra.fr` derrière Traefik :
`/auth` → Keycloak · `/api` + `/q` → API · `/` → nginx (PWA).
Le front et l'API sont donc *same-origin* : aucun préflight CORS en prod.

## 2. Frontend — `apps/web`

### Organisation

```
src/
  api/generated/librarius.ts   # client orval — GÉNÉRÉ, ne pas éditer
  app/AppShell.tsx             # layout + <Outlet/>, BottomNav.tsx
  auth/oidc.ts                 # configuration OIDC
  features/<écran>/            # une page par écran, autonome
  shared/
    api.ts                     # useApiAuth() → { authed, loading, login, opts }
    LoginGate.tsx              # garde d'authentification par écran
    theme/                     # ThemeProvider, themes.ts, context.ts
    styles/                    # tokens.css (variables CSS), global.css
    ui/                        # Icon, BookCover, primitives (Button, Chip, Segmented…)
  i18n/                        # i18next + locales/fr.json
```

### Conventions actuelles

- **Routage** : `react-router-dom` v7, routes déclarées dans `App.tsx`, toutes enfants
  de `AppShell`. Fallback `*` → Accueil.
- **Authentification** : chaque écran encapsule son contenu dans `<LoginGate>` ; le
  jeton est injecté manuellement via `opts` dans chaque appel généré.
- **Appels API** : fonctions orval typées (`getApiLibrary`, `postApiWishlist`, …)
  appelées dans un `useEffect`. Les réponses portent `{ status, data }` — **toujours
  tester `status === 200`** avant d'utiliser `data`.
- **Thème** : variables CSS (`--ink`, `--surface`, `--accent`, `--line`, `--muted`,
  `--faint`) définies dans `tokens.css` et commutées par `ThemeProvider`.

### Évolutions décidées

1. **TanStack Query** pour l'état serveur : cache, invalidation, retry, états de
   chargement homogènes. Orval sait générer les hooks (`client: 'react-query'`) — la
   bascule se fait dans `orval.config.ts`, pas à la main.
2. **Sortie des styles inline** vers CSS Modules adossés aux tokens. Cible : aucun
   `style={{…}}` porteur de mise en forme durable ; l'inline reste toléré pour les
   valeurs réellement dynamiques (couleur dérivée d'un titre, largeur calculée).
3. **Fabrique de couleur unique** : `colorFor()` et `PALETTE` sont dupliqués dans
   `HomePage`, `CollectionPage` et `DetailPage` → extraire dans `shared/ui/cover.ts`.
4. **Intercepteur d'authentification** : un `mutator` orval qui pose l'en-tête
   `Authorization` et rafraîchit le jeton, au lieu de passer `opts` partout.
5. **Frontière d'erreur** + composants `<Loading>`, `<ErrorState>`, `<EmptyState>`
   partagés.

## 3. Backend — `apps/api`

### Organisation

```
zelytra/librarius/
  domain/               # entités Panache + enums (Kind, LibraryStatus, WishPriority, GoalUnit)
    repository/         # PanacheRepositoryBase, toutes les requêtes scopées user
  web/                  # ressources JAX-RS + ApiDtos (records) + mappers d'exception
  catalog/              # CatalogProvider (SPI), CatalogService (agrégation + cache)
    provider/           # OpenLibraryProvider/Client, AniListProvider/Client
  imports/              # LibraryImporter (SPI), Booknode, Babelio, CSV, ImportService
  security/CurrentUser  # résout le « sub » Keycloak → AppUser (création JIT)
```

### Points structurants

- **Scoping utilisateur** : `CurrentUser.id()` retourne le `sub` du JWT ; `AppUser` est
  créé *just-in-time* au premier appel. **Toute requête de possession filtre sur
  `user_id`** — c'est la seule barrière d'isolation, il n'y a pas de RLS PostgreSQL.
- **DTOs** : `ApiDtos` regroupe des `record` Java avec des fabriques `of(entity)`.
  Les entités ne sont **jamais** sérialisées directement.
- **Catalogue** : `CatalogProvider` est un SPI CDI (`kind()`, `search()`, `upcoming()`).
  `CatalogService` indexe les providers par `Kind`, fan-out, dédoublonne par clé
  (`dedupKey`) et met en cache (`@CacheResult`, Caffeine, 6 h recherche / 12 h sorties).
  **Ajouter un provider = créer une classe `@ApplicationScoped implements
  CatalogProvider`** ; aucune autre modification n'est nécessaire.
- **Import** : même schéma SPI via `LibraryImporter`, exposé par
  `POST /api/import/{source}` et `POST /api/import/csv`.
- **Migrations** : Flyway au démarrage, Hibernate en `validate`. Le schéma est la
  vérité ; une entité qui ne colle pas fait échouer le démarrage — comportement voulu.
- **Tests** : `@QuarkusTest` avec **Dev Services** (PostgreSQL + Keycloak éphémères,
  utilisateurs `alice`/`bob`). Aucun service externe requis pour lancer `mvnw verify`.

### Évolutions décidées

1. **Table `series`** + `work.series_id`, avec migration de `work.series_title`.
2. **Agrégations SQL** pour les statistiques (aujourd'hui calculées en mémoire).
3. **Pagination** (`page`, `size`) sur `GET /api/library` et `GET /api/wishlist`.
4. **Rate limiting** sur `/api/catalog/*` (par utilisateur).
5. **`catalog_cache`** persistant en complément de Caffeine, pour survivre aux
   redémarrages et ménager les quotas des providers.
6. Suppression de `HelloResource`.

## 4. Contrat front ↔ back

```
apps/api (annotations JAX-RS)
   └─ mvnw package → apps/web/openapi/openapi.{json,yaml}   (généré au build)
        └─ pnpm gen:api (orval) → apps/web/src/api/generated/librarius.ts
```

Le workflow **`openapi-sync`** régénère et échoue sur diff : le schéma et le client
sont donc **toujours** committés à jour. Toute PR touchant l'API doit inclure la
régénération.

## 5. Sécurité

| Aspect | État |
|---|---|
| Authentification | Keycloak OIDC, `quarkus.oidc.application-type=service`, JWT validé par JWKS |
| Autorisation | `@Authenticated` sur toutes les ressources sauf `HelloResource` (à supprimer) |
| Isolation des données | Applicative, via `user_id` dans chaque requête — **à couvrir par des tests dédiés** |
| Secrets | ⚠️ En clair dans `helm/librarius/values.yaml` — à migrer vers des Secrets Kubernetes |
| Surface exposée | Swagger UI actif en production (`always-include=true`) — à restreindre |
| Rate limiting | Aucun |

## 6. Déploiement

Chart `helm/librarius` : `web`, `api`, `postgres` (PVC `local-path`, deux bases
`librarius` + `keycloak`), `keycloak`, `ingress` (Traefik + cert-manager).
Push sur `main` → `cd.yml` → build/push images GHCR taguées `<sha>` → `helm upgrade`.

Stratégie de déploiement `Recreate` (nœud contraint en CPU) : **coupure de service à
chaque livraison**, à corriger quand la capacité le permettra.

L'autorité OIDC est **gravée dans l'image web au build**
(`--build-arg VITE_OIDC_AUTHORITY`) : changer de domaine impose un rebuild.

## 7. Décisions d'architecture

| # | Décision | Raison | Statut |
|---|---|---|---|
| 1 | Catalogue partagé / possession privée | Évite la duplication, permet les stats globales | ✅ Appliqué |
| 2 | Keycloak plutôt qu'une auth maison | Ne pas réimplémenter register/reset/refresh | ✅ Appliqué |
| 3 | Flyway propriétaire du schéma | Migrations versionnées, pas de dérive Hibernate | ✅ Appliqué |
| 4 | Client TS généré (orval) + gate CI | Contrat front/back toujours synchronisé | ✅ Appliqué |
| 5 | Rang = colonne, pas table de liaison | Un titre porte au plus un rang | ✅ Appliqué (simplification vs vision initiale) |
| 6 | Dates de sortie provider, pas VF | Aucune API gratuite fiable pour les éditeurs FR | ✅ Assumé, à revoir |
| 7 | TanStack Query pour l'état serveur | Supprime cache/retry/invalidation faits main | 🔜 Décidé |
| 8 | CSS Modules + tokens, fin de l'inline | Dark mode, cohérence, réutilisation | 🔜 Décidé |
| 9 | Capacitor pour le mobile natif | Scan ISBN + notifications push, code partagé | 🔜 Décidé |
