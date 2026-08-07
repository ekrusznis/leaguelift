# ADR-077: Phase 25.4 SafeSport policy gate and guardian communication restrictions

Status: Accepted for implementation — external SafeSport/legal approval is **not** claimed.

## Decision

Rally26 will not equate the existence of reporting tools with SafeSport approval. Every organization receives a durable `message_safe_sport_policy` row that defaults to `PENDING` and `athlete_messaging_enabled=false`. The hard technical rules—guardian visibility, open/transparent adult-minor communication, preservation of messaging evidence, and enforcement of guardian discontinue-contact requests—cannot be turned off per organization.

A Rally26 platform administrator may record an `APPROVED` or `REJECTED` review with a durable reference. Athlete messaging may be enabled only when the recorded review is `APPROVED`; database constraints enforce that relationship. This is a release gate, not a representation that Rally26 itself provides legal certification.

Current 2025 MAAPP guidance requires adult-to-minor electronic communication to be open and transparent and requires organizations/adult participants to honor a parent/guardian request to discontinue electronic communication absent an emergency. Rally26 implements the stricter product default of automatic guardian read visibility for its coach-athlete threads and records guardian restrictions as retained safety history.

## Retention

No automatic message or moderation-history deletion job is introduced. `PRESERVE_NO_AUTOMATIC_DELETION` remains the only supported retention mode until the separate legal/data-retention decision is finalized. This is intentionally safer than inventing an unsupported deletion period.

## Guardian restriction types

- `ADULT_TO_MINOR`: staff-originated messages to the athlete are blocked/filtered.
- `ALL_MESSAGING`: also blocks athlete-created peer messaging once that feature is technically available.

Lifting a restriction records who lifted it, when, and why; the original row is never deleted.
