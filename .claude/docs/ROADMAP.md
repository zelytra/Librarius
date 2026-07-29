# Roadmap — Librarius

Target: a **public**, multi-user product, PWA plus a native mobile application.
Ten milestones, from the most structural to the most exposed. Each milestone is a **GitHub
milestone**; the issues carry the detail and the acceptance criteria.

> Live tracking: https://github.com/zelytra/Librarius/milestones
>
> **GitHub is the source of truth.** This file exists to give the ordering and the
> dependencies a milestone list cannot express; where the two disagree, the milestone wins
> and this file is wrong.

## Ordering principle

1. **v0.3 first**: without shared server state or a design system, every new feature
   multiplies the debt. Clean up before widening the scope. **Closed.**
2. **v0.4 next**: the `series` table gates the Series screen, the Series view in the
   Collection, missing volumes and personalised releases. It is the deepest dependency in
   the product. **Three issues left.**
3. **v0.5 can run in parallel**: the operational topics do not touch application code and
   can move independently.
4. **v0.6** depends on v0.3 (a clean API client) but not on v0.4.
5. **v0.7** turns the reading loop into a workflow: a fourth status, the screens a finished
   or abandoned title leads to, and the to-read pile on Home.
6. **v0.8** widens the catalog itself — authors as entities, editions enriched from the
   providers, a search that stops demanding a single medium, and media beyond books and
   manga. The deepest of the later milestones.
7. **v0.9** is the interface one: a real desktop layout, onboarding, a branded auth gate.
   It touches every screen, so it comes after the screens stop changing shape.
8. **v1.0** closes out what a public product requires.
9. **v1.1** builds the trust machinery — a trusted flag, reporting, contribution — which
   v1.2 needs before anything is shared between accounts.
10. **v1.2** opens the product to other people: follows, shared reviews, a feed. Last,
    because everything before it is what there would be to share.

## Where to start

An agent picking the project up with no more precise instruction takes, in this order:

