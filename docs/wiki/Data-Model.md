# Data model

Librarius keeps two kinds of data apart on purpose: a **shared catalog** — the books and
manga themselves — and **per-user ownership** — who owns or wants which one. Two readers of
the same novel point at the same catalog row; each still has their own, private answer to
"do I own it, have I read it, what did I think of it."

## The shared catalog

| Entity | What it is |
|---|---|
| `series` | An ordered grouping of works — "One Piece", a novel trilogy |
| `work` | The intellectual content: a novel, or *one volume* of a manga |
| `edition` | One materialisation of a work: a specific publisher, ISBN, language, format |
| `genre` | A normalised genre, decoupled from whatever free-text wording a provider used |

An `edition` with no known length carries a `page_count_estimate` (V28) — its series' average,
else a typical length for its kind — that every figure falls back on, and the publisher's
`list_price` read from the BnF record (V29), from which the library value is computed.

A `work` can have several `edition`s — a paperback and a hardcover of the same novel, say —
because entries are matched against the existing catalog before a new one is created. See
[Catalog & book search](https://github.com/zelytra/Librarius/wiki/Catalog-and-Book-Search)
for how that matching happens on the way in.

## What belongs to a user

| Entity | What it is |
|---|---|
| `app_user` | A Keycloak identity, provisioned on first login, with its Keycloak `username` (V40, #499, kept in step at each request) and `avatar_version` (V39) while it has a picture. No credential is stored here — Keycloak owns those |
| `user_avatar` | The member's profile picture (V39, #500): a 256 px JPEG the API wrote itself from the upload, never the upload. In the database like the cover thumbnails — a few tens of kB each, covered by the backups and the erasure with nothing else to run; one service (`AvatarService`) owns it, should it move to object storage one day |
| `library_item` | A title of the user's library: its own `price` (V30), `page_count` and `format` (V32), all private to the reader, reading status (`TO_READ`, `READING`, `READ`, `ABANDONED`), ownership (`OWNED`, `BORROWED`, `EBOOK`, `AUDIOBOOK`, `WISHLIST`, `NOT_OWNED` — V27, V33), rating (0.5 to 5 by half stars, a double checked in V34), review (private unless `review_shared`), acquisition date, rank |
| `library_view`, `library_view_entry` | A reader's saved library layouts (V33, #451) — kind, shelf, sort, `layout`, `group_by_category` — and the manual order of each: one entry per placed title (`item_id`) or series block (`series_id`), by `position`, cascading away with the view, the title or the series |
| `reading_progress` | Current page or percentage, start and finish dates — one row per `library_item` |
| `wishlist_item` | Priority, estimated price, a note |
| `series_follow` | "Tell me about the next volumes of this series" |
| `reading_goal` | An annual target for one kind of book (`kind`, unique per user, year and kind — V25), in books, volumes or pages |
| `cover_cache` | Resized BnF cover thumbnails by ark (`content` NULL = known miss) — V26 |
| `user_achievement` | One unlocked achievement tier (V31), `seen_at` once announced; the catalogue, XP and levels live in code |
| `user_activity` | One row per member and day they used the instance (V36, #491), `last_seen_at` moved forward at most once a minute, after the response — the monitoring's active members |
| `feed_event` | What a member did that the feed shows (V38, #209): `FINISHED_READING`, `WISHLIST_ADDED` or `REVIEW_SHARED` on an `edition`, once per member, type and edition. Logged where the action happens — finishing a title, adding a wish, sharing a review — never by an import or a bulk edit |
| `notification` | One line of a member's bell (V41, #516): `type` `ACTIVITY`, `REACTION`, `COMMENT` or `FOLLOW` (V42, #544: a new follower — no event; a follow after an unfollow replaces the earlier row), the `actor`, the `feed_event` and the `feed_comment` it is about, the `emote`, `read_at` once seen; cascades away with either account, the event or the comment. A new reaction from the same member replaces the previous notification. `user_follow.notify` (default true) and `app_user.notify_comments` (default true) say who wants which |
| `feed_reaction`, `feed_comment` | A member's reaction to a feed event (one per member and event, `PRIMARY KEY (feed_event_id, user_id)`) and their flat comments (V38, #210); both cascade away with the event or the account |
| `rank_category` | Gold / Silver / Bronze, built in and shared, plus any category a user defines |

`feedback` (V35, #489, #490) is the one table read across users, by the admins only: the
members' reports and the errors recorded by the web app and the API, one row per error
fingerprint with its `occurrences`, and where an admin stands with each (`status`, V37, #495:
`NEW`, `SEEN`, `HANDLED`, `IRRELEVANT`; an error marked handled that comes back is `NEW`
again). Its `user_id` is `ON DELETE SET NULL`: a report outlives
its author's account, which only forgets who sent it.

Every one of these carries a `user_id`, and every API resource filters on it: no query ever
returns another user's row. That is also what makes account deletion a single cascading
`DELETE` — see [Deployment](https://github.com/zelytra/Librarius/wiki/Deployment).

## How the schema is allowed to change

Hibernate runs in **validate** mode: it checks the schema matches the code and refuses to
start if it does not, but it never generates SQL itself. The schema exists only as
[Flyway](https://flywaydb.org/) migrations —
`apps/api/src/main/resources/db/migration/V<n>__description.sql` — applied in order and
never edited once merged, since Flyway checksums the whole file and touching an old one
breaks every database that already ran it. Changing the model always means a new migration,
generally shaped as: add the column **nullable**, backfill the data, then constrain it — in
that order, so a large table is never locked on a column that is not there yet.

The full table-by-table reference, including exactly which migration introduced which
column and what is still only planned, is
[`.claude/docs/DATA-MODEL.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/DATA-MODEL.md)
— the file a pull request that changes the schema is expected to update in the same commit.
