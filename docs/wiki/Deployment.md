# Deployment

This page is intentionally short: the full guide —
[`docs/DEPLOYMENT.md`](https://github.com/zelytra/Librarius/blob/main/docs/DEPLOYMENT.md) —
already covers this in depth and is kept current as the deployment changes, right down to
rollback and disaster recovery. Forking a second copy here would only end up disagreeing
with it.

## The short version

- Two Docker images — the Quarkus API and an nginx-served build of the web app — are pushed
  to GHCR: on every merge into `main` (then deployed straight to the **staging**
  environment) and on every `vX.Y.Z` tag (versioned, not auto-deployed).
- Staging runs at <https://librarius.zelytra.fr>, on a small Kubernetes (k3s) cluster,
  deployed through the Helm chart in `infra/helm/librarius/`. **Production does not exist
  yet** — it opens at the v1.0 milestone.
- Both deployments roll with zero downtime; database migrations run automatically at API
  startup, before the new version takes traffic.

## For the rest

[`docs/DEPLOYMENT.md`](https://github.com/zelytra/Librarius/blob/main/docs/DEPLOYMENT.md)
covers, among other things: publishing and pulling the images, cluster secrets and their
rotation, monitoring and alerting, automated backups and how to restore from one, GDPR
account deletion, and rolling back a bad release. If you are about to deploy or operate
Librarius rather than just read its code, that document — not this page — is the one to
follow.
