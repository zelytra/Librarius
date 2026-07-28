# REST API — Librarius

Base path: `/api`. Every resource is `@Authenticated` (Keycloak JWT in
`Authorization: Bearer …`).
Reference contract: `openapi/openapi.yaml` (generated at build time).

> ⚠️ Any change to a resource or a DTO requires regenerating the schema **and** the TS
> client, otherwise the `openapi-sync` CI job fails:
> `cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api`

## Inventory

| Method | Path | Role |
|---|---|---|
| GET | `/api/me` | Current profile (`MeDto`) — creates the `app_user` on the fly |
| GET | `/api/catalog/search?q=&kind=&limit=` | External catalog search. `kind` defaults to `BOOK`, `limit` clamped to 1–40 |
| GET | `/api/catalog/upcoming?kind=&limit=` | Upcoming releases. `kind` defaults to `MANGA`, `limit` clamped to 1–50 |
| GET | `/api/library?status=` | Owned titles, optional `OWNED\|READING\|READ` filter |
| POST | `/api/library` | Adds a title (`LibraryCreateDto`) — creates `work`+`edition` if needed |
| PUT | `/api/library/{id}/rank` | Assigns/removes a rank (`RankAssignDto`, a null `categoryId` removes it) |
| PUT | `/api/library/{id}/progress` | Status and reading progress (`ProgressDto`) |
| DELETE | `/api/library/{id}` | Removes a title from the collection |
| GET | `/api/wishlist` | Wishlist |
| POST | `/api/wishlist` | Adds a wish (`WishlistCreateDto`) |
| DELETE | `/api/wishlist/{id}` | Removes a wish |
| GET | `/api/categories` | Built-in ranks + the user's own categories |
| POST | `/api/categories` | Creates a custom category (`CategoryCreateDto`) |
| GET | `/api/goals` | Reading goals |
| PUT | `/api/goals/{year}` | Creates or updates a year's goal (`GoalUpsertDto`) |
| GET | `/api/stats` | Aggregated statistics (`StatsDto`) |
| POST | `/api/import/{source}` | Import by handle (`booknode`, `babelio`) — `{ "handle": "…" }` |
| POST | `/api/import/csv` | CSV import (the body is the raw content) |

Outside `/api`: `/q/health` and `/q/metrics`, **cluster-internal only** — the ingress does not route `/q`. Swagger UI is served in dev and test, and absent from the production build.

## Main DTOs

```java
MeDto(String id, String email, String displayName, String locale)

BookView(/* read projection of an edition and its work */)

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

Enums: `Kind {BOOK, MANGA}` · `LibraryStatus {OWNED, READING, READ}` ·
`WishPriority {PRIORITY, SOON, SOMEDAY}` · `GoalUnit {BOOKS, VOLUMES, PAGES}`.

## Conventions

- **Adding a title**: the client sends a complete `ManualBookDto`; the server matches or
  creates the `work` + `edition`, then creates the `library_item`. The front end never has
  to handle a catalog identifier.
- **Isolation**: every resource resolves `CurrentUser.id()` and filters on it. An `id`
  belonging to another user must answer **404**, never 403 (no leaking of existence).
- **Validation**: Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Min`) on the input DTOs;
  violations surface as 400.
- **Import errors**: `ImportException` → `ImportExceptionMapper`.

## Identified gaps

| # | Gap | Target milestone |
|---|---|---|
| A1 | No **pagination** on `/api/library` and `/api/wishlist` | Foundations |
| A2 | No `PATCH /api/me` (display name, language) | Public product |
| A3 | No `GET /api/export` (CSV/JSON) and no `DELETE /api/me` — **GDPR requirements** | Public product |
| A4 | No `/api/series` resource (details, volumes, follow) | Core product |
| A5 | No `DELETE`/`PUT` on `/api/categories/{id}` | Core product |
| A6 | No `PUT /api/wishlist/{id}` (edit priority/price/note) | Core product |
| A7 | No one-call conversion from wish to collection | Core product |
| A8 | No `/api/dashboard/layout` | Core product |
| A9 | No server-side search/filter on the collection | Foundations |
| A10 | No rate limiting on `/api/catalog/*` | Operations |
| A11 | No time-based statistics (`/api/stats/timeline`) | Core product |
