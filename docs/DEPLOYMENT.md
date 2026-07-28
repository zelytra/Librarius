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
| `librarius-backup` | `access-key-id`, `secret-access-key`, `encryption-passphrase` | the backup CronJob — **only** when `backup.enabled=true`, see § "Automated backups" |

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
pod was finally scheduled. The credential half of that failure is
[#136](https://github.com/zelytra/Librarius/issues/136) and is not addressed here; the
sizing half is. **The node takes a rolling update of the API, with 100m of margin.** No
extra capacity is required.

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

> **The rollout itself is unproven.** The chart renders, the arithmetic is taken from the
> live node and the probe is tested, but no deployment has been run with these settings —
> the measured "after" figure does not exist yet. Take it on the next merge into `main`,
> with the two probes above, and correct this section with what actually happened.
> [#64](https://github.com/zelytra/Librarius/issues/64) stays open until then.

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

k exec deploy/librarius-postgres -- \
  psql -U librarius -d librarius -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'

k exec -i deploy/librarius-postgres -- \
  psql -v ON_ERROR_STOP=1 -U librarius -d librarius < restore.sql
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

Keycloak lives in the same PostgreSQL instance but in its own `keycloak` database, which
this dump does **not** contain: after a full node loss, accounts have to be recreated. That
gap is deliberate for a staging environment and has to be closed before production.

> **Not yet exercised against the cluster.** The scripts themselves are tested end to end —
> dump, encryption, upload, retention pruning and a restore into a second database, against
> a real PostgreSQL and a real S3 (`infra/backup/verify.sh`). What has never been done is
> the procedure above, on `librarius.zelytra.fr`, with its data. Until somebody runs it once
> deliberately and corrects this section with what actually happened,
> [#59](https://github.com/zelytra/Librarius/issues/59) is not met.

## 🔔 Alerting

The alert rules live in `infra/prometheus/rules/librarius.rules.yml`. They are the answer to
a simple failure: continuous deployment was broken for four weeks and nobody was told.

**No Prometheus is deployed by the Helm chart today.** Prometheus and Grafana exist in
`infra/` for the compose stack only, so on the cluster these rules are *delivered, not
running*. What follows is what they need to become live.

### Loading them

Into the compose production stack — already wired, `infra/compose.prod.yml` mounts
`./prometheus/rules` and `prometheus.prod.yml` loads `/etc/prometheus/rules/*.yml`:

```bash
cd infra && docker compose -f compose.prod.yml up -d prometheus
docker compose -f compose.prod.yml exec prometheus promtool check rules /etc/prometheus/rules/librarius.rules.yml
```

Into a Prometheus running in Kubernetes, as a ConfigMap mounted on the rules directory:

```bash
kubectl -n librarius create configmap librarius-alert-rules \
  --from-file=infra/prometheus/rules/librarius.rules.yml \
  --dry-run=client -o yaml | kubectl apply -f -
```

Under the Prometheus Operator, the same file wrapped in a `PrometheusRule` object — the
`groups:` key is copied verbatim into `spec:`.

Validate any change before applying it:

```bash
docker run --rm -v "$PWD/infra/prometheus/rules:/rules:ro" \
  --entrypoint promtool prom/prometheus:v2.53.0 check rules /rules/librarius.rules.yml
```

### What each rule needs

A rule whose metric is missing never fires: an incomplete monitoring stack goes quiet, it
does not go noisy. That also means a rule can be silently useless — check this table before
trusting a green dashboard.

| Group | Requires | Missing today on the cluster |
|---|---|---|
| `librarius-api` | the `librarius-api` scrape job on `/q/metrics` | Prometheus itself |
| `librarius-cluster` | kubelet metrics + kube-state-metrics | both |
| `librarius-tls` | cert-manager metrics, or a blackbox exporter | both |
| `librarius-backup` | kube-state-metrics + `backup.enabled=true` | kube-state-metrics |

`LibrariusApiSlowResponses` reads `http_server_requests_seconds_bucket`. The Grafana
dashboard already assumes those histogram buckets; if the API does not publish them, the
rule stays silent rather than firing wrongly.

### Notification channel

Rules that fire into a UI nobody has open are not alerting. An Alertmanager is required, and
it is **not shipped here**: its configuration file holds the webhook URL or the SMTP
password in clear text, so it belongs in a Secret created by the maintainer, not in this
repository.

What has to be provided: a webhook URL (Slack, Discord, ntfy) *or* SMTP credentials and a
recipient address. Then add the routing to Prometheus and point it at the Alertmanager:

```yaml
# infra/prometheus/prometheus.prod.yml
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

Send `critical` somewhere that interrupts and `warning` somewhere that does not; grouping on
`alertname` keeps a restart loop to one message rather than one per evaluation.

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

> **Never triggered for real.** Each rule is syntactically valid (`promtool check rules`,
> 10 rules) and each expression is written against a metric the corresponding exporter is
> documented to publish — but no alert has been fired deliberately, no notification has been
> received, and the seven quiet days the issue asks for have not been observed.
> [#60](https://github.com/zelytra/Librarius/issues/60) is not met until they are.

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
