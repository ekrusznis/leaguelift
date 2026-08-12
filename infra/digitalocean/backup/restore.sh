#!/usr/bin/env bash
# Downloads a Rally26 Postgres backup from DO Spaces and restores it into a target
# database. See docs/BACKUP-RESTORE-RUNBOOK.md for the full procedure — this script
# is deliberately NOT wired into any automatic/scheduled job (unlike backup.sh's own
# container), and refuses to run against anything that looks like the real prod
# database unless RESTORE_CONFIRM_PROD=yes is explicitly set, because a restore
# overwrites the target database's public schema.
#
# Usage:
#   BACKUP_KEY=backups/rally26-20260812T040000Z.sql.gz \
#   RESTORE_HOST=localhost RESTORE_PORT=5434 RESTORE_DB=rally26 \
#   RESTORE_USER=rally26 RESTORE_PASSWORD=... \
#   SPACES_ENDPOINT=... SPACES_ACCESS_KEY=... SPACES_SECRET_KEY=... SPACES_BUCKET=... SPACES_REGION=... \
#   ./restore.sh
#
# Omit BACKUP_KEY to restore the most recent backup in the bucket instead of a specific one.
set -euo pipefail

RESTORE_HOST="${RESTORE_HOST:?RESTORE_HOST must be set — point this at a scratch instance, never prod directly}"
RESTORE_PORT="${RESTORE_PORT:-5432}"
RESTORE_DB="${RESTORE_DB:?RESTORE_DB must be set}"
RESTORE_USER="${RESTORE_USER:?RESTORE_USER must be set}"
RESTORE_PASSWORD="${RESTORE_PASSWORD:?RESTORE_PASSWORD must be set}"

if [ "${RESTORE_HOST}" = "postgres" ] || [[ "${RESTORE_HOST}" == *"rally26-prod"* ]]; then
  if [ "${RESTORE_CONFIRM_PROD:-}" != "yes" ]; then
    echo "[restore] RESTORE_HOST (${RESTORE_HOST}) looks like the production database." >&2
    echo "[restore] Refusing to run — this would overwrite it. If you really mean to" >&2
    echo "[restore] restore onto prod (you almost never do — restore into a scratch" >&2
    echo "[restore] instance instead, per docs/BACKUP-RESTORE-RUNBOOK.md), set" >&2
    echo "[restore] RESTORE_CONFIRM_PROD=yes explicitly and re-run." >&2
    exit 1
  fi
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

if [ -z "${BACKUP_KEY:-}" ]; then
  echo "[restore] BACKUP_KEY not set — finding the most recent backup in s3://${SPACES_BUCKET}/backups/"
  BACKUP_KEY="backups/$(
    AWS_ACCESS_KEY_ID="${SPACES_ACCESS_KEY}" AWS_SECRET_ACCESS_KEY="${SPACES_SECRET_KEY}" \
      aws s3 ls "s3://${SPACES_BUCKET}/backups/" --endpoint-url "${SPACES_ENDPOINT}" --region "${SPACES_REGION}" \
      | awk '{print $4}' | sort | tail -1
  )"
  if [ "${BACKUP_KEY}" = "backups/" ]; then
    echo "[restore] No backups found in s3://${SPACES_BUCKET}/backups/" >&2
    exit 1
  fi
fi

DUMP_FILE="${WORKDIR}/$(basename "${BACKUP_KEY}")"
echo "[restore] downloading s3://${SPACES_BUCKET}/${BACKUP_KEY}"
AWS_ACCESS_KEY_ID="${SPACES_ACCESS_KEY}" AWS_SECRET_ACCESS_KEY="${SPACES_SECRET_KEY}" \
  aws s3 cp "s3://${SPACES_BUCKET}/${BACKUP_KEY}" "${DUMP_FILE}" \
  --endpoint-url "${SPACES_ENDPOINT}" --region "${SPACES_REGION}"

echo "[restore] restoring into ${RESTORE_USER}@${RESTORE_HOST}:${RESTORE_PORT}/${RESTORE_DB}"
gunzip -c "${DUMP_FILE}" | PGPASSWORD="${RESTORE_PASSWORD}" psql \
  -h "${RESTORE_HOST}" -p "${RESTORE_PORT}" -U "${RESTORE_USER}" -d "${RESTORE_DB}" \
  -v ON_ERROR_STOP=1 \
  > "${WORKDIR}/restore-output.log" 2>&1 \
  || { echo "[restore] restore failed — see output below" >&2; cat "${WORKDIR}/restore-output.log" >&2; exit 1; }

if grep -qi "^ERROR" "${WORKDIR}/restore-output.log"; then
  echo "[restore] restore completed but errors were logged — review before trusting this restore:" >&2
  grep -i "^ERROR" "${WORKDIR}/restore-output.log" >&2
  exit 1
fi

echo "[restore] $(date -u) — restore of ${BACKUP_KEY} into ${RESTORE_DB} completed with no errors"
echo "[restore] next step: run the app (or 'flyway validate') against this database to confirm schema integrity — see docs/BACKUP-RESTORE-RUNBOOK.md"
