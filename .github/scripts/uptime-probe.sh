#!/usr/bin/env bash
# Ask the public URL the questions a user asks, from outside the cluster.
#
# This is the half of the alerting that needs no credential and no cluster
# access: the in-cluster Prometheus can tell that the API stopped answering it,
# but it cannot tell that Traefik, the certificate, the DNS record or the whole
# node went away — by then it is gone too. A probe that runs somewhere else can,
# which is why this one runs on a GitHub runner (.github/workflows/uptime.yml).
#
#   ./.github/scripts/uptime-probe.sh https://librarius.zelytra.fr
#
# Exit code 0 when everything answered as expected, 1 otherwise. The report on
# stdout is what ends up in the issue body, so it is written to be read cold.
set -uo pipefail

BASE=${1:?usage: uptime-probe.sh <base-url>}
BASE=${BASE%/}

# Three attempts spaced out, because a rolling deployment is not an outage: the
# whole point of maxUnavailable 0 is that it should not be visible from here, and
# a single unlucky sample must not open an issue.
ATTEMPTS=${ATTEMPTS:-3}
DELAY=${DELAY:-20}
TIMEOUT=${TIMEOUT:-15}
# Same threshold as the LibrariusCertificateExpiringSoon rule, so the two never
# disagree about when a certificate has become a problem.
TLS_WARN_DAYS=${TLS_WARN_DAYS:-15}

failures=0

report() { printf '%-28s %-18s %s\n' "$1" "$2" "$3"; }

# $1 label, $2 path, $3 expected HTTP status
check_status() {
  local label=$1 path=$2 expected=$3 attempt status=""
  for attempt in $(seq 1 "$ATTEMPTS"); do
    # curl already writes 000 through -w when it never got an answer; a `|| echo
    # 000` on top of that produces "000000" and a confusing report.
    status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" "${BASE}${path}" 2>/dev/null)
    status=${status:-000}
    [ "$status" = "$expected" ] && break
    [ "$attempt" -lt "$ATTEMPTS" ] && sleep "$DELAY"
  done
  if [ "$status" = "$expected" ]; then
    report "$label" "HTTP $status" "ok"
  else
    # 000 is curl failing to get an answer at all: DNS, TCP or TLS, not the app.
    report "$label" "HTTP $status" "FAIL (expected $expected, ${ATTEMPTS} attempts)"
    failures=$((failures + 1))
  fi
}

echo "probe of ${BASE} at $(date -u '+%Y-%m-%d %H:%M:%SZ')"
echo

# The PWA itself. 200 means Traefik, TLS and the web pod are all standing.
check_status "app  GET /" "/" 200

# 401 rather than 200 is the assertion that matters: /q is not routed and `/`
# catches everything else, so a request that reached nginx instead of the API
# would come back 200 with index.html and look healthy. Only the API answers 401.
check_status "api  GET /api/me" "/api/me" 401

# Certificate expiry, seen from the outside — which is what a browser sees, and
# not necessarily what cert-manager believes it has renewed.
host=${BASE#https://}
host=${host#http://}
host=${host%%/*}
not_after=$(echo | openssl s_client -servername "$host" -connect "${host}:443" 2>/dev/null \
  | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)
if [ -z "$not_after" ]; then
  report "tls  certificate" "unreadable" "FAIL (no certificate served)"
  failures=$((failures + 1))
else
  days=$(( ( $(date -u -d "$not_after" +%s) - $(date -u +%s) ) / 86400 ))
  if [ "$days" -lt "$TLS_WARN_DAYS" ]; then
    report "tls  certificate" "${days} days left" "FAIL (under ${TLS_WARN_DAYS})"
    failures=$((failures + 1))
  else
    report "tls  certificate" "${days} days left" "ok"
  fi
fi

echo
if [ "$failures" -eq 0 ]; then
  echo "RESULT: ok"
  exit 0
fi
echo "RESULT: ${failures} check(s) failed"
exit 1
