#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.13
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/noor-wrapper/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME_DIR/bin/gradle" ]; then
  mkdir -p "$(dirname "$GRADLE_HOME_DIR")"
  ZIP="$(dirname "$GRADLE_HOME_DIR")/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    else echo "Install curl or wget to download Gradle." >&2; exit 1; fi
  fi
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$ZIP" -d "$(dirname "$GRADLE_HOME_DIR")"
  else echo "Install unzip to extract Gradle." >&2; exit 1; fi
fi
exec "$GRADLE_HOME_DIR/bin/gradle" "$@"
