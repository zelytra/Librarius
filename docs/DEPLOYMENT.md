# Deployment — My Library (Librarius)

## Docker images

Two images are published to GHCR:

| Image | Contents | Dockerfile | Context |
|---|---|---|---|
| `ghcr.io/zelytra/librarius-api` | Quarkus API (JVM) | `apps/api/src/main/docker/Dockerfile.jvm` | `apps/api` |
| `ghcr.io/zelytra/librarius-web` | Static PWA (nginx) | `apps/web/Dockerfile` | repository root |

| Event | Workflow | Pushed | Image tags |
|---|---|---|---|
| Pull request | `docker-images.yml` | no — the Dockerfiles are only validated | — |
| Merge into `main` | `cd.yml` | yes, then deployed to staging | `latest`, `<sha>` |
| `vX.Y.Z` tag on `main` | `release.yml` | yes, nothing is deployed | `X.Y.Z`, `X.Y`, `X`, `<sha>` |

Both push with the `GITHUB_TOKEN` (`packages: write` permission).

`latest` means **the head of `main`**, i.e. what staging runs — not the last release. Ask
for a version by its number, never by `latest`.

## Versioning and releases

`main` is permanently releasable. A release is cut by tagging it; the tag is the only
thing that has to be decided by hand, everything below follows from it.

```bash
git checkout main && git pull
git tag -a v0.5.0 -m "v0.5.0"
git push origin v0.5.0
```

`release.yml` then, in order:

1. **Refuses the tag** if it is not a strict `vMAJOR.MINOR.PATCH[-prerelease]`, or if it
   points at a commit that is not contained in `main`.
2. **Builds and pushes** both images with the tags below.
3. **Renders the changelog** from the conventional commits between the previous tag and
   this one (`.github/scripts/changelog.sh`).
4. **Aligns the chart**: `Chart.yaml` (`version`, `appVersion`) and the default
   `web.image.tag` / `api.image.tag` in `values.yaml`
   (`.github/scripts/sync-version.sh`), then `helm lint` and `helm package`.
5. **Creates the GitHub release**, with those notes and the packaged chart attached.
6. **Opens a pull request** `chore/release-X.Y.Z` into `main` carrying the aligned chart
   and the new `CHANGELOG.md` section — `main` takes no direct commit, not even from CI.

### Image tags a tag produces

| Git tag | Image tags published |
|---|---|
| `v0.5.0` | `0.5.0`, `0.5`, `0`, `<sha>` |
| `v0.5.1` | `0.5.1`, `0.5`, `0`, `<sha>` — `0.5` and `0` now point here |
| `v1.0.0` | `1.0.0`, `1.0`, `1`, `<sha>` |
| `v1.0.0-rc.1` | `1.0.0-rc.1`, `<sha>` — a pre-release never moves `X` or `X.Y` |

`X.Y.Z` and `<sha>` never move; `X.Y` and `X` do. Pin `X.Y.Z` in anything you may have to
roll back.

### Which version is running

The version is stamped into the web bundle at build time (`VITE_APP_VERSION`, an `ARG` of
`apps/web/Dockerfile`) and shown at the bottom of the **Settings** screen: `0.5.0` for a
released image, `staging-<short sha>` for one built from `main`, `dev` for a local build.
From the cluster:

```bash
helm -n librarius list                     # chart version and appVersion
kubectl -n librarius get deploy librarius-web librarius-api \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}'
```

### Deploying a released version

