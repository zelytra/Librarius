# Developer wiki (staged)

[GitHub wikis](https://github.com/zelytra/Librarius/wiki) are enabled on this repository,
but no page exists yet: GitHub only provisions the wiki's git repository
(`Librarius.wiki.git`) once a first page is saved through the web UI, and nobody has done
that manual, one-time step. Until then, the pages below live here instead, so the content
exists and can be reviewed like any other change.

## Pages

- [Home](Home.md) — landing page, links to everything else
- [Running the project locally](Running-Locally.md)
- [Architecture](Architecture.md)
- [Catalog & book search](Catalog-and-Book-Search.md)
- [Data model](Data-Model.md)
- [Contributing](Contributing.md)
- [Deployment](Deployment.md)

Each page is written for a developer who has never seen this codebase before. They
summarise and link back to [`docs/`](../) and
[`.claude/docs/`](../../.claude/docs/README.md), which stay the source of truth — nothing
here forks that content, so nothing here can drift from it unnoticed.

## Publishing these pages to the actual wiki

1. Open <https://github.com/zelytra/Librarius/wiki> and click **Create the first page**.
   Title it `Home`, put anything in the body (it is overwritten in the next step) and save.
   This is the one action that only works from the browser — it is what makes GitHub create
   the wiki's git repository. Nothing below works before this step.
2. Clone the newly created wiki repository and copy the pages into it:

   ```bash
   git clone https://github.com/zelytra/Librarius.wiki.git
   cp docs/wiki/Home.md docs/wiki/Architecture.md docs/wiki/Catalog-and-Book-Search.md \
      docs/wiki/Data-Model.md docs/wiki/Running-Locally.md docs/wiki/Contributing.md \
      docs/wiki/Deployment.md Librarius.wiki/
   cd Librarius.wiki
   git add .
   git commit -m "Import the developer wiki"
   git push
   ```

3. Update the "Developer wiki" link in [`README.md`](../../README.md) so it points at
   <https://github.com/zelytra/Librarius/wiki> instead of `docs/wiki/`.
4. Optional: keep `docs/wiki/` as the source you edit and re-push from later, or remove it —
   either is fine. It only exists to carry the content until step 1 happens.

## Why the links inside these pages look the way they do

A GitHub wiki is its own git repository, entirely separate from this one: a relative link
that works while these files sit under `docs/wiki/` in the main repository (`../ARCHITECTURE.md`,
say) would silently break once copied into the wiki, which has no such file next to it. So
every link in these pages is a full URL instead —
`https://github.com/zelytra/Librarius/blob/main/...` for files in this repository,
`https://github.com/zelytra/Librarius/wiki/...` for other wiki pages — which resolves
correctly in both places, staged here or published there.
