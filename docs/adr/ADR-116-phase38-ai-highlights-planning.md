# ADR-116 — Phase 38: AI Highlights planning & technical specification

**Status:** Accepted (planning-only; no implementation)
**Date:** 2026-08-11

## Context

The founder authored a product vision brief (`docs/FEATURE-AI-HIGHLIGHT-REELS.md`) for an AI-powered highlight-reel feature — parents/coaches/staff upload game video, Rally26 organizes it into player/team/social/recruiting reels, with a longer-term differentiator ("CrowdSync") that stitches multiple parents' independent recordings of the same play into one multi-angle event. The founder asked for a full planning-phase deliverable, mirroring the treatment Phase 33 gave native mobile — real architecture, not just a scoped-down build list — before returning to finish Phase 37's remaining slices.

## Decision

**Phase 38 (this ADR) is planning-only**, producing `docs/PHASE38-AI-HIGHLIGHTS-PLANNING-PROJECTION.md`. It follows the Phase 33 precedent exactly: numbered planning slices, a reusable page-spec template, explicit acceptance criteria, and — critically — a set of *reserved but unscoped* future phases rather than one large "build AI Highlights" phase.

**The plan is grounded in real Rally26 infrastructure, not invented from the product brief alone.** A research pass into the existing `media/` module found: `MediaAsset`/`MediaAssignment`/`SpacesClient` are image-only, synchronous, single-HTTP-request, capped at 15MB, with the pipeline's own code explicitly documenting that constraint ("no async worker exists yet, and pilot-scale file sizes make synchronous validation acceptable"). Video breaks every one of those assumptions, so the plan calls for **new, parallel tables** (`video_asset`/`video_clip_tag`/`highlight_reel`) rather than stretching `MediaAsset`'s usage-slot enum to cover video — while explicitly reusing what does transfer: `SpacesClient`'s S3-compatible storage (extended with multipart upload, which doesn't exist today), the `Visibility` enum and `GuardianRelationship`-based authorization already proven for participant profile photos/documents (`MediaEntityAccessService`), and the outbox event-on-commit *pattern* (though not `OutboxWorker` itself, which is a synchronous 5-second poll loop unsuitable for multi-minute transcode jobs).

**The MVP is scoped narrower than the source brief's own MVP definition, and this is flagged as an open founder decision, not a silent override.** The brief's MVP includes "AI Assist" (tag/boundary suggestion). This plan's cost-modeling section found AI inference runs roughly 3-10x the per-minute cost of transcoding alone, with no vendor relationship or real pricing validated yet — so Phase 39 (reserved) is scoped to manual-only tagging (upload → associate to a real `Event` → manual participant/play-type tag → team/player library → manual-selection reel builder), buildable today with zero new AI vendor dependency. AI-assisted tagging, athlete/jersey detection, play recognition, and CrowdSync are each their own separately-gated reserved phase (40-42), explicitly not treated as an inevitable next step — CrowdSync in particular is called out as a materially different, harder problem (cross-upload audio/visual fingerprint matching) deserving its own go/no-go independent of whether AI tagging succeeds. Full Game AI and any facial/biometric recognition get no phase number at all, matching the source brief's own instruction to keep facial recognition out of the MVP dependency chain pending legal review.

**Privacy defaults to the most restrictive existing primitive, not a new one.** A coach/staff upload of a minor's video defaults to `TEAM_PRIVATE` visibility (mirroring how a participant's profile photo already works under manager upload today) — a guardian must affirmatively elevate visibility, not the reverse. The one genuinely new primitive this plan introduces is a lightweight, revocable public-sharing consent record — deliberately *not* Phase 31's full eligibility/e-sign machinery, which is built for legally-binding waivers and judged disproportionate for "can this clip go public," though the plan names this as an open question if the founder disagrees.

## Consequences

- No code, migration, or dependency changes ship from this ADR — it is a planning artifact, same as Phase 33 was before Phase 36.
- Phase 39 (reserved) inherits a concrete starting schema and a narrower, more defensible MVP boundary than the source brief alone specified — reducing the risk of the first implementation phase silently taking on unbudgeted AI infrastructure cost.
- Five open questions are recorded explicitly for founder decision before Phase 39 implementation begins (MVP scope-down confirmation, transcoding vendor choice, consent-record weight, launch-time quota enforcement, data-deletion integration) — this plan does not resolve them, it isolates them so Phase 39 doesn't have to rediscover them mid-build.
- Work on Phase 37's remaining slices (37.8-37.12) resumes after this planning pass, per the founder's own sequencing request.
