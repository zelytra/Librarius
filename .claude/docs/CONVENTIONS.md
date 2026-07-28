# Conventions de travail — Librarius

## 1. Langue

| Élément | Langue |
|---|---|
| Réponses, commits, PR, issues, documentation | **Français** |
| Code, identifiants, noms de fichiers, branches | **Anglais** |
| Commentaires de code | Suivre le fichier — l'existant est majoritairement en français |
| Textes affichés à l'utilisateur | Via i18n, jamais en dur |

## 2. Git flow

```text
main      ──●────────────────●──────────►  qualification (déploiement automatique)
             ↖ merge          ↖ merge
develop   ──●──●──●──●──●──●──●──────────►  intégration
             ↖  ↖  ↖
feature/*    ●  ●  ●
```

- **Aucun commit direct** sur `main` ni `develop`.
- Branche partant de `develop` **à jour** (`git fetch && git reset --hard origin/develop`).
- Nommage : `feature/<sujet-court>`, `fix/<sujet>`, `docs/<sujet>`, `ci/<sujet>`,
  `hotfix/<sujet>` (celui-ci part de `main`).
- Une branche = un changement cohérent = une PR.
- Jamais de force-push sur `main` ni `develop`.

### Livraison

1. `feature/x` → PR vers `develop`, CI verte, merge, suppression de la branche.
2. Pour livrer : PR `develop` → `main`, titre « Release — <sujet> ».
3. Le merge sur `main` déclenche `cd.yml` (build images + `helm upgrade`) vers la
   **qualification** `librarius.zelytra.fr` : **ne jamais merger sur `main` avec une CI
   rouge**. La coupure de service pendant le déploiement y est acceptable.

### Identité de commit

```bash
git config user.name "zelytra"
git config user.email "contact@zelytra.fr"
```

### Messages de commit

Conventional commits, sujet en français, impératif, ≤ 72 caractères :

```text
feat(web): jauge d'objectif annuel sur l'accueil

L'API /api/goals existait déjà mais n'était exposée dans aucun écran.
Ajoute la jauge, le calcul du rythme nécessaire et l'état vide quand
aucun objectif n'est défini.

Closes #42
```

Portées usuelles : `web`, `api`, `db`, `infra`, `deploy`, `ci`, `docs`, `mobile`.

## 3. Pull requests

Titre : `<type>(<portée>) — <résumé>`. Description structurée :

```markdown
## Problème
Ce qui ne va pas / ce qui manque aujourd'hui.

## Correctif
Ce que fait la PR, et pourquoi cette approche.

## Vérifications
- [ ] `pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build`
- [ ] `cd apps/api && ./mvnw -B verify`
- [ ] Client OpenAPI régénéré si l'API a changé
- [ ] Vérifié dans un navigateur (captures / DOM) si l'UI a changé
- [ ] Documentation `.claude/docs/` mise à jour

Closes #<issue>
```

## 4. Qualité — avant tout push

```bash
pnpm web:lint && pnpm --filter @librarius/web typecheck && pnpm web:test && pnpm web:build
```

```bash
cd apps/api && ./mvnw -B verify
```

Si l'API a changé :

```bash
cd apps/api && ./mvnw -B package -DskipTests && cd ../web && pnpm gen:api
```

Si de la documentation a changé (workflow `docs` : markdownlint + liens internes) :

```bash
npx markdownlint-cli2@0.23.2
```

**Changement d'UI** : le vérifier dans un vrai navigateur (`pnpm web:dev`), pas
seulement en test unitaire. Fournir la preuve dans la PR (texte rendu, styles
calculés, absence d'erreur console).

## 5. Style de code

### TypeScript / React

- Composants fonctionnels, hooks. Un écran par dossier dans `features/`.
- **Ne jamais éditer `src/api/generated/`** — régénérer.
- Vérifier `status === 200` avant d'utiliser `data` d'un appel orval.
- Nouveaux styles : CSS Modules adossés aux tokens (`tokens.css`). L'inline est réservé
  aux valeurs réellement dynamiques.
- Textes utilisateur via `useTranslation()` et clés dans `i18n/locales/`.
- Pas de `eslint-disable` sans commentaire justifiant la dérogation.

### Java / Quarkus

- Entités Panache dans `domain/`, requêtes dans `domain/repository/`, **toujours
  scopées `user_id`**.
- Ressources JAX-RS minces : validation + délégation. La logique va dans un service.
- DTOs = `record` dans `ApiDtos`, avec fabrique `of(entity)`. **Jamais** d'entité
  sérialisée directement.
- Toute évolution de schéma = nouvelle migration Flyway (voir
  [MODÈLE-DE-DONNÉES](MODELE-DE-DONNEES.md) § 4).

## 6. Tests

| Portée | Outil | Attendu |
|---|---|---|
| Front unitaire / composant | `vitest` + Testing Library | Tout nouvel écran ou composant partagé |
| Front e2e | Playwright 🔜 | Parcours P1–P5 de [PRODUIT](PRODUIT.md) |
| API intégration | `@QuarkusTest` + Dev Services | Tout nouvel endpoint, y compris le cas « données d'un autre utilisateur » |
| Isolation des données | `@QuarkusTest` avec `alice` et `bob` | **Obligatoire** sur toute ressource scopée utilisateur |

Un correctif de bug s'accompagne du test qui échouait avant.

## 7. Sécurité

- Aucun secret en clair dans le dépôt (ni `values.yaml`, ni `.env` committé, ni sortie
  de commande). Masquer les jetons dans les logs et les réponses.
- Toute nouvelle ressource est `@Authenticated` et scopée utilisateur, sauf décision
  explicite documentée.
- Les modifications de réglages de dépôt ou de cluster (protections de branches,
  secrets CI, permissions) **ne sont pas faites en autonomie** : fournir la procédure
  exacte et attendre validation.

## 8. Documentation

Une PR qui change le comportement, le schéma ou le contrat met à jour le document
concerné **dans la même PR** :

| Changement | Document |
|---|---|
| Nouveau comportement utilisateur | [PRODUIT](PRODUIT.md) |
| Nouveau module, dépendance, décision | [ARCHITECTURE](ARCHITECTURE.md) |
| Migration Flyway | [MODÈLE-DE-DONNÉES](MODELE-DE-DONNEES.md) |
| Endpoint ou DTO | [API](API.md) |
| Issue terminée | [ROADMAP](ROADMAP.md) |
| Dette résorbée ou découverte | [ÉTAT-DES-LIEUX](ETAT-DES-LIEUX.md) |

## 9. Parallélisme (agents)

- Ne jamais bloquer sur une attente longue (pipeline, déploiement, suite e2e) :
  lancer en tâche de fond et poursuivre.
- Grouper les appels d'outils indépendants dans un même message.
- Les gros travaux délégables (audit, traduction en masse, exploration) partent en
  agents parallèles.
