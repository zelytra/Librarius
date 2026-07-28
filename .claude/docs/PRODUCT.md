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
Two users who own the same volume point at the same `edition` but have two distinct
`library_item` rows.

## 4. Screens

### 4.1 Home ✅ / 🔜

Personal dashboard.

| Section | State | Detail |
|---|---|---|
| Header (date, greeting, Settings shortcut) | ✅ | Greeting to be made contextual (morning/evening) |
| Continue reading | ✅ | Carousel of `READING` titles — 🔜 show progress as a percentage |
| Counters (read / in progress / to read) | ✅ | |
| Upcoming releases | ✅ | 🔜 filter on the **series being followed** rather than on AniList trends |
| Recently read | ✅ | |
| Empty state | ✅ | Points to Discover |
| **Reorder / hide sections** | 🔜 | Persisted per user (`dashboard_layout`) |
| **Annual goal** | 🔜 | Progress gauge, the API already exists |

### 4.2 Collection ✅ / 🔜

The full inventory, with a **Books / Manga** toggle.

- ✅ Cover grid, rank badge, quick removal.
- ✅ Sorting: date added, title, author, genre. Filter by rank.
- 🔜 **Series view**: group by series, show "12 / 105 volumes", flag the missing volumes in
  a run the user owns.
- 🔜 Text search inside one's own collection.
- 🔜 Extra filters: status, year of acquisition, publisher, language.
- 🔜 Pagination / infinite scrolling (the API returns everything today).
- 🔜 Multi-selection → bulk action (change status, delete, assign a rank).

### 4.3 Title detail ✅ / 🔜

- ✅ Cover, title, authors, genres, pages, series, year, synopsis.
- ✅ Assigning a rank (Gold / Silver / Bronze).
- ✅ Marking "in progress" / "read".
- 🔜 **Progress input**: current page or percentage, start/finish dates.
- 🔜 **Personal rating** (1–5) and private review.
- 🔜 **Alternate editions**: list the other `edition` rows of the same `work`, let the user
  say which one they own.
- 🔜 **Series navigation**: previous / next volume, link to the series page.
- 🔜 Reading history (re-reads).

### 4.4 Discover ✅ / 🔜

Search across the external catalog (Open Library for books, AniList for manga).

- ✅ Keyword search, Book / Manga toggle, direct add to the collection or the wishlist.
- 🔜 Search **by author** and **by year** (announced in the vision, not implemented).
- 🔜 **ISBN barcode scanning** (camera) — the key feature in a bookshop, native through
  Capacitor.
- 🔜 A catalog page before adding: pick the *edition* and the initial *status*.
- 🔜 Personalised suggestions based on the genres most present in the collection.
- 🔜 Guided manual entry (for a book absent from the catalogs).

### 4.5 Wishlist ✅ / 🔜

- ✅ List with priority (Priority / Soon / Someday), estimated price, note, removal.
- 🔜 **Total budget** and budget per priority.
- 🔜 **Conversion to ownership** in one gesture (wish → collection, status `OWNED`).
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
- ✅ Theme selection.
- 🔜 **Profile**: display name, language, time zone.
- 🔜 **Library export** (CSV + JSON) — *GDPR requirement*.
- 🔜 **Account and data deletion** — *GDPR requirement*.
- 🔜 Notification preferences.
- 🔜 Custom category management (the API already exists).
- 🔜 Legal notice, terms of service, privacy policy.

### 4.8 Series 🔜 (screen to create)

The API is in place (`/api/series`, #44); what is left is the screen (#45, #46).

- Series page: cover, synopsis, status (ongoing / completed), total number of volumes.
- Volume grid: owned, read, missing, upcoming.
- A "follow this series" action → feeds upcoming releases and notifications.

## 5. Key journeys

**P1 — Add a book spotted in a bookshop**
Discover → ISBN scan 🔜 → catalog page → pick a status → added to the collection.

**P2 — Resume reading**
Home → "Continue reading" carousel → Detail → enter the current page 🔜 → progress updated.

**P3 — Complete a series**
Collection → Series view 🔜 → incomplete series → missing volumes → added to the wishlist in
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
   `finished_at`; moving to `READING` sets `started_at` if it is empty.
3. The **Gold / Silver / Bronze** ranks are built-ins (`user_id NULL`) and cannot be
   deleted. A user may create their own categories.
4. A title carries **at most one rank**.
5. The release dates shown are the **provider's** (often JP/EN) and must be flagged as such
   for as long as French release dates are unavailable.
6. Ownership data is **strictly private**: no resource ever returns another user's data.
7. An import never creates a duplicate: matching by ISBN13, then by title + author.

## 7. Out of scope (explicit decisions)

- Social network (friends, library sharing, public comments).
- Lending books between users.
- Reading content (the app manages *ownership*, not files).
- Marketplace / built-in purchasing: prices are **entered** by the user, not fetched.
