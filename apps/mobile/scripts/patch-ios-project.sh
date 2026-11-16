#!/usr/bin/env bash
# The hand edits the generated iOS project needs, re-applied in one command.
#
# `cap add ios` writes the project from Capacitor's template and never touches it again, so
# everything below has to survive a regeneration — run this script after any `cap add ios`,
# the same discipline .claude/docs/MOBILE.md § 7 asks of the Android project.
#
# macOS only: `plutil` edits the property lists in place without reformatting the whole file.
set -euo pipefail

plist="${1:-apps/mobile/ios/App/App/Info.plist}"
scheme="fr.zelytra.librarius"

if [ ! -f "$plist" ]; then
  echo "No Info.plist at $plist — generate the project with \`cap add ios\` first." >&2
  exit 1
fi

# The OAuth callback (#528): the authorization page runs in an ASWebAuthenticationSession, as
# RFC 8252 requires, and the answer comes back on the app's own scheme. Mirrors the Android
# <intent-filter> in AndroidManifest.xml.
if plutil -extract CFBundleURLTypes xml1 -o - "$plist" >/dev/null 2>&1; then
  echo "CFBundleURLTypes already present, left alone"
else
  plutil -insert CFBundleURLTypes -json \
    "[{\"CFBundleURLName\": \"$scheme\", \"CFBundleURLSchemes\": [\"$scheme\"]}]" "$plist"
  echo "CFBundleURLTypes: $scheme"
fi

# Shown by iOS in the permission prompt, so it is written in French like the rest of the
# interface. Without it the app is rejected at review the first time it opens the camera.
plutil -replace NSCameraUsageDescription -string \
  "Pour scanner le code-barres d'un livre et l'ajouter à votre bibliothèque." "$plist"
echo "NSCameraUsageDescription set"
