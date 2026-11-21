#!/usr/bin/env bash
# Renders the chart and checks what the audit of #575 asked of it:
#
#   - the ingress hands Keycloak only the realm the app uses and its static resources, never
#     the admin console nor the master realm (#577);
#   - every pod runs without privilege escalation, with every capability dropped but the few
#     an image needs, under the runtime's seccomp profile and with no service-account token,
#     and the api and Keycloak as a non-root user (#582);
#   - network policies deny by default and let each component be reached only by what talks
#     to it — PostgreSQL by the api, Keycloak and the backup alone (#582).
#
#   infra/helm/check-chart-security.sh
#
# Requires helm and yq (mikefarah, v4 — `-N` keeps it from printing `---` between the
# documents of the rendered stream). Run from anywhere.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

failures=0
check() { # check <description> <actual> <expected>
  if [ "$2" = "$3" ]; then
    echo "  ok    $1"
  else
    echo "  FAIL  $1"
    echo "        expected: $3"
    echo "        got:      $2"
    failures=$((failures + 1))
  fi
}

helm template librarius "$REPO/infra/helm/librarius" \
  --set postgres.existingSecret=check --set keycloak.existingSecret=check \
  --set backup.enabled=true --set backup.existingSecret=check --set backup.s3.bucket=check >"$WORK/rendered.yaml"
r="$WORK/rendered.yaml"

echo "Ingress (#577)"
paths="$(yq -N -r 'select(.kind == "Ingress") | .spec.rules[].http.paths[].path' "$r" | sort | tr '\n' ' ')"
check "Keycloak is routed by the realm and its resources only" \
  "$(printf '%s' "$paths" | tr ' ' '\n' | grep '^/auth' | tr '\n' ' ')" "/auth/realms/librarius /auth/resources "
check "nothing routes the bare /auth prefix" "$(printf '%s' "$paths" | tr ' ' '\n' | grep -cx '/auth' || true)" "0"

echo "Pods (#582)"
# Parenthesised whole: yq ranks `,` and `|` differently from jq, and without them the first
# branch went on unfiltered; and it answers null, not nothing, for a document that is no pod.
pods='(((select(.kind == "Deployment" or .kind == "Job" or .kind == "StatefulSet") | .spec.template.spec),
      (select(.kind == "CronJob") | .spec.jobTemplate.spec.template.spec)) | select(. != null))'
check "no pod mounts a service-account token" \
  "$(yq -N -r "$pods | select(.automountServiceAccountToken != false) | \"x\"" "$r" | wc -l | tr -d ' ')" "0"
check "every pod runs under the runtime's seccomp profile" \
  "$(yq -N -r "$pods | select(.securityContext.seccompProfile.type != \"RuntimeDefault\") | \"x\"" "$r" | wc -l | tr -d ' ')" "0"
containers="$pods | ((.containers[]), ((.initContainers // [])[]))"
check "no container may escalate its privileges" \
  "$(yq -N -r "$containers | select(.securityContext.allowPrivilegeEscalation != false) | .name" "$r" | tr '\n' ' ')" ""
check "every container drops all capabilities" \
  "$(yq -N -r "$containers | select((.securityContext.capabilities.drop // []) | contains([\"ALL\"]) | not) | .name" "$r" | tr '\n' ' ')" ""
for name in api keycloak; do
  check "$name runs as a non-root user" \
    "$(yq -N -r "$containers | select(.name == \"$name\") | .securityContext.runAsNonRoot" "$r")" "true"
done
check "the api's root filesystem is read-only" \
  "$(yq -N -r "$containers | select(.name == \"api\") | .securityContext.readOnlyRootFilesystem" "$r")" "true"

echo "Network policies (#582)"
np='select(.kind == "NetworkPolicy")'
check "the namespace denies by default" \
  "$(yq -N -r "$np | select(.metadata.name == \"librarius-default-deny\") | .spec.policyTypes[0]" "$r")" "Ingress"
from() { # from <component>: the components a policy lets reach it, sorted
  yq -N -r "$np | select(.spec.podSelector.matchLabels.app == \"librarius-$1\") | .spec.ingress[].from[]
         | (.podSelector.matchLabels.app // (\"ns:\" + .namespaceSelector.matchLabels.\"kubernetes.io/metadata.name\"))" "$r" \
    | sort | tr '\n' ' '
}
check "PostgreSQL: the api, Keycloak and the backup only" "$(from postgres)" "librarius-api librarius-backup librarius-keycloak librarius-keycloak-setup "
check "Keycloak: the ingress, the api and its setup Job"   "$(from keycloak)" "librarius-api librarius-keycloak-setup ns:kube-system "
check "the api: the ingress and Prometheus"                "$(from api)" "librarius-prometheus ns:kube-system "
check "the web: the ingress"                               "$(from web)" "ns:kube-system "

echo
if [ "$failures" -eq 0 ]; then
  echo "Chart security OK."
else
  echo "$failures check(s) failed."
  exit 1
fi
