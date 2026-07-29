# Working conventions — Librarius

## 1. Language

| Item | Language |
|---|---|
| **All code**: identifiers, comments, javadoc, test names, log messages, file and branch names | **English** |
| **Issues, milestones, labels** | **English** |
| **Commit messages** and **pull requests** (title and description) | **English** |
| **Project documentation**, this set included | **English** |
| Exchanges with the maintainer | **French** |
| Text shown to the user | **French and English**, through i18n — never hardcoded |

A pull request title becomes the commit subject when it is squashed: it must therefore be
written like a commit message — English, imperative mood.

The code is entirely in English, comments included: do not follow the language of the
neighbouring file if it is still French, write English and convert whatever you touch along
the way.

Two deliberate exceptions:

- **The Flyway migrations already shipped** (`V1__init.sql`, `V2__progress_and_ranks.sql`)
  keep their French comments. Flyway computes the checksum over **the entire file, comments
  included**: touching them would fail validation at startup on every database where the
  migration is already applied. Future migrations are written in English.
- **Messages the API renders to the user** (`ImportException`, the `Or`/`Argent`/`Bronze`
  category names) are still French, because nothing on the API side is translated yet: it
  has no notion of the caller's language. They are the one place where an English interface
  still shows French, and closing that gap means moving those strings behind a key the web
  app owns, or having the API honour `Accept-Language`. Log messages, on the other hand,
  are in English.

