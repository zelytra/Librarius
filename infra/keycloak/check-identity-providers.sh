#!/usr/bin/env bash
# Proves the sign-in with Google and Apple (#177) the chart sets up actually lands on
# a running Keycloak, and asks the providers for the e-mail address only (#538).
#
# Nothing else can see it before staging does. The providers are configured by the
# keycloak-setup Job, a bash script inside a Helm template; Apple is not even part
# of Keycloak, but an extension the Deployment fetches at start. A typo in either is
# a deploy that fails — or worse, a button that leads nowhere — the day the owner
# creates the Secrets. So this:
#
#   1. renders the chart with both providers forced on;
#   2. fetches the Apple extension from the URL the chart pins and checks its
#      SHA-256, exactly as the init container does;
#   3. starts the pinned Keycloak image with that jar, under the name and path the
#      Job talks to;
#   4. runs the rendered Job script — twice, since it runs on every deploy — with
#      dummy credentials;
#   5. reads back the realm and the sign-in page.
#
#   infra/keycloak/check-identity-providers.sh          # port 18098, or set PORT
#
# Requires docker, helm, yq and curl. Run from anywhere.

set -euo pipefail

PORT="${PORT:-18098}"
NETWORK="librarius-idp-check-$$"
KEYCLOAK="librarius-keycloak"          # the host name the rendered Job calls
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"

failures=0
check() { # check <description> <haystack> <expected-substring>
  if printf '%s' "$2" | grep -qF -- "$3"; then
    echo "  ok    $1"
  else
    echo "  FAIL  $1"
    echo "        expected to find: $3"
    echo "        got: $(printf '%s' "$2" | head -c 300)"
    failures=$((failures + 1))
  fi
}
refute() { # refute <description> <haystack> <unexpected-substring>
  if printf '%s' "$2" | grep -qF -- "$3"; then
    echo "  FAIL  $1"
    echo "        did not expect: $3"
    failures=$((failures + 1))
  else
    echo "  ok    $1"
  fi
}

cleanup() {
  docker rm -f "$KEYCLOAK-$$" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# ── 1. Render ─────────────────────────────────────────────────────────────────
echo "Rendering the chart with both providers on"
helm template librarius "$REPO/infra/helm/librarius" \
  --set postgres.existingSecret=check --set keycloak.existingSecret=check \
  --set keycloak.identityProviders.google.enabled=true \
  --set keycloak.identityProviders.apple.enabled=true >"$WORK/rendered.yaml"

yq -r 'select(.kind == "Job") | .spec.template.spec.containers[0].args[0]' \
  "$WORK/rendered.yaml" >"$WORK/setup.sh"
image="$(yq -r 'select(.kind == "Job") | .spec.template.spec.containers[0].image' "$WORK/rendered.yaml")"
init_env() {
  yq -r "select(.kind == \"Deployment\" and .metadata.name == \"librarius-keycloak\")
         | .spec.template.spec.initContainers[0].env[] | select(.name == \"$1\") | .value" \
    "$WORK/rendered.yaml"
}
jar_url="$(init_env URL)"
jar_sha="$(init_env SHA256)"
bash -n "$WORK/setup.sh"
echo "  ok    the Job script parses"

# ── 2. The Apple extension, as the init container fetches it ─────────────────
echo "Fetching $jar_url"
mkdir -p "$WORK/providers"
curl -fsSL --retry 3 -o "$WORK/providers/apple-identity-provider.jar" "$jar_url"
actual="$(sha256sum "$WORK/providers/apple-identity-provider.jar" | cut -d' ' -f1)"
if [ "$actual" != "$jar_sha" ]; then
  echo "  FAIL  the jar's SHA-256 is $actual, the chart pins $jar_sha"
  exit 1
fi
echo "  ok    its SHA-256 is the one the chart pins"
chmod -R a+rX "$WORK/providers"

# ── 3. Keycloak, where the Job expects it ─────────────────────────────────────
host_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

echo "Starting $image on :$PORT"
docker network create "$NETWORK" >/dev/null
MSYS_NO_PATHCONV=1 docker run -d --name "$KEYCLOAK-$$" --network "$NETWORK" \
  --network-alias "$KEYCLOAK" -p "$PORT:8081" \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v "$(host_path "$REPO/infra/keycloak"):/opt/keycloak/data/import:ro" \
  -v "$(host_path "$WORK/providers"):/opt/keycloak/providers:ro" \
  "$image" start-dev --import-realm --http-port 8081 --http-relative-path /auth >/dev/null

base="http://localhost:$PORT/auth"
for _ in $(seq 1 90); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$base/realms/librarius")" = "200" ] && break
  sleep 2
done
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$base/realms/librarius")" != "200" ]; then
  echo "  FAIL  the realm never came up"
  docker logs "$KEYCLOAK-$$" | tail -40
  exit 1
