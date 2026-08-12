# Rally26 Phase 38 — AI Highlights Planning & Technical Specification

**Status:** Projected / planning-only. No code in this phase. Source product brief: `docs/FEATURE-AI-HIGHLIGHT-REELS.md` (founder-authored vision doc, 2026-08-11) — this document translates that vision into a Rally26-grounded technical plan: real data model, real reuse of existing infrastructure, real cost estimates, and real phase-boundary decisions.
**Depends on:** nothing blocking — can start independently of Phase 36 (native mobile) and Phase 37 (gap closeout), though it will consume backend/frontend/mobile capacity those phases also need.
**Feeds:** Phase 39 (reserved — AI Highlights MVP implementation), and further reserved phases for AI-assisted tagging, athlete detection, and CrowdSync (see §38.7).

## Purpose

Phase 38 does not ship any AI Highlights code. It produces the implementation-ready architecture, data model, privacy model, cost model, and phased rollout plan so the subsequent implementation phase can build without inventing storage architecture, consent semantics, or vendor cost assumptions as it goes — the same discipline Phase 33 applied to native mobile.

The central technical fact this plan is built around: **Rally26 has no video infrastructure today.** The existing `media/` module (`MediaAsset`/`MediaAssignment`/`SpacesClient`) is image-only, synchronous, single-request, and caps uploads at 15MB — a deliberate, documented design ("no async worker exists yet, and pilot-scale file sizes make synchronous validation acceptable"). Highlight video breaks every one of those assumptions. This plan treats video as new infrastructure that *reuses proven Rally26 patterns* (S3-compatible storage via `SpacesClient`, the `Visibility`/`GuardianRelationship` access model, the outbox event-on-commit pattern) rather than reusing the image pipeline's actual code, which cannot carry video's size or async-processing needs.

## Core architecture decisions

### Storage: extend `SpacesClient`, don't replace it

Same DigitalOcean Spaces/MinIO bucket infrastructure (ADR-012), new key prefix (`organizations/{organizationId}/highlights/{videoAssetId}/...`) alongside the existing `organizations/{organizationId}/media/...` prefix. `SpacesClient` needs three new capabilities it doesn't have today:

