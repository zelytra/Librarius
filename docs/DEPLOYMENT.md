# Deployment — My Library (Librarius)

## Docker images

The `.github/workflows/release.yml` workflow builds two images:

| Image | Contents | Dockerfile | Context |
|---|---|---|---|
| `ghcr.io/zelytra/librarius-api` | Quarkus API (JVM) | `apps/api/src/main/docker/Dockerfile.jvm` | `apps/api` |
| `ghcr.io/zelytra/librarius-web` | Static PWA (nginx) | `apps/web/Dockerfile` | repository root |

- **On pull requests**: images are **built but not pushed** (the Dockerfiles are only validated).
- **On `main` / `v*` tags**: build **and push** to GHCR (tags `latest` and `<sha>`), using the `GITHUB_TOKEN` (`packages: write` permission).

## Running the production stack

```bash
cd infra
docker compose -f compose.prod.yml up -d
```

Services: `postgres`, `keycloak` (:8081), `api`, `web` (:8088), `prometheus`, `grafana` (:3000).
The PWA (`web`) serves the app and **proxies** `/api` and `/q` to the API (see `apps/web/nginx.conf`).

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `librarius` | Database |
| `OIDC_AUTH_SERVER_URL` | `http://localhost:8081/realms/librarius` | Realm the API validates against |
| `KC_HOSTNAME` | `http://localhost:8081` | Public host of Keycloak |
| `WEB_PORT` / `GRAFANA_PORT` | `8088` / `3000` | Exposed ports |
| `KEYCLOAK_ADMIN(_PASSWORD)` / `GF_ADMIN_*` | `admin` | Admin accounts (**change them**) |

## ⚠️ OIDC gotcha (issuer)

The OIDC login happens **in the browser**: the token issuer must be **identical** on the
browser side and on the API side. In practice:

1. Pick a stable public URL for Keycloak (e.g. `https://auth.mondomaine.fr`).
2. Pass it to Keycloak through `KC_HOSTNAME`, to the API through `OIDC_AUTH_SERVER_URL`,
   **and** to the web build through the `VITE_OIDC_AUTHORITY` arg (otherwise the default
   value `http://localhost:8081/...` is baked into the image).
3. Add the public URL of the web app to the `redirectUris` of the `librarius-web` client
   (in `infra/keycloak/realm-librarius.json`).

Rebuilding the web image with a custom domain:

```bash
docker build -f apps/web/Dockerfile \
  --build-arg VITE_OIDC_AUTHORITY=https://auth.mondomaine.fr/realms/librarius \
  -t ghcr.io/zelytra/librarius-web:latest .
```

## Kubernetes (Helm) — librarius.zelytra.fr

The **whole stack** is deployed on the k3s cluster through the `infra/helm/librarius`
chart (v0.2.0): **web** (PWA), **api** (Quarkus), **PostgreSQL** (PVC) and **Keycloak**.

- **Single host** `librarius.zelytra.fr` (Traefik + cert-manager `letsencrypt-prod`,
  secret `librarius-zelytra-fr-tls`). Path-based routing:
  `/auth` → Keycloak, `/api` + `/q` → api, `/` → web.
- **End-to-end OIDC**: issuer = `https://librarius.zelytra.fr/auth/realms/librarius`.
  The web app embeds that authority at build time (`VITE_OIDC_AUTHORITY`); the api
  validates internally (discovery/JWKS through the Keycloak service, dynamic
  backchannel).
- **Trigger**: a push to `main` runs `cd.yml`: build and push the GHCR images (the web
  image built with the OIDC authority), `ghcr-pull` secret, then
  `helm upgrade --install` with the `<sha>` tags.
- **PostgreSQL**: one instance, two databases (`librarius` + `keycloak`), `local-path` PVC.

### ⚠️ DNS prerequisite (action required)

`librarius.zelytra.fr` must resolve (A record) to the public IP of the cluster
(`92.170.11.63`). Until it does, the ingress is unreachable and cert-manager cannot
issue the certificate. Since Keycloak is served on a **path** (`/auth`), **a single**
DNS record is enough for the whole stack.

Test credentials (imported realm): **alice / alice** (self-registration is open).

## Later on

- A **native** image (GraalVM) for the API (faster startup, smaller footprint) — to be
  enabled in `release.yml` only (too slow for the pull request CI).
- Secrets managed outside `compose.prod.yml` (an unversioned `.env` file or a secret store).
- Grafana SSO through Keycloak (generic OAuth).
