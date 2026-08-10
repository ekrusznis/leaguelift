# ADR-095: Phase 29.4 — QuickBooks Truthful Activation Readiness

## Status
Accepted

## Context

Phase 19 created an organization-owned QuickBooks OAuth/provider scaffold and Phase 29.1-29.3 added transport contracts, owner-customizable accounting mappings, posting intents, deterministic provider-operation identity, and fail-closed retry/readback seams.

A generic integration connection row, saved QuickBooks realm/company metadata, or successful fixture/stub read is not sufficient evidence that Rally26 has verified real Intuit credentials, completed sandbox testing, obtained accounting approval, or approved provider writes. Presenting any of those weaker states as "connected", "healthy", or activation-ready would be misleading and could cause a later phase to bypass required accounting and provider controls.

## Decision

QuickBooks activation readiness is modeled as a separate gate-based policy owned by the QuickBooks domain.

The readiness stages are:

- `NOT_CONFIGURED`
- `SCAFFOLDED`
- `COMPANY_CONTEXT_REQUIRED`
- `MAPPINGS_REQUIRED`
- `MAPPING_REVALIDATION_REQUIRED`
- `CREDENTIALS_REQUIRED`
- `SANDBOX_VERIFICATION_REQUIRED`
- `ACCOUNTING_APPROVAL_REQUIRED`
- `WRITE_POLICY_APPROVAL_REQUIRED`
- `ACTIVATION_READY`
- `ACTIVE`

The underlying gates separately record:

1. a Rally26 integration connection record;
2. QuickBooks company/realm context;
3. complete accounting-role mappings;
4. a latest successful mapping revalidation;
5. verified real Intuit credentials/scopes;
6. completed end-to-end sandbox verification;
7. explicit owner/accountant accounting approval;
8. explicit approval of a versioned provider-write policy; and
9. provider-write enablement.

V71 adds durable future evidence fields for credential verification, sandbox verification, accounting approval, and versioned write-policy approval. Phase 29 exposes no endpoint or service action that can populate those future approval fields.

Mapping validation is the one readiness evidence item Phase 29 may update. Any provider account refresh or company refresh invalidates the previous mapping-validation result until the owner/admin explicitly revalidates or creates a new export preview.

`providerWritesEnabled` remains false. Therefore Phase 29 cannot reach `ACTIVE`, and even a hypothetically pre-populated future approval record would stop at `ACTIVATION_READY` until a separately approved activation phase changes the write policy.

The owner/admin QuickBooks Integrations UI must display this QuickBooks-specific readiness model instead of presenting the generic integration-connection status as accounting activation health.

## Consequences

- Saved realm/company metadata remains useful setup context without becoming a false health signal.
- Future activation work gets durable, auditable prerequisites without redesigning the QuickBooks setting model.
- Mapping changes or refreshed provider account data cannot rely on a stale validation result.
- No live Intuit credential is required by Phase 29 tests.
- No QuickBooks provider write can be activated by this slice.

## Alternatives Considered

### Reuse the generic integration connection status
Rejected because `CONNECTED`/`DEGRADED` describes the generic connection lifecycle and does not prove QuickBooks credential verification, accounting approval, sandbox verification, or write approval.

### Infer readiness from realm/company metadata
Rejected because metadata may be stale, fixture-derived, or left over from an earlier authorization and cannot establish provider health.

### Add approval endpoints during Phase 29
Rejected because credential, sandbox, accounting, and write-policy approvals belong to a later explicit credentialed activation phase.
