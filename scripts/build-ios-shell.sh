#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIGURATION="${1:-Debug}"

"$SCRIPT_DIR/prepare-ios-framework.sh" "$CONFIGURATION"

cd "$REPO_ROOT/ios"
xcodegen generate
xcodebuild \
  -project AudioAnalyser.xcodeproj \
  -scheme AudioAnalyser \
  -configuration "$CONFIGURATION" \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
