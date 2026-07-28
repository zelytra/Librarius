# Roadmap — Librarius

Cible : **produit public** multi-utilisateurs, PWA + application mobile native.
Cinq jalons, du plus structurant au plus exposé. Chaque jalon est un **milestone
GitHub** ; les issues portent le détail et les critères d'acceptation.

> Suivi à jour : https://github.com/zelytra/Librarius/milestones

## Principe d'ordonnancement

1. **v0.3 d'abord** : sans état serveur mutualisé ni design system, chaque nouvelle
   feature multiplie la dette. On assainit avant d'élargir.
2. **v0.4 ensuite** : la table `series` conditionne l'écran Série, la vue Séries de la
   Collection, les tomes manquants et les sorties personnalisées. C'est la dépendance
   la plus profonde du produit.
3. **v0.5 en parallèle possible** : les sujets d'exploitation ne touchent pas au code
   applicatif et peuvent avancer indépendamment.
4. **v0.6** dépend de v0.3 (client API propre) mais pas de v0.4.
5. **v1.0** ferme les exigences d'un produit public.

## Par où commencer

Un agent qui reprend le projet et n'a pas d'instruction plus précise prend, dans cet ordre :

1. **[#85](https://github.com/zelytra/Librarius/issues/85)** — le déploiement continu est
   cassé depuis le 1er juillet : plus rien n'est livré, quoi qu'on fasse par ailleurs.
2. **[#58](https://github.com/zelytra/Librarius/issues/58)** — secrets en clair dans un
   dépôt public : même si `librarius.zelytra.fr` n'est qu'une qualification, l'instance
   est joignable depuis Internet et ces identifiants sont exploitables tels quels.
3. **[#30](https://github.com/zelytra/Librarius/issues/30)** — l'état serveur conditionne
   presque tout le travail front qui suit.
4. **[#32](https://github.com/zelytra/Librarius/issues/32)** — les styles inline bloquent
   le thème sombre, l'accessibilité et toute réutilisation.

Déjà traités : [#36](https://github.com/zelytra/Librarius/issues/36) (couverture front)
et [#39](https://github.com/zelytra/Librarius/issues/39) (isolation des données), qui
sert désormais de filet pour le reste du chantier.

---

## v0.3 — Foundations & quality

**Objectif** : rendre le code extensible. Aucune nouvelle fonctionnalité utilisateur
visible, mais toutes les suivantes en dépendent.

**Critère de sortie** : plus aucun `useEffect` de chargement fait main, plus aucun
style de mise en forme inline durable, couverture front sur les 7 écrans, isolation
des données couverte par des tests.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#30](https://github.com/zelytra/Librarius/issues/30) | Move server state to TanStack Query | — |
| [#31](https://github.com/zelytra/Librarius/issues/31) | Orval mutator: token injection and refresh | #30 |
| [#32](https://github.com/zelytra/Librarius/issues/32) | Extract inline styles into CSS Modules | — |
| [#33](https://github.com/zelytra/Librarius/issues/33) | Shared loading, error and empty states | #30 |
| [#34](https://github.com/zelytra/Librarius/issues/34) | Factor out the cover palette and delete mockData.ts | #32 |
| [#35](https://github.com/zelytra/Librarius/issues/35) | Extract every hardcoded string into i18n | — |
| [#36](https://github.com/zelytra/Librarius/issues/36) | ✅ Cover the seven screens with Vitest | — |
| [#37](https://github.com/zelytra/Librarius/issues/37) | Playwright end-to-end suite on the critical journeys | #36 |
| [#38](https://github.com/zelytra/Librarius/issues/38) | Server-side pagination and filtering for library and wishlist | — |
| [#39](https://github.com/zelytra/Librarius/issues/39) | ✅ Per-user data isolation tests | — |
| [#40](https://github.com/zelytra/Librarius/issues/40) | Compute statistics in SQL | — |
| [#41](https://github.com/zelytra/Librarius/issues/41) | Remove HelloResource | — |
| [#42](https://github.com/zelytra/Librarius/issues/42) | Real dark theme driven by tokens | #32 |

---

## v0.4 — Core product

**Objectif** : livrer la profondeur fonctionnelle qui différencie Librarius —
séries, tomes, progression, éditions.

**Critère de sortie** : un utilisateur manga peut voir ses séries, ses tomes manquants,
suivre une série et recevoir ses sorties ; un lecteur de romans peut suivre sa
progression page à page et son objectif annuel.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#43](https://github.com/zelytra/Librarius/issues/43) | V3 migration: series and series_follow tables | — |
| [#44](https://github.com/zelytra/Librarius/issues/44) | /api/series API: details, volumes and follow | #43 |
| [#45](https://github.com/zelytra/Librarius/issues/45) | Series screen: volume grid | #44 |
| [#46](https://github.com/zelytra/Librarius/issues/46) | "Series" view in the Collection | #44 |
| [#47](https://github.com/zelytra/Librarius/issues/47) | Reading progress input in the Detail screen | — |
| [#48](https://github.com/zelytra/Librarius/issues/48) | Personal rating and private review | — |
| [#49](https://github.com/zelytra/Librarius/issues/49) | Alternate editions of a work in the Detail screen | — |
| [#50](https://github.com/zelytra/Librarius/issues/50) | Annual reading goal: setting and gauge on the Home screen | — |
| [#51](https://github.com/zelytra/Librarius/issues/51) | Custom category management | — |
| [#52](https://github.com/zelytra/Librarius/issues/52) | Richer wishlist: editing, budget and conversion | — |
| [#53](https://github.com/zelytra/Librarius/issues/53) | Advanced search and manual add in Discover | — |
| [#54](https://github.com/zelytra/Librarius/issues/54) | Customizable Home screen: reorder and hide sections | — |
| [#55](https://github.com/zelytra/Librarius/issues/55) | Time-based statistics and reading pace | #40 |
| [#56](https://github.com/zelytra/Librarius/issues/56) | V4 migration: normalized genres | — |
| [#57](https://github.com/zelytra/Librarius/issues/57) | Personalized upcoming releases and upcoming_release table | #43, #44 |

---

## v0.5 — Operations & security

**Objectif** : pouvoir exploiter le service sans risque de perte de données ni panne
silencieuse. Peut avancer en parallèle de v0.4.

**Critère de sortie** : aucun secret dans le dépôt, restauration de sauvegarde testée,
alerte reçue en cas d'incident, déploiement sans coupure.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#85](https://github.com/zelytra/Librarius/issues/85) | 🔴 CD deployment fails: Kubernetes credentials rejected | — |
| [#58](https://github.com/zelytra/Librarius/issues/58) | 🔴 Move secrets out of values.yaml into Kubernetes Secrets | — |
| [#59](https://github.com/zelytra/Librarius/issues/59) | 🔴 Automated PostgreSQL backups with a tested restore procedure | — |
| [#60](https://github.com/zelytra/Librarius/issues/60) | Prometheus and Grafana alerting | — |
| [#61](https://github.com/zelytra/Librarius/issues/61) | Rate limiting on catalog endpoints | — |
| [#62](https://github.com/zelytra/Librarius/issues/62) | Restrict Swagger UI and /q endpoints in production | — |
| [#63](https://github.com/zelytra/Librarius/issues/63) | Semantic versioning of images and a rollback procedure | — |
| [#64](https://github.com/zelytra/Librarius/issues/64) | Zero-downtime deployment | — |
| [#65](https://github.com/zelytra/Librarius/issues/65) | Persistent catalog cache | — |
| [#66](https://github.com/zelytra/Librarius/issues/66) | Harden CI: dependencies and static analysis | — |

---

## v0.6 — Native mobile app (Capacitor)

**Objectif** : le scan de code-barres en librairie, cas d'usage n°1 hors ligne du
produit, plus les notifications push.

**Critère de sortie** : APK Android installable produite par la CI, scan ISBN
fonctionnel, notification de sortie reçue.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#67](https://github.com/zelytra/Librarius/issues/67) | Capacitor bootstrap: apps/mobile application | #30, #31 |
| [#68](https://github.com/zelytra/Librarius/issues/68) | ISBN barcode scanning | #67, #53 |
| [#69](https://github.com/zelytra/Librarius/issues/69) | Push notifications: releases and wishlist | #57, #67 |
| [#70](https://github.com/zelytra/Librarius/issues/70) | Android build pipeline | #67 |
| [#71](https://github.com/zelytra/Librarius/issues/71) | iOS build and publishing procedure | #67, #70 |

---

## v1.0 — Public product

**Objectif** : ce qu'exige une mise à disposition publique : conformité, accueil des
nouveaux, langues, accessibilité, performance.

**Critère de sortie** : un inconnu peut créer un compte, comprendre l'app, exporter ou
supprimer ses données, en français ou en anglais, avec un score Lighthouse ≥ 90.

> C'est ce jalon qui ouvre une **production**. `librarius.zelytra.fr` reste une
> qualification jusque-là. Les points de v0.5 tolérés en qualification — secrets,
> sauvegardes, coupure au déploiement — deviennent bloquants ici.

| Issue | Sujet | Dépend de |
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

## Suivi

Cocher ici à la fermeture de chaque issue n'est pas nécessaire — GitHub fait foi.
En revanche, **à la fin de chaque milestone** :

1. Mettre à jour [ÉTAT-DES-LIEUX](ETAT-DES-LIEUX.md) (dette résorbée / découverte).
2. Réviser les dépendances du milestone suivant.
3. Livrer : PR `develop` → `main`, tag `v0.x.0`.
