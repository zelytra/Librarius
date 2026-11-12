# Authentication & sessions

Sign-in is **Keycloak** (realm `librarius`), with the authorization code flow and PKCE from
the web app (`react-oidc-context`, `apps/web/src/auth/oidc.ts`). The API validates the bearer
token and reads the user from its `sub` (`security/CurrentUser.java`); the first request of a
new account creates its `app_user` row.

## Long sessions

The web app asks for the `offline_access` scope. Keycloak then keeps the session in its
database as an **offline session**: no Keycloak restart or redeploy ends it, and it has no
maximum lifetime — only an idle limit (`offlineSessionIdleTimeout`, 90 days in the realm
file). The refresh token lives in `localStorage` and is renewed silently. Signing out revokes
it (`revokeTokensOnSignout`). The ordinary browser session is lengthened too (7 days idle, 30
at most).

The realm is imported **once**, on an empty database: a change to
`infra/keycloak/realm-librarius.json` or to the realm in the Helm chart reaches a running
Keycloak only by hand — the `kcadm` lines are in
[`docs/DEPLOYMENT.md`](https://github.com/zelytra/Librarius/blob/main/docs/DEPLOYMENT.md) § "Changing the realm of a running Keycloak".

## Locally

`pnpm infra:up` starts Keycloak on :8081 with two test accounts, `alice/alice` and `bob/bob`.
The Quarkus tests start their own Keycloak (Dev Services) on the same port: stop the local
stack before `./mvnw verify`.

## Keeping the live realm in line

Keycloak imports `realm-librarius.json` only when the realm does not exist yet, so a change
to it never reaches a running deployment on its own. On every deploy, the chart's
`keycloak-setup` Job applies to the live realm what has to stay true there: the session
lifetimes (`keycloak.sessions` in `values.yaml`), and the confidential client
`librarius-api-admin`, whose service account (`realm-management` → `manage-users` only)
lets the api delete a Keycloak login when a reader deletes their account. The chart
generates that client's secret and keeps it across upgrades. Details:
[`docs/DEPLOYMENT.md`](https://github.com/zelytra/Librarius/blob/main/docs/DEPLOYMENT.md)
§ "Account deletion".
