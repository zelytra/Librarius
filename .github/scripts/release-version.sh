#!/usr/bin/env bash
#
# Turns a release tag into everything the rest of the release needs to know.
#
# Usage: release-version.sh <git-tag> [commit-sha]
#
# Prints `key=value` lines, in the shape GitHub Actions expects on $GITHUB_OUTPUT:
#
#   version=1.4.2            the tag without its leading `v`
#   major=1                  moving alias, republished by every 1.x.y release
#   minor=1.4                moving alias, republished by every 1.4.y release
#   prerelease=false         whether this release may move the aliases
#   tags=1.4.2 1.4 1 <sha>   the image tags to publish, in that order
#
# Only a strict `vMAJOR.MINOR.PATCH[-prerelease]` tag is accepted. Build metadata
# (`+build`) is rejected on purpose: `+` is not a legal character in a container image
# tag, so such a tag could never be published faithfully.
#
# A pre-release (`v2.0.0-rc.1`) publishes its exact version and nothing else: whoever
# pulls `2` or `2.0` must keep getting the latest stable release, never a candidate.
set -euo pipefail

tag=${1:-}
sha=${2:-}

if [[ -z $tag ]]; then
  echo "usage: release-version.sh <git-tag> [commit-sha]" >&2
  exit 2
fi

# Semantic Versioning 2.0.0, minus build metadata, prefixed with `v` as git tags are.
pattern='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-([0-9A-Za-z][0-9A-Za-z.-]*))?$'
if [[ ! $tag =~ $pattern ]]; then
  echo "::error::'${tag}' is not a vMAJOR.MINOR.PATCH[-prerelease] tag" >&2
  exit 1
fi

major=${BASH_REMATCH[1]}
minor=${BASH_REMATCH[2]}
pre=${BASH_REMATCH[5]:-}
version=${tag#v}

tags=("$version")
prerelease=true
if [[ -z $pre ]]; then
  prerelease=false
  tags+=("${major}.${minor}" "$major")
fi

# The commit tag stays in the list: it is the only one that never moves and never
# collides, which is what `helm history` is read against during an incident.
[[ -n $sha ]] && tags+=("$sha")

printf 'version=%s\n' "$version"
printf 'major=%s\n' "$major"
printf 'minor=%s.%s\n' "$major" "$minor"
printf 'prerelease=%s\n' "$prerelease"
printf 'tags=%s\n' "${tags[*]}"
