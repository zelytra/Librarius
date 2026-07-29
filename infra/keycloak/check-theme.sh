#!/usr/bin/env bash
# Proves the `librarius` login theme reaches a running Keycloak and renders.
#
# A theme cannot fail at startup: Keycloak boots perfectly well with a broken one
# and only shows it when somebody asks for the sign-in page — which, on staging,
# means the first person trying to sign in. So this renders the page and reads it.
#
# It checks the theme **as the chart ships it**, not as it sits in the working
# tree: `helm template` is rendered first and the ConfigMap unpacked into the
# directory layout the Deployment's `items` mapping produces. That is the half
# nothing else covers — `.Files.Get` on a path that no longer exists returns an
# empty string and renders a perfectly valid, perfectly empty ConfigMap. The
# compose stacks mount the same source directory, so a chart that serves the right
# page means they do too.
#
#   infra/keycloak/check-theme.sh          # port 18099, or set PORT
#
# Requires docker and helm. Run from anywhere.

set -euo pipefail

PORT="${PORT:-18099}"
IMAGE="${IMAGE:-quay.io/keycloak/keycloak:25.0.6}"
CONTAINER="librarius-theme-check-$$"

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHART="$REPO/infra/helm/librarius"
WORK="$(mktemp -d)"
THEME="$WORK/theme"

failures=0
check() { # check <description> <condition-output> <expected-substring>
  if printf '%s' "$2" | grep -qF -- "$3"; then
    echo "  ok    $1"
  else
    echo "  FAIL  $1"
    echo "        expected to find: $3"
    echo "        got: $(printf '%s' "$2" | head -c 300)"
    failures=$((failures + 1))
  fi
}

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# ── 1. Unpack the theme out of the rendered chart ─────────────────────────────
echo "Rendering the chart"
helm template librarius "$CHART" \
  --set postgres.existingSecret=check \
  --set keycloak.existingSecret=check >"$WORK/rendered.yaml"

# Walks the librarius-keycloak-theme ConfigMap and writes each key to the path the
# volume declares for it. Keys and paths are listed here rather than parsed out of
# the Deployment: a mapping that silently stopped matching is one of the things
# this script exists to catch, so it states what it expects.
awk -v out="$THEME" '
  /^---/                                  { indoc = 0; key = "" }
  /^  name: librarius-keycloak-theme$/    { indoc = 1 }
  indoc && /^  [A-Za-z0-9_.-]+: \|$/      { key = $1; sub(/:$/, "", key); next }
  key != "" {
    if ($0 == "") { blanks = blanks "\n"; next }
    if ($0 !~ /^    /) { key = ""; blanks = ""; next }
    paths["theme.properties"]       = "login/theme.properties"
    paths["librarius.css"]          = "login/resources/css/librarius.css"
    paths["messages_fr.properties"] = "login/messages/messages_fr.properties"
    paths["messages_en.properties"] = "login/messages/messages_en.properties"
    if (!(key in paths)) { key = ""; blanks = ""; next }
    file = out "/" paths[key]
    printf "%s%s\n", blanks, substr($0, 5) >> file
    blanks = ""
  }
' <(mkdir -p "$THEME/login/resources/css" "$THEME/login/messages" && cat "$WORK/rendered.yaml")

# A size floor rather than a mere existence test: `.Files.Get` on a path that no
# longer exists returns an empty string, and `indent 4` turns that into a single
# space — a file that exists, is not zero bytes, and holds nothing.
for f in login/theme.properties login/resources/css/librarius.css \
         login/messages/messages_fr.properties login/messages/messages_en.properties; do
  size="$( [ -f "$THEME/$f" ] && wc -c <"$THEME/$f" | tr -d ' ' || echo 0)"
  if [ "$size" -lt 40 ]; then
    echo "  FAIL  the chart renders $size byte(s) for $f — the file it reads has moved"
    failures=$((failures + 1))
  fi
done
[ "$failures" -eq 0 ] || { echo "The chart does not carry the theme."; exit 1; }
echo "  ok    four files unpacked from the ConfigMap"

# ── 2. Serve them ─────────────────────────────────────────────────────────────
host_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

echo "Starting Keycloak on :$PORT"
MSYS_NO_PATHCONV=1 docker run -d --name "$CONTAINER" -p "$PORT:8080" \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v "$(host_path "$REPO/infra/keycloak"):/opt/keycloak/data/import:ro" \
  -v "$(host_path "$THEME"):/opt/keycloak/themes/librarius:ro" \
  "$IMAGE" start-dev --import-realm >/dev/null

base="http://localhost:$PORT"
for _ in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$base/realms/librarius")" = "200" ] && break
  sleep 2
done
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$base/realms/librarius")" != "200" ]; then
  echo "  FAIL  the realm never came up"
  docker logs "$CONTAINER" | tail -40
  exit 1
fi

# ── 3. Read the pages ─────────────────────────────────────────────────────────
# A PKCE challenge, because the realm's client requires one. The verifier is not a
# secret: nothing is exchanged here, the page is only rendered.
challenge="4Md_FO8pVFcpjDs3BIk-xjv1pQT3BzT0wTOgBsBgUGw"
query="client_id=librarius-web&response_type=code&scope=openid"
query="$query&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F"
query="$query&code_challenge_method=S256&code_challenge=$challenge"

login_fr="$(curl -sS "$base/realms/librarius/protocol/openid-connect/auth?$query")"
login_en="$(curl -sS -H 'Accept-Language: en' "$base/realms/librarius/protocol/openid-connect/auth?$query")"
register="$(curl -sS "$base/realms/librarius/protocol/openid-connect/registrations?$query")"

echo "Reading the pages"
check "the sign-in page loads the theme's stylesheet" "$login_fr" "login/librarius/css/librarius.css"
check "it names the application"                      "$login_fr" "Ma Bibliothèque"
check "it is French by default"                       "$login_fr" '<html class="login-pf" lang="fr">'
check "its French labels are translated"              "$login_fr" "Afficher le mot de passe"
check "the language selector offers both endonyms"    "$login_fr" "English"
check "an English reader gets English"                "$login_en" '<html class="login-pf" lang="en">'
check "and the English name of the application"       "$login_en" "My Library"
check "registration is themed too"                    "$register" "login/librarius/css/librarius.css"

css_url="$(printf '%s' "$login_fr" | grep -o '/resources/[^"]*/login/librarius/css/librarius.css' | head -1)"
css_status="$(curl -s -o "$WORK/served.css" -w '%{http_code}' "$base$css_url")"
css_bytes="$(wc -c <"$WORK/served.css" | tr -d ' ')"
check "the stylesheet itself is served"  "$css_status" "200"
check "and is not an empty file"         "$([ "$css_bytes" -gt 2000 ] && echo big || echo "only $css_bytes bytes")" "big"

echo
if [ "$failures" -eq 0 ]; then
  echo "Theme OK — served from the chart's own ConfigMap, in both locales."
else
  echo "$failures check(s) failed."
  exit 1
fi
