# ADR-086 — Phase 28.0 CI/CD and source-integrity stabilization

**Status:** Accepted  
**Date:** 2026-08-08

## Context

Phase 27 reached the merge/deploy path with the application test repair complete, but GitHub Actions exposed repository and source-integrity defects that local incremental overlays had not caught:

1. the Linux runner could not execute `backend/gradlew` because the Git index did not retain the executable bit;
2. Redocly could not parse `docs/openapi.yaml` because `TimezoneSuggestionResponse` was declared twice;
3. the frontend Docker build found `AthleteMessagingComposer` imported but never mounted;
4. the same build found an intentional `OwnerOnboardingPage` route whose shipped page file was missing from the committed tree.

These are deployment-gate defects, not reasons to redesign the product features they surfaced.

## Decision

Phase 28 begins with Slice 28.0 and treats merge/deploy integrity as an explicit prerequisite to new Settings persistence.

- `backend/gradlew` must be stored by Git as executable (`100755`). A Windows working copy must use `git update-index --chmod=+x backend/gradlew`; filesystem permission alone is not authoritative.
- OpenAPI remains a required CI gate. Duplicate YAML mapping keys are repaired at the source; validators are not weakened.
- The richer ADR-071 `TimezoneSuggestionResponse` is the single authoritative schema definition.
- Owner onboarding remains a shipped product flow. Its missing page is restored; the route is not deleted to make TypeScript pass.
- Athlete messaging remains SafeSport/runtime-gated. The composer is mounted and `ATHLETE_CONVERSATION` is treated as a conversation in the inbox rather than silencing the compiler by deleting the feature import.
- CI/deploy failures continue to fail closed. No workflow is allowed to report a successful production deployment until required checks, migrations, and readiness checks succeed.

## Consequences

- Phase 28.1 may introduce V66 only after the Phase 28.0 repairs are present.
- Subsequent Phase 28 slices use the same merge/deploy pipeline as their production gate.
- The deployment pipeline is part of the product's operational correctness, not an end-of-phase cleanup item.
