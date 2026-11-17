# Authentication & sessions

Sign-in is **Keycloak** (realm `librarius`), with the authorization code flow and PKCE from
the web app (`react-oidc-context`, `apps/web/src/auth/oidc.ts`). The API validates the bearer
token and reads the user from its `sub` (`security/CurrentUser.java`); the first request of a
new account creates its `app_user` row.

## Google and Apple

The sign-in page also offers **Google** and **Apple** (#177), as Keycloak identity
providers: the app itself knows nothing of them, a brokered account reaches the API as an
ordinary Keycloak token. They exist on a realm only once the owner has created their
credentials as Kubernetes Secrets; the `keycloak-setup` Job then configures them (see
"Keeping the live realm in line" below), and Apple, which Keycloak does not ship, comes
from the [klausbetz extension](https://github.com/klausbetz/apple-identity-provider-keycloak)
the Deployment fetches and checks against a pinned SHA-256. Owner procedure, console
screens and redirect URIs:
[`docs/DEPLOYMENT.md`](https://github.com/zelytra/Librarius/blob/main/docs/DEPLOYMENT.md)
§ "Sign in with Google and Apple". The mobile app signs in through the system browser, so
Google does not refuse it as an embedded WebView.

**Only the e-mail address is imported (#538).** The providers are asked for the e-mail
scope alone, and the realm's user profile makes first and last name optional
(`infra/helm/librarius/files/keycloak-user-profile.json`), so a first sign-in creates the
account without a form. Such an account's token carries no `name`, and its
`preferred_username` is the e-mail address: `CurrentUser.initialDisplayName` never takes a
value containing `@` as a display name — other members see it — so the account starts as
« Lecteur » and the reader names themselves in Settings.

`infra/keycloak/check-identity-providers.sh` (CI job *the identity providers reach the
realm*) renders the chart with both providers on, runs the rendered Job twice on the pinned
Keycloak image carrying the Apple jar, and reads the realm and the sign-in page back.

## The admin role

The realm role **`admin`** (#488) opens the admin screens — the members' reports, the
recorded errors and the monitoring — and the `/api/admin/*` endpoints
(`@RolesAllowed("admin")`, read from the token's `realm_access.roles`). `GET /api/me` answers
`admin: true` to whoever holds it, so the app shows the way in to them alone. The role exists
in the realm (the realm files and the `keycloak-setup` Job, for a realm created before it)
but is **granted to nobody**: the owner gives it by hand in the Keycloak console, *Users* →
the account → *Role mapping* → *Assign role* → `admin`, and the member signs in again for a
token that carries it. Locally `alice` holds it, as in the tests; `bob` does not.

## Long sessions

The web app asks for the `offline_access` scope. Keycloak then keeps the session in its
database as an **offline session**: no Keycloak restart or redeploy ends it, and it has no
maximum lifetime — only an idle limit (`offlineSessionIdleTimeout`, 90 days in the realm
file). The refresh token lives in `localStorage` and is renewed silently. Signing out revokes
it (`revokeTokensOnSignout`). The ordinary browser session is lengthened too (7 days idle, 30
at most).

When a session ends anyway — a 401 whose silent renewal is refused — the app **does not
redirect** (#494): it drops the local session and the screen's own sign-in prompt
(`LoginGate`) takes over where the reader is. `/` sends a visitor to the landing page only
when nobody has been signed in on this device (`librarius.sessionSeen` in `localStorage`,
cleared by an explicit sign-out or an account deletion).

Asking for `offline_access` needs the user to hold the realm role of that name, or Keycloak
refuses the code exchange (`CODE_TO_TOKEN_ERROR`, "Offline tokens not allowed") and the app
comes back signed out. The realm's `defaultRoles` and the local test users therefore carry
`offline_access` next to `user`: a `defaultRoles` of `["user"]` alone replaced Keycloak's own
default and left a freshly imported realm unable to sign anyone in.

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
lifetimes (`keycloak.sessions` in `values.yaml`), the user profile (first and last name
optional), the Google and Apple identity providers whose Secrets exist, the confidential client
`librarius-api-admin`, whose service account (`realm-management` → `manage-users` only)
lets the api delete a Keycloak login when a reader deletes their account, and the public
client `librarius-mobile` the installed application signs in with — PKCE `S256`, redirect
URI `fr.zelytra.librarius://auth`, and the container's two origins listed explicitly. The chart
generates that client's secret and keeps it across upgrades. Details:
[`docs/DEPLOYMENT.md`](https://github.com/zelytra/Librarius/blob/main/docs/DEPLOYMENT.md)
§ "Account deletion".
