#!/usr/bin/env bash
#
# Build the signed release APK and archive it into its release's own folder under
# local/releases/, under a name that says exactly what it is. Run at will; nothing in
# the build calls this.
#
#   ./scripts/archive-apk.sh              build, verify, archive
#   ./scripts/archive-apk.sh --force      allow replacing an existing archive
#   ./scripts/archive-apk.sh --no-build   use the APK already on disk
#
# Each archive is three files sharing one prefix: the APK, a .sha256, and the R8
# mapping.txt that turns an obfuscated stack trace from that build back into names. The
# mapping lives in app/build/ and a clean wipes it, so it is worth nothing unless it is
# kept with the APK it belongs to.
#
# A clean checkout produces "local/releases/v0.1.0/badal-v0.1.0.apk". A dirty working
# tree produces "local/releases/v0.1.0-dirty-g1a2b3c4/badal-v0.1.0-dirty-g1a2b3c4.apk",
# so work in progress can never take the name of a release, and an existing archive is
# never replaced without --force: same version, different commit, is the case that bites.

set -euo pipefail

# Print the header comment block, so the usage text cannot drift from the docs above it.
usage() { awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"; }

FORCE=0
BUILD=1
for arg in "$@"; do
    case "$arg" in
        --force) FORCE=1 ;;
        --no-build) BUILD=0 ;;
        -h | --help) usage; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "${BASH_SOURCE[0]}")/.."

APK=app/build/outputs/apk/release/app-release.apk
MAP=app/build/outputs/mapping/release/mapping.txt

# Gradle and apksigner need a JDK; a Mac with only Android Studio has one inside the app.
STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JBR" ]; then export JAVA_HOME="$STUDIO_JBR"; fi

SDK=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)
SDK="${SDK:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
TOOLS=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
[ -n "$TOOLS" ] || { echo "error: no Android build-tools under $SDK" >&2; exit 1; }

if [ "$BUILD" = 1 ]; then ./gradlew -q :app:assembleRelease; fi
[ -f "$APK" ] || { echo "error: $APK not found (drop --no-build?)" >&2; exit 1; }

# The version comes from the built APK, never from build.gradle.kts, so the name cannot
# disagree with the manifest that shipped.
BADGING=$("$TOOLS/aapt" dump badging "$APK" | head -1)
VNAME=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")
VCODE=$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$BADGING")
[ -n "$VNAME" ] && [ -n "$VCODE" ] || { echo "error: no version in $APK" >&2; exit 1; }

# Release comes out unsigned when local/keystore.properties is absent, and an unsigned
# APK under a release name is the mislabelled file this script exists to prevent.
SIGNER=$("$TOOLS/apksigner" verify --print-certs "$APK" 2>/dev/null |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p' || true)
[ -n "$SIGNER" ] || {
    echo "error: $APK is not signed. Add local/keystore.properties (README) and rebuild." >&2
    exit 1
}
[ -f "$MAP" ] || { echo "error: $MAP not found; it is written by R8 (drop --no-build)." >&2; exit 1; }

SUFFIX=""
if [ -n "$(git status --porcelain)" ]; then
    SUFFIX="-dirty-g$(git rev-parse --short HEAD)"
    echo "warning: working tree is dirty; archiving as a work-in-progress build"
fi

# The files keep the full name too, so one copied out of its folder, onto a phone,
# still says which build it is.
DEST="local/releases/v$VNAME$SUFFIX"
TARGET="$DEST/badal-v$VNAME$SUFFIX.apk"
if [ -e "$TARGET" ] && [ "$FORCE" != 1 ]; then
    echo "error: $TARGET exists. Bump versionCode/versionName, or pass --force." >&2
    exit 1
fi

mkdir -p "$DEST"
cp "$APK" "$TARGET"
shasum -a 256 "$TARGET" | awk '{print $1}' >"$TARGET.sha256"
cp "$MAP" "$TARGET.mapping.txt"

echo "archived: $TARGET  ($(wc -c <"$TARGET" | tr -d ' ') bytes)"
echo "version:  $VNAME ($VCODE)"
echo "sha256:   $(cat "$TARGET.sha256")"
echo "signer:   $SIGNER"
