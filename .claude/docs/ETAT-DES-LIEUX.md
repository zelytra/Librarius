# État des lieux — audit du dépôt

> Audit réalisé le **2026-07-28** sur `develop` (`af55df2`). Décrit le code **tel qu'il
> est**, pas tel qu'il devrait être. À réviser à chaque fin de milestone.

## Vue d'ensemble

Le projet est un squelette **complet et déployé** : les 7 écrans existent, l'API expose
9 ressources, l'auth OIDC fonctionne de bout en bout, la CI/CD livre sur k3s. Ce qui
manque relève de **la profondeur fonctionnelle** (séries, progression fine, sorties VF),
de **la qualité** (tests front quasi absents, styles inline, i18n incomplète) et de
**l'exploitation** (secrets en clair, aucune sauvegarde, aucune alerte).

## Ce qui fonctionne ✅

| Domaine | État |
|---|---|
| Monorepo | pnpm workspaces (`apps/web`) + Maven (`apps/api`), Node 20 / pnpm 9.15.9 / JDK 21 |
| Auth | Keycloak OIDC e2e — Dev Services en test, realm importé en dev, ingress `/auth` en prod |
| Persistence | PostgreSQL + Panache + Flyway (2 migrations), Hibernate en mode `validate` |
| Catalogue | `CatalogService` agrège Open Library (livres) et AniList (mangas), cache Caffeine 6 h / 12 h |
| Import | Booknode (scraping) + CSV, exposés dans les Réglages |
| Contrat API | OpenAPI généré au build → client TS orval, gate CI `openapi-sync` |
| Écrans | Accueil, Collection, Détail, Découvrir, Souhaits, Stats, Réglages — **tous câblés sur l'API live** |
| PWA | `vite-plugin-pwa`, icônes, exclusion `/auth` `/api` `/q` du fallback de navigation |
| Monitoring | Micrometer → `/q/metrics`, Prometheus + Grafana provisionnés, dashboard « Vue d'ensemble » |
| CI/CD | 5 workflows path-filtered ; push `main` → build images GHCR + `helm upgrade` |

## Dette technique identifiée 🔧

### Front — critique

1. **Styles inline massifs.** Chaque écran embarque son CSS en `style={{…}}` (≈ 1 300
   lignes de JSX dont une grande part de style). Les tokens (`shared/styles/tokens.css`)
   existent mais sont contournés. Conséquences : pas de dark mode réel, duplication de
   la palette `PALETTE`/`colorFor` dans 3 fichiers, aucune réutilisation possible.
2. **Aucun état serveur mutualisé.** Chaque page refait ses `fetch` dans un `useEffect`
   avec `// eslint-disable-next-line react-hooks/exhaustive-deps`, sans cache, sans
   invalidation, sans retry. Ouvrir Détail depuis Accueil recharge toute la
   bibliothèque (`getApiLibrary` puis `.find()` côté client).
3. **Gestion d'erreur et de chargement hétérogène.** `DiscoverPage` gère erreur + état
   vide ; `HomePage`, `CollectionPage`, `StatsPage` ignorent les échecs silencieusement.
4. **i18n incomplète.** Un seul fichier (`fr.json`, 66 lignes) alors que la moitié des
   libellés sont en dur dans le JSX (« Reprendre la lecture », « Classement »,
   « Marquer comme lu », « Titre introuvable »…). Aucune autre langue.
5. **Tests quasi inexistants.** Un seul fichier de test front (`App.test.tsx`) pour
   8 écrans. Aucun test d'intégration, aucun e2e.
6. **`mockData.ts` toujours importé** par `CollectionPage` et `DetailPage` pour
   `RANK_COLORS`/`RANK_ICONS` — reliquat de la phase maquette à extraire proprement.

### Back — modéré

7. **Table `series` absente** alors que l'architecture cible la prévoit : la série est
   un simple `work.series_title VARCHAR` dédupliqué par `toLowerCase()` dans
   `StatsResource`. Impossible de suivre « tomes possédés / total » correctement.
