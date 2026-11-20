#!/usr/bin/env bash
# Exercises attach-release-assets.sh against a stub `gh`, since the real one only ever runs
# on a tag (#541): the release appears after two polls, then every file is uploaded once.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

mkdir "$work/bin"
cat > "$work/bin/gh" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "$GH_STUB_LOG"
if [ "$1 $2" = "release view" ]; then
  count=$(grep -c '^release view' "$GH_STUB_LOG")
  [ "$count" -ge 3 ]
fi
STUB
chmod +x "$work/bin/gh"
touch "$work/app.apk" "$work/app.ipa"

export GH_STUB_LOG="$work/gh.log"
PATH="$work/bin:$PATH" RELEASE_POLL_SECONDS=0 \
  bash "$here/attach-release-assets.sh" v9.9.9 "$work/app.apk" "$work/app.ipa"

grep -q "^release upload v9.9.9 $work/app.apk $work/app.ipa --clobber$" "$GH_STUB_LOG"
test "$(grep -c '^release view' "$GH_STUB_LOG")" -eq 3

# A release that never appears fails the job rather than publishing nothing in silence.
: > "$GH_STUB_LOG"
cat > "$work/bin/gh" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "$GH_STUB_LOG"
exit 1
STUB
if PATH="$work/bin:$PATH" RELEASE_POLL_SECONDS=0 RELEASE_WAIT_ATTEMPTS=2 \
     bash "$here/attach-release-assets.sh" v9.9.9 "$work/app.apk" 2>/dev/null; then
  echo "expected a failure when the release never appears" >&2
  exit 1
fi
! grep -q '^release upload' "$GH_STUB_LOG"

# A missing file is a broken build, not something to skip.
if PATH="$work/bin:$PATH" bash "$here/attach-release-assets.sh" v9.9.9 "$work/missing.apk" 2>/dev/null; then
  echo "expected a failure on a missing file" >&2
  exit 1
fi

echo "attach-release-assets: ok"