fi

# ── 4. The Job, twice ─────────────────────────────────────────────────────────
# Dummy credentials: nothing here talks to Google or Apple. The key has the shape of
# a .p8 file, line breaks included, which the Job has to flatten.
run_setup() {
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" --entrypoint /bin/bash \
    -e KC_ADMIN=admin -e KC_ADMIN_PASSWORD=admin -e API_ADMIN_SECRET=check-admin-secret \
    -e GOOGLE_CLIENT_ID=check.apps.googleusercontent.com -e GOOGLE_CLIENT_SECRET=check-google \
    -e APPLE_CLIENT_ID=fr.zelytra.librarius.signin -e APPLE_TEAM_ID=ABCDE12345 \
    -e APPLE_KEY_ID=KEY1234567 \
    -e "APPLE_PRIVATE_KEY=$(printf -- '-----BEGIN PRIVATE KEY-----\r\nMIGTAgEA\r\nAwEHBHkw\r\n-----END PRIVATE KEY-----')" \
    -v "$(host_path "$WORK/setup.sh"):/setup.sh:ro" "$image" /setup.sh
}
echo "Running the keycloak-setup Job"
first="$(run_setup 2>&1)" || { echo "$first"; echo "  FAIL  the Job failed"; exit 1; }
check "it creates the Google provider" "$first" "identity provider google created"
check "it creates the Apple provider"  "$first" "identity provider apple created"
second="$(run_setup 2>&1)" || { echo "$second"; echo "  FAIL  the Job failed on its second run"; exit 1; }
check "a second deploy updates them"   "$second" "identity provider apple updated"

# ── 5. Read it all back ───────────────────────────────────────────────────────
token="$(curl -sS -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
  "$base/realms/master/protocol/openid-connect/token" | yq -r '.access_token')"
api() { curl -sS -H "Authorization: Bearer $token" "$base/admin/realms/librarius/$1"; }

google="$(api identity-provider/instances/google)"
apple="$(api identity-provider/instances/apple)"
profile="$(api users/profile)"

echo "Reading the realm"
check "Google asks for the e-mail only"     "$(printf '%s' "$google" | yq -r '.config.defaultScope')" "openid email"
check "Apple asks for the e-mail only"      "$(printf '%s' "$apple" | yq -r '.config.defaultScope')" "email"
refute "Apple does not ask for the name"    "$(printf '%s' "$apple" | yq -r '.config.defaultScope')" "name"
check "Apple is the extension's provider"   "$(printf '%s' "$apple" | yq -r '.providerId')" "apple"
check "Apple carries the team and key ids"  "$(printf '%s' "$apple" | yq -r '.config.teamId + "/" + .config.keyId')" "ABCDE12345/KEY1234567"
check "Google trusts the verified e-mail"   "$(printf '%s' "$google" | yq -r '.trustEmail')" "true"
check "Apple trusts the verified e-mail"    "$(printf '%s' "$apple" | yq -r '.trustEmail')" "true"
check "the first name is optional"          "$(printf '%s' "$profile" | yq -r '.attributes[] | select(.name == "firstName") | has("required")')" "false"
check "the last name is optional"           "$(printf '%s' "$profile" | yq -r '.attributes[] | select(.name == "lastName") | has("required")')" "false"
check "the e-mail stays required"           "$(printf '%s' "$profile" | yq -r '.attributes[] | select(.name == "email") | has("required")')" "true"

challenge="4Md_FO8pVFcpjDs3BIk-xjv1pQT3BzT0wTOgBsBgUGw"
query="client_id=librarius-web&response_type=code&scope=openid"
query="$query&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F"
query="$query&code_challenge_method=S256&code_challenge=$challenge"
login="$(curl -sS "$base/realms/librarius/protocol/openid-connect/auth?$query")"

echo "Reading the sign-in page"
check "it offers Google" "$login" 'id="social-google"'
check "it offers Apple"  "$login" 'id="social-apple"'

echo
if [ "$failures" -eq 0 ]; then
  echo "Identity providers OK — set up by the chart's own Job, e-mail only."
else
  echo "$failures check(s) failed."
  exit 1
fi
