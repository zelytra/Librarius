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
cp infra/.env.example infra/.env   # then fill in every empty value
cd infra
docker compose -f compose.prod.yml up -d
```

`POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD` and `GF_ADMIN_PASSWORD` have **no default
value**: compose refuses to start while one of them is missing, rather than falling back
to a password published in this repository. `infra/.env` is git-ignored.

Services: `postgres`, `keycloak` (:8081), `api`, `web` (:8088), `prometheus`, `grafana` (:3000).
The PWA (`web`) serves the app and **proxies** `/api` to the API (see `apps/web/nginx.conf`). `/q` is not proxied: Prometheus reaches the api container directly over the compose network.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_DB` | `librarius` | Database |
| `POSTGRES_PASSWORD` | **required** | Database password |
| `KEYCLOAK_ADMIN` / `GF_ADMIN_USER` | `admin` | Admin account names |
| `KEYCLOAK_ADMIN_PASSWORD` / `GF_ADMIN_PASSWORD` | **required** | Admin console passwords |
| `OIDC_AUTH_SERVER_URL` | `http://localhost:8081/realms/librarius` | Realm the API validates against |
| `KC_HOSTNAME` | `http://localhost:8081` | Public host of Keycloak |
| `WEB_PORT` / `GRAFANA_PORT` | `8088` / `3000` | Exposed ports |

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
  `/auth` → Keycloak, `/api` → api, `/` → web. `/q` is deliberately not routed.
- **End-to-end OIDC**: issuer = `https://librarius.zelytra.fr/auth/realms/librarius`.
  The web app embeds that authority at build time (`VITE_OIDC_AUTHORITY`); the api
  validates internally (discovery/JWKS through the Keycloak service, dynamic
  backchannel).
- **Trigger**: a push to `main` runs `cd.yml`: build and push the GHCR images (the web
  image built with the OIDC authority), `ghcr-pull` secret, then
  `helm upgrade --install` with the `<sha>` tags.
- **PostgreSQL**: one instance, two databases (`librarius` + `keycloak`), `local-path` PVC.
- **Credentials**: read from Kubernetes Secrets, see the section below. The chart carries
  none and ships no default.

### 🔑 Cluster secrets (manual action, before any deployment)

The chart reads two Secrets from the `librarius` namespace. Their names are passed by
`cd.yml` (`--set postgres.existingSecret=…`, `--set keycloak.existingSecret=…`); nothing
else about them lives in this repository.

| Secret | Key | Read by |
|---|---|---|
| `librarius-postgres` | `postgres-password` | postgres (`POSTGRES_PASSWORD`), api (`QUARKUS_DATASOURCE_PASSWORD`), keycloak (`KC_DB_PASSWORD`) |
| `librarius-keycloak` | `admin-password` | keycloak (`KEYCLOAK_ADMIN_PASSWORD`) |

Create them once, with values generated locally. The namespace has to exist first — the
pipeline creates it, but on a fresh cluster the secrets come before the first deployment:

```bash
kubectl create namespace librarius --dry-run=client -o yaml | kubectl apply -f -

kubectl -n librarius create secret generic librarius-postgres \
  --from-literal=postgres-password="$(openssl rand -base64 24)"

kubectl -n librarius create secret generic librarius-keycloak \
  --from-literal=admin-password="$(openssl rand -base64 24)"
```

Read a value back when you need it (to sign in to the admin console, for instance):

```bash
kubectl -n librarius get secret librarius-keycloak \
  -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

While a Secret is missing, `helm upgrade` stops at render time with
`postgres.existingSecret is required: …`: the deployment fails loudly instead of falling
back to a known password.

The chart seeds **no user** in the imported realm any more — a credential written there
would be a credential published here. Sign-up is open: create your account through the web
app.

### 🔄 Rotating the exposed credentials (required)

The passwords that used to sit in `values.yaml` **stay valid** until they are changed on
the cluster, and they remain readable in the git history of a public repository. Removing
them from the chart does not close the exposure; rotating them does.

**1. PostgreSQL.** The container only reads `POSTGRES_PASSWORD` when it initialises an
empty volume, so the role has to be altered in place, then the Secret updated to match:

```bash
NEW_PG=$(openssl rand -base64 24)

kubectl -n librarius exec deploy/librarius-postgres -- \
  psql -U librarius -d librarius -c "ALTER ROLE librarius WITH PASSWORD '$NEW_PG'"

kubectl -n librarius create secret generic librarius-postgres \
  --from-literal=postgres-password="$NEW_PG" \
  --dry-run=client -o yaml | kubectl apply -f -

# The consumers only read the Secret at startup.
kubectl -n librarius rollout restart deployment/librarius-api deployment/librarius-keycloak
unset NEW_PG
```

**2. Keycloak admin.** `KEYCLOAK_ADMIN_PASSWORD` only bootstraps the account on the first
startup: changing the Secret alone does not change an existing admin. Change it in the
console — `https://librarius.zelytra.fr/auth/admin`, *master* realm, user `admin`,
*Credentials* tab, *Reset password* — then align the Secret:

```bash
kubectl -n librarius create secret generic librarius-keycloak \
  --from-literal=admin-password='<the new password>' \
  --dry-run=client -o yaml | kubectl apply -f -
```

Use a shell where history is disabled, or `read -rs NEW_KC` to avoid leaving the value in
`~/.bash_history`.

**3. Check what the exposure may have left behind.** The admin console was reachable from
the Internet with a published password: in the *master* and *librarius* realms, review the
user list, the clients and the service accounts, and delete anything you did not create.

### ⚠️ DNS prerequisite (action required)

`librarius.zelytra.fr` must resolve (A record) to the public IP of the cluster
(`92.170.11.63`). Until it does, the ingress is unreachable and cert-manager cannot
issue the certificate. Since Keycloak is served on a **path** (`/auth`), **a single**
DNS record is enough for the whole stack.

No account is created by the deployment: sign-up is open, register from the web app.
The `alice` / `bob` test accounts only exist in the local stack (`infra/docker-compose.yml`
plus `infra/keycloak/realm-librarius.json`, bound to localhost).

## Later on

- A **native** image (GraalVM) for the API (faster startup, smaller footprint) — to be
  enabled in `release.yml` only (too slow for the pull request CI).
- A secret store (External Secrets, Sealed Secrets) instead of Secrets created by hand.
- Grafana SSO through Keycloak (generic OAuth).

## Secret scanning

The `secrets` workflow (`.github/workflows/secrets.yml`) runs `gitleaks` on the working
tree of every pull request; the rules live in `.gitleaks.toml`. Reproduce a CI failure
locally:

```bash
gitleaks detect --source . --config .gitleaks.toml --no-git --redact --verbose
```

The scan deliberately ignores the git history — it still holds the credentials removed
here, and rewriting the history of a public repository is not an option. The allowlist
covers the local development fixtures (`infra/docker-compose.yml`, the local realm, the
`%dev` profile of the API), which never leave localhost.
