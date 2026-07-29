#!/usr/bin/env bash
# Deliberately break things until the alerting stack notifies somebody, and fail
# if it does not.
#
# The point is that nothing here is a mock of the configuration: the Prometheus
# config, the alert rules and the Alertmanager config are rendered out of
# infra/helm/librarius with `helm template`, so the drill exercises the files the
# cluster runs. Only the API is fake, and only so that it can be made to misbehave
# on command.
#
#   ./infra/alerting/fire-drill.sh            # the three API rules, ~16 minutes
#   ./infra/alerting/fire-drill.sh down       # only LibrariusApiDown, ~4 minutes
#
# Needs docker, docker compose and helm. Nothing else, and no cluster.
set -euo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
chart="$here/../helm/librarius"
compose=(docker compose -f "$here/compose.drill.yml")

scope=${1:-all}
case "$scope" in
  all | down) ;;
  *) echo "usage: $0 [all|down]" >&2; exit 2 ;;
esac

DRILL_WORK=$(mktemp -d)
export DRILL_WORK
export DRILL_PROMETHEUS_PORT=${DRILL_PROMETHEUS_PORT:-9091}

cleanup() {
  "${compose[@]}" down --remove-orphans --timeout 5 >/dev/null 2>&1 || true
  rm -rf "$DRILL_WORK"
}
trap cleanup EXIT

# ── 1. Take the configuration out of the chart ───────────────────────────────
render="$DRILL_WORK/render.yaml"
helm template librarius "$chart" -n librarius \
  --set postgres.existingSecret=drill \
  --set keycloak.existingSecret=drill \
  --set monitoring.alertmanager.existingSecret=drill \
  -s templates/monitoring.yaml > "$render"

# Pulls one `key: |` block out of the rendered manifests and unindents it.
extract() {
  awk -v key="$1" '
    /^---$/ { grabbing = 0 }
    !grabbing && $0 ~ "^  " key ": \\|$" { grabbing = 1; next }
    grabbing {
      if ($0 ~ /^ ? ?[^ ]/) { grabbing = 0; next }
      sub(/^    /, "")
      print
    }
  ' "$render"
}

mkdir -p "$DRILL_WORK"/{config,rules,alertmanager,secrets}
extract prometheus.yml            > "$DRILL_WORK/config/prometheus.yml"
extract librarius.rules.yml       > "$DRILL_WORK/rules/librarius.rules.yml"
extract alertmanager.yml          > "$DRILL_WORK/alertmanager/alertmanager.yml"
# Stands in for the Secret key the chart mounts at the same path.
echo 'http://sink:8080/' > "$DRILL_WORK/secrets/webhook-url"

for f in config/prometheus.yml rules/librarius.rules.yml alertmanager/alertmanager.yml; do
  [ -s "$DRILL_WORK/$f" ] || { echo "could not extract $f from the chart" >&2; exit 1; }
done
echo "rules under drill: $(grep -c '^      - alert:' "$DRILL_WORK/rules/librarius.rules.yml")"

# ── 2. Bring the stack up ────────────────────────────────────────────────────
"${compose[@]}" up -d --quiet-pull
started=$(date +%s)
echo "stack up; Prometheus on http://localhost:${DRILL_PROMETHEUS_PORT}"

# Waits for one alert name to reach the sink, printing where the rule is in its
# lifecycle so a slow drill can be told apart from a broken one.
await() {
  local alert="$1" budget="$2" deadline=$(( $(date +%s) + $2 ))
  echo "waiting for ${alert} (up to $(( budget / 60 )) min)"
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if "${compose[@]}" logs --no-log-prefix sink 2>/dev/null | grep -q "\"alertname\":\"${alert}\""; then
      echo "  ✔ ${alert} notified after $(( $(date +%s) - started ))s"
      return 0
    fi
    state=$(curl -fsS "http://localhost:${DRILL_PROMETHEUS_PORT}/api/v1/alerts" 2>/dev/null \
      | tr ',' '\n' | grep -A1 "\"alertname\":\"${alert}\"" | grep -o '"state":"[a-z]*"' | head -1 || true)
    echo "  ... $(( $(date +%s) - started ))s elapsed${state:+, prometheus says ${state}}"
    sleep 15
  done
  echo "  ✘ ${alert} never reached the sink" >&2
  return 1
}

failed=0

if [ "$scope" = all ]; then
  # 5xx share and p95 are wrong from the first scrape; the wait is the `for:`
  # clause of each rule, which is the delay the cluster will have too.
  await LibrariusApiHighErrorRate 600 || failed=1
  await LibrariusApiSlowResponses 600 || failed=1
fi

echo "stopping the api container — LibrariusApiDown from here"
"${compose[@]}" stop librarius-api >/dev/null
await LibrariusApiDown 420 || failed=1

# ── 3. What was actually received ────────────────────────────────────────────
echo
echo "── notifications delivered to the webhook ───────────────────────────────"
"${compose[@]}" logs --no-log-prefix sink 2>/dev/null \
  | python3 -c '
import json, sys
for line in sys.stdin:
    line = line.strip()
    if not line.startswith("{"):
        continue
    payload = json.loads(line)
    for alert in payload.get("alerts", []):
        print("{status:8} {name:32} {summary}".format(
            status=alert.get("status", "?"),
            name=alert["labels"].get("alertname", "?"),
            summary=alert.get("annotations", {}).get("summary", "")))
' || "${compose[@]}" logs --no-log-prefix sink

exit "$failed"
