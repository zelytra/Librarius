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

1. **[#58](https://github.com/zelytra/Librarius/issues/58)** — secrets en clair dans un
   dépôt public : même si `librarius.zelytra.fr` n'est qu'une qualification, l'instance
   est joignable depuis Internet et ces identifiants sont exploitables tels quels.
2. **[#30](https://github.com/zelytra/Librarius/issues/30)** — l'état serveur conditionne
   presque tout le travail front qui suit.
3. **[#32](https://github.com/zelytra/Librarius/issues/32)** — les styles inline bloquent
   le thème sombre, l'accessibilité et toute réutilisation.
4. **[#39](https://github.com/zelytra/Librarius/issues/39)** — l'isolation des données
   n'est vérifiée par aucun test alors que le service est ouvert.

---

## v0.3 — Fondations & qualité

**Objectif** : rendre le code extensible. Aucune nouvelle fonctionnalité utilisateur
visible, mais toutes les suivantes en dépendent.

**Critère de sortie** : plus aucun `useEffect` de chargement fait main, plus aucun
style de mise en forme inline durable, couverture front sur les 7 écrans, isolation
des données couverte par des tests.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#30](https://github.com/zelytra/Librarius/issues/30) | Migrer l'état serveur vers TanStack Query (orval `react-query`) | — |
| [#31](https://github.com/zelytra/Librarius/issues/31) | Mutator orval : injection du jeton et rafraîchissement automatique | #30 |
| [#32](https://github.com/zelytra/Librarius/issues/32) | Sortir les styles inline vers des CSS Modules adossés aux tokens | — |
| [#33](https://github.com/zelytra/Librarius/issues/33) | Composants partagés `Loading` / `ErrorState` / `EmptyState` + `ErrorBoundary` | #30 |
| [#34](https://github.com/zelytra/Librarius/issues/34) | Factoriser `colorFor`/`PALETTE`, supprimer `mockData.ts` | #32 |
| [#35](https://github.com/zelytra/Librarius/issues/35) | Externaliser tous les textes en dur vers i18n | — |
| [#36](https://github.com/zelytra/Librarius/issues/36) | Couvrir les 7 écrans par des tests Vitest (avec MSW) | #30, #33 |
| [#37](https://github.com/zelytra/Librarius/issues/37) | Suite e2e Playwright sur les parcours P1–P5 | #36 |
| [#38](https://github.com/zelytra/Librarius/issues/38) | Pagination et filtres serveur sur `/api/library` et `/api/wishlist` | — |
| [#39](https://github.com/zelytra/Librarius/issues/39) | Tests d'isolation des données (`alice` vs `bob`) sur toutes les ressources | — |
| [#40](https://github.com/zelytra/Librarius/issues/40) | Calculer les statistiques en SQL au lieu de la mémoire | — |
| [#41](https://github.com/zelytra/Librarius/issues/41) | Supprimer `HelloResource` | — |
| [#42](https://github.com/zelytra/Librarius/issues/42) | Thème sombre réel, piloté par les tokens | #32 |

---

## v0.4 — Cœur produit

**Objectif** : livrer la profondeur fonctionnelle qui différencie Librarius —
séries, tomes, progression, éditions.

**Critère de sortie** : un utilisateur manga peut voir ses séries, ses tomes manquants,
suivre une série et recevoir ses sorties ; un lecteur de romans peut suivre sa
progression page à page et son objectif annuel.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#43](https://github.com/zelytra/Librarius/issues/43) | Migration V3 : tables `series` et `series_follow`, rattachement des `work` | — |
| [#44](https://github.com/zelytra/Librarius/issues/44) | API `/api/series` : fiche, tomes, suivi | #43 |
| [#45](https://github.com/zelytra/Librarius/issues/45) | Écran Série : grille des tomes, possédés / lus / manquants | #44 |
| [#46](https://github.com/zelytra/Librarius/issues/46) | Vue « Séries » dans la Collection avec progression `x / y` | #44 |
| [#47](https://github.com/zelytra/Librarius/issues/47) | Saisie de progression de lecture (page, %, dates) dans le Détail | — |
| [#48](https://github.com/zelytra/Librarius/issues/48) | Notation personnelle et commentaire privé | — |
| [#49](https://github.com/zelytra/Librarius/issues/49) | Éditions alternatives d'une œuvre dans le Détail | — |
| [#50](https://github.com/zelytra/Librarius/issues/50) | Objectif annuel : écran de réglage + jauge sur l'Accueil | — |
| [#51](https://github.com/zelytra/Librarius/issues/51) | Gestion des catégories personnalisées (UI + `PUT`/`DELETE` API) | — |
| [#52](https://github.com/zelytra/Librarius/issues/52) | Souhaits enrichis : modification, budget total, conversion en collection | — |
| [#53](https://github.com/zelytra/Librarius/issues/53) | Recherche avancée dans Découvrir (auteur, année) + ajout manuel guidé | — |
| [#54](https://github.com/zelytra/Librarius/issues/54) | Accueil personnalisable : réordonner et masquer les sections | — |
| [#55](https://github.com/zelytra/Librarius/issues/55) | Statistiques temporelles : lectures par mois, rythme, projection | #40 |
| [#56](https://github.com/zelytra/Librarius/issues/56) | Migration V4 : genres normalisés (`genre`, `work_genre`) | — |
| [#57](https://github.com/zelytra/Librarius/issues/57) | Prochaines sorties personnalisées + table `upcoming_release` curée | #43, #44 |

---

## v0.5 — Exploitation & sécurité

**Objectif** : pouvoir exploiter le service sans risque de perte de données ni panne
silencieuse. Peut avancer en parallèle de v0.4.

**Critère de sortie** : aucun secret dans le dépôt, restauration de sauvegarde testée,
alerte reçue en cas d'incident, déploiement sans coupure.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#58](https://github.com/zelytra/Librarius/issues/58) | 🔴 Sortir les secrets de `values.yaml` vers des Secrets Kubernetes | — |
| [#59](https://github.com/zelytra/Librarius/issues/59) | 🔴 Sauvegarde PostgreSQL automatisée **et restauration testée** | — |
| [#60](https://github.com/zelytra/Librarius/issues/60) | Alertes Prometheus / Grafana (API down, erreurs 5xx, disque, certificat) | — |
| [#61](https://github.com/zelytra/Librarius/issues/61) | Rate limiting sur `/api/catalog/*` | — |
| [#62](https://github.com/zelytra/Librarius/issues/62) | Restreindre Swagger UI et les endpoints `/q` en production | — |
| [#63](https://github.com/zelytra/Librarius/issues/63) | Versionnement sémantique des images + procédure de rollback | — |
| [#64](https://github.com/zelytra/Librarius/issues/64) | Déploiement sans coupure (`RollingUpdate` + sondes) | — |
| [#65](https://github.com/zelytra/Librarius/issues/65) | Cache catalogue persistant (`catalog_cache`) | — |
| [#66](https://github.com/zelytra/Librarius/issues/66) | Durcir la CI : Dependabot, audit de dépendances, analyse statique | — |

---

## v0.6 — Application mobile native (Capacitor)

**Objectif** : le scan de code-barres en librairie, cas d'usage n°1 hors ligne du
produit, plus les notifications push.

**Critère de sortie** : APK Android installable produite par la CI, scan ISBN
fonctionnel, notification de sortie reçue.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#67](https://github.com/zelytra/Librarius/issues/67) | Bootstrap Capacitor : `apps/mobile` réutilisant le build web | #30, #31 |
| [#68](https://github.com/zelytra/Librarius/issues/68) | Scan de code-barres ISBN (caméra) → fiche catalogue | #67, #53 |
| [#69](https://github.com/zelytra/Librarius/issues/69) | Notifications push : sorties des séries suivies, souhaits disponibles | #57, #67 |
| [#70](https://github.com/zelytra/Librarius/issues/70) | Pipeline de build Android (APK signée, artefact CI) | #67 |
| [#71](https://github.com/zelytra/Librarius/issues/71) | Build iOS et procédure de publication | #67, #70 |

---

## v1.0 — Produit public

**Objectif** : ce qu'exige une mise à disposition publique : conformité, accueil des
nouveaux, langues, accessibilité, performance.

**Critère de sortie** : un inconnu peut créer un compte, comprendre l'app, exporter ou
supprimer ses données, en français ou en anglais, avec un score Lighthouse ≥ 90.

> C'est ce jalon qui ouvre une **production**. `librarius.zelytra.fr` reste une
> qualification jusque-là. Les points de v0.5 tolérés en qualification — secrets,
> sauvegardes, coupure au déploiement — deviennent bloquants ici.

| Issue | Sujet | Dépend de |
|---|---|---|
| [#72](https://github.com/zelytra/Librarius/issues/72) | 🔴 Export de sa bibliothèque (CSV + JSON) — *RGPD* | — |
| [#73](https://github.com/zelytra/Librarius/issues/73) | 🔴 Suppression de compte et de toutes ses données — *RGPD* | — |
| [#74](https://github.com/zelytra/Librarius/issues/74) | CGU, politique de confidentialité, mentions légales | #72, #73 |
| [#75](https://github.com/zelytra/Librarius/issues/75) | Profil utilisateur : nom affiché, langue, fuseau (`PATCH /api/me`) | — |
| [#76](https://github.com/zelytra/Librarius/issues/76) | Onboarding première connexion (import ou premiers titres) | — |
| [#77](https://github.com/zelytra/Librarius/issues/77) | Locale anglaise complète | #35 |
| [#78](https://github.com/zelytra/Librarius/issues/78) | Accessibilité WCAG 2.1 AA | #32, #42 |
| [#79](https://github.com/zelytra/Librarius/issues/79) | Performance : budget de bundle, Lighthouse ≥ 90, images optimisées | — |
| [#80](https://github.com/zelytra/Librarius/issues/80) | Page publique de présentation (hors authentification) | — |
| [#81](https://github.com/zelytra/Librarius/issues/81) | Documentation utilisateur (aide en ligne, FAQ) | — |

---

## Suivi

Cocher ici à la fermeture de chaque issue n'est pas nécessaire — GitHub fait foi.
En revanche, **à la fin de chaque milestone** :

1. Mettre à jour [ÉTAT-DES-LIEUX](ETAT-DES-LIEUX.md) (dette résorbée / découverte).
2. Réviser les dépendances du milestone suivant.
3. Livrer : PR `develop` → `main`, tag `v0.x.0`.
