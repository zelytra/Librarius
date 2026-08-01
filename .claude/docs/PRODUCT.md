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
| **Work** (`work`) | The intellectual content: a novel, *one volume* of a manga, a comic (BD), a graphic novel or an audiobook — its `kind` (V15/#178) | Shared catalog |
| **Edition** (`edition`) | One materialisation: ISBN, publisher, language, page count, cover, format | Shared catalog |
| **Series** (`series`) | An ordered grouping of works ("One Piece", "A Song of Ice and Fire") | Shared catalog |
| **Author** (`author`) | A person credited on works, related to a work through `work_author` | Shared catalog |
| **Follow** (`series_follow`, `author_follow`) | The user ↔ series and user ↔ author links: "tell me about what comes next" | User |
| **Member follow** (`user_follow`) | The user ↔ user link: "I want to keep an eye on this member". One-directional; a mutual pair is what "friends" means. Distinct from the two follows above — those point at the catalog, this points at another account ([#200](https://github.com/zelytra/Librarius/issues/200)) | User |
| **Library item** (`library_item`) | The user ↔ edition link: status, rating, acquisition date, rank | User |
| **Progress** (`reading_progress`) | Current page / percentage, start and finish dates | User |
| **Wish** (`wishlist_item`) | Priority, estimated price, note | User |
| **Rank** (`rank_category`) | Gold / Silver / Bronze + custom categories | Shared (built-ins) + user |
| **Reading goal** (`reading_goal`) | Annual target in books, volumes or pages | User |
| **Upcoming release** (`upcoming_release`) | An announced volume of a series, dated and labelled per market (FR/JP/EN) | Shared catalog |

An author is one row per **spelling that folds alike**
([#182](https://github.com/zelytra/Librarius/issues/182)): "Isaac Asimov", "isaac asimov" and
"ISAAC ASIMOV" are one person, and so are "J.R.R. Tolkien" and "J. R. R. Tolkien". Nothing
goes further than that — two writers of the same name are one row, "A. Damasio" is not
"Alain Damasio", and a name written "Damasio, Alain" splits on the comma. Saying so matters
more than the mechanism: the author page ([#199](https://github.com/zelytra/Librarius/issues/199))
must not read as an identity nobody has established.

The API that page will read is live ([#196](https://github.com/zelytra/Librarius/issues/196)):
`/api/authors` searches authors by name and opens any of them by id — bibliography, photo and
a per-user **Follow**, exactly the pair the series already has. Unlike a series, an author is
a **catalog browser**: they can be found and opened whether or not the reader owns anything of
theirs, because an author is meant to be discovered, not recognised only once collected. The
bibliography lists what Librarius's own catalog credits them with; the fuller list a provider
knows is a later, on-demand enrichment.

**Structural rule**: the catalog is **shared** across all users, ownership is **private**.
Two users who own the same title point at the same `work` — entries are matched against the
catalog before a work is created — but keep two distinct `library_item` rows. They point at
the same `edition` only when they entered the same one; two printings of one novel are two
`edition` rows under one `work`, which is what the Detail screen lets a user choose between.

**Members follow each other** (v1.2, [#200](https://github.com/zelytra/Librarius/issues/200)):
a member can follow another member and unfollow them, and read the two lists of their own
account — who they follow, and who follows them. Following is **immediate and
one-directional** (no request to accept), and a "friend" is simply the case where both sides
follow each other. It is the first link the product draws between two accounts, and on its own
it unlocks nothing: what a **mutual** follow reveals — a member's collection, ranks and
reviews, once they have opted their account public — is
[#201](https://github.com/zelytra/Librarius/issues/201), and the screens to **find people**
and browse these lists are [#202](https://github.com/zelytra/Librarius/issues/202). Today the
relationship and its API exist; the follow button they carry is all the interface #200 adds. A
list never exposes more than a member's display name — never their email — and each member
only ever sees their own two lists.

**Who may see whose content** (v1.2, [#201](https://github.com/zelytra/Librarius/issues/201)):
the follow relationship above now decides visibility. By default an account is **private** —
another member sees its shared reviews, reading activity and library **only when the two follow
each other** (a mutual follow; a one-way follow reveals nothing). A member who prefers to be open
can turn their account **public** — a single opt-in preference, off by default, set in Settings
alongside the display name and language — and then any signed-in member can see their shared
content, no follow needed either way. Two things never depend on this: a member's display name
and trusted badge are visible to anyone signed in (the minimal surface a find-people search
needs), and a member's email, locale, time zone and *private* rating/review are visible to no
one but themselves. An account a caller may not see behaves as if it does not exist — the same
answer whether the id is unknown or simply out of reach — so the product confirms nothing about
who has an account. This step ships the **preference and the rule** only; the screens that let a
member *browse* other people (#202) and the places that actually surface a member's reviews
(#205) and reading feed (#209) are their own issues, each reusing this one rule.

## 4. Screens

### 4.1 Home ✅ / 🔜

Personal dashboard.

| Section | State | Detail |
|---|---|---|
| Header (date, greeting, Settings shortcut) | ✅ | Greeting to be made contextual (morning/evening) |
| Continue reading | ✅ | Carousel of `READING` titles, each cover carrying its progress |
| **To-read pile ("PAL")** | ✅ | Carousel of `OWNED` titles — owned and never opened, so neither what is being read nor what was given up on ([#166](https://github.com/zelytra/Librarius/issues/166)). One page of the pile, the header reporting how big the whole of it is; absent, rather than empty, when nothing is waiting |
| Counters (read / in progress / to read) | ✅ | |
| **Stack of books read** | ✅ | The "read" counter drawn out ([#181](https://github.com/zelytra/Librarius/issues/181)): a pile built from the design tokens, beside the number of books, the pages and the paper they come to in centimetres or metres. One spine per book up to eight, then one more per doubling and capped at fourteen, so three books and four hundred both read; the height is labelled as an estimate and names the sheet thickness it assumes. Absent, rather than empty, on a library with nothing read yet |
| Upcoming releases | ✅ | Personalised to the series the reader owns, wishes on or follows, each date labelled with its market (FR/JP/EN) and its precision ([#57](https://github.com/zelytra/Librarius/issues/57)). No stake in anything → invites the reader towards their collection rather than an empty section. 🔜 admin/CSV ingestion of curated dates — entries are seeded by hand for now |
| Recently read | ✅ | |
| Empty state | ✅ | Points to Discover |
| **Reorder / hide sections** | ✅ | "Personnaliser l'accueil" panel: move a section up/down, toggle it hidden — buttons rather than a drag gesture, so the same controls work with a finger, a mouse or a keyboard. A hidden section stays listed there, marked, so it can be found again. Persisted per user (`dashboard_layout`, #54); a fresh account sees the same order the sections used to be hard-coded in, and a section shipped later shows up on its own |
| **Annual goal** | ✅ | Gauge, what is left, pace to hold; invitation when no goal is set, and the previous year's target offered on 1 January |
| **First-login onboarding** | ✅ | A short, three-step, dismissible tour ([#76](https://github.com/zelytra/Librarius/issues/76)): import an existing library, discover titles, set a reading goal — each step's action leaves the tour for the screen it names. Shown once, to a **new** account only — an empty collection with the local "seen" flag unset — and never again once dismissed, skipped or finished, whichever comes first. A returning user with any library never sees it. Replayable on purpose from Settings, which bypasses the emptiness check: that trigger is a deliberate request, not the automatic first-login one |

### 4.2 Collection ✅ / 🔜

The full inventory, one shelf per support type.

- ✅ **A shelf switch per kind the catalog exposes**
  ([#183](https://github.com/zelytra/Librarius/issues/183)): the two-option Books / Manga
  toggle became a segmented control offering one option per kind the caller actually owns
  something of — comics, graphic novels and audiobooks join book and manga once a title of
  that kind exists. A hard separator, as before: switching kind replaces the shelf rather
  than filtering within a mixed one. No dedicated endpoint backs this — the client asks
  `GET /api/library?kind=` once per kind of the taxonomy (`size: 1`, only the `total` of
  the envelope matters) and keeps only the ones with something in them; an account that
  owns nothing yet falls back to the first kind. The shelf that opens first is the one the
  user owns the most of, resolved from those same counts, a tie or an empty collection
  falling back to the first kind of the taxonomy — never a hardcoded `'BOOK'`. Each kind
  gets its own cover tag through i18n.
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
- ✅ **Assigning a rank**: every category the caller has — the four built-ins and their own —
  rather than the three metals the row was first written for. Choosing the one in force
  clears it.
- ✅ Marking "in progress" / "read".
- ✅ **The end-of-reading sheet**: finishing a title, or giving up on one, records the status
  and then opens a single sheet asking for a star rating and a shelf
  ([#164](https://github.com/zelytra/Librarius/issues/164),
  [#165](https://github.com/zelytra/Librarius/issues/165)). Both answers are optional and
  written only on confirmation, so a sheet dismissed leaves the title exactly as the button
  left it. An abandonment opens on the built-in *Abandon* shelf — a starting point, not a
  constraint — and the sheet writes no reading position, which is what keeps the page the
  reader stopped on. Marking a title read again later asks nothing: the moment belongs to
  the transition.
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
- ✅ / 🔜 **Enriching the editions from the providers**
  ([#197](https://github.com/zelytra/Librarius/issues/197)): when a work carries a provider
  reference (V12), the section also lists the other printings that provider knows and nobody
  here entered — each with its own cover — merged next to the stored editions and
  deduplicated on the ISBN. A provider printing is a catalog suggestion, not a row: it is
  shown for the printing it adds, without the *"C'est l'édition que je possède"* action, which
  moves the collection onto an edition that exists. The call is cached and rate-limited like a
  search, and is best-effort — a work with no reference, or a provider that answers nothing or
  is down, shows the stored editions exactly as before, never an error. 🔜 It is dormant in
  practice until a provider stores a usable reference: Open Library returns none yet, so no
  real work is enriched for now — see [API](API.md) gap A12.
- ✅ **Report an error** (*"Signaler une erreur"*)
  ([#192](https://github.com/zelytra/Librarius/issues/192)): a quiet entry point at the foot of
  the screen opens a small dialog with a short reason picklist — wrong cover, wrong
  information, duplicate, other — and an optional comment. It flags the shared **work** the
  title belongs to, not the caller's private copy, since an error in the data is everyone's.
  The report is a private signal: it is sent and confirmed, never shown back to anyone, and
  feeds the automatic trust revocation now wired for this milestone
  ([#195](https://github.com/zelytra/Librarius/issues/195)): an account that collects too many
  *upheld* reports against its contributions loses its trusted standing. A report only weighs
  once a moderator upholds it, and the moderation screen that does so is not built yet — so the
  revocation waits on it before it can act on real reports.
- 🔜 **Series navigation**: previous / next volume.
- 🔜 Reading history (re-reads).

### 4.4 Discover ✅ / 🔜

Search across the external catalog (Open Library and the BnF for books, AniList for manga).

- ✅ **One result feed across every medium**
  ([#194](https://github.com/zelytra/Librarius/issues/194)): the plain search box, and the
  ISBN auto-detection sitting behind it, query every registered provider at once — no
  up-front Books/Manga toggle to clear before typing a word. Each hit names its own medium
  next to its author and year, since a mixed feed no longer says it implicitly through a
  screen-wide toggle. "Add to library" and "add to wishlist" send that result's own kind,
  never a screen default.
- ✅ **Advanced search**: author, year, language, publisher, folded away behind a toggle.
  The criteria a provider does not index are ignored rather than faked, and the panel says
  so — see [API](API.md#catalog-search) for what each one honours. The same panel carries an
  optional **medium filter**, a multi-select over the whole taxonomy next to the language
  field: left untouched, the search still reaches every medium; naming one or several
  narrows it, sending the same repeatable `kind` the API takes. Filtering to a medium with
  no provider registered yet (comic, graphic novel, audiobook) is a legal choice that
  answers nothing, same as any other criterion no provider currently honours.
- ✅ **ISBN recognised in the search field**: pasting the number off a back cover searches
  the ISBN rather than the digits as keywords. The check digit is verified, so a barcode
  or an order number still goes through as an ordinary search.
- ✅ **Manual entry** for a title absent from the catalogs — self-published, an old
  edition, a fanzine. Offered from both empty states, which is where the user meets it, and
  picks its own medium explicitly: the form no longer inherits an ambient screen-level kind,
  since #194 removed the toggle it used to read one from.
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
- ✅ **Replay the first-login tour** ([#76](https://github.com/zelytra/Librarius/issues/76)):
  a link back to the onboarding flow (§ 4.1) for whoever skipped it too fast, regardless of
  whether the collection is still empty.
- ✅ Theme selection: four palettes (Crème, Sauge, Rose, Nuit) plus **Système**, which
  follows the operating system preference and is the default. The choice is kept in
  `localStorage` and applied before the first paint, so a reload never flashes the light
  theme.
- ✅ **Language**: French and English, labelled in their own words (*Français*, *English*)
  so someone who landed on the wrong one still recognises theirs. A first visit follows the
  browser; the choice made here overrides it and is kept in `localStorage`, per device.
- ✅ **Profile**: display name, language and time zone, saved to the account through
  `PATCH /api/me` (#75). The language moves to the profile and follows the account across
  devices — a save applies it at once and stores it as this device's boot value; the switcher
  below stays as the quick, device-local toggle the app boots on before the profile has been
  fetched, and the one a signed-out visitor still has. The time zone is an IANA identifier, or
  blank to follow the device.
- ✅ **Trusted badge** ([#186](https://github.com/zelytra/Librarius/issues/186)): a small
  pill next to the display-name field, shown only when the account's server-computed
  `trusted` flag (#180) is `true` — nothing renders otherwise, no "not yet trusted"
  messaging. Icon and text together, never colour alone, the same state-encoding rule the
  Series volume grid follows below (§ 4.8). Display-only: no screen or form can set it, and
  this is the one place it appears today, since Settings is the only screen showing a
  display name before the v1.2 follow lists and profiles grow one.
- 🔜 **Public account** ([#201](https://github.com/zelytra/Librarius/issues/201)): a single
  opt-in toggle in the profile section deciding whether the account is public (its shared
  content visible to any signed-in member) or private (visible only through a mutual follow).
  The preference and the `PATCH /api/me` field ship now, defaulting to off; the toggle control
  itself is the front-end follow-up that lands with the profile screens (#202).
- ✅ **Library export** (JSON + CSV) — *GDPR art. 20*. The JSON archive holds everything
  and can be re-imported here; the CSV opens in a spreadsheet and carries the column names
  Goodreads and Booknode understand, so a user can leave for another tool.
- ✅ **Account and data deletion** — *GDPR art. 17*. Behind a confirmation that spells out
  what goes, what stays in the shared catalog and how long the encrypted backups keep it,
  asks the user to type their own username, and offers the export first. The Keycloak login
  goes with the data; if it cannot, nothing is erased and the screen says so.
- 🔜 Notification preferences.
- 🔜 Legal notice, terms of service, privacy policy.

Category management lives on its own screen (§ 4.10) rather than here: it is reached from
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
- ✅ **Report an error** (*"Signaler une erreur"*)
  ([#192](https://github.com/zelytra/Librarius/issues/192)): the same dialog as the title
  detail, here flagging the shared **series** — a wrong cover or title, a duplicated run.
  Write-only and private, exactly as on the detail screen.
- 🔜 Marking a volume as wished survives a reload: `SeriesVolumeDto` has no `wished` flag,
  so the marker is session-local for now.
- 🔜 Volume covers and titles: the grid shows numbers, since `/api/series/{id}` only names
  the volumes present in the shared catalog.

### 4.9 Author ✅ / 🔜

`/authors/:id`, reachable from any resolvable author name on the Detail screen, and from a
minimal search local to Discover ([#199](https://github.com/zelytra/Librarius/issues/199)).

- ✅ Header: portrait — the photo the shared catalog carries, or a coloured block of the
  author's initials when it has none, the same spirit as `Cover`'s fallback for a title
  with no image — name, the count of works the local catalog credits them with, and a
  **Follow** toggle in the same visual language as Series's. The toggle's state comes from
  re-reading the author after the mutation, not from a local flip, and so survives a
  reload.
- ✅ **Bibliography**, in the shared cover grid Collection draws its own titles in. Unlike
  Series's volume grid, a tile here opens nothing: the author page is a catalog browser,
  and a title on it is not necessarily one the caller owns.
- ✅ **A known author is never "not found."** Unlike a series, an author is shared-catalog
  data with no ownership gate (see [API](API.md) § Authors): a 404 only ever means the
  identifier itself is unknown.
- ✅ **Author search**, local to Discover: `GET /api/authors?q=` behind one field, each hit
  leading into its page. Deliberately minimal — this issue does not redesign navigation or
  the bottom nav, [#57](https://github.com/zelytra/Librarius/issues/57) is where a proper
  discovery surface is planned.
- ✅ **Linked from Detail**: `BookView.authors` is a free-text credit line, not a list of
  identifiers, so each name is resolved against the same local search and rendered as a
  link only when it matches — a name the shared catalog does not know (a work recorded
  before #182 backfilled it, or a spelling the fold does not fit) stays plain text rather
  than a link that would 404.
- 🔜 **"Tell me about what comes next"**: following an author records the relationship, but
  nothing reads it yet — the upcoming-releases feed (#57) still keys only on a followed
  series. Extending it to authors is a follow-up, not part of this issue.
- 🔜 A provider's fuller bibliography — [API](API.md) gap A13, the same shape of gap as the
  editions enrichment (#197).

### 4.10 Categories ✅ / 🔜

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

### 4.11 Waiting, on every screen ✅ / 🔜

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

### 4.12 Widths, on every screen ✅ / 🔜

Not a screen either: how much of the window the application is allowed to use.

- ✅ **A desktop window is no longer a phone on an empty desk.** Under 600 px the app is
  what it has always been, a column capped at 440 px. From 600 px it stops being a card
  floating in the middle of an empty page and fills the window, up to a page of 1 140 px
  that then stays centred whatever the monitor
  ([#171](https://github.com/zelytra/Librarius/issues/171)).
- ✅ **Nothing changes on a phone.** The widths are reasoned from the reader's own content
  — a shelf, a grid of covers — so a cover keeps the size it has today as the layout
  crosses over: three columns of 124 px at 599 px, three of 144 px at 600 px.
- ✅ **A desktop is navigated from the side, not from the bottom.** From 600 px the tab bar
  is replaced by a persistent column: a narrow rail of icons and labels up to 1 120 px, a
  full sidebar past it, both carrying the same five destinations plus a direct entry to
  Settings — which on a phone is still only reachable from Home's header
  ([#172](https://github.com/zelytra/Librarius/issues/172)). It stays visible on Detail,
  Series and Settings, where the bar hides itself: on a wide window nothing is gained by
  taking it away, and losing it would leave browser-back as the only way out.
- ✅ **Home and Collection use the width they are given.** ([#173](https://github.com/zelytra/Librarius/issues/173)).
  Both opt into the shared `Grid` primitive rather than a screen-specific layout: Collection's
  flat view draws the same cover grid past 600 px that it always has, just with as many
  columns as the width holds instead of three; its Series view turns its single-column list
  of runs into a multi-column one, `--grid-panel-min` sizing the cards. Home's two shelves
  ("Reprendre la lecture", "Derniers lus") stop being a scroller a mouse has to drag once
  the width holds more than one row's worth of covers, and wrap instead — the same reasoning
  #172 used for the navigation: which layout to *render* is a JS decision
  (`useViewportAtLeast('tablet')`), how it is *drawn* stays in the tokens. Nothing changes
  under 600 px in either screen.
- 🔜 Discover and the remaining screens still lay their content out in one column: that is
  #174 and #175, which the widths and the navigation above exist to serve.

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
9. **Trust is earned, never granted — and can be lost**
   ([#180](https://github.com/zelytra/Librarius/issues/180),
   [#195](https://github.com/zelytra/Librarius/issues/195)). An account carries a private
   "trusted" standing that the application computes on its own, from the account's own activity —
   enough tenure, enough titles actually finished, and a record clear of upheld reports. A user
   never sets it on themselves and no user grants it to another: it is decided off the request
   path by a scheduled evaluation, and no screen or endpoint can flip it. The same evaluation now
   also **takes the flag back**: an account that collects too many upheld reports against what it
   contributed loses its standing, and earns it back later if the reports age out or are
   dismissed — revocation is a state, not a ban. What "trustworthy" precisely means, and how many
   upheld reports is "too many", are thresholds meant to be retuned, not fixed lines. The flag is
   surfaced as a badge next to the caller's own display name in Settings
   ([#186](https://github.com/zelytra/Librarius/issues/186), § 4.7). **One piece is still
   maintainer-gated**: a report only counts once a moderator marks it *upheld*, and the
   moderation surface that does so lives behind an admin role that is not built yet — so today the
   evaluation grants in practice and the revocation path, though wired and tested, waits on that
   surface (and on the contribution attribution of
   [#198](https://github.com/zelytra/Librarius/issues/198)) to have real reports to act on.

## 7. Out of scope (explicit decisions)

- Social network beyond the v1.2 follow: member-to-member following ships (#200), and
  mutual-follow visibility of a public account is #201 — but library sharing at large,
  messaging and public comments stay out.
- Lending books between users.
- Reading content (the app manages *ownership*, not files).
- Marketplace / built-in purchasing: prices are **entered** by the user, not fetched.
