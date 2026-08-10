# Phase 29 QuickBooks Closeout Checklist

**Date:** 2026-08-09
**Scope:** Phase 29.1-29.5 QuickBooks scaffolding only
**Activation:** intentionally inactive; provider writes disabled

## Product acceptance

- [ ] 29.1 contract fixtures/tests pass for CompanyInfo, Preferences/home currency, Account query, and Fault envelopes.
- [ ] 29.2 mapping-policy, posting-intent, owner custom-mapping, warning acknowledgement, inactive-account, stale-account, and revalidation tests pass.
- [ ] 29.3 request identity, idempotency, failure classification, retry/readback, and disabled-write tests pass.
- [ ] 29.4 activation-readiness policy tests pass and no Phase 29 API can satisfy future approval gates.
- [ ] 29.5 authorization/audit/privacy closeout tests pass.
- [ ] `providerWritesEnabled` remains false everywhere in the Phase 29 runtime path.
- [ ] Export preview returns `exportAllowed = false` even when mappings are valid.
- [ ] No live Intuit credential is needed for any Phase 29 test.

## Authorization and organization isolation

- [ ] Owner and Administrator can open their own organization's QuickBooks setup.
- [ ] Coach, Guardian, and Athlete cannot manage organization QuickBooks setup without a separate Owner/Administrator grant.
- [ ] A connection ID from Organization B cannot be used under Organization A.
- [ ] Mapping revalidation authorizes the organization/connection pair before querying saved mappings.
- [ ] Export candidate queries and preview history remain organization-scoped.
- [ ] Platform Admin customer access is exercised only through an active, reasoned organization support session and the Platform Admin remains the audit actor.

## Audit and privacy

- [ ] Company read, account read, mapping update, mapping revalidation, and export preview produce the expected organization-scoped audit events.
- [ ] QuickBooks API response DTOs expose no access token, refresh token, client secret, credential ciphertext, or PKCE/code verifier.
- [ ] Audit metadata contains no QuickBooks OAuth credential or chart-of-accounts payload.
- [ ] Logs/errors remain redacted through the existing integration error/sync infrastructure.

## Database and migration

- [ ] Flyway applies V69, V70, V71, and V72 in order on a database that already contains Phase 19 V42.
- [ ] V72 only forward-updates Help Center content; it adds no provider credential, activation flag, or accounting-write capability.
- [ ] Existing QuickBooks preview/history rows remain readable after all Phase 29 migrations.

## API and frontend

- [ ] `docs/openapi.yaml` parses and passes the repository's OpenAPI lint/security checks.
- [ ] QuickBooks owner/admin UI shows activation-readiness gates rather than generic connection status as accounting health.
- [ ] Custom mapping uses the organization's provider Account IDs and preserves deliberate same-account reuse.
- [ ] Refresh/revalidation errors are understandable and no UI control claims that QuickBooks writes are active.
- [ ] No create-in-QuickBooks, activate, sync-now-write, invoice, payment, account-creation, or journal-entry button is present.

## Required local suite

```powershell
cd backend
./gradlew clean test ktlintCheck

cd ../frontend
npm run typecheck
npm test -- --run
npm run lint
npm run build

cd ..
npx --yes @redocly/cli lint docs/openapi.yaml
git ls-files --stage backend/gradlew
```

`backend/gradlew` must remain executable in Git (`100755`).

## Required pull-request / security gates

Do not mark the operational phase complete by weakening or skipping a failing validator. Confirm the real repository checks are green, including:

- backend test/build;
- frontend typecheck/test/lint/build;
- OpenAPI parsing/lint/security validation;
- dependency review / dependency security checks configured for the repository;
- secret scanning / source-integrity checks configured for the repository;
- migration validation;
- protected-main merge requirements.

## Protected-main deployment verification

Phase 29 product code can be complete while operational closeout remains pending. After merge, confirm the normal protected-main production deployment/readiness path succeeds. This verification proves the overlay did not break deployment; it does **not** activate QuickBooks.

After deployment, QuickBooks must still be inactive unless a separately approved future credentialed phase has intentionally changed the policy.