1. **[#58](https://github.com/zelytra/Librarius/issues/58)** — the chart no longer carries
   any credential, but the exposed passwords are still valid: **rotating them on the
   cluster** is a manual step, procedure in `docs/DEPLOYMENT.md`. Nothing an agent can do
   alone, and nothing else in this list matters as much.
2. **[#60](https://github.com/zelytra/Librarius/issues/60)** — the rules are evaluated now,
   on the cluster and from outside it, but the in-cluster Alertmanager still notifies
   nothing: **create the webhook Secret** (one `kubectl` command, `docs/DEPLOYMENT.md`
   § "Alerting") and add the two `--set` lines to `cd.yml`. Nothing an agent can do alone.
3. **[#59](https://github.com/zelytra/Librarius/issues/59)** — the backup CronJob is
   shipped but off, and no restore has ever been run against the cluster.
4. **[#187](https://github.com/zelytra/Librarius/issues/187)** — the chart can autoscale
   now, and the node cannot: 3805m of its 4000m of CPU requests are committed, ~3500m of
   them by other stacks. **Freeing capacity, adding a node or taking a bigger one** is a
   decision only the maintainer can make, and until it is made `autoscaling.enabled` stays
   off. `docs/DEPLOYMENT.md` § "Scaling out".

Already handled: [#36](https://github.com/zelytra/Librarius/issues/36) (front-end coverage)
and [#39](https://github.com/zelytra/Librarius/issues/39) (data isolation), which now acts as
a safety net for the rest of the work, plus
[#32](https://github.com/zelytra/Librarius/issues/32) and
[#34](https://github.com/zelytra/Librarius/issues/34) (CSS Modules and the shared palette),
and [#42](https://github.com/zelytra/Librarius/issues/42) (the dark theme and the System
preference).

---

## v0.3 — Foundations & quality

**Goal**: make the code extensible. No new user-visible feature, but every later one depends
on it.

**Exit criterion**: no hand-rolled loading `useEffect` left, no durable inline presentation
styling left, front-end coverage on the 7 screens, data isolation covered by tests.

| Issue | Topic | Depends on |
|---|---|---|
| [#30](https://github.com/zelytra/Librarius/issues/30) | ✅ Move server state to TanStack Query | — |
| [#31](https://github.com/zelytra/Librarius/issues/31) | ✅ Orval mutator: token injection and refresh | #30 |
| [#32](https://github.com/zelytra/Librarius/issues/32) | ✅ Extract inline styles into CSS Modules | — |
| [#33](https://github.com/zelytra/Librarius/issues/33) | ✅ Shared loading, error and empty states | #30 |
| [#34](https://github.com/zelytra/Librarius/issues/34) | ✅ Factor out the cover palette and delete mockData.ts | #32 |
| [#35](https://github.com/zelytra/Librarius/issues/35) | ✅ Extract every hardcoded string into i18n | — |
| [#36](https://github.com/zelytra/Librarius/issues/36) | ✅ Cover the seven screens with Vitest | — |
| [#37](https://github.com/zelytra/Librarius/issues/37) | ✅ Playwright end-to-end suite on the critical journeys | #36 |
| [#38](https://github.com/zelytra/Librarius/issues/38) | ✅ Server-side pagination and filtering for library and wishlist | — |
| [#39](https://github.com/zelytra/Librarius/issues/39) | ✅ Per-user data isolation tests | — |
| [#40](https://github.com/zelytra/Librarius/issues/40) | ✅ Compute statistics in SQL | — |
| [#41](https://github.com/zelytra/Librarius/issues/41) | ✅ Remove HelloResource | — |
| [#42](https://github.com/zelytra/Librarius/issues/42) | ✅ Real dark theme driven by tokens | #32 |

---

## v0.4 — Core product

**Goal**: deliver the functional depth that sets Librarius apart — series, volumes, reading
progress, editions.

**Exit criterion**: a manga reader can see their series, their missing volumes, follow a
series and get its releases; a novel reader can track their progress page by page and their
annual goal.

| Issue | Topic | Depends on |
|---|---|---|
| [#43](https://github.com/zelytra/Librarius/issues/43) | ✅ V4 migration: series and series_follow tables | — |
| [#44](https://github.com/zelytra/Librarius/issues/44) | ✅ /api/series API: details, volumes and follow | #43 |
| [#45](https://github.com/zelytra/Librarius/issues/45) | ✅ Series screen: volume grid | #44 |
| [#46](https://github.com/zelytra/Librarius/issues/46) | ✅ "Series" view in the Collection | #44 |
| [#47](https://github.com/zelytra/Librarius/issues/47) | ✅ Reading progress input in the Detail screen | — |
| [#48](https://github.com/zelytra/Librarius/issues/48) | ✅ Personal rating and private review (V7 migration) | — |
| [#49](https://github.com/zelytra/Librarius/issues/49) | ✅ Alternate editions of a work in the Detail screen — enriching them from the providers left open | — |
| [#50](https://github.com/zelytra/Librarius/issues/50) | ✅ Annual reading goal: setting and gauge on the Home screen | — |
| [#51](https://github.com/zelytra/Librarius/issues/51) | ✅ Custom category management (V9 migration) — assigning one from the Detail screen still pending | — |
| [#52](https://github.com/zelytra/Librarius/issues/52) | ✅ Richer wishlist: editing, budget and conversion | — |
| [#114](https://github.com/zelytra/Librarius/issues/114) | ✅ Order the wishlist by urgency and not by the enum name | — |
| [#53](https://github.com/zelytra/Librarius/issues/53) | ✅ Advanced search and manual add in Discover | — |
| [#54](https://github.com/zelytra/Librarius/issues/54) | ✅ Customizable Home screen: reorder and hide sections | — |
| [#55](https://github.com/zelytra/Librarius/issues/55) | ✅ Time-based statistics and reading pace | #40 |
| [#56](https://github.com/zelytra/Librarius/issues/56) | ✅ V6 migration: normalized genres — API, genre filter pending in the Collection screen | — |
| [#57](https://github.com/zelytra/Librarius/issues/57) | ✅ Personalized upcoming releases and upcoming_release table (V8 migration) | #43, #44 |

---

## v0.5 — Operations & security

**Goal**: be able to run the service without risking data loss or a silent outage. Can move
in parallel with v0.4.

**Exit criterion**: no secret in the repository, a restore procedure actually tested, an
alert received when something breaks, deployment without downtime.

| Issue | Topic | Depends on |
|---|---|---|
| [#85](https://github.com/zelytra/Librarius/issues/85) | ✅ CD deployment fails: Kubernetes credentials rejected — settled by the move to the `librarius` namespace, eight green deployments since | — |
| [#58](https://github.com/zelytra/Librarius/issues/58) | 🔴 Move secrets out of values.yaml into Kubernetes Secrets — chart done, rotation pending on the cluster | — |
| [#59](https://github.com/zelytra/Librarius/issues/59) | 🔴 Automated PostgreSQL backups with a tested restore procedure — CronJob and procedure shipped (off by default), the restore itself is still to be exercised on the cluster | — |
| [#60](https://github.com/zelytra/Librarius/issues/60) | Prometheus and Grafana alerting — the chart deploys Prometheus + Alertmanager, an `uptime` workflow opens an issue when the public URL stops answering, and three rules have been fired for real by `infra/alerting/fire-drill.sh`. Left: the webhook Secret so the cluster notifies too, kube-state-metrics for the pod and backup rules, and 7 quiet days | — |
| [#61](https://github.com/zelytra/Librarius/issues/61) | ✅ Rate limiting on catalog endpoints | — |
| [#62](https://github.com/zelytra/Librarius/issues/62) | ✅ Restrict Swagger UI and /q endpoints in production | — |
| [#63](https://github.com/zelytra/Librarius/issues/63) | Semantic versioning of images and a rollback procedure — pipeline and documentation done, the rollback itself is still to be exercised on the cluster | — |
| [#64](https://github.com/zelytra/Librarius/issues/64) | ✅ Zero-downtime deployment — exercised on the cluster, 21 s rollout, 124 probes without a single failure | — |
| [#136](https://github.com/zelytra/Librarius/issues/136) | ✅ Image pull no longer depends on a credential: the GHCR packages are public, the chart carries no pull secret, and `cd.yml` checks the anonymous pull before deploying | #64 |
| [#65](https://github.com/zelytra/Librarius/issues/65) | ✅ Persistent catalog cache | — |
| [#66](https://github.com/zelytra/Librarius/issues/66) | ✅ Harden CI: dependencies and static analysis | — |
| [#133](https://github.com/zelytra/Librarius/issues/133) | Stage the web toolchain upgrade, and clear the advisories it blocks — both blockers are upstream, see [INVENTORY](INVENTORY.md) debt #20 | — |
| [#187](https://github.com/zelytra/Librarius/issues/187) | 🔴 Size and autoscale api and web — target recorded, HPA and PostgreSQL connection budget shipped, k6 load test written. The HPA ships **off**: the node has 195m of CPU requests left, enough for a second `api` pod but not for a release on top of one. Freeing that capacity is a cluster-sizing decision, and nothing here has been run against the cluster | #64 |

---

## v0.6 — Native mobile app (Capacitor)

**Goal**: barcode scanning in a bookshop, the product's number-one offline use case, plus
push notifications.

**Exit criterion**: an installable Android APK produced by CI, working ISBN scanning, a
release notification actually received.

| Issue | Topic | Depends on |
|---|---|---|
| [#67](https://github.com/zelytra/Librarius/issues/67) | ✅ Capacitor bootstrap: apps/mobile application — shell over the web bundle; native sign-in still blocked, see [MOBILE](MOBILE.md) | #30, #31 |
| [#68](https://github.com/zelytra/Librarius/issues/68) | ISBN barcode scanning | #67, #53 |
| [#69](https://github.com/zelytra/Librarius/issues/69) | Push notifications: releases and wishlist | #57, #67 |
| [#70](https://github.com/zelytra/Librarius/issues/70) | 🔴 Android build pipeline: CI builds and publishes a debug APK on every push and pull request; release signing is wired but blocked on a keystore only the project owner can create, see [MOBILE](MOBILE.md) § 8 | #67 |
| [#71](https://github.com/zelytra/Librarius/issues/71) | iOS build and publishing procedure | #67, #70 |
| [#185](https://github.com/zelytra/Librarius/issues/185) | Over-the-air updates for the mobile app's web bundle — ship a web fix without a store review | #67, #70 |

---

## v0.7 — Reading workflow & book states

**Goal**: make the reading loop a workflow rather than three buttons. Finishing a book
should lead somewhere, giving up on one should be sayable, and what is waiting should be
visible.

**Exit criterion**: a title can be abandoned and says so; finishing or abandoning one opens
the same rating-and-shelving screen; the to-read pile is on Home; a scan in a bookshop adds
a volume without a confirmation per item.

| Issue | Topic | Depends on |
|---|---|---|
| [#163](https://github.com/zelytra/Librarius/issues/163) | ✅ Track abandoned titles as a fourth reading status (V11 migration) — `ABANDONED` keeps the reading position and is excluded from everything counted as "finished" | — |
| [#164](https://github.com/zelytra/Librarius/issues/164) | Show a rating and shelving screen when a title is finished | #48 |
| [#165](https://github.com/zelytra/Librarius/issues/165) | Offer the same rating and shelving screen when a title is abandoned | #163, #164 |
| [#166](https://github.com/zelytra/Librarius/issues/166) | Surface the to-read pile on the Home screen | — |
| [#168](https://github.com/zelytra/Librarius/issues/168) | Barcode scanning: pick a target state and skip per-item confirmation | #68 |
| [#193](https://github.com/zelytra/Librarius/issues/193) | Babelio import is offered by handle in Settings, but always fails | — |

---

## v0.8 — Universal search, authors & editions

**Goal**: widen the catalog itself. Authors become entities rather than a string on a work,
editions get enriched from the providers, and search stops assuming a single medium.

**Exit criterion**: one search feed across every medium; an author has a page with their
bibliography and can be followed; a work's editions carry covers the providers know about.

| Issue | Topic | Depends on |
|---|---|---|
| [#184](https://github.com/zelytra/Librarius/issues/184) | ✅ Catalog entries stop losing their provider reference on add — the prerequisite for enriching anything (V12). Nothing is backfilled: only entries added from Discover after it carry a reference, and Open Library returns none to store until its provider is taught to ask for the work key | — |
| [#178](https://github.com/zelytra/Librarius/issues/178) | Grow the medium taxonomy beyond books and manga | — |
| [#189](https://github.com/zelytra/Librarius/issues/189) | Catalog search stops requiring a single mandatory kind | #178 |
| [#194](https://github.com/zelytra/Librarius/issues/194) | Discover: one result feed across every medium | #189 |
| [#179](https://github.com/zelytra/Librarius/issues/179) | Add Google Books as a second catalog provider | — |
| [#197](https://github.com/zelytra/Librarius/issues/197) | Enrich a work's editions and their covers from its provider | #49, #184 |
| [#182](https://github.com/zelytra/Librarius/issues/182) | Author catalog entities: author, work_author and author_follow tables | — |
| [#196](https://github.com/zelytra/Librarius/issues/196) | Author API: bibliography, search and follow | #182 |
| [#199](https://github.com/zelytra/Librarius/issues/199) | Author page: portrait, bibliography and follow | #196 |

---

## v0.9 — Desktop, onboarding & interface foundations

**Goal**: the application is a phone screen stretched wide. This milestone gives it a real
desktop layout and the interface pieces every screen leans on.

**Exit criterion**: a wide viewport gets a side nav and a grid rather than a centred column;
signing in shows something branded; loading and empty states stop flashing.

| Issue | Topic | Depends on |
|---|---|---|
| [#169](https://github.com/zelytra/Librarius/issues/169) | ✅ Loading indicator: compact and large formats, with an appearance delay | #33 |
| [#170](https://github.com/zelytra/Librarius/issues/170) | ✅ Authentication gate: a branded waiting screen instead of plain text | #169 |
| [#171](https://github.com/zelytra/Librarius/issues/171) | ✅ Desktop layout foundation: breakpoints and a real grid | — |
| [#172](https://github.com/zelytra/Librarius/issues/172) | Desktop navigation: a side nav for wide viewports | #171 |
| [#173](https://github.com/zelytra/Librarius/issues/173) | Desktop layout: Home and Collection | #171, #172 |
| [#174](https://github.com/zelytra/Librarius/issues/174) | Desktop layout: Discover, Wishlist and Stats | #171, #172 |
| [#175](https://github.com/zelytra/Librarius/issues/175) | Desktop layout: Detail, Series and Settings | #171, #172 |
| [#181](https://github.com/zelytra/Librarius/issues/181) | Home screen: a book stack visualizing total books and pages read | — |
| [#183](https://github.com/zelytra/Librarius/issues/183) | Library screen: separate shelves by support type | #178 |
| [#176](https://github.com/zelytra/Librarius/issues/176) | A Keycloak login theme matching the application | — |
| [#177](https://github.com/zelytra/Librarius/issues/177) | Social sign-in with Google and Apple | #176 |

---

## v1.0 — Public product

**Goal**: what a public release demands — compliance, onboarding, languages, accessibility,
performance.

**Exit criterion**: a stranger can create an account, understand the app, export or delete
their data, in French or in English, with a Lighthouse score ≥ 90.

> This is the milestone that opens a **production** environment. `librarius.zelytra.fr`
> remains staging until then. The v0.5 items tolerated in staging — secrets, backups,
> downtime on deployment — become blocking here.

| Issue | Topic | Depends on |
|---|---|---|
| ✅ [#72](https://github.com/zelytra/Librarius/issues/72) | Library export (CSV and JSON) | — |
| [#73](https://github.com/zelytra/Librarius/issues/73) | 🔴 Account deletion and erasure of all user data — API, cascade and UI shipped; the Keycloak side is only exercised against a CDI stand-in, never a live Keycloak. Stays open until the service account is created on the cluster and a real deletion has been exercised, see `docs/DEPLOYMENT.md` § "Account deletion" | — |
| [#74](https://github.com/zelytra/Librarius/issues/74) | Terms of service, privacy policy and legal notice | #72, #73 |
| [#75](https://github.com/zelytra/Librarius/issues/75) | User profile: display name, language, time zone | — |
| [#76](https://github.com/zelytra/Librarius/issues/76) | First-login onboarding | — |
| ✅ [#77](https://github.com/zelytra/Librarius/issues/77) | Complete English locale | #35 |
| [#78](https://github.com/zelytra/Librarius/issues/78) | Accessibility: WCAG 2.1 level AA | #32, #42 |
| [#79](https://github.com/zelytra/Librarius/issues/79) | ✅ Performance: bundle budget and Lighthouse — first payload shrunk and locked down in CI | — |
| [#80](https://github.com/zelytra/Librarius/issues/80) | Public landing page | — |
| [#81](https://github.com/zelytra/Librarius/issues/81) | User documentation | — |
| [#103](https://github.com/zelytra/Librarius/issues/103) | Provision the production environment on librarius.fr — the milestone's own exit condition, and a domain nobody has bought | #58, #59, #63 |

---

## v1.1 — Trust & contributions

**Goal**: let members improve the shared catalog without letting anyone vandalise it. The
trust machinery has to exist before v1.2 shares anything between accounts.

**Exit criterion**: a member can report a catalog error and contribute a missing cover; a
trust flag is computed, shown, and revoked automatically when reports are upheld.

| Issue | Topic | Depends on |
|---|---|---|
| [#180](https://github.com/zelytra/Librarius/issues/180) | Trust flag: automatic evaluation and storage on app_user | — |
| [#186](https://github.com/zelytra/Librarius/issues/186) | Trusted badge next to a member's display name | #180 |
| [#192](https://github.com/zelytra/Librarius/issues/192) | Report button: flag catalog errors | — |
| [#195](https://github.com/zelytra/Librarius/issues/195) | Automatic trust revocation from upheld reports | #180, #192 |
| [#198](https://github.com/zelytra/Librarius/issues/198) | Trusted contribution: missing covers and unlisted catalog entries | #180, #197 |

---

## v1.2 — Social

**Goal**: open the product to other people. Everything before it is what there would be to
share; this is where a library stops being private.

**Exit criterion**: a member can follow another, see a feed of what they read, and share a
review — with a visibility gate, a block and a report path that all work before anything is
public.

| Issue | Topic | Depends on |
|---|---|---|
| [#200](https://github.com/zelytra/Librarius/issues/200) | Follow relationship: data model and API | — |
| [#201](https://github.com/zelytra/Librarius/issues/201) | Public account preference and the mutual-follow visibility gate | #200 |
| [#202](https://github.com/zelytra/Librarius/issues/202) | Find people: search, follow and followers screens | #200, #201 |
| [#203](https://github.com/zelytra/Librarius/issues/203) | Block a user and hide their content | #200 |
| [#205](https://github.com/zelytra/Librarius/issues/205) | Review visibility: private or shared | #48, #201 |
| [#190](https://github.com/zelytra/Librarius/issues/190) | Reviews on a series | #48 |
| [#206](https://github.com/zelytra/Librarius/issues/206) | Review aggregation for books, and the visibility gate on series reviews | #190, #205 |
| [#207](https://github.com/zelytra/Librarius/issues/207) | Reviews and ratings on the Detail and Series screens | #206 |
| [#208](https://github.com/zelytra/Librarius/issues/208) | Report and hide public reviews | #205, #192 |
| [#209](https://github.com/zelytra/Librarius/issues/209) | Social feed: activity log and API | #200, #201 |
| [#210](https://github.com/zelytra/Librarius/issues/210) | Social feed screen with reactions and comments | #209 |
| [#211](https://github.com/zelytra/Librarius/issues/211) | Notify on a comment received on a review | #69, #210 |

---

## Tracking

Ticking a box here when an issue closes is not necessary — GitHub is the source of truth.
At the **end of every milestone**, however:

1. Update [INVENTORY](INVENTORY.md) (debt cleared / discovered).
2. Review the dependencies of the next milestone.
3. Tag `main` with `v0.x.0` once the milestone is complete.
