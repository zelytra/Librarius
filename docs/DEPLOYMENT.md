# Déploiement — Ma Bibliothèque (Librarius)

## Images Docker

Deux images sont construites par le workflow `.github/workflows/release.yml` :

| Image | Contenu | Dockerfile | Contexte |
|---|---|---|---|
| `ghcr.io/zelytra/librarius-api` | API Quarkus (JVM) | `apps/api/src/main/docker/Dockerfile.jvm` | `apps/api` |
| `ghcr.io/zelytra/librarius-web` | PWA statique (nginx) | `apps/web/Dockerfile` | racine du repo |

- **Sur les PR** : les images sont **construites mais pas poussées** (validation des Dockerfiles).
- **Sur `main` / tags `v*`** : build **+ push** vers GHCR (tags `latest` et `<sha>`), via le `GITHUB_TOKEN` (permission `packages: write`).

## Lancer la stack de production

```bash
cd infra
docker compose -f compose.prod.yml up -d
```

Services : `postgres`, `keycloak` (:8081), `api`, `web` (:8088), `prometheus`, `grafana` (:3000).
La PWA (`web`) sert l'app et **proxifie** `/api` et `/q` vers l'API (voir `apps/web/nginx.conf`).

## Variables d'environnement

| Variable | Défaut | Rôle |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `librarius` | Base de données |
| `OIDC_AUTH_SERVER_URL` | `http://localhost:8081/realms/librarius` | Realm validé par l'API |
| `KC_HOSTNAME` | `http://localhost:8081` | Hôte public de Keycloak |
| `WEB_PORT` / `GRAFANA_PORT` | `8088` / `3000` | Ports exposés |
| `KEYCLOAK_ADMIN(_PASSWORD)` / `GF_ADMIN_*` | `admin` | Comptes admin (**à changer**) |

## ⚠️ Point d'attention OIDC (issuer)

La connexion OIDC se fait **dans le navigateur** : l'« issuer » des jetons doit être
**identique** entre le navigateur et l'API. En pratique :

1. Choisir une URL publique stable pour Keycloak (ex. `https://auth.mondomaine.fr`).
2. La passer à Keycloak via `KC_HOSTNAME`, à l'API via `OIDC_AUTH_SERVER_URL`,
   **et** au build du web via l'arg `VITE_OIDC_AUTHORITY` (sinon la valeur par
   défaut `http://localhost:8081/...` est gravée dans l'image).
3. Ajouter l'URL publique du web aux `redirectUris` du client `librarius-web`
   (dans `infra/keycloak/realm-librarius.json`).

Rebuild du web avec un domaine personnalisé :

```bash
docker build -f apps/web/Dockerfile \
  --build-arg VITE_OIDC_AUTHORITY=https://auth.mondomaine.fr/realms/librarius \
  -t ghcr.io/zelytra/librarius-web:latest .
```

## Kubernetes (Helm) — librarius.zelytra.fr

La **stack complète** est déployée sur le cluster k3s via la chart `helm/librarius`
(v0.2.0) : **web** (PWA), **api** (Quarkus), **PostgreSQL** (PVC) et **Keycloak**.

- **Hôte unique** `librarius.zelytra.fr` (Traefik + cert-manager `letsencrypt-prod`,
  secret `librarius-zelytra-fr-tls`). Routage par chemin :
  `/auth` → Keycloak, `/api` + `/q` → api, `/` → web.
- **OIDC e2e** : issuer = `https://librarius.zelytra.fr/auth/realms/librarius`.
  Le web embarque cette autorité au build (`VITE_OIDC_AUTHORITY`) ; l'api valide en
  interne (discovery/JWKS via le service Keycloak, backchannel dynamique).
- **Déclencheur** : push `main` → `cd.yml` : build+push images GHCR (web buildé avec
  l'autorité OIDC), secret `ghcr-pull`, `helm upgrade --install` avec les tags `<sha>`.
- **PostgreSQL** : une instance, deux bases (`librarius` + `keycloak`), PVC `local-path`.

### ⚠️ Prérequis DNS (action requise)

`librarius.zelytra.fr` doit pointer (A) vers l'IP publique du cluster (`92.170.11.63`).
Tant que ce n'est pas le cas, l'ingress n'est pas joignable et cert-manager ne peut
pas émettre le certificat. Comme Keycloak est servi en **chemin** (`/auth`), **un seul**
enregistrement DNS suffit pour toute la stack.

Identifiants de test (realm importé) : **alice / alice** (l'inscription est ouverte).

## Pistes ultérieures

- Image **native** (GraalVM) pour l'API (démarrage/empreinte réduits) — à activer
  dans `release.yml` uniquement (trop lent pour la CI de PR).
- Secrets gérés hors `compose.prod.yml` (fichier `.env` non versionné ou secret store).
- SSO Grafana via Keycloak (OAuth générique).
