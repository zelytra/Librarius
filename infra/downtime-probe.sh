#!/usr/bin/env bash
#
# Measures the outage a deployment causes, seen from outside the cluster —
# which is the only vantage point that matters, since it is the one the user
# has. Run it, merge into `main`, and read the total when the pipeline is done.
#
# A sample counts as DOWN when the request never reaches an application: a
# connection failure, a timeout, or a 5xx served by Traefik because no endpoint
# is left behind the Service. Everything else counts as UP — a 401 on /api is
# the API answering, and answering is the whole question here.
#
# Needs bash and curl, nothing else, and no cluster access.
#
#   ./infra/downtime-probe.sh                                  # web, 10 min
#   ./infra/downtime-probe.sh https://librarius.zelytra.fr/api/books 600
#
# Reference figures and the measurement protocol: docs/DEPLOYMENT.md
# § "Deploying without downtime".
#
set -uo pipefail

URL=${1:-https://librarius.zelytra.fr/}
DURATION=${2:-600}
INTERVAL=${3:-0.25}

printf 'probing %s every %ss for %ss — Ctrl-C to stop early\n\n' \
  "$URL" "$INTERVAL" "$DURATION"

total=0
failed=0
outages=0
outage_start=""
longest=0

# Reports the outage that just ended, and keeps the longest one. The label
# distinguishes a recovery from a probe that simply ran out of time mid-outage.
close_outage() {
  local now=$1 label=${2:-UP} length
  length=$(awk -v a="$now" -v b="$outage_start" 'BEGIN { printf "%.2f", a - b }')
  printf '%s  %-5s after %ss down\n' "$(date -u +%H:%M:%S)" "$label" "$length"
  awk -v l="$length" -v m="$longest" 'BEGIN { exit !(l > m) }' && longest=$length
  outage_start=""
}

trap 'printf "\n"; report; exit 0' INT

report() {
  local pct="n/a"
  [ "$total" -gt 0 ] && pct=$(awk -v f="$failed" -v t="$total" \
    'BEGIN { printf "%.2f", 100 * f / t }')
  printf '\n── %s ──\n' "$URL"
  printf '  samples        %s\n' "$total"
  printf '  failed         %s (%s%%)\n' "$failed" "$pct"
  printf '  outages        %s\n' "$outages"
  printf '  longest outage %ss\n' "${longest:-0}"
  # Approximate: samples are not perfectly evenly spaced, curl itself takes time.
  printf '  downtime       ~%ss\n' \
    "$(awk -v f="$failed" -v i="$INTERVAL" 'BEGIN { printf "%.1f", f * i }')"
}

started=$(date +%s)
deadline=$((started + DURATION))

while [ "$(date +%s)" -lt "$deadline" ]; do
  now=$(date +%s.%N)
  # curl already prints 000 when it never got a response, and exits non-zero
  # doing so — hence the `|| true` rather than a fallback that would concatenate
  # a second 000 onto the first and match neither branch below.
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$URL") || true
  [ -z "$code" ] && code=000
  total=$((total + 1))

  if [ "$code" = "000" ] || [ "$code" -ge 500 ]; then
    failed=$((failed + 1))
    if [ -z "$outage_start" ]; then
      outages=$((outages + 1))
      outage_start=$now
      printf '%s  DOWN  (%s)\n' "$(date -u +%H:%M:%S)" "$code"
    fi
  elif [ -n "$outage_start" ]; then
    close_outage "$now"
  fi

  sleep "$INTERVAL"
done

# A probe that ends mid-outage still has to count it — and must not claim the
# service came back, because it did not.
[ -n "$outage_start" ] && close_outage "$(date +%s.%N)" "END"
report
