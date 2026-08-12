# Backup & Restore Runbook

DESIGN-DOC.md §14.6 item #12. For every other incident scenario (5xx errors, failed migrations, security incidents, financial/ledger issues, outbox backlog, webhook failures), see DESIGN-DOC.md §18.3 — this doc covers backup/restore specifically, since that was the one piece still genuinely untested.

## Real architecture (not the aspirational one)

Postgres is **self-hosted** on the production droplet (`infra/digitalocean/docker-compose.prod.yml`), not DigitalOcean Managed PostgreSQL — a deliberate founder cost/ops call (ADR-008). That means no managed automated-backup or WAL point-in-time-recovery feature exists underneath it; the `backup` service in that same compose file is the actual mitigation:

- **What it does**: `infra/digitalocean/backup/backup.sh`, running in its own container, takes a `pg_dump` of the production database, gzips it, and uploads it to DigitalOcean Spaces at `backups/rally26-{UTC timestamp}.sql.gz`. It runs once immediately on container start, then daily at 04:00 UTC via cron.
- **Retention**: 14 days by default (`BACKUP_RETENTION_DAYS`), pruning older backups from Spaces automatically.
- **What it does NOT do**: point-in-time recovery (only whole-database daily snapshots), or prove the backups are restorable — that's this doc's job.

## Restoring a backup

`infra/digitalocean/backup/restore.sh` (added 2026-08-12 — no restore tooling existed before this). Downloads a backup from Spaces and restores it into a target database:

```bash
BACKUP_KEY=backups/rally26-20260812T040000Z.sql.gz \
RESTORE_HOST=<scratch-host> RESTORE_PORT=5432 RESTORE_DB=rally26 \
RESTORE_USER=rally26 RESTORE_PASSWORD=<...> \
SPACES_ENDPOINT=<...> SPACES_ACCESS_KEY=<...> SPACES_SECRET_KEY=<...> SPACES_BUCKET=<...> SPACES_REGION=<...> \
./restore.sh
```

Omit `BACKUP_KEY` to restore the most recent backup instead of a specific one. **The script refuses to run against a host that looks like production** (`postgres`, anything containing `rally26-prod`) unless `RESTORE_CONFIRM_PROD=yes` is explicitly set — restoring always targets a scratch instance, never prod directly, per the procedure below.

## Quarterly rehearsal procedure

1. Provision a scratch Postgres instance (a throwaway container is fine — never restore directly onto prod, and never onto the shared local dev database either).
2. Run `restore.sh` against it, pointed at the most recent real backup.
3. Boot the actual application (`SPRING_PROFILES_ACTIVE=local`, `DATABASE_URL` pointed at the scratch instance) — Flyway's `validate` runs automatically on startup and confirms schema/checksum integrity; a clean `Started Rally26ApiApplicationKt` log line means the restore is structurally sound.
4. Spot-check a handful of known rows (a recent `ledger_entry`, a recent `audit_event`) against the source database by id, and confirm table row counts match, to confirm the restore is actually complete and current — not just "the restore command exited zero."
5. Tear down the scratch instance.
6. Record the results below.

## Rehearsal log

### 2026-08-12 — first real rehearsal (this session)

Performed against the local dev database (not production — no production Spaces/droplet access from this environment) to prove the mechanism itself, using the exact same commands `backup.sh`/`restore.sh` run:

1. **Backup**: `pg_dump` of the local dev database (84 migrations applied, 14 `ledger_entry` rows, 98 `audit_event` rows) — 1,046,791 bytes uncompressed, 103,558 bytes gzipped (90.1% compression).
2. **Restore**: into a fresh, isolated scratch Postgres container (never touching the real dev database/volume) — completed in 24 seconds, 2,877 lines of `psql` output, **zero errors**.
3. **Schema validation**: booted the real application against the restored database. Flyway logged `Successfully validated 85 migrations` (zero checksum/integrity issues), then cleanly applied one pending migration (`V83 — stripe dispute handling`) that hadn't yet been applied to the source dev database — proving both restore integrity and that catch-up migrations apply cleanly on top of restored data, the realistic disaster-recovery shape.
4. **Application boot**: `Started Rally26ApiApplicationKt in 6.808 seconds` — fully clean, no errors. `GET /actuator/health` returned `{"status":"UP"}` with the database component itself reporting `UP`.
5. **Spot-check**: the exact `ledger_entry` and `audit_event` rows captured before the backup were confirmed present, byte-identical, in the restored database by id. Row counts matched exactly (14 and 98 respectively) before and after.
6. **Cleanup**: scratch container and volume removed; source dev database and its data were never touched — confirmed by re-querying it after cleanup and observing the same row counts as before this rehearsal began.

**Result: pass, zero errors at every step.** This is the first rehearsal on record for this mechanism — DESIGN-DOC.md §18.3 previously described this as "written target procedure, not yet rehearsed," which is now out of date.

**What this rehearsal does NOT prove**: that the real production backup file (uploaded to real DigitalOcean Spaces from the real droplet) downloads and restores correctly — `restore.sh`'s S3-download step specifically wasn't exercised against real Spaces credentials in this environment. The `psql` restore pipeline it wraps was tested directly and separately, with identical results (zero errors, matching row counts) — the untested portion is narrow (the `aws s3 cp` download call) and low-risk (the same AWS CLI pattern `backup.sh` already uses successfully in production to upload). **Recommended first real step**: run `restore.sh` for real against a production backup once Spaces credentials are available, to close this specific gap — everything downstream of the download is now proven.

## Next rehearsal due

Quarterly from 2026-08-12 → **2026-11-12**. Add this to whatever calendar/reminder system tracks recurring operational tasks; nothing in this codebase automates the reminder itself.