**The application ships in French and in English**
([#77](https://github.com/zelytra/Librarius/issues/77)). Both locales are complete and stay
that way: `apps/web/src/i18n/locales.test.ts` fails the build on the first key that exists
in one file and not the other, so a new key is added to `fr.json` **and** `en.json` in the
same commit — there is no "translate it later".

- French remains the locale the copy is **authored** in: write the French first, translate
  it, and let `fallbackLng` point at `fr`.
- English is **American** spelling (`favorites`, `customize`, `catalog`), the default for
  software interfaces.
- Plural keys are not copied from one locale to the other. Each language declares the forms
  its own CLDR rules ask for, and they disagree: French counts zero as singular
  (« 0 série »), English does not ("0 series").

## 2. Git flow

```text
feature/*   ──●──●──●
               ↘  ↘  ↘
main        ────●──●──●──────────●──────────►  merge → deploy to staging
                               v1.0.0          tag   → deploy to production
```

There is **no `develop` branch**. Pull requests target `main` directly: the two branches
held identical content most of the time, and the extra merge bought nothing.

- **No direct commit** on `main`.
- Branch from an **up-to-date** `main` (`git fetch && git reset --hard origin/main`).
- Naming: `feature/<short-topic>`, `fix/<topic>`, `docs/<topic>`, `ci/<topic>`.
- One branch = one coherent change = one pull request.
- Never force-push `main`.
- `main` stays permanently releasable: a tag can be cut from it at any point.

### Releasing

1. `feature/x` → pull request into `main`, green CI, merge, delete the branch.
2. Merging into `main` triggers `cd.yml`: image build, then `helm upgrade` into the
   `librarius` namespace on the **staging** environment `librarius.zelytra.fr`.
   **Never merge with a red CI.** Downtime during that deployment is acceptable there.
3. A `vX.Y.Z` tag on `main` runs `release.yml`: images tagged `X.Y.Z` / `X.Y` / `X` /
   `<sha>`, a changelog built from the commit subjects below, the chart aligned on the
   version, and a GitHub release. It **deploys nothing**. Full procedure and rollback:
   [docs/DEPLOYMENT.md](../../docs/DEPLOYMENT.md).
4. Production is deployed by **tagging** `main`, never by merging. It does not exist yet
   — see [#103](https://github.com/zelytra/Librarius/issues/103), pending a domain.

### When a tag is cut

**A version is tagged when its milestone closes**, and the tag carries the milestone's
number: `v0.4 — Core product` fully closed → `v0.4.0` on the current `main`. Nothing else
justifies a tag. A milestone still holding an open issue does not get one, however
finished it feels.

Two consequences worth knowing before reaching for `git tag`:

- **Never tag retroactively.** GitHub runs the workflow **as it exists at the tag**, so a
  tag placed on an old commit runs that commit's `release.yml` — or nothing at all, if the
  workflow did not exist yet. And `main` has moved on: labelling an old point with a
  version whose work came later publishes a release that lies about what it contains.
- The alignment pull request is cut from the **current `main`**, not from the tag, on
  purpose — a branch based on the tag would ask to revert everything that landed in
  between. `release.yml` says so in its own comments; do not "fix" it.

Because the changelog is generated from the commit subjects, a badly typed subject ends up
verbatim in a published release: `type(scope): summary`, nothing else.

Because pull requests now target the default branch, a `Closes #nn` in the body actually
closes the issue on merge. It did not while they targeted `develop`.

### Commit identity

```bash
git config user.name "zelytra"
git config user.email "contact@zelytra.fr"
```

**No mention of tooling.** No `Co-Authored-By` trailer, no "Generated with …", no assistant
signature in commit messages, PR descriptions, issues, code or project documentation. The
single exception is the files that exist to maintain the agents — `CLAUDE.md` and
`.claude/`. Referring to them by path from an issue or a PR remains normal: that is a file
reference, not an attribution.

### Commit messages

Conventional commits, subject in **English**, imperative mood, ≤ 72 characters. The body
explains the **why** rather than the what — the diff already says what changes:

```text
feat(web): expose the annual reading goal on the home screen

/api/goals had been working since the initial back-end milestone, but no
screen ever called it: the feature existed and was invisible. Adds the
gauge, the pace needed to stay on track, and an empty state inviting the
user to set a goal.

Closes #50
```

Usual scopes: `web`, `api`, `db`, `infra`, `deploy`, `ci`, `docs`, `mobile`.

## 3. Pull requests

Written **in English**, title included — the repository squash-merges, so the pull request
title becomes the commit subject on `main`. Same format as commits:
`<type>(<scope>): <summary>`.

```markdown
## Problem
What is wrong, or missing, today.

## Fix
What the change does, and why this approach over the alternatives.

## Verification
- [ ] `pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build`
- [ ] `node apps/web/scripts/check-bundle-size.mjs` if the front end changed
- [ ] `cd apps/api && ./mvnw -B verify`
- [ ] OpenAPI client regenerated if the API changed
- [ ] Checked in a real browser (screenshots / DOM) if the UI changed
- [ ] `.claude/docs/` updated

Closes #<issue>
```

## 4. Quality — before any push

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
```

```bash
cd apps/api && ./mvnw -B verify
```

If `apps/mobile` or the web build output changed — the native shell has to still find the
bundle:

```bash
pnpm mobile:build
```

### The size budget

The app is read on a phone, often on a bookshop's network, so the weight of the first
payload is a feature. The `web` workflow **fails** on it, and the same check runs
locally on the `dist/` a build just produced:

```bash
node apps/web/scripts/check-bundle-size.mjs
```

Three budgets, all measured **gzipped** — what nginx puts on the wire:

| Budget | Covers | Ceiling | Measured on 2026-07-29 |
|---|---|---|---|
| initial | everything `index.html` loads before the first paint | 155 kB | 147.1 kB |
| deferred asset | any single lazy route or runtime chunk, on its own | 10 kB | 5.2 kB |
| whole build | every file, i.e. what the service worker precaches | 230 kB | 199.8 kB |

The rule matters more than the figures: **the measurement plus about 15%**. A budget
with 60% of slack catches nothing, and one set flush against the current size gets
switched off the first time it goes red. The failure names the asset that went over and
by how much, so a per-screen chunk points at the screen.

Raising a budget is a legitimate decision — a dependency has to land somewhere — but it
happens **in the diff**, in `apps/web/scripts/check-bundle-size.mjs`, with the reason in
the pull request. Never by deleting the step. And it is re-derived only when the
**baseline** deliberately changes: `@capacitor/core` (#154) is eager, permanent and
decided, so it moved the number up; the react-router 8 and orval 8 upgrade (#157) moved
it back down, by more than #154 had added; the advanced search (#146) and the
alternative editions (#152) did not move it at all, because both landed behind the route
split, in the Discover and Detail chunks. A screen growing is not a reason to raise a
budget — that is the budget working.

The English locale (#77) moved **whole build** from 200 to 230 kB, and it is worth reading
why, because the headline figure is misleading: the second locale costs 1.4 kB gzipped
(198.4 → 199.8 kB), JSON compressing well against the French sitting beside it. The ceiling
moved because it had not been re-derived since it was set from a 176.4 kB measurement,
while `main` had quietly drifted to 198.4 kB — the check was one merge from going red on
whatever landed next. **Re-derive on the way past.** A budget left at 99% for several
merges is a budget that fails the wrong pull request.

### Lighthouse

The same workflow audits the built app with **Lighthouse** on the mobile profile, three
runs, median, thresholds in `apps/web/lighthouserc.json`.

It measures the **signed-out** app: a static build has neither API nor Keycloak behind
it, so what is audited is the shell — boot, theme, fonts, first paint, the sign-in
prompt. That is the part every visitor pays for, and it is the only part measurable
reproducibly; the signed-in screens need the whole stack and belong to the e2e suite.

| Category | Threshold | Observed on CI — 24 runs of the same code |
|---|---|---|
| accessibility | ≥ 0.95 | 1.00 every time |
| best practices | ≥ 0.90 | 1.00 every time |
| performance | ≥ 0.50 | single runs 0.48 – 0.98, **medians 0.65 – 0.98** |

The first two carry no timing, so they cannot flap. **Performance can and does**: the
page blocks its first paint on a third-party stylesheet (Google Fonts), whose latency
belongs to somebody else, so the same commit scores 0.48 on one run and 0.98 on another.
Two decisions follow, and both matter more than the number:

- **Aggregate on the median**, not on LHCI's default optimistic pick. One lucky run
  cannot then hide a regression, and one unlucky run cannot invent one — the 0.48 above
  is precisely the sample a median throws away.
- **Assert a floor, not the target.** 0.50 is 0.15 under the lowest of the eight medians
  CI has produced: low enough never to go red on the weather, high enough to fail a
  build that ships a genuinely heavy regression.

It is raised to the **0.9** the v1.0 milestone requires by the change that takes the
fonts off the critical path. A check that goes red at random teaches people to ignore
red checks, which is worse than not having it.

That was also the biggest performance item outstanding: a first load was **750 kB**, of
which **602 kB were the three Google fonts** — 407 kB for the full Material Symbols set
alone, downloaded to draw the ~38 icons the app actually uses — against 139 kB of script.
The bundle was never what made this app heavy.

The icon third is fixed ([#161](https://github.com/zelytra/Librarius/issues/161)): `Icon`
(`shared/ui/Icon.tsx`) now draws from a self-hosted, same-origin subset of ~38 glyphs
(`shared/ui/iconSubset.ts`, regenerated by `apps/web/scripts/generate-icon-font.mjs`) —
4.6 kB in place of 407 kB, and cached by the service worker, so the icons survive a cold
offline start instead of arriving as missing glyphs. Newsreader and DM Sans (~195 kB)
still load from `fonts.googleapis.com` as a render-blocking stylesheet, which is why the
performance threshold above is not raised yet: that stylesheet, not the bundle, is what
still ties a run's score to somebody else's latency.

If the API changed:

```bash
cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api
```

⚠️ **Regenerate with `package`, never with `verify`, and check `openapi/` before
committing.** Both write the schema, but under different profiles, and the schema differs:
`securitySchemes.openIdConnectUrl` comes from `quarkus.oidc.auth-server-url`, which `%prod`
sets and `%test` leaves to Dev Services. So `package` writes the line and **`verify`
silently rewrites the file without it**.

The deletion is the part that bites, because it is small enough to slip through a review.
`verify` also **adds** noise the contract has no business carrying: the AniList client
records show up as schema components, and a bogus `/` path appears. Those are loud enough
to be caught; the missing one line is not.

The committed contract is the `package` one — the same one `openapi-sync` regenerates. The
trap is that running the test suite leaves `openapi/` dirty, and a `git add -A` then commits
it without anyone noticing. It has happened three times, once breaking `openapi-sync` on
every API branch until someone traced it.

**So: after running the test suite, look at `git status` before staging.** Any change under
`openapi/` that you did not intend is the test profile talking — discard it:

```bash
git checkout -- openapi/
```

If documentation changed (the `docs` workflow: markdownlint + internal links):

```bash
npx markdownlint-cli2@0.23.2
```

### A machine without a JDK or Docker

The API tests need Docker (Dev Services starts PostgreSQL and Keycloak). On a corporate
Windows machine that has neither a JDK nor Docker, go through WSL — where Docker works — and
run Maven inside a container:

```bash
wsl -d Ubuntu -- bash -lc 'docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /mnt/c/Users/<user>/WebstormProjects/Librarius:/workspace \
  -v librarius-m2:/root/.m2 \
  -w /workspace/apps/api \
  maven:3.9-eclipse-temurin-21 mvn -B verify'
```

The named volume `librarius-m2` keeps the Maven cache between runs: the first run downloads
everything and takes several minutes, the following ones are fast.

**Do not add `--network host`.** It looks harmless and it is not: the container then shares
WSL's network stack, so if the local dev stack is up (`pnpm infra:up`, Keycloak on 8081)
Quarkus cannot bind its test port and the run fails with *Unable to start HTTP server* —
one error followed by dozens of skipped tests, which reads like a code failure and is not.
Testcontainers publishes its containers on the bridge and hands back
`172.17.0.1:<port>`, so the default network works fine.

**`CatalogResourceTest` fails in that container, whole, and is expected to.** It uses
`@InjectMock`, and Mockito's inline mock maker needs Byte Buddy to attach an agent to the
running JVM. That attachment does not work inside the container, so **every one of its
tests** errors out with *Could not self-attach to current VM* before anything of theirs
runs. Neither `--cap-add=SYS_PTRACE` nor `-Djdk.attach.allowAttachSelf=true` helps.

It is a property of the sandbox, not of the code: the `api` workflow runs the same commits
green on a GitHub runner. So a local run whose errors are **exactly the tests of that class
and nothing else** is a pass. Count the class, not the errors: it held two tests when this
was written and five since the catalog search grew, so a fixed number here would only ever
mislead. Read the failing test names before concluding.

On the front-end side, Node and pnpm work natively. One caveat: on Node ≥ 22 the native
`localStorage` takes precedence over the jsdom one — `src/test/setup.ts` neutralises it, do
not remove that safeguard.

**UI change**: check it in a real browser (`pnpm web:dev`), not only in a unit test. Provide
the evidence in the PR (rendered text, computed styles, no console error).

## 5. Code style

### TypeScript / React

- Function components, hooks. One screen per folder under `features/`.
- **Never edit `src/api/generated/`** — regenerate it.
- Read the API through the generated React Query hooks. **Never fetch from a
  `useEffect`**, and invalidate the affected queries after a mutation, using the generated
  key helpers (`getGetApiLibraryQueryKey()`) rather than hand-written strings.
- New styles: CSS Modules backed by the tokens (`tokens.css`). Inline is reserved for
  genuinely dynamic values.
- User-facing text through `useTranslation()` with keys in `i18n/locales/`.
- No `eslint-disable` without a comment justifying the exemption.

### Java / Quarkus

- Panache entities in `domain/`, queries in `domain/repository/`, **always scoped by
  `user_id`**.
- Thin JAX-RS resources: validation + delegation. The logic belongs in a service.
- DTOs are `record` types in `ApiDtos`, with an `of(entity)` factory. **Never** serialise an
  entity directly.
- Any schema change means a new Flyway migration (see [DATA-MODEL](DATA-MODEL.md) § 4).

## 6. Tests

| Scope | Tool | Expectation |
|---|---|---|
| Front-end unit / component | `vitest` + Testing Library | Every new screen or shared component |
| End-to-end | Playwright, in `e2e/` | Journeys P1–P5 from [PRODUCT](PRODUCT.md) |
| API integration | `@QuarkusTest` + Dev Services | Every new endpoint, including the "another user's data" case |
| Data isolation | `@QuarkusTest` with `alice` and `bob` | **Mandatory** on every user-scoped resource |

A bug fix ships with the test that was failing before it.

### End-to-end suite

`e2e/` drives a real browser against a real stack — PostgreSQL, Keycloak, the API and the
web image — brought up by `e2e/docker-compose.e2e.yml`. It is the only place where
authentication, the HTTP routing and the database are checked together, which is exactly
where the regressions that reached production came from.

```bash
pnpm e2e:install     # first run only: Playwright and its browser
cd e2e && pnpm exec playwright install chromium
pnpm e2e             # builds the images if needed, runs the journeys, tears the stack down
```

Points worth knowing before touching it:

- **It is not a pnpm workspace member.** The web image build context only copies the
  manifests it needs; an importer declared in `pnpm-workspace.yaml` but missing from that
  context breaks `pnpm install --frozen-lockfile` inside the Dockerfile. `e2e/` therefore
  keeps its own lockfile and is installed with `--ignore-workspace`.
- **Sign-in is programmatic.** The token is obtained through the direct access grant the
  `librarius-web` client already allows, then written into `localStorage` in the shape
  `oidc-client-ts` expects. No test pays for the Keycloak login form.
- **The external catalogs are stubbed** (`e2e/stack/catalog-stub.conf`): the providers
  swallow their own failures and return an empty list, so an unavailable Open Library
  would surface as an empty result set and a puzzling failure.
- **Assert through the interface, never on an API payload.** The suite must survive a
  change of response shape; the only place that reads the API directly is the fixture
  that empties the account between tests, and it tolerates both shapes.
- **Ports 4173 and 8081 must be free.** The web image bakes `localhost:8081` in as its
  OIDC authority and the realm only allows `localhost:5173` and `localhost:4173` as
  redirect URIs; the suite takes 4173 so it can run next to a `pnpm web:dev`, but it does
  collide with a `pnpm infra:up` Keycloak.
- `E2E_STACK=external` skips the compose lifecycle and runs against an already-running
  stack; `E2E_KEEP_STACK=1` leaves it up after a failure.

## 7. Security

- No plaintext secret in the repository (not in `values.yaml`, not in a committed `.env`,
  not in command output). Mask tokens in logs and responses.
- Every new resource is `@Authenticated` and user-scoped, unless a documented explicit
  decision says otherwise.
- Changes to repository or cluster settings (branch protections, CI secrets, permissions)
  **are not made autonomously**: provide the exact procedure and wait for approval.

## 8. Documentation

A PR that changes behaviour, the schema or the contract updates the relevant document **in
the same PR**:

| Change | Document |
|---|---|
| New user-facing behaviour | [PRODUCT](PRODUCT.md) |
| New module, dependency, decision | [ARCHITECTURE](ARCHITECTURE.md) |
| Flyway migration | [DATA-MODEL](DATA-MODEL.md) |
| Endpoint or DTO | [API](API.md) |
| Issue completed | [ROADMAP](ROADMAP.md) |
| Debt cleared or discovered | [INVENTORY](INVENTORY.md) |

## 9. Parallelism (agents)

- Never block on a long wait (pipeline, deployment, e2e suite): start it in the background
  and carry on.
- Group independent tool calls into a single message.
- Large delegable jobs (audits, bulk translation, exploration) go to parallel agents.
