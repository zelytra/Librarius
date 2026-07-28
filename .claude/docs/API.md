# API REST — Librarius

Base : `/api`. Toutes les ressources sont `@Authenticated` (JWT Keycloak en
`Authorization: Bearer …`) **sauf `/api/hello`**, endpoint de démo à supprimer.
Contrat de référence : `openapi/openapi.yaml` (généré au build).

> ⚠️ Toute modification d'une ressource ou d'un DTO impose de régénérer le schéma **et**
> le client TS, sinon la CI `openapi-sync` échoue :
> `cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api`

## Inventaire

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/api/me` | Profil courant (`MeDto`) — crée l'`app_user` à la volée |
| GET | `/api/catalog/search?q=&kind=&limit=` | Recherche catalogue externe. `kind` défaut `BOOK`, `limit` borné 1–40 |
| GET | `/api/catalog/upcoming?kind=&limit=` | Prochaines sorties. `kind` défaut `MANGA`, `limit` borné 1–50 |
| GET | `/api/library?status=` | Titres possédés, filtre optionnel `OWNED\|READING\|READ` |
| POST | `/api/library` | Ajoute un titre (`LibraryCreateDto`) — crée `work`+`edition` si besoin |
| PUT | `/api/library/{id}/rank` | Assigne/retire un rang (`RankAssignDto`, `categoryId` nul = retrait) |
| PUT | `/api/library/{id}/progress` | Statut et progression (`ProgressDto`) |
| DELETE | `/api/library/{id}` | Retire un titre de la collection |
| GET | `/api/wishlist` | Liste de souhaits |
| POST | `/api/wishlist` | Ajoute un souhait (`WishlistCreateDto`) |
| DELETE | `/api/wishlist/{id}` | Retire un souhait |
| GET | `/api/categories` | Rangs built-in + catégories de l'utilisateur |
| POST | `/api/categories` | Crée une catégorie personnalisée (`CategoryCreateDto`) |
| GET | `/api/goals` | Objectifs de lecture |
| PUT | `/api/goals/{year}` | Crée ou met à jour l'objectif d'une année (`GoalUpsertDto`) |
| GET | `/api/stats` | Statistiques agrégées (`StatsDto`) |
| POST | `/api/import/{source}` | Import par pseudo (`booknode`, `babelio`) — `{ "handle": "…" }` |
| POST | `/api/import/csv` | Import CSV (corps = contenu brut) |
| GET | `/api/hello` | 🔴 Démo non authentifiée — **à supprimer** |

Hors `/api` : `/q/health`, `/q/metrics` (Prometheus), `/q/swagger-ui`.

## DTOs principaux

```java
MeDto(String id, String email, String displayName, String locale)

BookView(/* projection lecture d'une edition + son work */)

ManualBookDto(Kind kind, String title, String authors, String seriesTitle,
              Integer volumeNumber, String isbn13, String publisher, String language,
              Integer pageCount, String coverUrl, String format, LocalDate releaseDate,
              Integer originalYear, String synopsis, String genres)

LibraryCreateDto(ManualBookDto book, LibraryStatus status, Integer rating, LocalDate acquiredAt)
LibraryItemDto(UUID id, String status, Integer rating, LocalDate acquiredAt,
               String rankCode, BookView book)

ProgressDto(Integer currentPage, Integer percent, LibraryStatus status)
RankAssignDto(UUID categoryId)

WishlistCreateDto(ManualBookDto book, WishPriority priority, BigDecimal estimatedPrice, String note)
WishlistItemDto(UUID id, String priority, BigDecimal estimatedPrice, String note, BookView book)

CategoryDto(UUID id, String code, String label, String color, boolean builtin)
CategoryCreateDto(String label, String color)

GoalDto(UUID id, int year, int targetCount, String unit)
GoalUpsertDto(Integer targetCount, GoalUnit unit)

StatsDto(long read, long reading, long toRead, long pagesRead, long seriesCount,
         Integer goalTarget, long goalCurrent, List<GenreCount> byGenre)
GenreCount(String genre, long count)
```

Enums : `Kind {BOOK, MANGA}` · `LibraryStatus {OWNED, READING, READ}` ·
`WishPriority {PRIORITY, SOON, SOMEDAY}` · `GoalUnit {BOOKS, VOLUMES, PAGES}`.

## Conventions

- **Ajout de titre** : le client envoie un `ManualBookDto` complet ; le serveur
  rapproche ou crée `work` + `edition`, puis crée le `library_item`. Le front n'a
  jamais à manipuler d'identifiant de catalogue.
- **Isolation** : chaque ressource résout `CurrentUser.id()` et filtre. Un `id`
  appartenant à un autre utilisateur doit répondre **404**, jamais 403 (pas de fuite
  d'existence).
- **Validation** : Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Min`) sur les DTOs
  d'entrée ; les violations remontent en 400.
- **Erreurs d'import** : `ImportException` → `ImportExceptionMapper`.

## Manques identifiés

| # | Manque | Milestone visé |
|---|---|---|
| A1 | Pas de **pagination** sur `/api/library` et `/api/wishlist` | Fondations |
| A2 | Pas de `PATCH /api/me` (nom affiché, langue) | Produit public |
| A3 | Pas d'`GET /api/export` (CSV/JSON) ni de `DELETE /api/me` — **exigences RGPD** | Produit public |
| A4 | Pas de ressource `/api/series` (fiche, tomes, suivi) | Cœur produit |
| A5 | Pas de `DELETE`/`PUT` sur `/api/categories/{id}` | Cœur produit |
| A6 | Pas de `PUT /api/wishlist/{id}` (modifier priorité/prix/note) | Cœur produit |
| A7 | Pas de conversion souhait → collection en un appel | Cœur produit |
| A8 | Pas de `/api/dashboard/layout` | Cœur produit |
| A9 | Pas de recherche/filtre serveur sur la collection | Fondations |
| A10 | Pas de rate limiting sur `/api/catalog/*` | Exploitation |
| A11 | Pas de statistiques temporelles (`/api/stats/timeline`) | Cœur produit |
