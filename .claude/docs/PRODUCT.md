# Functional specification — Librarius

> This document describes **the product we are aiming at**. What is already shipped is
> marked ✅, what is left to build 🔜. The factual state of the code lives in
> [INVENTORY](INVENTORY.md).

## 1. Value proposition

Librarius is a **personal library** for readers of **novels and manga**. It answers three
needs that existing tools handle badly together:

1. **Know what I own** — down to the *volume* and the *edition*, so I do not buy a
   duplicate while standing in a bookshop.
2. **Know where I am** — books in progress, reading progress, history, goals.
3. **Know what is coming** — upcoming releases of the series I follow, a prioritised
   wishlist with an estimated budget.

The deliberate differentiator: proper handling of **series and volumes** (manga), where
competitors reason in terms of standalone books.

## 2. Personas

| Persona | Profile | Dominant need |
|---|---|---|
| **Léa — the manga collector** | 400 volumes, 25 series in progress | Never buy a volume twice; know which volume is missing; know the French release dates |
| **Marc — the novel reader** | 120 novels, 2 books in progress | Track his progress, hold an annual goal, find again what he enjoyed |
| **Sarah — the newcomer** | Coming from Booknode/Babelio | Import her existing library without retyping it |

v1.0 target: a **public**, multi-user product, open sign-up, French first.

## 3. Business objects

| Object | Definition | Owned by |
|---|---|---|
| **Work** (`work`) | The intellectual content: a novel, or *one volume* of a manga | Shared catalog |
| **Edition** (`edition`) | One materialisation: ISBN, publisher, language, page count, cover, format | Shared catalog |
| **Series** (`series`) | An ordered grouping of works ("One Piece", "A Song of Ice and Fire") | Shared catalog |
| **Follow** (`series_follow`) | The user ↔ series link: "tell me about the next volumes" | User |
| **Library item** (`library_item`) | The user ↔ edition link: status, rating, acquisition date, rank | User |
| **Progress** (`reading_progress`) | Current page / percentage, start and finish dates | User |
| **Wish** (`wishlist_item`) | Priority, estimated price, note | User |
| **Rank** (`rank_category`) | Gold / Silver / Bronze + custom categories | Shared (built-ins) + user |
| **Reading goal** (`reading_goal`) | Annual target in books, volumes or pages | User |

**Structural rule**: the catalog is **shared** across all users, ownership is **private**.
Two users who own the same title point at the same `work` — entries are matched against the
catalog before a work is created — but keep two distinct `library_item` rows. They point at
the same `edition` only when they entered the same one; two printings of one novel are two
`edition` rows under one `work`, which is what the Detail screen lets a user choose between.

## 4. Screens

### 4.1 Home ✅ / 🔜

Personal dashboard.

| Section | State | Detail |
|---|---|---|
| Header (date, greeting, Settings shortcut) | ✅ | Greeting to be made contextual (morning/evening) |
| Continue reading | ✅ | Carousel of `READING` titles, each cover carrying its progress |
| Counters (read / in progress / to read) | ✅ | |
| Upcoming releases | ✅ | 🔜 filter on the **series being followed** rather than on AniList trends |
| Recently read | ✅ | |
| Empty state | ✅ | Points to Discover |
| **Reorder / hide sections** | 🔜 | Persisted per user (`dashboard_layout`) |
| **Annual goal** | 🔜 | Progress gauge, the API already exists |

### 4.2 Collection ✅ / 🔜

The full inventory, with a **Books / Manga** toggle.

- ✅ Cover grid, rank badge, quick removal.
- ✅ Sorting: date added, title, author, genre, rating. Filter by rank, and "my favourites"
  (rated 4 or more).
- ✅ **Series view**: one row per series with its cover, `12 / 105 volumes` and a progress
  bar, an *Incomplète* badge on a run with volumes left to buy, ordering by progress or
  title, and a way into the series screen. The kind switch and the search carry across the
  toggle; the rank and favourites chips apply to a title, not to a run, and are hidden there.