8. **`genres` est une `VARCHAR(512)`** traitée comme une valeur atomique dans les stats
   (un livre « Fantasy, Aventure » compte comme un genre distinct de « Fantasy »).
9. **Statistiques calculées en mémoire** : `StatsResource` charge *toute* la
   bibliothèque de l'utilisateur puis agrège en Java. Correct à 100 titres, pas à 5 000.
10. **Pas de pagination** sur `GET /api/library` ni `GET /api/wishlist`.
11. **Tables planifiées mais non créées** : `series`, `catalog_cache`,
    `dashboard_layout`, `notification_pref`, `upcoming_release`, `library_item_rank`
    (le rang est une colonne, pas une table — simplification acceptable à documenter).
12. **`HelloResource` non authentifiée** — endpoint de démo à supprimer.

### Exploitation — critique

13. **Secrets en clair dans `helm/librarius/values.yaml`** : `postgres.password:
    librarius`, `keycloak.adminPassword: admin`, versionnés dans un dépôt public.
14. **Aucune sauvegarde PostgreSQL.** PVC `local-path` sur un nœud unique : une perte
    de disque = perte totale des bibliothèques.
15. **Aucune alerte.** Grafana affiche, personne n'est prévenu.
16. **Stratégie `Recreate`** (contrainte CPU du nœud) → coupure à chaque déploiement.
17. **Tags d'images `<sha>`** poussés sur `latest` : pas de versionnement sémantique ni
    de possibilité de rollback simple.

## Écarts fonctionnels vs vision 📋

| Attendu (docs/ARCHITECTURE.md) | Réalité |
|---|---|
| Éditions multiples par œuvre | Schéma prêt (`work` 1→N `edition`), **aucun écran** ne permet de choisir/comparer une édition |
| Prochaines sorties **VF** | `GET /api/catalog/upcoming` retourne les dates **providers** (JP/EN), affichées « dates indicatives ». Aucune donnée éditeur français |
| Accueil réordonnable/masquable | Sections figées dans `HomePage.tsx` |
| Progression de lecture | Table `reading_progress` existe ; l'UI ne propose que READING / READ (pas de page courante ni de %) |
| Objectifs de lecture | API `GET/PUT /api/goals` opérationnelle, **aucun écran** ne l'expose |
| Catégories custom | `POST /api/categories` opérationnel, l'UI ne montre que Or/Argent/Bronze |
| Notifications | Rien (ni préférences, ni push, ni mail) |
| Séries / tomes | Ni écran série, ni suivi « tome manquant » |
| Export / suppression de compte | Rien — **bloquant pour un produit public (RGPD)** |
| Multilingue | Structure i18n en place, une seule locale |
| Mobile natif | Aucun projet Capacitor |

## Sécurité — points à traiter

- Secrets versionnés (voir dette #13).
- `quarkus.http.cors.origins=http://localhost:5173` en dur : vérifier la configuration
  de production (le web est servi par le même hôte, donc same-origin — à confirmer).
- Swagger UI exposé en production (`quarkus.swagger-ui.always-include=true`).
- Inscription Keycloak **ouverte** sur le realm importé : n'importe qui peut créer un
  compte sur `librarius.zelytra.fr`. Choix assumé ou non ? À trancher.
- Import Booknode = scraping d'un site tiers : dépendance fragile et juridiquement
  grise, à documenter (conditions d'utilisation) et à isoler derrière un feature flag.
- Aucun rate limiting sur `/api/catalog/search` → un utilisateur peut faire consommer
  le quota Open Library / AniList de l'instance.

## Métriques du dépôt

| Indicateur | Valeur |
|---|---|
| Classes Java (main) | 46 |
| Tests Java | 8 fichiers |
| Fichiers front (src) | 30 |
| Tests front | 1 fichier |
| Migrations Flyway | 2 |
| Endpoints REST exposés | 19 (9 ressources) |
| Locales | 1 (fr) |
| Workflows CI | 5 |
