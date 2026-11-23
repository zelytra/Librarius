# Contributing to Librarius

Thanks for wanting to help. Bug reports, ideas, translations and code are all welcome.

## Before you start

- **Read the [Code of Conduct](CODE_OF_CONDUCT.md).** It applies everywhere in this project.
- **Found a security problem?** Don't open an issue. Follow [SECURITY.md](SECURITY.md).
- **Have a question?** See [SUPPORT.md](SUPPORT.md).

## Reporting a bug or asking for a feature

Open an [issue](https://github.com/zelytra/Librarius/issues/new/choose) and pick the
template that fits. One issue per problem: a short title, what you did, what you expected,
what happened instead. A screenshot helps more than you'd think.

Every change starts as an issue, so the reasoning stays in one place even when the work
stops halfway.

## Contributing code

1. **Open or pick an issue first**, and say there that you are working on it, so two people
   don't build the same thing.
2. **Fork and branch** from an up-to-date `main`: `feature/<topic>`, `fix/<topic>`,
   `docs/<topic>`. There is no `develop` branch.
3. **Set up the stack**: [Running locally](docs/wiki/Running-Locally.md) walks through it
   (Node 24 + pnpm 9, JDK 21, Docker).
4. **Write the test first.** The project is test-driven: a failing test, the smallest change
   that makes it pass, then the clean-up. A bug is reproduced by a test before it is fixed.
5. **Run the quality gate** before you push. It is exactly what CI runs:

   ```bash
   pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
   ```

   ```bash
   cd apps/api && ./mvnw -B verify
   ```

6. **Open a pull request** against `main` and fill in the template. Write the title as a
   [conventional commit](https://www.conventionalcommits.org/) (`feat(web): …`,
   `fix(api): …`, `docs: …`), in English: pull requests are squash-merged, so the title
   becomes the commit on `main`. Put `Closes #<issue>` in the description.

The detailed rules live in the [developer wiki](docs/wiki/Contributing.md): engineering
rules, the git flow, security (every API resource is scoped to the signed-in user), the
database (Flyway owns the schema), accessibility (WCAG 2.1 AA) and the OpenAPI client that
must be regenerated after an API change.

## Language

Code, comments, commits, pull requests and documentation are written in **English**. The
application itself speaks French and English: user-facing text goes through i18n
(`apps/web/src/i18n/locales/`), never as a hard-coded string. Translations into other
languages are very welcome.

## License of contributions

Librarius is published under the [PolyForm Noncommercial License 1.0.0](LICENSE): anyone
may read, run, modify and share it for **non-commercial** purposes, and commercial use is
reserved to the project owner.

So that this stays possible, by submitting a contribution (code, documentation, artwork or
translation) you agree that:

- the contribution is your own work, or you have the right to submit it;
- it is licensed to everyone under the same PolyForm Noncommercial License 1.0.0 as the rest
  of the project; and
- you also grant the project owner, [zelytra](https://github.com/zelytra), a perpetual,
  worldwide, non-exclusive, royalty-free and irrevocable license to use, modify, sublicense
  and distribute your contribution for any purpose, **commercial use included**.

You keep the copyright on your contribution. If you can't agree to this, say so in the pull
request before it is merged.
