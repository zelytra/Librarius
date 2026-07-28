# Working documentation — Librarius

Documentation aimed at the **agents** and contributors picking the project up.
It complements the public documentation (`docs/ARCHITECTURE.md`, `docs/DEPLOYMENT.md`)
by describing *what the code actually is*, the working rules and the scope left to build.

## Recommended reading order

| # | Document | Read it when |
|---|---|---|
| 1 | [INVENTORY](INVENTORY.md) | **Always first.** What really exists, what is missing, the debt already identified |
| 2 | [CONVENTIONS](CONVENTIONS.md) | Before writing a single line: git flow, commits, PRs, code style, tests |
| 3 | [PRODUCT](PRODUCT.md) | Functional work: personas, screens, journeys, business rules |
| 4 | [ARCHITECTURE](ARCHITECTURE.md) | Technical work: modules, flows, dependencies, structural decisions |
| 5 | [DATA-MODEL](DATA-MODEL.md) | Any change to the schema or to an entity |
| 6 | [API](API.md) | Adding or changing an endpoint, front↔back contract |
| 7 | [ROADMAP](ROADMAP.md) | Choosing the next task: milestones, priorities, dependencies |

## Update rule

These documents are **living**. A PR that changes behaviour, the schema or the API
contract updates the relevant document **in the same PR**. A PR that closes an issue
ticks the matching line in [ROADMAP](ROADMAP.md).

If a document contradicts the code, **the code wins**: fix the document and flag the
discrepancy in the PR description.
