# ADR-098 — Phase 34 sports-data integration catalog narrowing

**Status:** Accepted
**Date:** 2026-08-10

## Context

The Phase 19 SPORTS_DATA provider catalog (ADR-054/055/056) listed SportsEngine, GameChanger, and MaxPreps side by side as `PARTNER_PENDING`/`NOT_CONFIGURED` cards on the Integrations page, implying all three were equally plausible future connections. A 2026-08-10 review of each vendor's actual developer-access model found that isn't true:

- **TeamSnap** publishes a real, self-serve OAuth2 API — an organization admin can authorize Rally26 the same way any OAuth integration works. This is the strongest automated-integration candidate, and it's a catalog dependency Phase 31 (Athlete Eligibility) will need for its `RegistrationEvidenceProvider` adapter.
- **SportsEngine** also has a real OAuth2/GraphQL API, but access is limited to organizations already on SportsEngine, and the platform changed ownership three months before this review — its catalog entry already carries "pending confirmed current product access" language and stays as-is.
- **MaxPreps** has no public developer API and no partner program; the only third-party access is unofficial scraping, which Rally26 does not do.
- **GameChanger** has no public API, no OAuth, and no webhooks — only a manually triggered CSV/PDF export a coach can download. It was never going to be a live "connect" card, only a file-import source, and Rally26's existing CSV event import already covers that path.

This phase was pulled ahead of Phase 31 (Athlete Eligibility) in actual execution order — smaller, self-contained scope compared to Phase 31's new eligibility/waiver subsystem. See `DESIGN-DOC.md` §14.1's execution-order note; phase numbers were intentionally left unchanged, only execution order shifted (founder decision).

## Decision

1. Removed `MAXPREPS` and `GAMECHANGER` from the `IntegrationProvider` enum and the `integration_provider_catalog` table (V73 migration); added `TEAMSNAP` at the same `NOT_CONFIGURED`/`OAUTH_SCAFFOLD` readiness SportsEngine already uses, pending a registered and verified Rally26 developer application.
2. Added `ScaffoldTeamSnapProviderClient`, mirroring the existing `ScaffoldSportsEngineProviderClient` stub pattern, and registered TeamSnap in `DeterministicStubIntegrationAdapter`'s supported OAuth providers.
3. Retired the file-import preview path (`SportsDataService.previewFile`, its `/file-preview` controller endpoint, and the `SportsDataFilePreviewRequest`/`SportsDataExternalRecordRequest` DTOs) — it existed only to serve GameChanger/MaxPreps partner-pending file review, and no SPORTS_DATA provider uses `FILE_IMPORT` mode after this phase. Simplified `SportsDataService.preview()` accordingly (dropped its now-always-`OAUTH` `sourceMode` parameter) rather than leaving an unreachable code path in place.
4. Updated the Integrations page's `SportsDataScaffoldPanel.tsx`: grid narrows from 3 to 2 provider cards, the OAuth-connected-preview eligibility check now covers both SportsEngine and TeamSnap, and the footer copy points to the real GameChanger CSV-export-then-import path instead of describing it as partner-pending.
5. Updated the `gamechanger-maxpreps-imports` Help Center article to describe the real GameChanger CSV path and MaxPreps's lack of any integration path, and added a new `teamsnap-integration-readiness` article modeled on SportsEngine's.
6. Updated `docs/openapi.yaml` (provider enum, endpoint summaries, removed the retired file-preview endpoint/schemas) to match.

**Explicitly out of scope:** the separate, older `EventSourceProvider`/`EventSourceType` enums (`event_source_connection` table, `event.source_type` column — Phase 12, ADR-031) also carry `MAXPREPS`/`GAMECHANGER` values, but they're a distinct legacy subsystem with no connect flow ever wired for either value and no UI card driven by them today. The Phase 34 spec named `integration_provider_catalog`/`IntegrationProvider` specifically, not this system, so it was left untouched rather than cleaned up as an unrequested drive-by change.

## Consequences

- The Integrations page's SPORTS_DATA section now shows exactly two automated/connectable cards — SportsEngine and TeamSnap — both `NOT_CONFIGURED` until Rally26 registers and verifies a developer application for each. This narrows the catalog; it does not activate either provider.
- No card anywhere claims GameChanger or MaxPreps as a connectable provider. GameChanger's one real supported path (CSV export → Rally26's existing CSV event import) is now the only thing the product claims for it.
- Phase 31's `RegistrationEvidenceProvider` adapter work has a real TeamSnap catalog entry to build on top of.
- A future phase that wants the same cleanup applied to `EventSourceProvider`/`EventSourceType` can do so independently — nothing in this phase blocks or requires it.
