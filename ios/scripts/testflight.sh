#!/bin/bash
# Archive a Release build and upload it to TestFlight.
#
# Needs an App Store Connect API key:
#   ASC_KEY_ID      key ID (the .p8 must be at ~/.appstoreconnect/private_keys/AuthKey_<id>.p8)
#   ASC_ISSUER_ID   issuer ID from App Store Connect > Users and Access > Integrations
#
# The build number defaults to a UTC timestamp so every upload is unique; override with BUILD_NUMBER.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ASC_KEY_ID:?set ASC_KEY_ID}"
: "${ASC_ISSUER_ID:?set ASC_ISSUER_ID}"
KEY_PATH="$HOME/.appstoreconnect/private_keys/AuthKey_${ASC_KEY_ID}.p8"
BUILD_NUMBER="${BUILD_NUMBER:-$(date -u +%y%m%d%H%M)}"
OUT="${OUT:-build/testflight}"
AUTH=(-allowProvisioningUpdates
      -authenticationKeyPath "$KEY_PATH"
      -authenticationKeyID "$ASC_KEY_ID"
      -authenticationKeyIssuerID "$ASC_ISSUER_ID")

rm -rf "$OUT"
xcodebuild -project TraefikManager.xcodeproj -scheme TraefikManager -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$OUT/TraefikManager.xcarchive" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" "${AUTH[@]}" archive

xcodebuild -exportArchive -archivePath "$OUT/TraefikManager.xcarchive" \
  -exportOptionsPlist scripts/ExportOptions.plist -exportPath "$OUT/export" "${AUTH[@]}"

echo "Uploaded build $BUILD_NUMBER to App Store Connect"
