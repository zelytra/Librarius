<!-- markdownlint-disable MD041 -->
<!-- Title: a conventional commit in English, e.g. `feat(web): …`, `fix(api): …`, `docs: …`.
     Pull requests are squash-merged, so the title becomes the commit on `main`. -->

## Why

<!-- The problem this solves. -->

Closes #

## What changed

<!-- The change and the reasoning behind it, not a list of files. -->

## Checklist

- [ ] A test was written first and locks the new behaviour (or reproduces the bug)
- [ ] Web quality gate is green: `pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build`
- [ ] API is green: `cd apps/api && ./mvnw -B verify`
- [ ] OpenAPI client regenerated, if a resource or DTO changed
- [ ] Matching `docs/wiki/` page updated, if behaviour it describes changed
- [ ] Checked in a real browser at phone width, if a screen changed (screenshot below)
