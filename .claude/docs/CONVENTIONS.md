# Working conventions — Librarius

## 1. Language

| Item | Language |
|---|---|
| **All code**: identifiers, comments, javadoc, test names, log messages, file and branch names | **English** |
| **Issues, milestones, labels** | **English** |
| **Commit messages** and **pull requests** (title and description) | **English** |
| **Project documentation**, this set included | **English** |
| Exchanges with the maintainer | **French** |
| Text shown to the user | **French**, through i18n — never hardcoded |

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
- **Messages rendered to the user** (`ImportException`, the `Or`/`Argent`/`Bronze` labels)
  stay in French, since the interface is French. Log messages, on the other hand, are in
  English.

**The application itself stays in French**: `fr` is the only locale, and the user-facing
copy is written in French in `i18n/locales/fr.json`. English will come with
[#77](https://github.com/zelytra/Librarius/issues/77); until then, no translation of the
interface.

## 2. Git flow

```text
main      ──●────────────────●──────────►  staging (automatic deployment)
             ↖ merge          ↖ merge
develop   ──●──●──●──●──●──●──●──────────►  integration
             ↖  ↖  ↖
feature/*    ●  ●  ●
```

- **No direct commit** on `main` or `develop`.
- Branch from an **up-to-date** `develop` (`git fetch && git reset --hard origin/develop`).
- Naming: `feature/<short-topic>`, `fix/<topic>`, `docs/<topic>`, `ci/<topic>`,
  `hotfix/<topic>` (that last one branches from `main`).
- One branch = one coherent change = one PR.
- Never force-push `main` or `develop`.

### Releasing

1. `feature/x` → PR into `develop`, green CI, merge, delete the branch.
2. To release: PR `develop` → `main`, titled "Release — <topic>".
3. Merging into `main` triggers `cd.yml` (image build + `helm upgrade`) towards the
   **staging** environment `librarius.zelytra.fr`: **never merge into `main` with a red
   CI**. Downtime during that deployment is acceptable there.

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
title becomes the commit subject on `develop`. Same format as commits:
`<type>(<scope>): <summary>`.

```markdown
## Problem
What is wrong, or missing, today.

## Fix
What the change does, and why this approach over the alternatives.

## Verification
- [ ] `pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build`
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

If the API changed:

```bash
cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api
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
wsl -d Ubuntu -- bash -lc 'docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /mnt/c/Users/<user>/WebstormProjects/Librarius:/workspace \
  -v librarius-m2:/root/.m2 \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -w /workspace/apps/api \
  maven:3.9-eclipse-temurin-21 mvn -B verify'
```

The named volume `librarius-m2` keeps the Maven cache between runs: the first run downloads
everything and takes several minutes, the following ones are fast.

On the front-end side, Node and pnpm work natively. One caveat: on Node ≥ 22 the native
`localStorage` takes precedence over the jsdom one — `src/test/setup.ts` neutralises it, do
not remove that safeguard.

**UI change**: check it in a real browser (`pnpm web:dev`), not only in a unit test. Provide
the evidence in the PR (rendered text, computed styles, no console error).

## 5. Code style

### TypeScript / React

- Function components, hooks. One screen per folder under `features/`.
- **Never edit `src/api/generated/`** — regenerate it.
- Check `status === 200` before using the `data` of an orval call.
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
| Front-end e2e | Playwright 🔜 | Journeys P1–P5 from [PRODUCT](PRODUCT.md) |
| API integration | `@QuarkusTest` + Dev Services | Every new endpoint, including the "another user's data" case |
| Data isolation | `@QuarkusTest` with `alice` and `bob` | **Mandatory** on every user-scoped resource |

A bug fix ships with the test that was failing before it.

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
