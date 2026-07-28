# Conventions de travail — Librarius

## 1. Langue

| Élément | Langue |
|---|---|
| **Tout le code** : identifiants, commentaires, javadoc, noms de tests, messages de log, noms de fichiers et de branches | **Anglais** |
| **Issues, milestones, labels** | **Anglais** |
| Commits, pull requests, documentation du projet, échanges | **Français** |
| Textes affichés à l'utilisateur | **Français**, via i18n — jamais en dur |

Le code est intégralement en anglais, commentaires compris : ne pas suivre la langue du
fichier voisin s'il est encore en français, l'écrire en anglais et convertir ce qu'on
touche au passage.

Deux exceptions, volontaires :

- **Les migrations Flyway déjà livrées** (`V1__init.sql`, `V2__progress_and_ranks.sql`)
  gardent leurs commentaires français. Flyway calcule le checksum sur **le contenu
  entier du fichier, commentaires compris** : les retoucher ferait échouer la validation
  au démarrage sur toute base où la migration est déjà appliquée. Les migrations à venir
  sont écrites en anglais.
- **Les messages rendus à l'utilisateur** (`ImportException`, libellés `Or`/`Argent`/
  `Bronze`) restent en français, puisque l'interface l'est. Les messages de log, eux,
  sont en anglais.

**L'application, elle, reste en français** : `fr` est la seule locale, et les textes
utilisateur sont rédigés en français dans `i18n/locales/fr.json`. L'anglais viendra avec
[#77](https://github.com/zelytra/Librarius/issues/77) ; d'ici là, aucune traduction de
l'interface.

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

**Aucune mention d'outillage.** Pas de trailer `Co-Authored-By`, pas de « Generated
with … », aucune signature d'assistant dans les messages de commit, les descriptions de
PR, les issues, le code ou la documentation du projet. Seule exception : les fichiers
destinés au maintien des agents — `CLAUDE.md` et `.claude/`. Y renvoyer par leur chemin
depuis une issue ou une PR reste normal : c'est une référence de fichier, pas une
attribution.

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

### Poste sans JDK ni Docker

Les tests de l'API exigent Docker (Dev Services démarre PostgreSQL et Keycloak). Sur un
poste Windows d'entreprise qui n'a ni JDK ni Docker, passer par WSL — où Docker
fonctionne — et exécuter Maven dans un conteneur :

```bash
wsl -d Ubuntu -- bash -lc 'docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /mnt/c/Users/<utilisateur>/WebstormProjects/Librarius:/workspace \
  -v librarius-m2:/root/.m2 \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -w /workspace/apps/api \
  maven:3.9-eclipse-temurin-21 mvn -B verify'
```

Le volume nommé `librarius-m2` conserve le cache Maven d'une exécution à l'autre : le
premier lancement télécharge tout et prend plusieurs minutes, les suivants sont rapides.

Côté front, Node et pnpm fonctionnent nativement. Attention toutefois : en Node ≥ 22,
le `localStorage` natif prend le pas sur celui de jsdom — `src/test/setup.ts` le
neutralise, ne pas retirer ce garde-fou.

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