- **Multipart/resumable presigned upload** — `presignedPutUrl` today issues one presigned PUT for one object; a 200MB+ mobile upload over a stadium wifi connection needs S3 multipart upload (`createMultipartUpload` + per-part presigned URLs + `completeMultipartUpload`), so a dropped connection resumes the failed part instead of restarting the whole file.
- **Longer presigned URL TTLs for uploads** — the existing 15-minute default is sized for small images; large video uploads need a longer, explicitly-configured TTL (`SpacesClient.presignedPutUrl`'s `ttl` parameter already supports this per-call, so this is a call-site decision, not new plumbing).
- **A signed CDN-fronted playback URL**, distinct from `presignedGetUrl` — highlight playback should go through a CDN (DigitalOcean Spaces CDN or CloudFront in front of the bucket), not direct-from-bucket presigned GETs, given expected repeat-view traffic on a popular clip.

### Data model — new tables, not new `MediaAsset` usage slots

`MediaAsset`/`MediaAssignment` stay exactly as they are (image/document-only, synchronous). Video gets parallel tables that mirror the same *shape* (asset row → status lifecycle → entity-linking) but with fields video actually needs:

```sql
-- VideoAsset: the uploaded file itself (mirrors MediaAsset's role)
create table video_asset (
    id                  uuid primary key,
    organization_id     uuid not null references organization(id),
    uploaded_by_user_id uuid not null references app_user(id),
    event_id            uuid references event(id),          -- nullable: "this game" (Phase 1 MVP requires this)
    team_id             uuid references team(id),            -- nullable: denormalized from event for query convenience
    storage_key         text not null,
    original_file_name  text not null,
    declared_content_type text not null,
    byte_size           bigint,
    duration_seconds     numeric,                             -- populated after transcode probe, null until then
    status               text not null,                        -- PENDING_UPLOAD, UPLOADED, TRANSCODING, READY, FAILED, REJECTED, ARCHIVED
    rejection_reason     text,
    playback_key_720p    text,                                 -- populated by transcode job
    thumbnail_key        text,                                 -- populated by transcode job
    visibility           text not null,                        -- reuses media.domain.Visibility values
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);

-- ClipTag: manually- or AI-suggested tag on a time range within a VideoAsset (Phase 1 MVP: manual only)
create table video_clip_tag (
    id                uuid primary key,
    video_asset_id    uuid not null references video_asset(id),
    participant_id    uuid references participant(id),        -- nullable: team-level tag with no specific athlete
    play_type         text,                                     -- free text initially, e.g. "kill", "goal" — see §38.3 on sport-specific vocabularies
    start_seconds     numeric not null,
    end_seconds       numeric not null,
    source            text not null,                            -- MANUAL, AI_SUGGESTED
    confirmed_by_user_id uuid references app_user(id),          -- null until a human confirms an AI_SUGGESTED tag
    ai_confidence     numeric,                                   -- null for MANUAL
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

-- HighlightReel: a generated/curated output (Phase 1 MVP: simple clip-concatenation only)
create table highlight_reel (
    id                uuid primary key,
    organization_id   uuid not null references organization(id),
    owner_type        text not null,                            -- PARTICIPANT, TEAM, ORGANIZATION
    owner_id          uuid not null,
    title             text not null,
    format             text not null,                            -- PLAYER, TEAM, SOCIAL, RECRUITING (mirrors the source doc's reel types)
    source_clip_tag_ids uuid[] not null,                        -- ordered list of video_clip_tag.id
    visibility        text not null,
    export_status     text not null,                             -- PENDING, RENDERING, READY, FAILED
    output_key        text,
    created_by_user_id uuid not null references app_user(id),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
```

This is a Phase 39 migration, not Phase 38 — reproduced here so the implementation phase inherits a real starting schema instead of designing one from scratch. `event_id` being required (not nullable) for the MVP's `VideoAsset` is a deliberate scope-narrowing decision: it makes "which game" and "which team's roster" unambiguous for free (via `Event.teamId` → `participant_team`), matching how `resolveFamilyRecipients`/`AnnouncementRepository` already resolve team rosters elsewhere in this codebase. A future CrowdSync phase (§38.7) is what would need event-less, cross-upload matching — explicitly deferred.

### Processing: new dedicated worker, not the outbox worker

`OutboxWorker` is a 5-second poll loop that runs each handler synchronously inline on the scheduler thread with no timeout/cancellation/progress concept — the research pass confirmed this is unsuitable for a multi-minute transcode job. The **pattern** stays consistent (write an event in the same transaction as the state change, process it asynchronously, track status on the row itself using the same `PENDING_UPLOAD → ...→ READY/FAILED` shape `MediaAsset` already established), but the **mechanism** needs a dedicated video-processing worker:

- `VideoAsset.status = UPLOADED` triggers a job (queued via the outbox pattern — `video.asset.uploaded` — but consumed by a new dedicated worker, not `OutboxWorker`'s handler map).
- That worker calls an external transcoding/AI vendor (see §38.4 for vendor evaluation and cost), polls or receives a webhook for job completion, and updates `VideoAsset.status`/`playbackKey720p`/`thumbnailKey`/`durationSeconds`.
- No ffmpeg or media-processing library exists anywhere in this codebase today (confirmed by dependency/import search) — Phase 39 either integrates a managed transcoding API (recommended — no new infra to operate) or stands up self-hosted transcoding (recommended against for the MVP: operational burden the founder hasn't asked for, and this codebase has zero prior investment in media-processing infrastructure to build on).

### Privacy & visibility — reuse `Visibility` and `GuardianRelationship`, don't invent a new consent model

The source doc's "Privacy and Youth Safety Requirements" section (Private/Team/Club/Public visibility levels, guardian control over identification/sharing/download/deletion) maps directly onto infrastructure that already exists and is already tested:

- **`media.domain.Visibility`** (`PUBLIC, AUTHENTICATED, ORGANIZATION_PRIVATE, TEAM_PRIVATE, HOUSEHOLD_PRIVATE, SELF_PRIVATE, PLATFORM_PRIVATE`) is reused as-is on `VideoAsset`/`HighlightReel` — this is exactly the "who can see this" mechanism the source doc asks for, already built, already tested (`MediaEntityAccessService`).
- **`GuardianRelationship`/`AuthorizationService.hasGuardianRelationship`** is exactly the "who can act on behalf of this minor" mechanism already used for a `PARTICIPANT`'s private profile photo and eligibility documents — a highlight video tagging a minor participant reuses this unchanged, not a new permission model.
- **Public-sharing consent** (the source doc's "Public sharing should require explicit permission") is the one genuinely new piece needed. Recommendation: a lightweight `video_public_sharing_consent` record per `(organizationId, participantId)` — guardian-granted, revocable, audited via the existing `AuditService` (same pattern as every other consent-adjacent action in this codebase) — **not** the full Phase 31 eligibility/e-sign machinery, which is built for legally-binding waivers and is disproportionate for "can this clip of my kid go on the internet." If the founder decides public highlight sharing needs the same legal weight as a liability waiver, Phase 31's `EligibilityRequirement`/`EligibilityEvidence` pattern is the model to escalate to — flagged as an open founder decision, not resolved here.
- **Facial recognition stays explicitly out of scope for every phase this document schedules** (§38.7) — the source doc already correctly treats it as legally sensitive; jersey-number/uniform-based identification (manual tagging first, OCR-assisted later) is the only identification method scheduled.

### Cost model (see §38.4 for full vendor evaluation)

Every number below is a planning estimate to be re-verified against real vendor pricing at Phase 39 kickoff (pricing changes; do not treat these as commitments):

| Operation | Rough cost driver | Illustrative estimate |
|---|---|---|
| Storage | DigitalOcean Spaces, $5/mo per 250GB + $0.01/GB CDN transfer | A 90-second 1080p clip ≈ 150-250MB; 1,000 clips/month ≈ 200GB/mo storage growth |
| Transcoding (managed, e.g. AWS MediaConvert-class pricing) | Per output-minute, resolution-dependent | Roughly $0.01-0.03 per output minute at 1080p — a 90-second clip ≈ $0.02-0.05 |
| AI tagging/detection (Phase 40+, not MVP) | Per input-minute of video analyzed (label/object detection class of API) | Roughly $0.10-0.12 per input minute — meaningfully more expensive than transcoding itself, and the main reason AI-assisted tagging is scoped to a later, cost-validated phase rather than bundled into the MVP |

This is the load-bearing reason §38.7 splits "AI Assist" out of the MVP even though the source doc's own MVP definition includes it — AI inference cost per minute is roughly 3-10x transcoding cost, and no real vendor contract or volume-pricing exists yet to make a founder cost commitment against.

## Phase 38 planning slices

### 38.0 — Data model, storage, and processing architecture (this document's core; formalize as migration + ADR at Phase 39 kickoff)

Deliverables: the `video_asset`/`video_clip_tag`/`highlight_reel` schema above, reviewed and adjusted; `SpacesClient` multipart-upload extension design; vendor selection for transcoding (see §38.4); dedicated video-processing worker design (queue mechanism, retry/backoff, status reporting back onto `VideoAsset`).

### 38.1 — Privacy, consent, and youth-safety review

Formalize the `Visibility`/`GuardianRelationship` reuse above into a real authorization design mirroring `MediaEntityAccessService`, plus the new public-sharing-consent record. Must explicitly answer: can a coach/team-staff member upload video of a minor without guardian upload-time consent (recommendation: yes, mirroring how team staff can already upload a participant's profile photo/documents today under `MediaEntityAccessService`'s existing manager-upload path — visibility defaults to `TEAM_PRIVATE`, not public, until a guardian affirmatively elevates it); what happens to `VideoAsset`/`HighlightReel` rows when a guardian requests data deletion (must integrate with any existing account/data-deletion procedure — flagged as needing cross-reference against Rally26's current deletion story, which this research pass did not audit); whether an athlete (not just a guardian) can ever independently manage their own highlight visibility, and at what age/role (mirrors the existing self-vs-guardian pattern already resolved for RSVP/messaging elsewhere in this codebase — reuse that precedent, don't re-litigate it).

### 38.2 — Upload & processing pipeline specification

Full upload flow spec (multipart presigned upload initiation → client-side chunked PUT → completion → `UPLOADED` status → async transcode trigger → `TRANSCODING` → `READY`/`FAILED`), covering: mobile background-upload behavior (a 200MB upload must survive the app being backgrounded — a real native capability gap versus the current synchronous web-only image pipeline), retry/resume UX for a failed/interrupted upload, and a hard per-organization or per-plan storage/upload-count quota (the source doc explicitly asks for "enforce upload and processing limits based on subscription tier if necessary" — recommend yes, gated by `SubscriptionPlan.code`, mirroring how `contactOnly` plans already gate self-serve checkout).

### 38.3 — Tagging & reel-generation UX (web + mobile)

Page-by-page specification (reusing the Phase 33 screen-spec template — purpose, personas, phone/tablet layout, loading/empty/error states, permissions) for:

- **Upload flow**: attach a clip to an `Event`, select participant(s) (from that event's team roster, via `participant_team`), select a play-type tag (start with a small fixed vocabulary per sport, matching the source doc's volleyball/soccer/basketball/baseball/football lists — these become `video_clip_tag.play_type` free-text values initially, formalized into a real enum once a second sport proves the vocabulary is stable).
- **Team/player media library**: filterable by event/team/participant/play-type, mirroring existing list-page patterns (`TeamRosterPage`-style filter chips, per Phase 37.1 precedent).
- **Reel builder**: select clips (manually, from the tagged library — no AI ranking in the MVP), reorder, choose format (Player/Team/Social/Recruiting per the source doc), trigger `HighlightReel` render job, view `PENDING`/`RENDERING`/`READY`/`FAILED` status.
- **Player Highlight Profile** (source doc's own concept) — deferred past MVP; the MVP's team/player media library filter view covers the same need without a dedicated profile surface.

### 38.4 — AI vendor evaluation (blocks any AI-assisted phase, not the MVP)

Before any phase beyond the MVP starts, evaluate and select: a transcoding vendor (managed API vs. self-hosted — recommend managed for the MVP regardless, since transcoding itself is required even without AI), a video-labeling/action-recognition vendor for jersey-number OCR and play detection (the source doc's own Phase 3/4), and real production-volume cost projections against actual Rally26 usage (not the illustrative estimates in this document). This slice's output is a vendor-selection ADR with real quoted pricing, not a build.

### 38.5 — Monetization & tier gating

Formalize the source doc's "Rally26 Highlights+" concept against the real `SubscriptionPlan`/`OrganizationSubscription` model (`planCode`-based, not a fixed tier enum — see research above). Must decide: is basic highlight upload/tagging included in every existing plan, or does it require a new `SubscriptionPlan` row; are AI-assisted features (once built) a separate parent-level add-on (as the source doc suggests) or bundled into organization-level plan tiers; how upload/storage quotas map to plan codes (feeds §38.2's quota design).

### 38.6 — Viral/marketing loop & sponsor features

The source doc's public-share branding loop and "Moment of the Match" sponsor-voting concept are real product ideas but have zero technical dependencies blocking the MVP — scope as a fast-follow once public sharing (§38.1's consent model) and basic reel generation (§38.3) both exist. Not further specified in this planning pass; revisit once the MVP ships and real usage data exists to validate demand.

### 38.7 — Phase sequencing and explicit gates

Mapping the source doc's own internal Phase 1-6 structure onto real, separately-gated Rally26 phase numbers — **deliberately more conservative than the source doc's own MVP definition**, because this planning pass found no existing AI vendor relationship, no cost validation, and no video infrastructure of any kind to build the source doc's full MVP (which includes "AI Assist" tag suggestion) against:

| Rally26 phase (reserved) | Scope | Gate to start |
|---|---|---|
| **Phase 39** | Smart Media Library — manual-only. Upload, associate to an `Event`, manual participant/play-type tagging, team/player media library, manual-clip-selection reel builder (Player/Team/Social/Recruiting formats), sharing with the `Visibility`/consent model from §38.1. **No AI of any kind.** | This planning phase's founder approval. This is the true MVP — buildable today with zero new vendor relationships beyond a transcoding API. |
| **Phase 40 (reserved, unscoped)** | AI-assisted tagging (source doc Phase 2) + athlete/jersey detection (source doc Phase 3) | A selected, cost-validated AI vendor (§38.4) and real usage data from Phase 39 showing manual tagging volume that justifies the added per-minute inference cost. |
| **Phase 41 (reserved, unscoped)** | Automatic play recognition (source doc Phase 4) | Phase 40 shipped and proven accurate enough to be useful, not just technically functional. |
| **Phase 42 (reserved, unscoped)** | CrowdSync — multi-angle synchronization (source doc Phase 5) | Its own founder go/no-go as a major R&D investment, independent of Phases 40-41's success — this is explicitly not a natural next step after AI tagging, it's a different, harder problem (audio/visual fingerprint matching across independently-recorded uploads). |
| **Not scheduled** | Full Game AI (source doc Phase 6), any facial/biometric recognition | No phase number assigned. Revisit only after explicit legal review, per the source doc's own stated requirement. |

## Screen specification standard (reused from Phase 33)

```text
Screen:
Personas:
Source concept (from FEATURE-AI-HIGHLIGHT-REELS.md):
API dependencies:

Phone / compact:
Tablet / wide:

Primary action:
Secondary actions:

Loading:
Empty:
Error/retry:
Upload progress/resume:
Permission denied/revoked:
Destructive confirmation:

Sensitive data (minor identification, visibility level):
Guardian consent state:
Accessibility:
Acceptance tests:
```

## Phase 38 acceptance criteria

1. A real, reviewable data model exists (`video_asset`/`video_clip_tag`/`highlight_reel`) grounded in actual `Event`/`Team`/`Participant` relationships, not invented generic IDs.
2. Storage/upload architecture explicitly accounts for video's size (multipart upload) and processing needs (dedicated async worker, not the outbox worker), with the specific reasons the existing image pipeline can't be reused.
3. Privacy/visibility reuses `Visibility` and `GuardianRelationship` exactly as already proven for participant profile photos/documents; the one genuinely new primitive (public-sharing consent) is named and scoped, not left implicit.
4. AI-assisted features are cost-modeled (even at planning-grade accuracy) before being scheduled into any phase, and are explicitly separated from the manual-only MVP rather than bundled in by default.
5. CrowdSync and Full Game AI are recognized as materially different risk/cost categories from tagging/detection and are gated by their own separate founder decisions, not treated as an inevitable next step.
6. Facial recognition has no phase number and no implementation path in this document.
7. The approved output is a founder-reviewed phase sequence (Phase 39 MVP scope + reserved Phase 40-42 gates), not a single monolithic "build AI Highlights" phase.

## Open questions for founder decision (not resolved by this planning pass)

1. **MVP scope-down**: this document narrows the source doc's own MVP (which included AI-assisted tag suggestion) to manual-only tagging for Phase 39, given no AI vendor/cost validation exists yet. Confirm or override this narrowing.
2. **Transcoding vendor**: managed API (recommended) vs. self-hosted — needs real quotes, not this document's illustrative estimates.
3. **Public-sharing consent weight**: lightweight revocable consent record (recommended) vs. escalating to Phase 31-grade eligibility/e-sign machinery.
4. **Storage/upload quotas by plan**: whether Phase 39 needs quota enforcement at launch or can ship unmetered with quotas added once real usage volume is observed.
5. **Data-deletion integration**: this pass did not audit Rally26's current account/data-deletion procedure closely enough to confirm how `VideoAsset`/`HighlightReel` rows integrate with it — needs a follow-up check before Phase 39 implementation, not before Phase 39 is scoped.
