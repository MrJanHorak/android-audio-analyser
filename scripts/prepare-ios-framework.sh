#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIGURATION="${1:-Debug}"

case "$CONFIGURATION" in
  Debug|Release) ;;
  *)
    echo "Unsupported configuration: $CONFIGURATION"
    echo "Use Debug or Release."
    exit 1
    ;;
esac

if [[ -z "${JAVA_HOME:-}" ]]; then
  ANDROID_STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -x "$ANDROID_STUDIO_JBR/bin/java" ]]; then
    export JAVA_HOME="$ANDROID_STUDIO_JBR"
  fi
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME is not set to a usable JDK."
  echo "Install JDK 17+ or Android Studio and retry."
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

CONFIGURATION_LOWER="$(printf '%s' "$CONFIGURATION" | tr '[:upper:]' '[:lower:]')"
GRADLE_TASK=":shared:assembleShared${CONFIGURATION}XCFramework"
SOURCE_FRAMEWORK="$REPO_ROOT/shared/build/XCFrameworks/$CONFIGURATION_LOWER/Shared.xcframework"
DESTINATION_DIR="$REPO_ROOT/ios/Frameworks"
DESTINATION_FRAMEWORK="$DESTINATION_DIR/Shared.xcframework"

cd "$REPO_ROOT"
bash ./gradlew --no-daemon "$GRADLE_TASK"

mkdir -p "$DESTINATION_DIR"
rm -rf "$DESTINATION_FRAMEWORK"
cp -R "$SOURCE_FRAMEWORK" "$DESTINATION_FRAMEWORK"

echo "Prepared $DESTINATION_FRAMEWORK from $GRADLE_TASK"
