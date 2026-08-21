#!/usr/bin/env bash
# Generates mobile/google-services.json entirely from the 3 Firebase secrets — no
# project ids/keys/bucket names are committed to the repo in any form (not even a
# template). project_number is parsed out of FIREBASE_APP_ID (format
# 1:{project_number}:android:{hash}) and project_id out of FIREBASE_STORAGE_BUCKET
# (format {project_id}.firebasestorage.app), so no extra secrets are needed beyond
# these 3. package_name is the one non-secret constant (already public everywhere
# else in this repo: app.config.ts, App Store/Play Store listings).
#
# Usage: FIREBASE_APP_ID=... FIREBASE_API_KEY=... FIREBASE_STORAGE_BUCKET=... \
#   mobile/scripts/generate-google-services.sh
set -euo pipefail

: "${FIREBASE_APP_ID:?Set FIREBASE_APP_ID (e.g. 1:1083100005024:android:...)}"
: "${FIREBASE_API_KEY:?Set FIREBASE_API_KEY}"
: "${FIREBASE_STORAGE_BUCKET:?Set FIREBASE_STORAGE_BUCKET (e.g. rally26-c3e63.firebasestorage.app)}"

PROJECT_NUMBER="$(echo "$FIREBASE_APP_ID" | cut -d: -f2)"
PROJECT_ID="${FIREBASE_STORAGE_BUCKET%.firebasestorage.app}"
PACKAGE_NAME="com.rally26.mobile"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT="$SCRIPT_DIR/../google-services.json"

cat > "$OUTPUT" <<EOF
{
  "project_info": {
    "project_number": "${PROJECT_NUMBER}",
    "project_id": "${PROJECT_ID}",
    "storage_bucket": "${FIREBASE_STORAGE_BUCKET}"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "${FIREBASE_APP_ID}",
        "android_client_info": {
          "package_name": "${PACKAGE_NAME}"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "${FIREBASE_API_KEY}"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
EOF

echo "Wrote $OUTPUT"
