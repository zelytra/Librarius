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

### Pulling the images

**Both packages are public and the chart carries no pull secret**: `imagePullSecrets` is
empty in `values.yaml`, so the kubelet fetches `ghcr.io/zelytra/librarius-*` anonymously,
exactly as anyone else would.

That is a decision, not an omission. `cd.yml` used to write a `ghcr-pull` Secret from the
workflow's `GITHUB_TOKEN`, which is **revoked when the run ends**. The pull then only
worked while the run was still alive: in
[#125](https://github.com/zelytra/Librarius/issues/125) a pod sat `Pending` for nine
minutes on a saturated node, and by the time the scheduler placed it the credential was
dead — `ImagePullBackOff` on a release that was perfectly good, reported as an
authentication error that said nothing about expiry. A pod carrying an `imagePullSecret`
gets **no anonymous retry**, either: the kubelet uses the credentials in its keyring and
stops there, so a dead token failed the pull of an image that needed no credential at all.

The trade-off is that the images can be downloaded by anyone. The source they are built
from already can be, and they carry no credential — every password comes from a Kubernetes
Secret at runtime, and the only build arguments are the public OIDC authority and the
version string — so publishing them exposes nothing this repository does not.

What it buys: **no credential is involved in a pull**. Nothing expires, nothing has to be
rotated, nothing can leak, and a pod evicted and rescheduled a week later pulls exactly
like the first one did.

Checking the visibility without credentials — the same question the kubelet asks:

```bash
repo=zelytra/librarius-web
token=$(curl -fsS "https://ghcr.io/token?scope=repository:$repo:pull&service=ghcr.io" | jq -r .token)
curl -sSI -H "Authorization: Bearer $token" \
  -H 'Accept: application/vnd.oci.image.index.v1+json' \
  "https://ghcr.io/v2/$repo/manifests/latest" | head -1
```

`HTTP/1.1 200 OK` is public; `403 Forbidden` is private, and every pod will end in
`ImagePullBackOff`. `cd.yml` runs that check on both images before it touches the cluster,
so a package flipped back to private fails the deployment in seconds with a message naming
the visibility, instead of an authentication error several minutes later.

The `ghcr-pull` Secret left behind by the old pipeline is no longer referenced by anything.
It holds a token that expired long ago; delete it once, so it cannot be mistaken later for
a credential the deployment depends on:

```bash
kubectl -n librarius delete secret ghcr-pull --ignore-not-found
```

#### Making a package public

Only needed if one is not. GitHub → the `zelytra` profile → **Packages** →
`librarius-web` → **Package settings** (right-hand column) → **Danger Zone** → **Change
visibility** → *Public*, then type the package name to confirm. Repeat for
`librarius-api`. Nothing in this repository can do it: it is an account-level setting.

#### If the images ever have to be private

A credential is then required, and it has to outlive the deployment run — never the
workflow's `GITHUB_TOKEN`:

1. Create a **classic** personal access token whose only scope is `read:packages`
   (GitHub → *Settings* → *Developer settings* → *Personal access tokens* → *Tokens
   (classic)* → *Generate new token (classic)*). Give it an expiry date you will actually
   honour rather than none at all.
2. Write it into the namespace once, by hand — not from CI, which is what expired:

   ```bash
   kubectl -n librarius create secret docker-registry ghcr-pull \
     --docker-server=ghcr.io \
     --docker-username='<github user>' \
     --docker-password='<the token>' \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

3. Deploy with `--set imagePullSecrets[0].name=ghcr-pull`, and remove the anonymous check
   from `cd.yml` — it asserts the opposite decision.

**Rotating that credential**: the same command overwrites the Secret, and it has to be run
before the token expires. Running pods are not affected — the Secret is only read when
something is pulled — which is exactly the trap: nothing fails until the next pod is
created, possibly weeks later and out of hours. So follow the rotation with
`kubectl -n librarius rollout restart deploy/librarius-web deploy/librarius-api`, and watch
that pull succeed rather than find out at 3 a.m. A token left to expire unnoticed is
[#136](https://github.com/zelytra/Librarius/issues/136) again, one credential later.

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
| `KEYCLOAK_ADMIN_ENABLED` | `false` | Whether the API may delete Keycloak accounts — see § "Account deletion" |
| `KEYCLOAK_ADMIN_SERVER_URL` | *(empty)* | Base URL of Keycloak **without the realm**, e.g. `http://librarius-keycloak:8081/auth` |
| `KEYCLOAK_ADMIN_REALM` | `librarius` | Realm the accounts live in |
| `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET` | *(empty)* | Service account used for the admin API |

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
  image built with the OIDC authority), check they are anonymously pullable, then
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
| `librarius-backup` | `access-key-id`, `secret-access-key`, `encryption-passphrase` | the backup CronJob — **only** when `backup.enabled=true`, see § "Automated backups" |
| `librarius-keycloak-admin` | `admin-client-secret` | the api, to delete Keycloak accounts — **only** when `api.accountDeletion.enabled=true`, see § "Account deletion" |

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

## 🚦 Deploying without downtime

Both deployments roll: `maxSurge: 1`, `maxUnavailable: 0`. The replacement pod has to
report ready before the running one is stopped, so a merge into `main` no longer takes the
site down.

That was not free. `Recreate` had been chosen because the node could not fit two API pods
at once, and the fix is a sizing one before it is a strategy one — the requests were wrong,
not the node.

### What the node actually has

`zeserver`, single-node k3s, **4 CPU** allocatable. Requests are what the scheduler
counts, and they were **96% committed** (3850m) while the node was running at **8%**
(318m) of real load. A surge pod does not fail to schedule because the machine is busy; it
fails because the pods already on it have claimed CPU they never use.

Measured on the cluster over six minutes, against what the chart used to ask for:

| Pod | CPU used | CPU requested (before) | Memory used | Memory requested |
|---|---|---|---|---|
| `api` | 2–8m | 150m | 222Mi | 256Mi |
| `web` | 1m | 50m | 6Mi | 64Mi |
| `postgres` | 3–6m | 50m | 41Mi | 128Mi |
| `keycloak` | 3–63m | 100m | 506Mi | 384Mi |

The API request drops to **100m** and the web request to **25m** — still 12× and 25× what
they draw. A request is a scheduling floor and a share weight under contention, not a
budget: the JVM boot still bursts up to its 1 CPU limit and is not throttled by the lower
request.

### The arithmetic that makes it fit

`helm upgrade` rolls both deployments at once, so the peak is both surge pods together:

| | CPU requests | Free of 4000m |
|---|---|---|
| Before, steady | 3850m | 150m |
| Before, rolling both | 4050m | **−50m — does not schedule** |
| After, steady | 3775m | 225m |
| After, rolling both | 3900m | 100m |

