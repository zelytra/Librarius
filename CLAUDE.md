# Librarius — instructions projet

Bibliothèque personnelle **livres & mangas** : collection, suivi de lecture, souhaits,
découverte catalogue, statistiques. PWA React + API Quarkus + PostgreSQL + Keycloak.

> 📚 **Documentation complète pour agents : [`.claude/docs/`](.claude/docs/README.md).**
> Lis au minimum [ÉTAT-DES-LIEUX](.claude/docs/ETAT-DES-LIEUX.md) et
> [CONVENTIONS](.claude/docs/CONVENTIONS.md) avant toute modification.

## Langue

Réponses et documentation **en français**. Code, commits, pull requests, issues et
milestones **en anglais** — le titre d'une PR devient le message de commit au squash.

**Tout le code est en anglais**, commentaires et javadoc compris — y compris là où
l'existant est encore en français : convertis ce que tu touches.

**L'application reste en français** : `fr` est la seule locale, les textes utilisateur
sont rédigés en français dans `i18n/locales/fr.json`.

## Commandes

```bash
pnpm install            # dépendances front (pnpm 9, Node 20)
pnpm infra:up           # postgres :5432 · keycloak :8081 · prometheus :9090 · grafana :3000
pnpm api:dev            # API Quarkus :8080 (mvnw quarkus:dev)
pnpm web:dev            # PWA Vite :5173 (proxy /api → 8080)
```

Qualité — **à lancer avant tout push** :

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
```

```bash
cd apps/api && ./mvnw -B verify
```

Après toute modification d'une ressource JAX-RS ou d'un DTO, régénérer le client TS —
sinon la CI `openapi-sync` échoue :

```bash
cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api
```

## Structure

| Chemin | Contenu |
|---|---|
| `apps/web/` | PWA React 19 + Vite 6 + TS. `features/<écran>/`, `shared/` (ui, theme, styles), `api/generated/` (orval — **ne jamais éditer à la main**) |
| `apps/api/` | Quarkus 3 / Java 21. `domain/` (entités + repositories Panache), `web/` (ressources JAX-RS + DTOs), `catalog/` (providers externes), `imports/`, `security/` |
| `openapi/` | **Contrat** entre l'api et le web : schéma produit au build de l'api, consommé par orval. N'appartient à aucune des deux applications |
| `packages/` | Bibliothèques partagées — vide à ce jour, le glob du workspace l'attend |
| `infra/` | docker-compose dev & prod, realm Keycloak, Prometheus, Grafana, chart Helm |
| `infra/helm/librarius/` | chart de déploiement k3s (web, api, postgres, keycloak, ingress) |
| `docs/` | doc publique (ARCHITECTURE, DEPLOYMENT) |
| `.claude/docs/` | doc de travail détaillée pour les agents |

## Git flow — non négociable

`main` (prod) ← `develop` (intégration) ← `feature/*`. Correctifs urgents : `hotfix/*`.

- **Jamais de commit direct** sur `main` ni `develop`.
- Une branche par changement, partant de `develop` à jour.
- Commits conventionnels **en anglais** : `feat(web): …`, `fix(api): …`, `docs: …`, `ci: …`.
- Identité de commit : `zelytra` / `contact@zelytra.fr`.
- Toute modification passe par une PR vers `develop`, puis `develop` → `main` pour livrer.
- **Ne jamais merger avec une CI rouge.** `cd.yml` déploie automatiquement sur push `main`,
  vers la **qualification** (pas de production ouverte à ce jour).

## Règles de code

- **Pas de sur-ingénierie.** Solution simple et standard d'abord.
- **Sécurité** : toute ressource est `@Authenticated` et **scopée par `CurrentUser.id()`** —
  jamais d'accès à une entité sans filtrer sur `user_id`. Voir `security/CurrentUser.java`.
- **Base** : Flyway possède le schéma (`hibernate-orm.database.generation=validate`).
  Toute évolution du modèle passe par une migration `V<n>__description.sql`, jamais par
  une modification d'une migration déjà livrée.
- **Front** : pas de secret ni de clé dans le bundle ; textes utilisateur via `i18n`
  (`useTranslation`), pas de chaîne en dur dans les nouveaux écrans.
- **Tests** : tout comportement corrigé ou ajouté est verrouillé par un test
  (`vitest` côté web, `@QuarkusTest` côté api).

## Environnements

| Env | URL | Notes |
|---|---|---|
| Local | http://localhost:5173 | comptes de test `alice/alice`, `bob/bob` |
| Qualification | https://librarius.zelytra.fr | k3s, ingress unique ; `/auth` → Keycloak, `/api` + `/q` → api |
| Production | — | n'existe pas encore ; à ouvrir au jalon v1.0 |

⚠️ `librarius.zelytra.fr` est un environnement de **qualification**, pas de production :
une coupure de service à la livraison est acceptable, et les données y sont considérées
comme jetables. Cette hypothèse tombe à l'ouverture de la production — voir
[ROADMAP](.claude/docs/ROADMAP.md).
