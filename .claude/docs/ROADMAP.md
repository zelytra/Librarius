# Roadmap — Librarius

Target: a **public**, multi-user product, PWA plus a native mobile application.
Five milestones, from the most structural to the most exposed. Each milestone is a **GitHub
milestone**; the issues carry the detail and the acceptance criteria.

> Live tracking: https://github.com/zelytra/Librarius/milestones

## Ordering principle

1. **v0.3 first**: without shared server state or a design system, every new feature
   multiplies the debt. Clean up before widening the scope.
2. **v0.4 next**: the `series` table gates the Series screen, the Series view in the
   Collection, missing volumes and personalised releases. It is the deepest dependency in
   the product.
3. **v0.5 can run in parallel**: the operational topics do not touch application code and
   can move independently.
4. **v0.6** depends on v0.3 (a clean API client) but not on v0.4.
5. **v1.0** closes out what a public product requires.

## Where to start

An agent picking the project up with no more precise instruction takes, in this order:

1. **[#85](https://github.com/zelytra/Librarius/issues/85)** — continuous deployment has
   been broken since 1 July: nothing ships any more, whatever else gets done.
2. **[#58](https://github.com/zelytra/Librarius/issues/58)** — the chart no longer carries
   any credential, but the exposed passwords are still valid: **rotating them on the
   cluster** is a manual step, procedure in `docs/DEPLOYMENT.md`.
3. **[#30](https://github.com/zelytra/Librarius/issues/30)** — server state gates almost all
   the front-end work that follows.

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
| [#33](https://github.com/zelytra/Librarius/issues/33) | Shared loading, error and empty states | #30 |
| [#34](https://github.com/zelytra/Librarius/issues/34) | ✅ Factor out the cover palette and delete mockData.ts | #32 |
| [#35](https://github.com/zelytra/Librarius/issues/35) | Extract every hardcoded string into i18n | — |
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
| [#49](https://github.com/zelytra/Librarius/issues/49) | Alternate editions of a work in the Detail screen | — |
| [#50](https://github.com/zelytra/Librarius/issues/50) | Annual reading goal: setting and gauge on the Home screen | — |
| [#51](https://github.com/zelytra/Librarius/issues/51) | Custom category management | — |
| [#52](https://github.com/zelytra/Librarius/issues/52) | Richer wishlist: editing, budget and conversion — ✅ API, screen pending | — |
| [#114](https://github.com/zelytra/Librarius/issues/114) | ✅ Order the wishlist by urgency and not by the enum name | — |
| [#53](https://github.com/zelytra/Librarius/issues/53) | ✅ Advanced search and manual add in Discover | — |
| [#54](https://github.com/zelytra/Librarius/issues/54) | Customizable Home screen: reorder and hide sections | — |
| [#55](https://github.com/zelytra/Librarius/issues/55) | Time-based statistics and reading pace | #40 |
| [#56](https://github.com/zelytra/Librarius/issues/56) | ✅ V6 migration: normalized genres — API, genre filter pending in the Collection screen | — |
| [#57](https://github.com/zelytra/Librarius/issues/57) | Personalized upcoming releases and upcoming_release table | #43, #44 |

---

## v0.5 — Operations & security

**Goal**: be able to run the service without risking data loss or a silent outage. Can move
in parallel with v0.4.

**Exit criterion**: no secret in the repository, a restore procedure actually tested, an
alert received when something breaks, deployment without downtime.

| Issue | Topic | Depends on |
|---|---|---|
| [#85](https://github.com/zelytra/Librarius/issues/85) | 🔴 CD deployment fails: Kubernetes credentials rejected | — |
| [#58](https://github.com/zelytra/Librarius/issues/58) | 🔴 Move secrets out of values.yaml into Kubernetes Secrets — chart done, rotation pending on the cluster | — |
| [#59](https://github.com/zelytra/Librarius/issues/59) | 🔴 Automated PostgreSQL backups with a tested restore procedure — CronJob and procedure shipped (off by default), the restore itself is still to be exercised on the cluster | — |
| [#60](https://github.com/zelytra/Librarius/issues/60) | Prometheus and Grafana alerting — rules and runbooks shipped; needs a Prometheus, kube-state-metrics and an Alertmanager on the cluster | — |
| [#61](https://github.com/zelytra/Librarius/issues/61) | Rate limiting on catalog endpoints | — |
| [#62](https://github.com/zelytra/Librarius/issues/62) | Restrict Swagger UI and /q endpoints in production | — |
| [#63](https://github.com/zelytra/Librarius/issues/63) | Semantic versioning of images and a rollback procedure — pipeline and documentation done, the rollback itself is still to be exercised on the cluster | — |
| [#64](https://github.com/zelytra/Librarius/issues/64) | Zero-downtime deployment | — |
| [#65](https://github.com/zelytra/Librarius/issues/65) | Persistent catalog cache | — |
| [#66](https://github.com/zelytra/Librarius/issues/66) | Harden CI: dependencies and static analysis | — |

---

## v0.6 — Native mobile app (Capacitor)

**Goal**: barcode scanning in a bookshop, the product's number-one offline use case, plus
push notifications.

**Exit criterion**: an installable Android APK produced by CI, working ISBN scanning, a
release notification actually received.

| Issue | Topic | Depends on |
|---|---|---|
| [#67](https://github.com/zelytra/Librarius/issues/67) | Capacitor bootstrap: apps/mobile application | #30, #31 |
| [#68](https://github.com/zelytra/Librarius/issues/68) | ISBN barcode scanning | #67, #53 |
| [#69](https://github.com/zelytra/Librarius/issues/69) | Push notifications: releases and wishlist | #57, #67 |
| [#70](https://github.com/zelytra/Librarius/issues/70) | Android build pipeline | #67 |
| [#71](https://github.com/zelytra/Librarius/issues/71) | iOS build and publishing procedure | #67, #70 |

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
| [#72](https://github.com/zelytra/Librarius/issues/72) | Library export (CSV and JSON) | — |
| [#73](https://github.com/zelytra/Librarius/issues/73) | Account deletion and erasure of all user data | — |
| [#74](https://github.com/zelytra/Librarius/issues/74) | Terms of service, privacy policy and legal notice | #72, #73 |
| [#75](https://github.com/zelytra/Librarius/issues/75) | User profile: display name, language, time zone | — |
| [#76](https://github.com/zelytra/Librarius/issues/76) | First-login onboarding | — |
| [#77](https://github.com/zelytra/Librarius/issues/77) | Complete English locale | #35 |
| [#78](https://github.com/zelytra/Librarius/issues/78) | Accessibility: WCAG 2.1 level AA | #32, #42 |
| [#79](https://github.com/zelytra/Librarius/issues/79) | Performance: bundle budget and Lighthouse | — |
| [#80](https://github.com/zelytra/Librarius/issues/80) | Public landing page | — |
| [#81](https://github.com/zelytra/Librarius/issues/81) | User documentation | — |

---

## Tracking

Ticking a box here when an issue closes is not necessary — GitHub is the source of truth.
At the **end of every milestone**, however:

1. Update [INVENTORY](INVENTORY.md) (debt cleared / discovered).
2. Review the dependencies of the next milestone.
3. Tag `main` with `v0.x.0` once the milestone is complete.
