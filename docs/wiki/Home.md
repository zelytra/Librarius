# Librarius developer wiki

Librarius is a personal library manager for **books and manga**: collection, reading
progress, wishlist and catalog discovery, behind a React PWA talking to a Quarkus API and a
PostgreSQL database, with Keycloak for sign-in. The interface is French-only today; this
wiki, like the rest of the codebase, is in English.

A staging build runs at <https://librarius.zelytra.fr> — open sign-up, so the fastest way to
see the product is to create an account there. This wiki is about the code behind it.

## Where to start

Read in order if the project is entirely new to you; jump straight to a page if you already
know what you need.

1. **[Running the project locally](https://github.com/zelytra/Librarius/wiki/Running-Locally)**
   — get the app on your machine in four commands.
2. **[Architecture](https://github.com/zelytra/Librarius/wiki/Architecture)** — the web,
   api, database and auth layers, why each technology was picked, and how the repository is
   laid out.
3. **[Catalog & book search](https://github.com/zelytra/Librarius/wiki/Catalog-and-Book-Search)**
   — where book and manga data comes from, and how a search is resolved between providers.
4. **[Data model](https://github.com/zelytra/Librarius/wiki/Data-Model)** — the entities the
   product is built from, and how the schema is allowed to change.
5. **[Contributing](https://github.com/zelytra/Librarius/wiki/Contributing)** — git flow,
   commit and pull request conventions, the checks that have to pass before a push.
6. **[Deployment](https://github.com/zelytra/Librarius/wiki/Deployment)** — how a merge
   becomes a running instance, in short.

## Beyond this wiki

These pages summarise and link back to
[`docs/`](https://github.com/zelytra/Librarius/tree/main/docs) and
[`.claude/docs/`](https://github.com/zelytra/Librarius/tree/main/.claude/docs), which are
kept up to date as the code changes and stay the source of truth — if anything here ever
disagrees with them, they are right. `.claude/docs/` reads as working notes rather than an
introduction, because it is written for an AI coding agent picking up a task; filling that
gap for a human is what this wiki is for.

End-user help is a separate, not yet built effort
([issue #81](https://github.com/zelytra/Librarius/issues/81)) — everything in this wiki is
for contributors.