- 🔜 Ordering the Series view by most recently added — `SeriesSummaryDto` carries no date.
- 🔜 Text search inside one's own collection.
- 🔜 Extra filters: status, year of acquisition, publisher, language.
- 🔜 Pagination / infinite scrolling (the API returns everything today).
- 🔜 Multi-selection → bulk action (change status, delete, assign a rank).

### 4.3 Title detail ✅ / 🔜

- ✅ Cover, title, authors, genres, pages, series, year, synopsis.
- ✅ Assigning a rank (Gold / Silver / Bronze).
- ✅ Marking "in progress" / "read".
- ✅ **Progress input**: current page or percentage — each derived from the other when the
  edition carries a page count — start and finish dates, and a progress bar.
- ✅ **Personal rating** (1–5) and private review, saved optimistically. Neither is ever
  shared nor aggregated across users.
- ✅ **Link to the series**: the *série* cell of the stat strip opens the series screen when
  the user has a stake in that series.
- ✅ **Alternate editions**: the other `edition` rows of the same `work` — publisher,
  language, format, page count, ISBN, release date — with **"C'est l'édition que je
  possède"** on each. The section is hidden when the catalog knows a single edition, and an
  edition already in the collection is named as such instead of being offered. Switching
  keeps status, rating, review and rank, and re-anchors the reading position on its
  percentage (see [API](API.md) § Editions).
- 🔜 **Enriching the editions from the providers**: the list holds what users entered.
  `work` carries no provider reference, so no provider can be asked for "the other editions
  of *this* work" — see [API](API.md) gap A12.
- 🔜 **Series navigation**: previous / next volume.
- 🔜 Reading history (re-reads).

### 4.4 Discover ✅ / 🔜

Search across the external catalog (Open Library for books, AniList for manga).

