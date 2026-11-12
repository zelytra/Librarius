# API

The Quarkus API (`apps/api`) serves JSON under `/api`. Its OpenAPI schema is produced by the
build into [`openapi/`](https://github.com/zelytra/Librarius/tree/main/openapi) and the web
client is **generated** from it (orval, `apps/web/src/api/generated/` — never edited by hand).

## Rules every resource follows

- **Authenticated**: every resource is `@Authenticated` with a Keycloak bearer token.
- **Scoped to the caller**: an entity is read through the caller's id (`CurrentUser.id()`),
  never by its id alone. Another user's item answers 404, not 403.
- **Errors** are JSON `{ "message": "…" }` in French when meant for the reader (an import
  failure, an invalid ISBN), a bare status otherwise.
- **Changing a resource or a DTO** means regenerating the client in the same change:
  `cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api` (with
  `quarkus:dev` stopped). CI fails a pull request whose schema and client disagree.

## Endpoints

| Area | Endpoints | Notes |
|---|---|---|
| Library | `GET/POST /api/library`, `GET/DELETE /api/library/{id}`, `PUT …/progress`, `…/rank`, `…/review`, `…/edition` | `…/review` takes `rating`, `review` and `shared` (private unless `true`). List is paged and filtered server-side (`kind`, `status`, `rank`, `minRating`, `q`, `standalone`); sorts `recent` (default: latest of finished, started, added), `added`, `title`, `author`, `genre` (first normalised genre, unknown last), `rating`. A title added as `READ` is finished that day |
| Wishlist | `GET/POST /api/wishlist`, `PUT/DELETE /api/wishlist/{id}`, `POST …/acquire` | acquiring moves a wish to the library |
| Series | `GET /api/series`, `GET /api/series/{id}`, `…/missing`, `…/follow`, `…/review`, `…/reviews` | `…/review` takes `shared` like a book review; `…/reviews` lists the shared ones the caller may see. The summary carries `authors` and `genre` (what the grouped library sorts a run on next to the one-shots), and carries owned/read counts and `lastActivity` |
| Works | `GET /api/works/{id}/editions`, `GET /api/works/{id}/reviews` | stored editions (empty and duplicate ones left out) plus the provider's; reviews = readers' shared reviews the caller may see, with the average of their ratings |
| Authors | `GET /api/authors?q=`, `GET /api/authors/{id}`, `…/follow` | bibliography = the reader's titles plus the providers' |
| Catalog | `GET /api/catalog/search`, `GET /api/catalog/upcoming` | see [Catalog & book search](https://github.com/zelytra/Librarius/wiki/Catalog-and-Book-Search) |
| <a id="scan"></a>Scan | `GET /api/scan/{isbn}` | `match`: `ISBN`, `TITLE`, `SERIES_VOLUME` or `NONE`; `owned`, `wished`, `catalog` |
| Import | `POST /api/import/{booknode\|babelio}`, `POST /api/import/collected`, `POST /api/import/csv`, `POST /api/import/json`, `GET /api/import/jobs/{id}` | asynchronous jobs, polled; see [Imports](https://github.com/zelytra/Librarius/wiki/Imports) |
| Export | `GET /api/export`, `GET /api/export/{jobId}` | JSON or CSV; large accounts in the background |
| Stats | `GET /api/stats`, `GET /api/stats/timeline` | timeline points carry `books`, `pages` and `byKind`; `readWithPageCount` says how many read titles have a known length |
| Goals | `GET /api/goals`, `PUT /api/goals/{year}` | books or pages |
| Categories | `GET/POST /api/categories`, `PUT/DELETE /api/categories/{id}` | Or, Argent, Bronze, Abandon are built in |
| Me | `GET/PATCH/DELETE /api/me`, `DELETE /api/me/library`, `…/followers`, `…/following`, `…/blocked` | `DELETE /api/me` erases the account and its Keycloak login; `DELETE /api/me/library` empties the library (titles, readings, wishes, goals, own categories, followed series and authors, series reviews) and keeps the account, profile, social links and Home layout |
| Social | `GET /api/users?q=`, `PUT/DELETE /api/users/{id}/follow`, `…/block`, `POST /api/reports` | members found by display name (two letters at least; the caller and blocked members left out), each with whether the caller follows them. Reports target `WORK`, `EDITION`, `SERIES`, or a shared review (`REVIEW`, `SERIES_REVIEW`, with the reasons `SPOILER`, `OFFENSIVE`, `SPAM`, `OTHER`) |
| Misc | `GET /api/genres`, `GET/PUT /api/dashboard/layout`, `GET /api/releases/upcoming` | |

The full reference, parameter by parameter, is [`.claude/docs/API.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/API.md);
the schema itself is [`openapi/openapi.yaml`](https://github.com/zelytra/Librarius/blob/main/openapi/openapi.yaml).

## Who sees a shared review

A rating or review is private unless its author ticks "share". A shared one is listed by
`GET /api/works/{id}/reviews` and `GET /api/series/{id}/reviews` only to the readers
`VisibilityGate.canView` lets through: never anyone with a block in either direction between
them, otherwise everyone when the author's account is public, otherwise readers who follow
the author mutually. The average is computed over those visible ratings only, so it never
leaks a private one.
