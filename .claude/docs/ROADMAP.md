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

---

## v0.3 — Fondations & qualité

**Objectif** : rendre le code extensible. Aucune nouvelle fonctionnalité utilisateur
visible, mais toutes les suivantes en dépendent.

**Critère de sortie** : plus aucun `useEffect` de chargement fait main, plus aucun
style de mise en forme inline durable, couverture front sur les 7 écrans, isolation
des données couverte par des tests.

| # | Issue | Dépend de |
|---|---|---|
| 1 | Migrer l'état serveur vers TanStack Query (orval `react-query`) | — |
| 2 | Mutator orval : injection du jeton et rafraîchissement automatique | 1 |
| 3 | Sortir les styles inline vers des CSS Modules adossés aux tokens | — |
| 4 | Composants partagés `Loading` / `ErrorState` / `EmptyState` + `ErrorBoundary` | 1 |
| 5 | Factoriser `colorFor`/`PALETTE`, supprimer `mockData.ts` | 3 |
| 6 | Externaliser tous les textes en dur vers i18n | — |
| 7 | Couvrir les 7 écrans par des tests Vitest (avec MSW) | 1, 4 |
| 8 | Suite e2e Playwright sur les parcours P1–P5 | 7 |
| 9 | Pagination et filtres serveur sur `/api/library` et `/api/wishlist` | — |
| 10 | Tests d'isolation des données (`alice` vs `bob`) sur toutes les ressources | — |
| 11 | Calculer les statistiques en SQL au lieu de la mémoire | — |
| 12 | Supprimer `HelloResource` | — |
| 13 | Thème sombre réel, piloté par les tokens | 3 |

---

## v0.4 — Cœur produit

**Objectif** : livrer la profondeur fonctionnelle qui différencie Librarius —
séries, tomes, progression, éditions.

**Critère de sortie** : un utilisateur manga peut voir ses séries, ses tomes manquants,
suivre une série et recevoir ses sorties ; un lecteur de romans peut suivre sa
progression page à page et son objectif annuel.

| # | Issue | Dépend de |
|---|---|---|
| 14 | Migration V3 : tables `series` et `series_follow`, rattachement des `work` | — |
| 15 | API `/api/series` : fiche, tomes, suivi | 14 |
| 16 | Écran Série : grille des tomes, possédés / lus / manquants | 15 |
| 17 | Vue « Séries » dans la Collection avec progression `x / y` | 15 |
| 18 | Saisie de progression de lecture (page, %, dates) dans le Détail | — |
| 19 | Notation personnelle et commentaire privé | — |
| 20 | Éditions alternatives d'une œuvre dans le Détail | — |
| 21 | Objectif annuel : écran de réglage + jauge sur l'Accueil | — |
| 22 | Gestion des catégories personnalisées (UI + `PUT`/`DELETE` API) | — |
| 23 | Souhaits enrichis : modification, budget total, conversion en collection | — |
| 24 | Recherche avancée dans Découvrir (auteur, année) + ajout manuel guidé | — |
| 25 | Accueil personnalisable : réordonner et masquer les sections | — |
| 26 | Statistiques temporelles : lectures par mois, rythme, projection | 11 |
| 27 | Migration V4 : genres normalisés (`genre`, `work_genre`) | — |
| 28 | Prochaines sorties personnalisées + table `upcoming_release` curée | 14, 15 |

---

## v0.5 — Exploitation & sécurité

**Objectif** : pouvoir exploiter le service sans risque de perte de données ni panne
silencieuse. Peut avancer en parallèle de v0.4.

**Critère de sortie** : aucun secret dans le dépôt, restauration de sauvegarde testée,
alerte reçue en cas d'incident, déploiement sans coupure.

| # | Issue | Dépend de |
|---|---|---|
| 29 | Sortir les secrets de `values.yaml` vers des Secrets Kubernetes | — |
| 30 | Sauvegarde PostgreSQL automatisée **et restauration testée** | — |
| 31 | Alertes Prometheus / Grafana (API down, erreurs 5xx, disque, certificat) | — |
| 32 | Rate limiting sur `/api/catalog/*` | — |
| 33 | Restreindre Swagger UI et les endpoints `/q` en production | — |
| 34 | Versionnement sémantique des images + procédure de rollback | — |
| 35 | Déploiement sans coupure (`RollingUpdate` + sondes) | — |
| 36 | Cache catalogue persistant (`catalog_cache`) | — |
| 37 | Durcir la CI : Dependabot, audit de dépendances, analyse statique | — |

---

## v0.6 — Application mobile native (Capacitor)

**Objectif** : le scan de code-barres en librairie, cas d'usage n°1 hors ligne du
produit, plus les notifications push.

**Critère de sortie** : APK Android installable produite par la CI, scan ISBN
fonctionnel, notification de sortie reçue.

| # | Issue | Dépend de |
|---|---|---|
| 38 | Bootstrap Capacitor : `apps/mobile` réutilisant le build web | v0.3 #1, #2 |
| 39 | Scan de code-barres ISBN (caméra) → fiche catalogue | 38, 24 |
| 40 | Notifications push : sorties des séries suivies, souhaits disponibles | 28, 38 |
| 41 | Pipeline de build Android (APK signée, artefact CI) | 38 |
| 42 | Build iOS et procédure de publication | 38, 41 |

---

## v1.0 — Produit public

**Objectif** : ce qu'exige une mise à disposition publique : conformité, accueil des
nouveaux, langues, accessibilité, performance.

**Critère de sortie** : un inconnu peut créer un compte, comprendre l'app, exporter ou
supprimer ses données, en français ou en anglais, avec un score Lighthouse ≥ 90.

| # | Issue | Dépend de |
|---|---|---|
| 43 | Export de sa bibliothèque (CSV + JSON) — *RGPD* | — |
| 44 | Suppression de compte et de toutes ses données — *RGPD* | — |
| 45 | CGU, politique de confidentialité, mentions légales | 43, 44 |
| 46 | Profil utilisateur : nom affiché, langue, fuseau (`PATCH /api/me`) | — |
| 47 | Onboarding première connexion (import ou premiers titres) | — |
| 48 | Locale anglaise complète | v0.3 #6 |
| 49 | Accessibilité WCAG 2.1 AA | v0.3 #3, #13 |
| 50 | Performance : budget de bundle, Lighthouse ≥ 90, images optimisées | — |
| 51 | Page publique de présentation (hors authentification) | — |
| 52 | Documentation utilisateur (aide en ligne, FAQ) | — |

---

## Suivi

Cocher ici à la fermeture de chaque issue n'est pas nécessaire — GitHub fait foi.
En revanche, **à la fin de chaque milestone** :

1. Mettre à jour [ÉTAT-DES-LIEUX](ETAT-DES-LIEUX.md) (dette résorbée / découverte).
2. Réviser les dépendances du milestone suivant.
3. Livrer : PR `develop` → `main`, tag `v0.x.0`.
