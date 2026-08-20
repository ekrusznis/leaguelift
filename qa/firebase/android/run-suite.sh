#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <suite> <apk-path> [--smoke]"
  echo "Examples:"
  echo "  $0 coach ./rally26-preview.apk --smoke"
  echo "  $0 authority/owner-viewer ./rally26-preview.apk"
  exit 2
fi

SUITE="$1"
APK="$2"
MODE="${3:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="$SCRIPT_DIR/tests/$SUITE"
DEVICE_FILE="${TEST_DEVICES_FILE:-$SCRIPT_DIR/test-devices.txt}"
SAFE_SUITE="${SUITE//\//_}"
CRED_DIR="${QA_CREDENTIAL_DIR:-$SCRIPT_DIR/.credentials}"
USERNAME_FILE="$CRED_DIR/$SAFE_SUITE.username"
PASSWORD_FILE="$CRED_DIR/$SAFE_SUITE.password"

: "${FIREBASE_APP_ID:?Set FIREBASE_APP_ID to the Firebase Android App ID.}"

if [[ ! -d "$TEST_DIR" ]]; then
  echo "No test suite directory: $TEST_DIR" >&2
  exit 2
fi

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 2
fi

if [[ ! -f "$DEVICE_FILE" ]]; then
  echo "Device file missing: $DEVICE_FILE" >&2
  echo "Copy test-devices.txt.example to test-devices.txt and verify current device models." >&2
  exit 2
fi

if [[ ! -f "$USERNAME_FILE" || ! -f "$PASSWORD_FILE" ]]; then
  echo "Credential files missing for suite '$SUITE'." >&2
  echo "Expected: $USERNAME_FILE and $PASSWORD_FILE" >&2
  exit 2
fi

USERNAME="$(tr -d '\r\n' < "$USERNAME_FILE")"

ARGS=(
  apptesting:execute
  "--app=$FIREBASE_APP_ID"
  "--test-dir=$TEST_DIR"
  "--test-devices-file=$DEVICE_FILE"
  "--test-username=$USERNAME"
  "--test-password-file=$PASSWORD_FILE"
)

if [[ "$MODE" == "--smoke" ]]; then
  ARGS+=('--test-name-pattern=^\[SMOKE\]')
fi

firebase "${ARGS[@]}" "$APK"
