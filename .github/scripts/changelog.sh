#!/usr/bin/env bash
#
# Renders the changelog section of one release, from the conventional commits it
# contains. Prints Markdown on stdout; the caller decides where it goes (the GitHub
# release notes, and the top of CHANGELOG.md).
#
# Usage: changelog.sh <version> <range> [compare-url]
#
#   version      1.4.2 — the released version, without its `v`
#   range        v1.4.1..v1.4.2, or a single ref for the very first release
#   compare-url  optional link appended at the end of the section
#
# Subjects are read as `type(scope)!: summary`. This repository squash-merges, so the
# subject on `main` is the pull request title; some of them use `type(scope) — summary`
# instead, which is accepted as the same shape. Anything that matches neither is kept
# under "Other" rather than dropped: a changelog that silently loses commits is worse
# than one with an untidy section.
set -euo pipefail

version=${1:?usage: changelog.sh <version> <range> [compare-url]}
range=${2:?usage: changelog.sh <version> <range> [compare-url]}
compare=${3:-}

declare -A heading=(
  [feat]='Features'
  [fix]='Fixes'
  [perf]='Performance'
  [refactor]='Refactoring'
  [docs]='Documentation'
  [test]='Tests'
  [build]='Build'
  [ci]='CI'
  [chore]='Chores'
  [style]='Style'
  [revert]='Reverts'
  [other]='Other'
)

# Reading order: what a user cares about first, housekeeping last.
order=(feat fix perf refactor docs test build ci chore style revert other)

declare -A entries=()
breaking=''
count=0

while IFS= read -r subject; do
  [[ -z $subject ]] && continue
  type=''
  scope=''
  bang=''
  summary=$subject

  if [[ $subject =~ ^([a-z]+)(\(([^\)]+)\))?(!)?:[[:space:]]+(.+)$ ]] ||
    [[ $subject =~ ^([a-z]+)(\(([^\)]+)\))?(!)?[[:space:]]+—[[:space:]]+(.+)$ ]]; then
    type=${BASH_REMATCH[1]}
    scope=${BASH_REMATCH[3]}
    bang=${BASH_REMATCH[4]}
    summary=${BASH_REMATCH[5]}
  fi

  # `$type` is empty when the subject matched neither shape, and bash refuses an empty
  # subscript on an associative array — so it has to be tested before the lookup, not
  # inside it. Without the first test this line aborts the whole run on the very
  # commits the "Other" bucket exists for.
  [[ -n $type && -n ${heading[$type]:-} ]] || type=other

  line='- '
  [[ -n $scope ]] && line+="**${scope}**: "
  line+=$summary

  [[ -n $bang ]] && breaking+="${line}"$'\n'
  entries[$type]+="${line}"$'\n'
  count=$((count + 1))
done < <(git log --no-merges --reverse --format='%s' "$range")

# The date of the release itself, not of the machine rendering the notes: regenerating
# an old section must produce the same text.
released_at=$(git log -1 --format=%cs "${range##*..}")

printf '## [%s] - %s\n' "$version" "$released_at"

if [[ $count -eq 0 ]]; then
  printf '\nNo commit in this range.\n'
  exit 0
fi

if [[ -n $breaking ]]; then
  printf '\n### Breaking changes\n\n%s' "$breaking"
fi

for type in "${order[@]}"; do
  [[ -n ${entries[$type]:-} ]] || continue
  printf '\n### %s\n\n%s' "${heading[$type]}" "${entries[$type]}"
done

[[ -n $compare ]] && printf '\n[Full comparison](%s)\n' "$compare"

exit 0
