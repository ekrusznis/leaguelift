#!/usr/bin/env bash
# Wraps `eas build` to produce a real distributable artifact for Rally26 mobile —
# an installable Android APK (development/preview), an Android AAB (production, for
# Google Play), or a signed iOS IPA (preview/production, for TestFlight/App Store).
# See DESIGN-DOC.md §14.1 Phase 36 and eas.json for what each profile actually builds.
#
# Requires: `npm install -g eas-cli` (or run via `npx eas-cli`), and `eas login` with
# real Expo account credentials plus a real EAS project (`eas init`) — this script
# does not manage credentials itself; EAS prompts for/stores Apple and Google signing
# credentials on first real build per docs.expo.dev/app-signing/app-credentials.
#
# Usage:
#   ./scripts/build-release.sh <android|ios|all> <development|preview|production> [--local]
#
# --local builds on this machine instead of EAS's cloud build service (Android only —
# iOS local builds still require a Mac with Xcode; use cloud builds from any OS otherwise).

set -euo pipefail

PLATFORM="${1:-}"
PROFILE="${2:-}"
LOCAL_FLAG="${3:-}"

usage() {
  echo "Usage: $0 <android|ios|all> <development|preview|production> [--local]" >&2
  exit 1
}

if [[ -z "$PLATFORM" || -z "$PROFILE" ]]; then
  usage
fi

case "$PLATFORM" in
  android|ios|all) ;;
  *) usage ;;
esac

case "$PROFILE" in
  development|preview|production) ;;
  *) usage ;;
esac

EXTRA_ARGS=()
if [[ "$LOCAL_FLAG" == "--local" ]]; then
  if [[ "$PLATFORM" == "ios" || "$PLATFORM" == "all" ]]; then
    echo "Warning: --local for iOS still requires a Mac with Xcode installed. EAS cloud build is the only option from Windows/Linux." >&2
  fi
  EXTRA_ARGS+=(--local)
fi

echo "Building Rally26 mobile — platform=$PLATFORM profile=$PROFILE"
cd "$(dirname "$0")/.."

npx eas-cli build --platform "$PLATFORM" --profile "$PROFILE" --non-interactive "${EXTRA_ARGS[@]}"
