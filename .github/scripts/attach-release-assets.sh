#!/usr/bin/env bash
# Uploads the mobile install files to the GitHub release of a tag (#541).
#
# release.yml creates that release, but mobile-release.yml and ios-release.yml run on the
# same tag in parallel and usually finish first — so this waits for the release to exist
# rather than assuming an order between the workflows.
#
# usage: attach-release-assets.sh <tag> <file>...
# needs GH_TOKEN with contents: write.
set -euo pipefail

tag=$1
shift
attempts=${RELEASE_WAIT_ATTEMPTS:-60}
poll=${RELEASE_POLL_SECONDS:-30}

for file in "$@"; do
  if [ ! -f "$file" ]; then
    echo "::error::${file} does not exist — nothing to attach to ${tag}" >&2
    exit 1
  fi
done

for ((i = 1; i <= attempts; i++)); do
  if gh release view "$tag" > /dev/null 2>&1; then
    # --clobber: re-running the workflow replaces the files rather than failing on them.
    gh release upload "$tag" "$@" --clobber
    exit 0
  fi
  sleep "$poll"
done

echo "::error::the ${tag} release did not appear after $((attempts * poll))s — has release.yml failed?" >&2
exit 1
