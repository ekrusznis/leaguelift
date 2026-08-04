#!/usr/bin/env bash
# Dumps the prod Postgres database and uploads it to DO Spaces (S3-compatible).
# Proves backups are *taken* — does not by itself prove they're *restorable*. A real
# quarterly restore rehearsal (DESIGN-DOC.md section 18.3) is a separate, still-open
# launch-gate item; don't treat this script as satisfying it.
set -euo pipefail

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="/tmp/rally26-${TIMESTAMP}.sql.gz"
S3_KEY="backups/rally26-${TIMESTAMP}.sql.gz"

echo "[backup] $(date -u) — dumping ${POSTGRES_DB} from ${POSTGRES_HOST}"
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
  -h "${POSTGRES_HOST}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  | gzip > "${DUMP_FILE}"

echo "[backup] uploading to spaces://${SPACES_BUCKET}/${S3_KEY}"
AWS_ACCESS_KEY_ID="${SPACES_ACCESS_KEY}" \
AWS_SECRET_ACCESS_KEY="${SPACES_SECRET_KEY}" \
aws s3 cp "${DUMP_FILE}" "s3://${SPACES_BUCKET}/${S3_KEY}" \
  --endpoint-url "${SPACES_ENDPOINT}" \
  --region "${SPACES_REGION}"

rm -f "${DUMP_FILE}"

echo "[backup] pruning backups older than ${RETENTION_DAYS:-14} days"
CUTOFF="$(date -u -d "-${RETENTION_DAYS:-14} days" +%Y%m%d 2>/dev/null || date -u +%Y%m%d)"
AWS_ACCESS_KEY_ID="${SPACES_ACCESS_KEY}" \
AWS_SECRET_ACCESS_KEY="${SPACES_SECRET_KEY}" \
aws s3 ls "s3://${SPACES_BUCKET}/backups/" \
  --endpoint-url "${SPACES_ENDPOINT}" --region "${SPACES_REGION}" \
  | awk '{print $4}' \
  | grep -E '^rally26-[0-9]{8}T' \
  | while read -r key; do
      file_date="${key#rally26-}"
      file_date="${file_date%%T*}"
      if [ -n "${file_date}" ] && [ "${file_date}" -lt "${CUTOFF}" ]; then
        echo "[backup] deleting expired backup ${key}"
        AWS_ACCESS_KEY_ID="${SPACES_ACCESS_KEY}" \
        AWS_SECRET_ACCESS_KEY="${SPACES_SECRET_KEY}" \
        aws s3 rm "s3://${SPACES_BUCKET}/backups/${key}" \
          --endpoint-url "${SPACES_ENDPOINT}" --region "${SPACES_REGION}"
      fi
    done

echo "[backup] $(date -u) — done"
