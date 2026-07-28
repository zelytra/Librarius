#!/usr/bin/env bash
#
# Aligns the Helm chart with the version being released, so that `helm history` and
# `helm list` name the same thing as the git tag and the image tags. Keeping those in
# step by hand is exactly what stopped happening: the chart still said 0.2.0 while the
# cluster ran images tagged with a commit sha.
#
# Usage: sync-version.sh <version>   # e.g. sync-version.sh 1.4.2
#
# Run from the repository root. Idempotent: running it twice changes nothing.
set -euo pipefail

version=${1:?usage: sync-version.sh <version>}

if [[ ! $version =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z][0-9A-Za-z.-]*)?$ ]]; then
  echo "::error::'${version}' is not a MAJOR.MINOR.PATCH[-prerelease] version" >&2
  exit 1
fi

chart=infra/helm/librarius/Chart.yaml
values=infra/helm/librarius/values.yaml

# `version` is the chart's own version, `appVersion` the application it ships. They are
# released together here, so they hold the same value.
sed -i -E "s|^version: .*|version: ${version}|" "$chart"
sed -i -E "s|^appVersion: .*|appVersion: \"${version}\"|" "$chart"

# The default image tags. Only web.image.tag and api.image.tag sit at that indentation;
# the postgres and keycloak images are pinned upstream versions and stay untouched.
sed -i -E "s|^    tag: \".*\"|    tag: \"${version}\"|" "$values"

grep -E '^(version|appVersion):' "$chart"
grep -nE '^    tag: ' "$values"