- ✅ Keyword search, Book / Manga toggle, direct add to the collection or the wishlist.
- ✅ **Advanced search**: author, year, language, publisher, folded away behind a toggle.
  The criteria a provider does not index are ignored rather than faked, and the panel says
  so — see [API](API.md#catalog-search) for what each one honours.
- ✅ **ISBN recognised in the search field**: pasting the number off a back cover searches
  the ISBN rather than the digits as keywords. The check digit is verified, so a barcode
  or an order number still goes through as an ordinary search.
- ✅ **Manual entry** for a title absent from the catalogs — self-published, an old
  edition, a fanzine. Offered from both empty states, which is where the user meets it.
- 🔜 **ISBN barcode scanning** (camera) — the key feature in a bookshop, native through
  Capacitor. The ISBN search it feeds already exists.
- 🔜 A catalog page before adding: pick the *edition* and the initial *status*.
- 🔜 Personalised suggestions based on the genres most present in the collection.

### 4.5 Wishlist ✅ / 🔜

- ✅ List with priority (Priority / Soon / Someday), estimated price, note, removal.
- ✅ Ordered from the most urgent to the least, and not alphabetically (#114).
- ✅ Grouped by priority, each bucket carrying its own count and subtotal (#52).
- ✅ **Editing** a wish inline: priority, estimated price, note (#52).
- ✅ **Total budget** in the header and budget per priority in the bucket headers — read
  from the server, so they cover the whole wishlist and not the loaded page (#52).
- ✅ **Conversion to ownership** in one gesture: "Je l'ai acheté" moves the wish into the
  collection as `OWNED`, dated the day of the purchase (#52).
- 🔜 A "one of your wishes is out soon" alert (cross-referenced with upcoming releases).

### 4.6 Statistics ✅ / 🔜

- ✅ Read / in progress / to read, pages read, number of series, breakdown by genre (top 6).
- 🔜 **Trend over time**: books read per month, per year.
- 🔜 **Annual goal**: gauge, pace required, projection.
- 🔜 Reading pace (pages/day), average time to finish a book.
- 🔜 Breakdown by author, publisher, language, rank.
- 🔜 A shareable year in review.

### 4.7 Settings ✅ / 🔜

- ✅ Booknode / Babelio import (by handle) and CSV import.
- ✅ Theme selection: four palettes (Crème, Sauge, Rose, Nuit) plus **Système**, which
  follows the operating system preference and is the default. The choice is kept in
  `localStorage` and applied before the first paint, so a reload never flashes the light
  theme.
- 🔜 **Profile**: display name, language, time zone.
- 🔜 **Library export** (CSV + JSON) — *GDPR requirement*.
- 🔜 **Account and data deletion** — *GDPR requirement*.
- 🔜 Notification preferences.
- 🔜 Custom category management (the API already exists).
- 🔜 Legal notice, terms of service, privacy policy.

### 4.8 Series ✅ / 🔜

`/series/:id`, reachable from a volume's detail screen and from the Series view of the
collection.

- ✅ Header: cover, title, publication status, `x / y volumes` with a progress bar, the
  number read, and a **Follow** toggle.
- ✅ Volume grid. The four states are told apart **without reading anything**, on three
  channels at once: a fill, an icon, and an outline — colour alone would be lost on a
  colour-blind reader.

  | State | Fill | Icon | Outline |
  |---|---|---|---|
  | Read | `--accent`, solid | `check_circle` | plain |
  | Owned | `--tint-sage` | `book_2` | plain |
  | Missing | `--tint-rose` | `priority_high` | **dashed** |
  | Upcoming | `--chip`, neutral | `schedule` | plain |

- ✅ A volume already owned opens its detail screen; a missing or upcoming one opens the
  two ways of getting it — **wishlist** or **collection** — in one gesture. The holes in
  the run are named under the grid.
- 🔜 Marking a volume as wished survives a reload: `SeriesVolumeDto` has no `wished` flag,
  so the marker is session-local for now.
- 🔜 Volume covers and titles: the grid shows numbers, since `/api/series/{id}` only names
  the volumes present in the shared catalog.

## 5. Key journeys

**P1 — Add a book spotted in a bookshop**
Discover → ISBN scan 🔜 → catalog page → pick a status → added to the collection.

**P2 — Resume reading**
Home → "Continue reading" carousel, each cover showing where the reader stands → Detail →
enter the current page or the percentage → progress updated everywhere.

**P3 — Complete a series**
Collection → Series view → incomplete series → missing volumes → added to the wishlist in
one gesture.

**P4 — Arrive on Librarius with an existing library**
Settings → Import → Booknode handle → titles matched against the catalog → collection
populated.

**P5 — Track the annual goal**
Settings/Stats → set the goal → gauge on the Home screen 🔜 → year in review at the end of
the year 🔜.

## 6. Business rules

1. A user can own the same edition **only once** (`UNIQUE(user, edition)`). A re-read is not
   a duplicate: it belongs to the reading history 🔜.
2. Statuses: `OWNED` (owned, unread) → `READING` → `READ`. Moving to `READ` sets
   `finished_at` and completes the position (100 %, last page); moving to `READING` sets
   `started_at` if it is empty. A date supplied by the user always wins over the default.
   The page and the percentage are two views of one position: the server derives whichever
   one the client left out, so no two screens can show different figures.
3. The **Gold / Silver / Bronze** ranks are built-ins (`user_id NULL`) and cannot be
   deleted. A user may create their own categories.
4. A title carries **at most one rank**.
5. The release dates shown are the **provider's** (often JP/EN) and must be flagged as such
   for as long as French release dates are unavailable.
6. Ownership data is **strictly private**: no resource ever returns another user's data.
   The rating and the review are the sharpest case: they live on the user's own
   `library_item`, are never shared, and are never folded into a score across accounts.
7. An import never creates a duplicate: matching by ISBN13, then by title + author.
8. Correcting the **edition** of an owned title changes the object, not the reading of it:
   status, rating, review, rank and the reading dates carry over untouched. The position
   carries over as a **percentage** — the only measure that survives a change of pagination —
   and the page is recomputed from the new page count. A user cannot own the same edition
   twice, so a switch onto one already in their collection is refused with a message.

## 7. Out of scope (explicit decisions)

- Social network (friends, library sharing, public comments).
- Lending books between users.
- Reading content (the app manages *ownership*, not files).
- Marketplace / built-in purchasing: prices are **entered** by the user, not fetched.