That −50m is the nine-minute `Pending` of [#125](https://github.com/zelytra/Librarius/issues/125),
and the reason its image pull then failed: the `ghcr-pull` token had expired by the time the
pod was finally scheduled. This section fixes the sizing half; the credential half is
[#136](https://github.com/zelytra/Librarius/issues/136), settled by pulling public images
with no credential at all (§ "Pulling the images"). **The node takes a rolling update of
the API, with 100m of margin.** No extra capacity is required.

The margin is real but it is not large, and it is shared with projects this repository does
not own — `default` holds five other stacks, together requesting 3500m for well under 100m
of actual use. Before adding a component here, check the headroom rather than assuming it:

```bash
kubectl describe node zeserver | grep -A6 'Allocated resources'
```

### Why the API needed more than a strategy change

A surge pod is only useful if the platform can tell "still starting" from "broken". It
could not: the liveness probe gave up at 50s (`initialDelaySeconds: 20`, three periods of
10s) against a JVM boot measured at **23.2s**, Flyway included. On a good day that is 19s
of slack. On a contended node it is a healthy pod killed mid-boot, restarted, and killed
again — a crash loop caused entirely by the probe.

A `startupProbe` on `/q/health/started` removes the question: while it runs, liveness and
readiness are **disabled outright**, and it allows 30 attempts at 5s intervals — 150s of
boot budget. Quarkus answers that endpoint as soon as the HTTP layer is up, which is
exactly the signal wanted.

Both pods also get a **5s `preStop` pause**. Removing a pod from the Service endpoints and
sending it SIGTERM are concurrent operations, not sequential ones: without the pause,
Traefik keeps routing to a container that has already started shutting down, and a rolling
update produces a handful of 502s precisely because it is rolling. The pause fits inside
the termination grace period (30s for the API, 15s for nginx).

### Two things this changes about how you deploy

**Migrations now overlap.** A surging API runs its Flyway migrations while the previous
one is still serving requests, for the ~30s the two coexist. Under `Recreate` the old pod
was already gone. A migration that drops or renames anything the previous version reads
will break it for that window:

```bash
# Deliberate downtime, for a migration that is not backward compatible.
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set api.strategy=Recreate \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak
```

Write migrations expand-then-contract and this never comes up: add the new column, ship
the code that reads both, drop the old one a release later.

**A node drain now blocks.** The `PodDisruptionBudget` asks for `minAvailable: 1` on a
single replica, so the eviction API refuses to take it — which is the point for an
accidental eviction, and an obstacle for a deliberate drain:

```bash
kubectl drain zeserver --ignore-daemonsets --disable-eviction   # bypasses the PDB
# or, to remove the budgets entirely:
helm -n librarius upgrade ... --set podDisruptionBudget.enabled=false
```

### Measuring it

From outside the cluster, through the ingress — where the user stands. `infra/downtime-probe.sh`
polls four times a second and counts a connection failure or a 5xx as down; anything the
application answered, a 401 on `/api/me` included, is up.

```bash
./infra/downtime-probe.sh https://librarius.zelytra.fr/ 600 &      # frontend
./infra/downtime-probe.sh https://librarius.zelytra.fr/api/me 600  # API
```

Start it, merge, and read the totals when `cd.yml` reports green. Do **not** probe
`/q/health/ready` from outside: `/q` is not routed, so `/` catches it and nginx serves
`index.html` with a 200 — the probe would report the API healthy while it is down.

The reference to beat, measured on the deployment of 2026-07-28 17:17 UTC, the last one
made under `Recreate`, from the pod events (`Killing` on the old pod → `Ready` on its
replacement):

| | Old pod stopped | New pod ready | Outage |
|---|---|---|---|
| `web` | 17:17:45 | 17:17:56 | **11s** |
| `api` | 17:17:44 | 17:18:15 | **31s** |

Both images pulled in under 3s and both pods scheduled instantly, so those are best-case
figures for the old strategy, not typical ones.

### Measured, 2026-07-28

Both deployments were restarted at once on the cluster while an external probe polled the
public URL every ~3 s:

```text
22:51:43  kubectl -n librarius rollout restart deploy/librarius-web deploy/librarius-api
          Waiting for deployment "librarius-web" rollout to finish: 1 old replicas are pending termination...
          deployment "librarius-web" successfully rolled out
          Waiting for deployment "librarius-api" rollout to finish: 1 old replicas are pending termination...
          deployment "librarius-api" successfully rolled out
22:52:04  done
```

**21 s** for both, and `1 old replicas are pending termination` is the ordering being
respected: the replacement was already serving when the outgoing pod was removed — that is
what `maxUnavailable: 0` buys.

The probe covered the window and kept going for seven minutes: **124 samples, 124× `200`
on `/` and 124× `401` on `/api/me`** — no 5xx, no connection error, no gap. `401` rather
than `200` on the API is the point: it is the correct answer to an unauthenticated call, so
it proves the API answered rather than the ingress falling back to the PWA.

Compare with the `Recreate` strategy it replaces: 11 s of outage on `web`, 31 s on `api`.

## 📈 Scaling out

The previous section makes a release invisible at one replica. This one is about the
replica after that: what the stack is expected to take, what happens when it does not, and
why the autoscaling the chart now carries is **switched off**.

### The target

There is no real traffic yet, so this is a chosen ceiling rather than a measurement.
Replace it the day one exists.

| | Load | Pass |
|---|---|---|
| Concurrent sessions | 50 | — |
| `GET /api/library` | **20 req/s sustained**, 5 minutes | p95 < 500 ms, no 5xx |
| `GET /api/catalog/search` | **2 req/s sustained**, warm cache | p95 < 500 ms, no 5xx |

The library figure is [#187](https://github.com/zelytra/Librarius/issues/187)'s proposal
unchanged. **The catalog figure is not**, and the reason is worth keeping: a caller is
capped at 30 catalog calls a minute (`librarius.catalog.rate-limit.per-minute`), so 20 req/s
would need 40 distinct accounts and would measure the rate limiter rather than the API.
Above that, a cold miss is bounded by the provider deadline
(`librarius.catalog.provider.call-timeout`, 12 s) and not by anything this stack controls,
so only a warm cache says something about the API. 2 req/s over the session pool keeps the
catalog path represented in the percentile without turning the run into a quota test.

What the target implies, before anything is measured: `/api/library` is one paginated query,
and the api pod has a **1 CPU limit**. That is the arithmetic that decides when a second pod
is needed, and the load test below is what turns it into a number.

### What the node has — and does not

Requests are what the scheduler counts. `zeserver` has **4000m allocatable**, of which
Librarius accounts for 305m and five unrelated stacks in `default` for ~3500m — for well
under 100m of measured use, but a request is a claim whether or not it is used.

An extra `api` pod costs **100m**, an extra `web` pod **25m**:

| | CPU requests | Free of 4000m |
|---|---|---|
| Today, steady | 3805m | 195m |
| Today, rolling `web` + `api` | 3930m | 70m |
| `api` at 2, steady | 3905m | 95m |
| `api` at 2 + `web` at 2, steady | 3930m | 70m |
| `api` at 2, **rolling `api`** | 4005m | **−5m — does not schedule** |
| `api` at 2 + `web` at 2, **rolling both** | 4055m | **−55m — does not schedule** |
| `api` at 3, steady | 4005m | **−5m — does not schedule** |

Read the last three rows before enabling anything. The node **can** hold a second `api` pod
standing still, and **cannot** hold a deployment while it does: `maxSurge: 1` asks for a
third, and a third does not fit. The failure is not loud — `maxUnavailable: 0` means the old
pod keeps serving, so the site stays up while the rollout sits in `Pending` until something
scales back down. That is [#125](https://github.com/zelytra/Librarius/issues/125) with the
site still standing, and it is why autoscaling ships **off**:

```bash
kubectl describe node zeserver | grep -A6 'Allocated resources'
```

**This is a cluster-sizing decision, not a chart one.** The 3500m that `default` requests
for under 100m of use is where the room is; freeing it, adding a node or taking a bigger one
are the three ways forward, and none of them belongs in this repository. Until one happens,
the honest statement is that Librarius has capacity for **one** extra replica of either
component and no capacity for a release while it is scaled out.

### The PostgreSQL connection budget

One PostgreSQL instance serves the API, Keycloak and the backup job. Its `max_connections`
is the ceiling every pool is carved out of, and both defaults were wrong for a stack with
more than one API pod: Agroal's 50 per pod and **Keycloak's own 100** together exceed the
server on their own. The chart now states all three numbers rather than inheriting them:

| Consumer | Connections | Set by |
|---|---|---|
| `api`, per pod | 25 | `api.datasource.maxSize` → `QUARKUS_DATASOURCE_JDBC_MAX_SIZE` |
| `api`, worst case | **75** | 3 pods — 2 replicas plus the surge of a rolling update |
| Keycloak | 15 | `keycloak.dbPoolMaxSize` → `KC_DB_POOL_MAX_SIZE` |
| Backup CronJob | 2 | `pg_dump`, a couple of minutes a day |
| A human holding a `psql` | 5 | left free on purpose |
| **Total** | **97** | `postgres.maxConnections` (100) − 3 reserved for superusers |

The point of making the sum fit is the *shape of the failure*. Pools that overcommit the
server do not queue: PostgreSQL answers `FATAL: sorry, too many clients already`, which
reaches the user as a 5xx and — since `/q/metrics` publishes nothing about the pool
([#132](https://github.com/zelytra/Librarius/issues/132)) — is invisible until
`LibrariusApiHighErrorRate` fires. Pools that fit **wait**, which is slow and recoverable.

25 is a ceiling, not a reservation: Agroal opens nothing until a request needs it. Sized
against the target rather than against the server, it is still generous — 20 req/s spending
200 ms in a transaction needs 4 connections, plus the 4 a cold catalog fetch may hold
(`librarius.catalog.cache.fetch.concurrency`), leaving 21 for everything else. Note that
this changes the ratio `application.properties` reasons about out loud: 4 of 25 rather than
4 of 50, so 21 always remain rather than 46.

Raising `postgres.maxConnections` is the wrong first move. A connection is a backend process
worth several MiB and the container has a 512Mi limit, so the pools come down before the
ceiling goes up.

### The catalog rate limiter at two replicas

`catalog/RateLimiter.java` keeps its counters in memory, per instance. At N pods the quota
is enforced N times and the effective ceiling is multiplied by N — at the 2 replicas this
chart can reach, **60 calls a minute and 1000 a day per caller** instead of 30 and 500.

That is accepted rather than fixed, and the reasoning is: the limit exists to stop one
caller from burning the instance's Open Library and AniList quota, not to bill anyone. A
ceiling that doubles is still a ceiling, it is bounded and known, and the alternative is a
shared counter — which means Redis, a fourth stateful component, on a node that has no room
for a second API pod.

What doubles alongside it is the number of concurrent outbound fetches: 4 per pod becomes 8.
That is the figure that actually reaches the providers, and it is the one to watch.

If the aggregate has to be held exactly, divide it by the replica count — at the cost of
halving the limit whenever only one pod is running:

```bash
helm -n librarius upgrade ... \
  --set api.catalogRateLimit.perMinute=15 \
  --set api.catalogRateLimit.perDay=250
```

### Turning autoscaling on

**Check the node first** (table above). Then check that the cluster can measure CPU at all:
an HPA without metrics-server sits at `<unknown>/70%` and never scales, quietly.

```bash
kubectl top nodes                                   # the direct answer
kubectl get apiservice v1beta1.metrics.k8s.io       # must report True
kubectl -n kube-system get deploy metrics-server
```

k3s bundles metrics-server unless the server was started with
`--disable=metrics-server`. **That has not been verified on `zeserver`** — it is an
assumption, and the three commands above are how it stops being one.

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set autoscaling.enabled=true \
  --wait --timeout 8m

kubectl -n librarius get hpa
```

```text
NAME             REFERENCE                   TARGETS   MINPODS   MAXPODS   REPLICAS
librarius-api    Deployment/librarius-api    6%/70%    1         2         1
librarius-web    Deployment/librarius-web    4%/70%    1         2         1
```

`TARGETS` reading `<unknown>/70%` is the metrics-server answer, not a load answer.

Two consequences of switching it on:

- **`replicas` disappears from both Deployments.** The chart omits it while
  `autoscaling.enabled` is true, on purpose: a value there would be reasserted by every
  `helm upgrade` and undone by the HPA moments later, so a deployment would reset the
  scale-out it was deployed into. `api.replicas` and `web.replicas` stop being read.
- **The `--set` has to go into `cd.yml`**, next to the two Secret names, or the next merge
  into `main` removes the HPAs. That is the same trap as `backup.enabled`.

The disruption budgets improve on their own: `minAvailable: 1` against 2 replicas finally
allows an eviction instead of denying every one, so a `kubectl drain` stops needing
`--disable-eviction` while the deployment is scaled out.

### Scaling out by hand

The fallback if metrics-server turns out to be absent, and the right tool for a scale-out
that is planned rather than reactive. Go through Helm, not through `kubectl scale`: with
autoscaling off the chart templates `replicas`, so a `helm upgrade` would put it straight
back to 1.

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set api.replicas=2 \
  --wait --timeout 8m
```

`kubectl -n librarius scale deploy/librarius-api --replicas=2` is still the fastest thing to
type in an incident, and it holds until the next deployment. Know which of the two you are
doing.

Coming back down is the same command with `1`, and it matters: while the deployment is at 2
replicas, **the next release cannot schedule its surge pod** (table above).

### Running the load test

`infra/loadtest/librarius-load.js` drives the target above with
[k6](https://k6.io) and encodes it as thresholds, so the run exits non-zero when it is
missed — a pass or a fail rather than a graph. It is **read-only**, it creates no account
and writes no row, and it signs in through the direct access grant the `librarius-web`
client already allows, the same route the e2e suite takes.

Accounts are supplied, never created. Use at least 4 so the catalog scenario stays under the
per-caller quota, and prefer accounts whose library holds a realistic number of titles — an
empty collection measures an empty query.

```bash
k6 run \
  -e BASE_URL=https://librarius.zelytra.fr \
  -e ACCOUNTS='loadtest1:<pw>,loadtest2:<pw>,loadtest3:<pw>,loadtest4:<pw>' \
  infra/loadtest/librarius-load.js
```

Locally, against `pnpm infra:up` plus `pnpm api:dev`, Keycloak is on its own host:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e AUTH_URL=http://localhost:8081 \
  -e ACCOUNTS='alice:alice,bob:bob' infra/loadtest/librarius-load.js
```

Watch the cluster while it runs — the numbers that matter are not all in k6's output:

```bash
kubectl -n librarius get hpa -w                 # does it scale, and when
kubectl -n librarius top pods                   # CPU against the 1 CPU limit
kubectl -n librarius get pods -w                # a surge pod stuck Pending
```

Record the result in this section. Three things make it worth reading later: the request
rate at which p95 crossed 500 ms, the CPU the api pod was drawing at that point, and whether
the HPA reacted before the latency did.

> **Not yet run.** Nothing in this section has been executed against
> `librarius.zelytra.fr`: the load test has never been run, no HPA has ever existed on the
> cluster, metrics-server has not been confirmed present, and no `api` pod has ever been
> scheduled alongside another. The chart renders and lints, the arithmetic above is derived
> from the measurements in § "Deploying without downtime" and § "Alerting", and that is all
> it is. [#187](https://github.com/zelytra/Librarius/issues/187) stays open on it.

## 💾 Automated backups

PostgreSQL runs on a `local-path` PVC, on a single node, with no redundancy of any kind:
losing that disk loses every library ever entered by hand. The chart ships a CronJob that
dumps the database off the node, encrypted, once a day.

It is **disabled by default** (`backup.enabled: false`), and it stays disabled until
somebody decides where the archives are to be stored. That decision is not the chart's to
make, so the chart invents neither a bucket nor a credential.

### What the maintainer has to provide

Three things, none of which exist in this repository:

1. **A bucket, outside the cluster.** Any S3-compatible provider works — AWS S3, Scaleway
   Object Storage, OVH, Backblaze B2, a MinIO on another machine. It must be a *different*
   failure domain than the node; a bucket on the same host backs up nothing.
2. **A credential limited to that bucket**: an access key and a secret key, with write and
   delete rights on the chosen prefix and nothing else. Delete is required — the job prunes
   its own retention tiers.
3. **An encryption passphrase**, generated locally and *kept somewhere other than the
   cluster*. The archives are encrypted with it; without it they are unreadable, including
   by you. Storing it only in the cluster you are backing up defeats the point.

### Enabling it

Create the Secret once, in the `librarius` namespace:

```bash
kubectl -n librarius create secret generic librarius-backup \
  --from-literal=access-key-id='<access key>' \
  --from-literal=secret-access-key='<secret key>' \
  --from-literal=encryption-passphrase="$(openssl rand -base64 32)"
```

Read the passphrase back **immediately** and store it in a password manager — this is the
one value you cannot regenerate later:

```bash
kubectl -n librarius get secret librarius-backup \
  -o jsonpath='{.data.encryption-passphrase}' | base64 -d; echo
```

Then deploy with backups on. `backup.s3.endpoint` is left empty for AWS S3 itself and set
to the provider's endpoint for anything else:

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set backup.enabled=true \
  --set backup.existingSecret=librarius-backup \
  --set backup.s3.bucket=librarius-backups \
  --set backup.s3.region=fr-par \
  --set backup.s3.endpoint=https://s3.fr-par.scw.cloud \
  --wait --timeout 8m
```

`helm upgrade` refuses to render while `backup.s3.bucket` or `backup.existingSecret` is
missing, in the same way it already refuses without the PostgreSQL Secret.

**Beware of the reverse**: a later `helm upgrade` that omits `--set backup.enabled=true`
removes the CronJob. Backups then stop without anything failing, which is exactly what the
`LibrariusBackupTooOld` alert exists to catch.

### What it does, and what it costs

| | |
|---|---|
| Schedule | `backup.schedule`, `15 2 * * *` UTC by default |
| Dump | `pg_dump` plain SQL, `gzip -9`, in the `postgres:16-alpine` image (init container) |
| Encryption | `gpg --symmetric --cipher-algo AES256`, passphrase from the Secret |
| Destination | `s3://<bucket>/<prefix>/{daily,weekly,monthly}/librarius-<UTC timestamp>.sql.gz.gpg` |
| Retention | 7 daily · 4 weekly (Sundays) · 6 monthly (the 1st) — pruned oldest first, per tier |
| Footprint | one pod, a couple of minutes a day, 50m CPU requested. Nothing stays resident |

Plain SQL rather than the custom format is deliberate: a restore then needs `psql`, which
the database pod already has, and not a matching `pg_restore` version at three in the
morning.

### Checking that it works

```bash
# 1. The CronJob exists, is not suspended, and has run.
kubectl -n librarius get cronjob librarius-postgres-backup
```

```text
NAME                        SCHEDULE      SUSPEND   ACTIVE   LAST SCHEDULE   AGE
librarius-postgres-backup   15 2 * * *    False     0        7h32m           3d
```

`SUSPEND=True` or an empty `LAST SCHEDULE` means no backup is being taken.

```bash
# 2. Run one now rather than waiting for tomorrow.
kubectl -n librarius create job --from=cronjob/librarius-postgres-backup backup-manual-1
kubectl -n librarius wait --for=condition=complete job/backup-manual-1 --timeout=10m
kubectl -n librarius logs job/backup-manual-1 --all-containers
```

The log of a successful run ends like this — the last two lines are the job reading its own
object back from the bucket:

```text
dump: 184320 bytes compressed
uploaded librarius/daily/librarius-20260728T021503Z.sql.gz.gpg
2026-07-28 02:15:11     184512 librarius-20260728T021503Z.sql.gz.gpg
backup complete
```

Delete the manual job afterwards (`kubectl -n librarius delete job backup-manual-1`), so it
does not sit in the history the alerting reads.

## 🗑️ Account deletion (GDPR art. 17)

`DELETE /api/me` deletes the caller's `app_user` row — every foreign key pointing at it is
`ON DELETE CASCADE`, so the collection, the wishlist, the reading progress, the goals, the
custom categories and the followed series go with it — **and** the Keycloak account, so
that signing back in is impossible.

The second half needs a service account, and this repository ships none: a credential
committed here is a credential published. Account deletion is therefore **off by default**,
and an instance that has not been configured answers `503` with a message saying that
nothing was touched. That direction is deliberate and is not a fallback to soften: erasing
the library while the login survives would hand the user a freshly provisioned empty
account on their next sign-in, indistinguishable from having lost everything.

### What the maintainer has to provide

A Keycloak client in the `librarius` realm, with a service account allowed to delete users
in that realm — and nothing else. In the admin console
(`https://librarius.zelytra.fr/auth/admin`, realm *librarius*):

1. **Clients → Create client.** Client ID `librarius-api-admin`, type *OpenID Connect*.
   Next.
2. **Capability config**: *Client authentication* **on**, *Service accounts roles* **on**,
   *Standard flow* **off**, *Direct access grants* **off**. Next, then Save.
3. **Credentials tab**: copy the *Client secret*. This is the only value to carry over.
4. **Service accounts roles tab → Assign role → Filter by clients**: assign
   **`realm-management` → `manage-users`**, and only that. `realm-admin` would work and
   grants far more than deleting a user.

Then create the Secret and turn the feature on:

```bash
kubectl -n librarius create secret generic librarius-keycloak-admin \
  --from-literal=admin-client-secret='<the client secret>'
```

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set api.accountDeletion.enabled=true \
  --set api.accountDeletion.existingSecret=librarius-keycloak-admin \
  --wait --timeout 8m
```

`helm upgrade` refuses to render while `api.accountDeletion.existingSecret` is missing, in
the same way it already refuses without the PostgreSQL Secret.

Check it from the api logs: a successful deletion writes one line, carrying the technical
identifier and the counters and **no personal data** — no email, no display name, no title.

```text
Account erased: subject=8b1c… at=2026-07-28T18:41:02Z keycloak=DELETED items=412 wishes=18 …
```

A `Account deletion requested but no Keycloak service account is configured` warning means
the feature is still off and the caller got a 503.

### How long the data really survives

This is what the interface tells the user before they confirm, and it has to stay true:

| Where | When it is gone |
|---|---|
| `librarius` database | **Immediately** — one `DELETE`, cascaded by the schema, inside the request |
| Keycloak | **Immediately**, before the rows: the deletion is refused outright if this fails |
| Application logs | Whatever the cluster keeps. Only the technical identifier is written |
| **Encrypted backups** | **Up to six months** |

The backups are the honest part. `backup.retention` keeps 7 daily, 4 weekly and 6 monthly
archives, so a deleted account's rows remain inside the archives taken *before* the
deletion until the last of them is pruned — up to **six months** for the monthly tier. They
are encrypted, held outside the cluster, and only ever read to recover from a disaster; but
they exist, and telling a user their data is gone in five minutes would not be true.

Nothing restores them selectively: a restore brings back the whole database as it was, the
deleted account included. If that ever happens after a deletion, the deletion has to be
replayed — the api log line above is what says which subject to delete.

If the retention has to be shortened for a legal request, it is `backup.retention.monthly`
in `values.yaml`, and the archives already in the bucket have to be pruned by hand.

## ♻️ Restoring PostgreSQL

Read this section top to bottom **before** typing anything. It assumes the worst realistic
case: the node is gone, the PVC with it, and the only thing left is the bucket.

```bash
export KUBECONFIG=~/.kube/librarius.yaml
alias k='kubectl -n librarius'
```

You need, on the machine you are working from: `aws` (or any S3 client), `gpg`, `gunzip`,
and the **encryption passphrase**. If you do not have the passphrase, stop — the archives
cannot be opened, and no amount of cluster access changes that.

### 1. Find the archive to restore

```bash
export AWS_ACCESS_KEY_ID=<access key>
export AWS_SECRET_ACCESS_KEY=<secret key>
export AWS_DEFAULT_REGION=fr-par
alias s3='aws --endpoint-url https://s3.fr-par.scw.cloud s3'

s3 ls s3://librarius-backups/librarius/daily/
```

```text
2026-07-26 02:15:11     184512 librarius-20260726T021503Z.sql.gz.gpg
2026-07-27 02:15:09     184704 librarius-20260727T021502Z.sql.gz.gpg
2026-07-28 02:15:11     184896 librarius-20260728T021503Z.sql.gz.gpg
```

Take the most recent one, unless the incident is data corruption rather than data loss — in
which case take the last one from *before* the corruption, and check `weekly/` and
`monthly/` if you have to go further back. Sizes should look alike; an archive an order of
magnitude smaller than its neighbours is a bad dump, take the one before it.

### 2. Download, decrypt, decompress

```bash
ARCHIVE=librarius-20260728T021503Z.sql.gz.gpg
s3 cp "s3://librarius-backups/librarius/daily/$ARCHIVE" .

gpg --batch --decrypt --output restore.sql.gz "$ARCHIVE"   # asks for the passphrase
gunzip restore.sql
```

`gunzip` writes `restore.sql`. Check you are holding a real dump before going near the
database:

```bash
head -5 restore.sql && grep -c 'CREATE TABLE' restore.sql
```

```text
--
-- PostgreSQL database dump
--
...
14
```

A `gpg: decryption failed: Bad session key` means a wrong passphrase, not a corrupt
archive — try the other passphrase before re-downloading.

### 3. Bring up an empty database

If the cluster is intact and only the data is wrong, skip to step 4. After a full node
loss, recreate the Secrets (§ "Cluster secrets") and deploy the stack **with the API scaled
to zero**, so that Flyway does not create a schema the dump is about to recreate:

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set api.replicas=0 \
  --wait --timeout 8m

k get pods -l component=postgres      # wait for 1/1 Running
```

### 4. Load the dump

The dump recreates the tables, so the target database has to be empty. Drop and recreate
the schema, then pipe the file in — `ON_ERROR_STOP=1` matters, without it psql reports
success after hundreds of errors:

```bash
k scale deploy/librarius-api --replicas=0

The archive holds **both** databases — `librarius` and `keycloak` — so both have to be
emptied, and the file is piped into `postgres` rather than into either of them: it carries
its own `\connect` lines and creates `keycloak` if the instance has never had it.

```bash
k exec deploy/librarius-postgres -- \
  psql -U librarius -d librarius -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'

# Skip this one only if the instance has no keycloak database at all.
k exec deploy/librarius-postgres -- \
  psql -U librarius -d keycloak -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'

k exec -i deploy/librarius-postgres -- \
  psql -v ON_ERROR_STOP=1 -U librarius -d postgres < restore.sql
```

Expected output — a long list of `CREATE TABLE` / `COPY nnn` / `ALTER TABLE`, and **no**
`ERROR:` line. It ends on the constraints:

```text
CREATE TABLE
COPY 4213
...
ALTER TABLE
```

A `psql: error: connection to server ... failed` means the pod is not ready yet. An
`ERROR: relation "book" already exists` means the schema was not dropped — go back one
command.

### 5. Check, then let the application back in

```bash
# Row counts, on the tables that matter.
k exec deploy/librarius-postgres -- psql -U librarius -d librarius -c \
  "SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10;"

# Flyway's own history must be there: without it the API re-runs every migration.
k exec deploy/librarius-postgres -- psql -U librarius -d librarius -c \
  'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'

k scale deploy/librarius-api --replicas=1
k rollout status deploy/librarius-api --timeout=5m
k logs deploy/librarius-api --tail=50        # Flyway must report "up to date"
```

Then, in a browser: sign in and open the library. **The restore worked** when the book
count on screen matches `n_live_tup` above, and the API log shows Flyway validating rather
than migrating.

Keycloak lives in the same PostgreSQL instance, in its own `keycloak` database, and the
archive carries it. That is not a detail: every application row is owned by the Keycloak
`sub`, so restoring the library without the accounts would give back the whole collection
owned by subjects nobody can sign in as — a restore that reports success and hands back
nothing ([#155](https://github.com/zelytra/Librarius/issues/155)).

Check the accounts came back before letting anyone in:

```bash
k exec deploy/librarius-postgres -- psql -U librarius -d keycloak -tAc \
  'SELECT count(*) FROM user_entity;'
```

Roles are deliberately **not** in the archive. `pg_dumpall` would carry the role passwords
as they stood at dump time, and loading them would silently revert a rotated password,
leaving the API locked out of the database it had just restored. The role comes from the
Secret, which is the value worth keeping.

> **Not yet exercised against the cluster.** The scripts themselves are tested end to end —
> dump, encryption, upload, retention pruning and a restore into a second database, against
> a real PostgreSQL and a real S3 (`infra/backup/verify.sh`). What has never been done is
> the procedure above, on `librarius.zelytra.fr`, with its data. Until somebody runs it once
> deliberately and corrects this section with what actually happened,
> [#59](https://github.com/zelytra/Librarius/issues/59) is not met.

## 🔔 Alerting

Two independent paths, because they fail in different ways:

| | Where it runs | Sees | Notifies | Needs |
|---|---|---|---|---|
| **Prometheus + Alertmanager** | in the `librarius` namespace, deployed by the chart | the API's own metrics: unreachable, 5xx share, p95 latency | a webhook | a Secret holding the URL |
| **`uptime` workflow** | a GitHub runner, every 15 min | the public URL, the API through the ingress, the TLS certificate | a GitHub issue | **nothing** |

The second one is the one that works out of the box, and it is deliberately the one that
watches from outside: a Prometheus inside the cluster cannot report that the cluster is
gone, and its Alertmanager cannot notify through a credential that does not exist yet. The
first one sees what the second cannot — a 5xx rate, a latency regression, an API that
answers the ingress but has lost its database.

Both exist because of the same failure: continuous deployment was broken for four weeks
and was found by hand ([#85](https://github.com/zelytra/Librarius/issues/85)).

### What the chart deploys

`monitoring.enabled` is **true** by default: two pods, no CRD, no cluster-scoped RBAC.

| | |
|---|---|
| Prometheus | `prom/prometheus:v2.53.0`, scrapes `librarius-api:8080/q/metrics` and itself, every 30 s |
| Rules | `infra/helm/librarius/files/librarius.rules.yml`, mounted from a ConfigMap the chart renders |
| Alertmanager | `prom/alertmanager:v0.27.0`, grouping on `alertname`, `critical` repeating every 4 h and `warning` every 24 h |
| Reachable at | nothing routes to them — `kubectl -n librarius port-forward svc/librarius-prometheus 9090:9090` |

Neither is exposed through the ingress, for the same reason `/q` is not.

**A rule change restarts the pod.** The pod template carries a checksum of the values and
of the rules file, so editing a rule and running `helm upgrade` reloads it. Without that,
the ConfigMap would change and the running Prometheus would keep the old rules — edited in
git, absent from the process, which is precisely the kind of silence this feature exists
to remove.

#### Why not kube-prometheus-stack

Measured on chart 62.7.0, rendered with `helm template`:

| | kube-prometheus-stack | what the chart ships |
|---|---|---|
| Objects | **125** (640 KB of manifests) | **6** — two ConfigMaps, two Deployments, two Services |
| CRDs | **10**, 3.9 MB | none |
| Cluster-scoped objects | 5 ClusterRoles/bindings, 2 admission webhooks | none |
| Resident pods | **6** — operator, Prometheus, Alertmanager, Grafana, kube-state-metrics, a node-exporter DaemonSet | **2** |
| Resource requests declared | **none at all** — every pod lands BestEffort | measured, below |

The last row is what settles it on this node. `zeserver` has 4 CPU allocatable and its
requests were already 94% committed; six pods that request nothing are six pods the
scheduler cannot reason about and the kubelet evicts first under pressure. The operator and
its CRDs buy `ServiceMonitor` objects and multi-tenant discovery, neither of which this
single-namespace stack has any use for.

#### What it costs the node

Measured with `docker stats` while the fire drill below was running, two targets scraped
every 30 s and ten rules evaluated:

| | CPU at startup | CPU settled | Memory after 11 min | Requested |
|---|---|---|---|---|
| Prometheus | 2.5 m | under 1 m | 36 MiB | 20 m / 128 Mi |
| Alertmanager | 16 m (boot and gossip) | 2 m | 20 MiB | 10 m / 48 Mi |

Requests sit just above the settled figure rather than at a comfortable round number,
because CPU requests are the scarce resource here (§ "Deploying without downtime") and a
request is a scheduling floor, not a cap — the boot burst is covered by the limit:

| | CPU requests | Free of 4000 m |
|---|---|---|
| Before this change, steady | 3775 m | 225 m |
| Before this change, rolling web + api | 3900 m | 100 m |
| With monitoring, steady | 3805 m | 195 m |
| With monitoring, rolling web + api | 3930 m | **70 m** |

Both monitoring pods use the `Recreate` strategy, so they never surge and never appear
twice in that arithmetic. If the node ever needs the 30 m back:

```bash
helm -n librarius upgrade ... --set monitoring.enabled=false
```

The `uptime` workflow keeps running: it depends on nothing in the cluster.

#### Retention, and why there is no PVC

`emptyDir`, 24 h, capped at 384 MB of TSDB and 512 Mi of volume. Alerting reads at most the
last ten minutes — the longest window in any rule — so a Prometheus that lost its history
is firing again within two evaluation cycles. A `local-path` PVC would pin the pod to the
node and add a volume worth backing up that holds nothing anybody would miss. Raise
`monitoring.prometheus.retention` and give it a PVC the day a Grafana on the cluster reads
from it; until then history is a cost with no reader.

Alertmanager's `emptyDir` holds silences and deduplication state. Losing it on a restart
re-sends a notification, which is the harmless direction to fail in.

### Loading the rules elsewhere

Into the compose production stack — already wired, `infra/compose.prod.yml` mounts the
chart's copy of the rules and `prometheus.prod.yml` loads `/etc/prometheus/rules/*.yml`:

```bash
cd infra && docker compose -f compose.prod.yml up -d prometheus
docker compose -f compose.prod.yml exec prometheus promtool check rules /etc/prometheus/rules/librarius.rules.yml
```

Under a Prometheus Operator, the same file wrapped in a `PrometheusRule` object — the
`groups:` key is copied verbatim into `spec:`.

Validate any change before applying it — this is also what the `alerting` workflow runs on
every pull request, against the rendered ConfigMap rather than the source file:

```bash
docker run --rm -v "$PWD/infra/helm/librarius/files:/rules:ro" \
  --entrypoint promtool prom/prometheus:v2.53.0 check rules /rules/librarius.rules.yml
```

### What each rule needs

A rule whose metric is missing never fires: an incomplete monitoring stack goes quiet, it
does not go noisy. That also means a rule can be silently useless — check this table before
trusting a green dashboard.

| Group | Requires | State on the cluster |
|---|---|---|
| `librarius-api` | the `librarius-api` scrape job on `/q/metrics` | ✅ scraped by the chart's Prometheus |
| `librarius-cluster` | kubelet metrics + kube-state-metrics | ❌ neither exporter is deployed |
| `librarius-tls` | cert-manager metrics, or a blackbox exporter | ❌ — covered from outside by the `uptime` workflow instead |
| `librarius-backup` | kube-state-metrics + `backup.enabled=true` | ❌ kube-state-metrics, and backups are off |

Both missing exporters need **cluster-scoped RBAC** — a ClusterRole to read pods and volume
stats across the cluster. The chart deliberately creates none: `cd.yml` deploys with a
kubeconfig whose rights are not known to be cluster-admin, and a chart that suddenly needs
a ClusterRole would fail every deployment rather than only the monitoring part of one.
Adding kube-state-metrics is a deliberate, separate step:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm -n librarius upgrade --install kube-state-metrics prometheus-community/kube-state-metrics \
  --set resources.requests.cpu=10m --set resources.requests.memory=48Mi
```

then add its Service to `scrape_configs` in `templates/monitoring.yaml`. Do it only after
checking the node has the headroom (`kubectl describe node zeserver | grep -A6 'Allocated
resources'`).

`LibrariusApiSlowResponses` reads `http_server_requests_seconds_bucket`. The Grafana
dashboard already assumes those histogram buckets; if the API does not publish them, the
rule stays silent rather than firing wrongly.

### Notification channel

#### The one that works with no secret: a GitHub issue

`.github/workflows/uptime.yml` runs `.github/scripts/uptime-probe.sh` against
`https://librarius.zelytra.fr` every 15 minutes and, when it does not answer, **opens an
issue** labelled `infra` / `P0` — which mails whoever watches the repository. A second
failure comments on the same issue instead of opening another; the first success closes it.
It authenticates with the run's own `GITHUB_TOKEN`, so there is nothing to create and
nothing to rotate.

What it checks, and why each one:

| Check | Expected | Why that answer |
|---|---|---|
| `GET /` | `200` | Traefik, TLS and the web pod are all standing |
| `GET /api/me` | `401` | only the API answers 401 — `/q` is not routed and `/` catches everything else, so a request that reached nginx would come back `200` with `index.html` and look healthy |
| TLS certificate | more than 15 days left | what a browser sees, which is not necessarily what cert-manager believes it renewed |

Three attempts, 20 s apart, before anything is declared down: a rolling deployment is not
an outage, and one unlucky sample must not open an issue. Run it by hand against any
environment:

```bash
./.github/scripts/uptime-probe.sh https://librarius.zelytra.fr
```

```text
probe of https://librarius.zelytra.fr at 2026-07-28 21:44:07Z

app  GET /                   HTTP 200           ok
api  GET /api/me             HTTP 401           ok
tls  certificate             89 days left       ok

RESULT: ok
```

A failure is as legible, and exits non-zero — which is what opens the issue:

```text
app  GET /                   HTTP 200           ok
api  GET /api/me             HTTP 404           FAIL (expected 401, 1 attempts)
tls  certificate             56 days left       ok

RESULT: 1 check(s) failed
```

Two limits worth knowing: GitHub queues scheduled workflows and drops them when runners are
busy, so 15 minutes is a floor rather than a promise; and GitHub disables scheduled
workflows on a repository with no activity for 60 days.

#### The one that needs a Secret: the Alertmanager webhook

Alertmanager is deployed and evaluating, and until a Secret exists its route points at a
receiver that holds nothing — alerts are grouped and visible, and no notification leaves
the cluster. **No URL appears in this repository**: a webhook URL is a credential, and
Alertmanager reads it from a mounted file (`url_file`), the same rule the database and
Keycloak passwords already follow.

Pick a destination that costs nothing to create:

| Destination | URL to use | `receiverType` |
|---|---|---|
| [ntfy.sh](https://ntfy.sh) | `https://ntfy.sh/<a topic name only you know>` | `webhook` |
| Slack | an incoming webhook of the workspace | `slack` |
| Discord | the channel webhook, **with `/slack` appended** — Discord accepts Slack-shaped payloads | `slack` |
| Gotify, Mattermost, a bot of your own | its POST endpoint | `webhook` |

Then, once:

```bash
kubectl -n librarius create secret generic librarius-alerting \
  --from-literal=webhook-url='<the URL>'
```

and deploy with it — add the two `--set` lines to the `helm upgrade` step of `cd.yml`, or
they are lost at the next deployment:

```bash
helm -n librarius upgrade --install librarius ./infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set monitoring.alertmanager.existingSecret=librarius-alerting \
  --set monitoring.alertmanager.receiverType=webhook \
  --wait --timeout 8m
```

Check it arrived, without waiting for something to break — this posts a fake alert straight
into Alertmanager, which routes it exactly as it would a real one:

```bash
kubectl -n librarius port-forward svc/librarius-alertmanager 9093:9093 &
curl -sS -XPOST http://localhost:9093/api/v2/alerts -H 'Content-Type: application/json' -d '[{
  "labels": {"alertname":"LibrariusNotificationTest","severity":"critical","service":"librarius"},
  "annotations": {"summary":"Manual test of the notification path.","runbook":"Nothing is wrong. Delete this alert."}
}]'
kill %1
```

The notification lands after `group_wait`, so about 30 seconds later. If nothing arrives,
`kubectl -n librarius logs deploy/librarius-alertmanager` names the reason — a 404 on the
webhook URL and an unreachable host look nothing alike in that log.

### Firing the alerts on purpose

`infra/alerting/fire-drill.sh` runs the alerting stack on a laptop and breaks things until
it notifies. Nothing in it is a mock of the configuration: the Prometheus config, the rules
and the Alertmanager config are pulled out of the chart with `helm template`, so what is
exercised is what the cluster runs. Only the API is fake, and only so it can be made to
misbehave on command.

```bash
./infra/alerting/fire-drill.sh          # the three API rules, ~16 min
./infra/alerting/fire-drill.sh down     # LibrariusApiDown only, ~4 min
```

It needs docker, docker compose and helm, and no cluster. The wait is the `for:` clause of
each rule — the same delay the cluster will have. Run it after touching a rule, a threshold
or the routing. It ends on what was received, and exits non-zero if anything was not:

```text
  ✔ LibrariusApiHighErrorRate notified after 396s
  ✔ LibrariusApiSlowResponses notified after 700s
stopping the api container — LibrariusApiDown from here
  ✔ LibrariusApiDown notified after 900s

── notifications delivered to the webhook ───────────────────────────────
firing   LibrariusApiHighErrorRate        More than 5% of API responses have been 5xx for 5 minutes.
firing   LibrariusApiSlowResponses        API p95 latency has been above 2 s for 10 minutes.
firing   LibrariusApiDown                 The Librarius API has been unreachable for 2 minutes.
```

### Runbooks

Every alert carries its runbook in its own `runbook` annotation — symptom, likely cause,
first action — so it travels with the notification instead of living in a document nobody
opens at 3 a.m. Summarised:

| Alert | Severity | Symptom | First action |
|---|---|---|---|
| `LibrariusApiDown` | critical | API unreachable for 2 min, no library loads | `k get pods -l component=api`, then `k logs deploy/librarius-api --tail=100`; check PostgreSQL if the pod is Running |
| `LibrariusApiHighErrorRate` | critical | > 5% of responses are 5xx over 5 min | `k logs deploy/librarius-api --tail=200 \| grep -i error` to find the endpoint; roll back if it started at a deployment |
| `LibrariusApiSlowResponses` | warning | p95 above 2 s for 10 min, the app drags | Grafana "Overview" for the slow URI, then `k top pods` to tell a slow query from a saturated node |
| `LibrariusPersistentVolumeAlmostFull` | warning | PVC over 80%; writes stop when it fills | `k exec deploy/librarius-postgres -- df -h /var/lib/postgresql/data`, then raise `postgres.storage` — local-path does not grow by itself |
| `LibrariusPodCrashLooping` | critical | A pod restarts endlessly, never ready | `k logs <pod> --previous --tail=100` — the reason is in the *previous* container's log |
| `LibrariusCertificateExpiringSoon` | warning | TLS certificate expires in under 15 days | `k describe certificate librarius-zelytra-fr-tls` and read the Events; the ACME challenge is what is failing |
| `LibrariusPublicCertificateExpiringSoon` | warning | Same, seen from outside — what users get | `curl -vI https://librarius.zelytra.fr 2>&1 \| grep -i expire`; restart Traefik if the Secret is newer than what is served |
| `LibrariusBackupJobFailed` | critical | Last night's backup did not complete | `k logs job/<name> --all-containers` — dump and upload are separate containers, the log names which failed |
| `LibrariusBackupTooOld` | critical | No backup in 48 h, and nothing looks wrong | `k get cronjob librarius-postgres-backup`, check SUSPEND and LAST SCHEDULE, then run one by hand |
| `LibrariusBackupCronJobMissing` | critical | No backup Job exists at all | `helm -n librarius get values librarius --all \| grep -A3 backup`, redeploy with `backup.enabled=true` |

### What has actually been proven

| | |
|---|---|
| ✅ The rules load and evaluate | `promtool check rules` on the rendered ConfigMap: 10 rules, and all ten report `health=ok` in a running Prometheus |
| ✅ Three alerts fired for real | fire drill of 2026-07-28: `LibrariusApiHighErrorRate` notified 6 min 36 s in, `LibrariusApiSlowResponses` 11 min 40 s in, `LibrariusApiDown` 3 min 20 s after the container was stopped — each one its `for:` clause plus the 30 s `group_wait`, which is the delay the cluster will have too |
| ✅ A notification left the stack | each one delivered as a JSON POST to a webhook, carrying its `summary`, its `runbook` and the `environment: staging` label, routed through the `severity="critical"` branch |
| ✅ The URL came from a mounted file | the drill mounts it where the chart mounts the Secret, so `url_file` is exercised, not assumed |
| ❌ Not from the cluster | the drill runs on a laptop. Nothing has been deployed or fired on `librarius.zelytra.fr`, and the in-cluster Alertmanager notifies nothing until the webhook Secret exists |
| ❌ No `uptime` issue has been opened | the workflow only runs on `main`; the probe script itself has been run against the live URL, the issue-opening half repeats what `cd.yml` already does |
| ❌ Seven quiet days | not observed. The rules have never run for a day, let alone a week |
| ❌ Seven of the ten rules | PVC, CrashLoopBackOff, TLS and the three backup rules have no exporter to read on the cluster (§ "What each rule needs") |

[#60](https://github.com/zelytra/Librarius/issues/60) stays open on the last four rows.

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
#    /api/me answers 401 without a token, which is the API answering; once the
#    Service has no endpoint, Traefik answers 503 instead. Do NOT check
#    /q/health/ready from outside — /q is not routed, so `/` catches it and nginx
#    serves index.html with a 200 while the API is down.
curl -sSI https://librarius.zelytra.fr/ | head -1
curl -sSI https://librarius.zelytra.fr/api/me | head -1

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
| Pods stuck in `ImagePullBackOff` | Nothing authenticates that pull any more, so the tag is missing or the package went private: check it with the anonymous request in § "Pulling the images". If a pod still carries a stale `imagePullSecrets` from an older revision, `k get pod <name> -o jsonpath='{.spec.imagePullSecrets}'` shows it — redeploy from the current chart. |
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
