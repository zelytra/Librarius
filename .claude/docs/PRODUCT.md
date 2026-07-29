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

v1.0 target: a **public**, multi-user product, open sign-up, French first — and English
alongside it since [#77](https://github.com/zelytra/Librarius/issues/77), the copy still
being authored in French and translated.

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
| **Upcoming release** (`upcoming_release`) | An announced volume of a series, dated and labelled per market (FR/JP/EN) | Shared catalog |

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
| Upcoming releases | ✅ | Personalised to the series the reader owns, wishes on or follows, each date labelled with its market (FR/JP/EN) and its precision ([#57](https://github.com/zelytra/Librarius/issues/57)). No stake in anything → invites the reader towards their collection rather than an empty section. 🔜 admin/CSV ingestion of curated dates — entries are seeded by hand for now |
| Recently read | ✅ | |
| Empty state | ✅ | Points to Discover |
| **Reorder / hide sections** | ✅ | "Personnaliser l'accueil" panel: move a section up/down, toggle it hidden — buttons rather than a drag gesture, so the same controls work with a finger, a mouse or a keyboard. A hidden section stays listed there, marked, so it can be found again. Persisted per user (`dashboard_layout`, #54); a fresh account sees the same order the sections used to be hard-coded in, and a section shipped later shows up on its own |
| **Annual goal** | ✅ | Gauge, what is left, pace to hold; invitation when no goal is set, and the previous year's target offered on 1 January |

### 4.2 Collection ✅ / 🔜

The full inventory, with a **Books / Manga** toggle.

- ✅ Cover grid, rank badge — in the colour of the category the title is filed under —,
  quick removal.
- ✅ Sorting: date added, title, author, genre, rating. Filter by rank, and "my favourites"
  (rated 4 or more). The rank chips are **the categories the user has**, built-ins and their
  own alike, so a category created on the management screen becomes a shelf here; the row
  ends on the way into that screen (#51).
- ✅ **Series view**: one row per series with its cover, `12 / 105 volumes` and a progress
  bar, an *Incomplète* badge on a run with volumes left to buy, ordering by progress or
  title, and a way into the series screen. The kind switch and the search carry across the
  toggle; the rank and favourites chips apply to a title, not to a run, and are hidden there.
- ✅ **Text search** inside one's own collection: matched by the database against the
  title, the authors and the series, one request per pause in the typing.
- ✅ **Server-side pagination**, with a "voir plus" appending the next page — filtering,
  sorting and slicing all happen in SQL, so a 5000-title collection costs no more to
  display than a 50-title one.
- 🔜 Ordering the Series view by most recently added — `SeriesSummaryDto` carries no date.
- 🔜 Extra filters: status, year of acquisition, publisher, language. The genre filter
  exists in the API since #56 and is not offered by the screen.
- 🔜 Multi-selection → bulk action (change status, delete, assign a rank).

### 4.3 Title detail ✅ / 🔜

- ✅ Cover, title, authors, genres, pages, series, year, synopsis.
- ✅ Assigning a rank (Gold / Silver / Bronze).
- ✅ Marking "in progress" / "read".
- ✅ **Giving up on a title**: *"J'abandonne ce livre"*, offered while the title is owned or
  being read and on nothing else. It records the day the reader stopped and **keeps the
  position untouched** — the page reached is the point of the state. The screen then says
  so, and the main button becomes *"Reprendre la lecture"*: coming back to a book one gave
  up on is a normal thing to do ([#163](https://github.com/zelytra/Librarius/issues/163)).
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
- ✅ **Annual goal**: gauge, what is left, pace required.
- ✅ **Trend over time**: titles and pages finished per month, running total against the goal.
- ✅ Reading pace (pages/day), average time to finish a title, best month.
- ✅ Breakdown by author, publisher, language, rank.
- 🔜 A shareable year in review.

Everything over time is counted from the day a title was **finished**
(`reading_progress.finished_at`), so the history starts the day the user first marks
something as read — an existing collection carries no reading dates.

### 4.7 Settings ✅ / 🔜

- ✅ **Import**: Booknode **by handle**, and a **CSV file** — the two sources are not
  interchangeable. Babelio has no API and a member's shelves need a session, so there is
  nothing to fetch from a handle: picking Babelio offers no handle field at all, it says the
  library is not public and points at the CSV export, one click below. Only Booknode is
  scraped.
- ✅ **Dashboard** shortcut to Home's own "Personnaliser l'accueil" panel (§ 4.1) — Settings
  is not where the reordering happens, but a reader would not necessarily think to look on
  Home for it.
- ✅ Theme selection: four palettes (Crème, Sauge, Rose, Nuit) plus **Système**, which
  follows the operating system preference and is the default. The choice is kept in
  `localStorage` and applied before the first paint, so a reload never flashes the light
  theme.
- ✅ **Language**: French and English, labelled in their own words (*Français*, *English*)
  so someone who landed on the wrong one still recognises theirs. A first visit follows the
  browser; the choice made here overrides it and is kept in `localStorage`, per device.
- 🔜 **Profile**: display name, language, time zone. The language moves to the profile there
  and follows the account across devices; the switcher above stays as the way to change it,
  and the local copy as what the app boots on before the profile has been fetched.
- ✅ **Library export** (JSON + CSV) — *GDPR art. 20*. The JSON archive holds everything
  and can be re-imported here; the CSV opens in a spreadsheet and carries the column names
  Goodreads and Booknode understand, so a user can leave for another tool.
- ✅ **Account and data deletion** — *GDPR art. 17*. Behind a confirmation that spells out
  what goes, what stays in the shared catalog and how long the encrypted backups keep it,
  asks the user to type their own username, and offers the export first. The Keycloak login
  goes with the data; if it cannot, nothing is erased and the screen says so.
- 🔜 Notification preferences.
- 🔜 Legal notice, terms of service, privacy policy.

Category management lives on its own screen (§ 4.9) rather than here: it is reached from
the Collection, where the categories are used, and not from a settings list nobody visits
while looking at their shelves.

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

### 4.9 Categories ✅ / 🔜

`/categories`, reached from the shelf row of the Collection — where the user is standing
when they realise they want another shelf.

- ✅ The list of the categories the user has: the four built-ins first — *Or*, *Argent*,
  *Bronze* and *Abandon* — flagged *Intégrée* and offering no action, then their own.
- ✅ Creating one by name, renaming one, deleting one.
- ✅ Deleting asks for a confirmation that **says what it costs**: the titles filed under the
  category stay in the collection and lose only their rank. That is not something to
  discover after the fact.
- ✅ A name already used is reported ("Tu as déjà une catégorie de ce nom.") instead of
  failing silently.
- 🔜 Reordering the categories: `sort_order` exists in the schema, every custom one is
  created at 100 and the list falls back to alphabetical order.
- 🔜 Picking a colour. A custom category gets a neutral one; the built-ins keep the gold,
  silver and bronze of V1, and the muted grey V11 gave *Abandon*.

### 4.10 Waiting, on every screen ✅ / 🔜

Not a screen: what every screen shows while it has nothing yet.

- ✅ **One loading indicator, two formats** — a large one where a screen has nothing to
  show, a compact one inside the control an action is running from. Both are the same
  component, so the animated logo that replaces today's placeholder ring lands everywhere
  at once ([#169](https://github.com/zelytra/Librarius/issues/169)).
- ✅ **It only shows up when there is something to wait for**: nothing appears under
  ~400 ms, and once it has appeared it stays about as long. Most calls answer inside that
  window and therefore flash nothing at all — an indicator that comes and goes inside the
  blink it was meant to explain reads as a glitch. Motion is dropped entirely for a reader
  who asks for reduced motion.
- ✅ **Actions say they are running.** Adding a title by hand, importing a library, editing
  a wish, and the Detail screen's progress, rank, review and edition switch all surface
  their wait; the buttons keep their label instead of swapping it for an ellipsis.
- ✅ **A welcome screen rather than a line of grey text** while the session is being
  resolved, and while it is missing: the application names itself, says what it is, and
  then either waits or invites the reader to sign in — the same screen at two moments, not
  an error ([#170](https://github.com/zelytra/Librarius/issues/170)). It matters most on
  the mobile shell, which opens straight onto whatever route the router lands on.
- 🔜 The animated logo itself, in place of the placeholder ring.

### 4.11 Widths, on every screen ✅ / 🔜

Not a screen either: how much of the window the application is allowed to use.

- ✅ **A desktop window is no longer a phone on an empty desk.** Under 600 px the app is
  what it has always been, a column capped at 440 px. From 600 px it stops being a card
  floating in the middle of an empty page and fills the window, up to a page of 1 140 px
  that then stays centred whatever the monitor
  ([#171](https://github.com/zelytra/Librarius/issues/171)).
- ✅ **Nothing changes on a phone.** The widths are reasoned from the reader's own content
  — a shelf, a grid of covers — so a cover keeps the size it has today as the layout
  crosses over: three columns of 124 px at 599 px, four of 127 px at 600 px.
- 🔜 The screens themselves still lay their content out in one column, and the navigation
  is still a bottom bar: that is #172 to #175, which the widths above exist to serve.

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
Coming from **Babelio** the first step differs, and only the first: Settings → Import →
Babelio → export the list from Babelio → import that CSV file → same matching, same
collection. Nothing is fetched from a Babelio handle, and the screen says so before
anything is submitted.

**P5 — Track the annual goal**
Settings → set the goal → gauge on the Home screen → year in review at the end of the
year 🔜.

## 6. Business rules

1. A user can own the same edition **only once** (`UNIQUE(user, edition)`). A re-read is not
   a duplicate: it belongs to the reading history 🔜.
2. Statuses: `OWNED` (owned, unread) → `READING` → `READ`, plus `ABANDONED` for a title
   given up on partway through. Moving to `READ` sets `finished_at` and completes the
   position (100 %, last page); moving to `READING` sets `started_at` if it is empty and
   clears `finished_at` — a title being read again is not a finished one. A date supplied by
   the user always wins over the default. The page and the percentage are two views of one
   position: the server derives whichever one the client left out, so no two screens can
   show different figures. `started_at` and `finished_at` are what the annual goal and the
   reading timeline are counted from.
   **Abandoning** sets `finished_at` — the day the reader stopped — and leaves the position
   exactly where it was: a book put down at page 120 was read up to page 120, and rounding
   it up to 100 % would erase the only thing the status records. It is therefore **not** a
   variety of "read": an abandoned title advances no goal, fills no timeline bucket and
   counts in none of the read / in progress / to read figures, but keeps a counter of its
   own. There is a way back — picking a book up again is the ordinary move to `READING`,
   and one given up on can still be marked `READ`.
3. The **Gold / Silver / Bronze** ranks are built-ins (`user_id NULL`), and so is
   **Abandon**, the shelf a title given up on is filed under: shared by every account, they
   can be neither renamed nor deleted. Filing a title under *Abandon* and marking it
   `ABANDONED` are two separate gestures — a rank says what the reader thinks of a book, a
   status where they are in it — and neither implies the other. A user creates as many categories of
   their own as they like; they are **private** — invisible and unassignable to anyone else
   — and their names are unique per user, so two accounts may both have a "Coup de cœur".
   **Deleting a category never deletes a title**: the titles filed under it stay in the
   collection, unranked.
4. A title carries **at most one rank**.
5. A release date is never shown without the **market** it belongs to (FR/JP/EN) — the
   generic catalog trends (`/api/catalog/upcoming`) are the provider's, often JP/EN, and are
   captioned as indicative as a whole; the personalised releases
   (`/api/releases/upcoming`, [#57](https://github.com/zelytra/Librarius/issues/57)) label
   each date individually, since French and original-edition dates can sit side by side in
   the same list.
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