Production is not wired: it has no domain yet
([#103](https://github.com/zelytra/Librarius/issues/103)), and `release.yml` deploys
nothing. To run a released version on the existing cluster, deploy it explicitly:

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set web.image.tag=0.5.0 \
  --set api.image.tag=0.5.0 \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --wait --timeout 8m
```

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

## 🔙 Rolling back

Read this one top to bottom. Nothing has to be rebuilt: the previous images are still in
GHCR under their immutable tags, and Helm kept the values of every past revision.

Everything below assumes the `librarius` namespace and the `librarius` release:

```bash
export KUBECONFIG=~/.kube/librarius.yaml   # whatever you keep the cluster config in
alias h='helm -n librarius'
alias k='kubectl -n librarius'
```

### 1. See what is deployed, and what came before

```bash
h history librarius
```

```text
REVISION  UPDATED                   STATUS      CHART            APP VERSION  DESCRIPTION
12        Mon Jul 27 21:04:11 2026  superseded  librarius-0.4.1  0.4.1        Upgrade complete
13        Tue Jul 28 09:12:40 2026  deployed    librarius-0.5.0  0.5.0        Upgrade complete
```

The revision to go back to is the last one whose `APP VERSION` is the one that worked —
here `12`. Check which images it actually carried before committing to it:

```bash
h get values librarius --revision 12 --all | grep -A2 'image:'
```

### 2. Roll back

```bash
h rollback librarius 12 --wait --timeout 8m
```

Without a revision number, `h rollback librarius` goes back exactly one step. Prefer the
explicit number: after a second failed attempt, "one step back" is no longer the version
you have in mind.

Helm records the rollback as a **new revision** (14 here) rather than deleting anything,
so a rollback can itself be rolled back.

### 3. Check, in this order

```bash
# 1. Pods: every one Running and Ready, RESTARTS not climbing.
k get pods -l release=librarius

# 2. Images: the tags you expected, on both deployments.
k get deploy librarius-web librarius-api \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}'

# 3. API health, bypassing ingress and TLS. Leave the forward running in a second
#    shell; it needs no tooling inside the container.
k port-forward deploy/librarius-api 8080:8080 &
curl -s localhost:8080/q/health/ready; kill %1

# 4. Public entry point: 200 on the app, and the API reachable through the ingress.
#    (/api needs a token — /q/health does not, so it is the honest check here.)
curl -sSI https://librarius.zelytra.fr/ | head -1
curl -s https://librarius.zelytra.fr/q/health/ready

# 5. The API log, for a migration or a datasource refusing to start.
k logs deploy/librarius-api --tail=80
```

Then, in a browser: sign in through Keycloak (the OIDC round trip is what a bad `web`
image breaks first), open **Settings** and read the version at the bottom — it must be the
one you rolled back to. Force-reload once: the PWA service worker serves the previous
bundle until it updates.

### If the rollback itself fails

| Symptom | What to do |
|---|---|
| `Error: release: not found` | Wrong namespace. `helm list -A \| grep librarius`. |
| `another operation (install/upgrade/rollback) is in progress` | A previous run was interrupted. `h status librarius` shows `pending-upgrade`; wait for the lock to expire, then `h rollback librarius <revision>`. Never delete the release to unblock it — that drops the PVC. |
| Pods stuck in `ImagePullBackOff` | The `ghcr-pull` secret is missing or expired in the namespace: recreate it (see `cd.yml`), then `k rollout restart deploy/librarius-web deploy/librarius-api`. |
| `--wait` times out but the pods look fine | The readiness probe is failing. `k describe pod <name>` and `k logs <name>` — decide, do not re-run blindly. |
| Everything failed and the site is down | `h rollback librarius <last known good revision>` again; then, only if Helm itself is stuck, `k rollout undo deploy/librarius-api` as a stopgap — it moves the pods without touching the Helm history, which will then be out of step with the cluster. |

### What a rollback does **not** undo

- **Database migrations.** Flyway migrations are applied forward at API startup and are
  never reverted. An older API image against a migrated schema starts only if the
  migration was backward compatible; if it was not, `k logs deploy/librarius-api` shows
  Flyway refusing to start, and the way out is forward (fix and release), not back. Check
  what the release contained before assuming a rollback is safe.
- **Keycloak realm changes** made in the admin console: they live in the database, not in
  the chart.
- **Data written by the newer version.** Rows already created stay.
- **The `latest` tag**, which keeps pointing at the head of `main`.

### Rolling back without a usable Helm history

If `h history` is gone (release re-installed, cluster rebuilt), deploy the version by
number instead — the images are still there:

```bash
h upgrade --install librarius ./infra/helm/librarius \
  --set web.image.tag=0.4.1 \
  --set api.image.tag=0.4.1 \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --wait --timeout 8m
```

Use the chart from the tag that produced those images (`git checkout v0.4.1`), not the one
from `main`: a chart from a later commit may template values the older images do not read.

> Not yet exercised on the cluster. The commands above are the documented procedure; the
> first real rollback should be run once deliberately, out of hours, and this section
> corrected with what actually happens
> ([#63](https://github.com/zelytra/Librarius/issues/63)).

## Later on

- A **native** image (GraalVM) for the API (faster startup, smaller footprint) — to be
  enabled on the tag pipeline (`release.yml`) only, too slow for the pull request CI.
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
