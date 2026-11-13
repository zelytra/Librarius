# Working method

How a request becomes a merged change on this project. [CONVENTIONS](CONVENTIONS.md) says how
the code, the commits and the pull requests are written; this page says in what order things
happen, and why.

## 1. Feedback goes into issues first

Every fix or request the owner reports is written into a GitHub issue **before** any work on
it starts — so a session cut short, or a fix that misses part of the request, leaves a trace.

- One issue per distinct thing. A message listing seven remarks becomes seven issues.
- Search for an existing issue first; comment on it rather than opening a duplicate.
- The issue states the report in the owner's terms, the cause once it is known, what is
  expected, and how it will be checked.
- A request that is ambiguous is still recorded; the reading taken is written in the issue
  and repeated to the owner.
- A part of a request that is left out is split into its own issue, never dropped silently.

## 2. Measure before fixing

"It works badly" is not a specification. Before changing behaviour that is judged on its
results — search relevance, statistics, an import — reproduce it and write the figures down
in the issue:

- run the real thing (the local stack against real providers, a real export file);
- keep the script and the queries, so the same run can be repeated after the change;
- report what was measured, including what could not be checked and why.

The search overhaul ([#404](https://github.com/zelytra/Librarius/issues/404)) is the
reference: 38 real queries run before, and again after each step.

## 3. One branch, one pull request per batch

- Cut the branch from an up-to-date `main`; never commit on `main`.
- Each fixed or added behaviour is locked by a test (`vitest`, `@QuarkusTest`).
- Run the quality gate before every push (commands in `CLAUDE.md`).
- A change to a resource or a DTO regenerates the client from a **clean** build
  (`./mvnw -B clean package -DskipTests`, then `pnpm gen:api`): a stale `target/` leaks test
  classes into the schema.
- The pull request closes its issues (`Closes #n`) and updates the documentation it makes
  stale in the same change: `docs/wiki/` for developers, `.claude/docs/` for agents.

## 4. Merge on green, then watch the deploy

- A pull request is merged (squash) only when every CI check is green. The owner has
  authorised agents to merge their own pull requests on that condition.
- A red check is root-caused and fixed, never skipped or retried blindly.
- Merging into `main` deploys to staging and runs the end-to-end journeys: when a change
  touches the chart, a migration or a journey, follow that run to its end.
- What could only be checked by tests, and not by eye in a browser, is said so in the report.

## 5. Delegating to sub-agents

Only when the owner asks for it. The session that received the request stays responsible
for the result: it orchestrates, it does not hand over the thinking.

1. **Diagnose and design first.** The orchestrator measures the problem, picks the solution
   and writes the issues. Sub-agents implement a design; they do not choose it.
2. **Fix the shared contract before fanning out**: the data shape every part depends on, and
   recorded real payloads as test fixtures, committed before any agent starts.
3. **One agent per independent part**, each in its own worktree, each owning named files and
   told which files belong to others. Parts that depend on the others' results come after.
4. **Match the model to the difficulty**: a cheaper model for a well-specified, mechanical
   part; the most capable one for the part where judgement decides the outcome.
5. **Brief with facts, not intentions**: what was verified against the live system, the exact
   expected behaviour, the tests to write, the commands allowed (parallel agents must not run
   suites that share ports), and what to report back.
6. **Verify independently.** An agent's "tests pass" is a claim: merge its branch, rerun the
   suites, and rerun the measurement of step 2 on the real system. Fixtures hide what a live
   run shows.
7. **Iterate on evidence**: send the same agent the concrete defects the live run showed,
   with the queries and the results, until the measurement is good.

## 6. Reporting

Tell the owner what was done, what was measured, what was not verified, and what is left —
in that order, in French, in plain terms. A known weakness is stated, with its issue number.
