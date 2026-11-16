# Contributing

The full, exact rules live in
[`CLAUDE.md`](https://github.com/zelytra/Librarius/blob/main/CLAUDE.md) and
[`.claude/docs/CONVENTIONS.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/CONVENTIONS.md)
at the root of the repository — this page is the short version.

## Engineering rules

- **No over-engineering**: the smallest change that solves the request, with what the
  repository already has; no speculative abstraction, option or dependency.
- **Good practices**: clear names, one responsibility per unit, one source of truth, no dead
  code, errors handled where they can be acted on.
- **TDD**: the failing test first, then the least code that passes it, then the refactor. A
  bug is reproduced by a test before it is fixed.
- **Regression tests**: every behaviour, edge case, error path and cross-user case a change
  touches is tested; a test is never deleted, skipped or loosened to get green.
- **UI/UX**: design tokens and shared components, WCAG 2.1 AA, feedback for every action,
  mobile first, and a look in a real browser at phone width before the pull request.

## Git flow

There is no `develop` branch. Every change is a branch cut from an up-to-date `main`, opened
as a pull request against `main` directly:

```text
feature/*   ──●──●──●
               ↘  ↘  ↘
main        ────●──●──●──────────●──────────►  merge → deploy to staging
                               v1.0.0          tag   → deploy to production (not open yet)
```

- No direct commits on `main`, no force-push.
- Branch names follow `feature/<topic>`, `fix/<topic>`, `docs/<topic>`, `ci/<topic>`.
- Pull requests are **squash-merged only** — merge commits are refused — so the pull
  request title becomes the commit subject on `main`. Write it accordingly.
- CI has to be green before a merge, no exceptions.

## Commits and pull requests

Conventional commits, in English, imperative mood: `feat(web): …`, `fix(api): …`,
`docs: …`. Common scopes are `web`, `api`, `db`, `infra`, `deploy`, `ci`, `docs`, `mobile`.
A commit body explains **why**, not what — the diff already shows what changed.

Pull requests follow the same title format and a short template: what was wrong, what the
change does and why, and a checklist of what was verified. `Closes #<issue>` in the
description closes the issue automatically on merge.

## Language

Code, comments, commit messages, pull requests and this wiki: **English**. Two deliberate
exceptions — the application's own user interface, which is **French** (the only locale so
far, routed through i18n, never a hardcoded string), and the Flyway migrations that had
already shipped before this rule existed, whose French comments stay as they are because
Flyway checksums the whole file.

## Before every push

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
cd apps/api && ./mvnw -B verify
```

Both are exactly what CI runs, so a green local run is a strong signal, not a guess. A few
things worth knowing before relying on it:

- Changing a JAX-RS resource or a DTO means regenerating the OpenAPI client
  (`./mvnw -B package -DskipTests && cd ../web && pnpm gen:api`, **not** `verify` — the two
  Maven profiles write a different schema), or the `openapi-sync` CI job fails.
- The front-end build enforces a **gzipped size budget** on the first payload, so a
  dependency that is worth its weight still has to be a deliberate, reviewed change to the
  budget itself, in the same pull request.
- A machine with no local JDK or Docker (a locked-down Windows laptop, typically) can still
  run the API tests, inside a Maven container through WSL — the exact command is in
  `.claude/docs/CONVENTIONS.md`.
- UI changes are checked in an actual browser before they are pushed, not only through a
  unit test.

## Tests

| Scope | Tool | Expected on |
|---|---|---|
| Front-end unit / component | Vitest + Testing Library | Every new screen or shared component |
| End-to-end | Playwright (`e2e/`) | The key user journeys — run locally with `pnpm e2e`; no CI workflow runs them since #439 |
| API integration | `@QuarkusTest` | Every new endpoint |
| Data isolation | `@QuarkusTest`, two accounts | **Mandatory** on every user-scoped resource — another user's identifier must answer 404 |

A bug fix ships together with the test that was failing before it — written first.

### Accessibility audit (#78)

`e2e/tests/a11y.spec.ts` runs axe-core (`@axe-core/playwright`, WCAG 2.1 A + AA rules) on the
main screens at 390 px in each of the four themes, signed in, and fails on any serious or
critical violation. jsdom computes no colours, so contrast is only checked there. A contrast
failure is fixed at the token (`shared/styles/tokens.css`), not on one screen: `--faint` is
set per theme to stay ≥ 4.5:1 on `--chip`, text on a filled accent sits on `--accent` (never
`--accent-deep`), initials on a cover pastel use `--cover-ink`. Red, amber and green that say « error », « to
look at » and « done » (admin triage and services, the stats tiles, the error toast) come from
the status tokens `--danger` / `--warning` / `--success`, each with a `-soft` background and an
`-ink` (#522): vivid, and held to AA in every palette by `shared/styles/statusColors.test.ts`.
Touch targets are 44 px —
a small visual control (the cross on a cover) widens its hit area with an `::after`.
Keyboard and reading order were checked by hand in Chromium; no real screen reader has been
run yet (NVDA / VoiceOver remain to do).

## Keeping this wiki current

A change that alters the architecture, an endpoint, the data model, an import, the
pipeline or how to run the project updates the matching page of this wiki **in the same
piece of work**. The wiki is a separate git repository (`Librarius.wiki.git`); its pages are
plain Markdown.
