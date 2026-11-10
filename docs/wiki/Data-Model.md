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

A `work` can have several `edition`s — a paperback and a hardcover of the same novel, say —
because entries are matched against the existing catalog before a new one is created. See
[Catalog & book search](https://github.com/zelytra/Librarius/wiki/Catalog-and-Book-Search)
for how that matching happens on the way in.

## What belongs to a user

| Entity | What it is |
|---|---|
| `app_user` | A Keycloak identity, provisioned on first login. No credential is stored here — Keycloak owns those |
| `library_item` | "I own this edition": status, rating, private review, acquisition date, rank |
| `reading_progress` | Current page or percentage, start and finish dates — one row per `library_item` |
| `wishlist_item` | Priority, estimated price, a note |
| `series_follow` | "Tell me about the next volumes of this series" |
| `reading_goal` | An annual target, in books, volumes or pages |
| `rank_category` | Gold / Silver / Bronze, built in and shared, plus any category a user defines |

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
