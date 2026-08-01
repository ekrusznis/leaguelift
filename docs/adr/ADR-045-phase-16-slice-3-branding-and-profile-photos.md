# ADR-045: Phase 16 Slice 3 — Team, Tournament, and Profile Branding

## Status
Accepted

## Context

LeagueLift already has a signed-upload media pipeline for organization branding, product designs, sponsor logos, and documents. Phase 16 requires team/tournament branding plus adult and participant profile photos. Building a second upload or storage system would duplicate validation, object ownership, retirement, visibility, and audit behavior. At the same time, the original media endpoints are organization-manager-only and cannot safely authorize team-scoped staff, guardians, or controlled athlete-self accounts.

## Decision

**1. Reuse the existing media pipeline.** Migration V31 widens the existing check constraints with `TEAM`, `TOURNAMENT`, `HOUSEHOLD_ADULT`, and `PARTICIPANT` entity types plus a raster-only `PROFILE_PHOTO` usage slot. No profile-photo column or separate blob table is introduced.

**2. Introduce one resource-aware authorization boundary.** `MediaEntityAccessService` resolves every polymorphic target through its owning repository before a read or mutation. Team logo/cover management requires `team.page.edit`; tournament logo/cover management requires `tournament.page.edit`. Ordinary reads require the matching view capability. Organization owner/administrator inheritance and Platform Admin support access continue through the existing authorization model.

**3. Keep adult and participant photos private.** Adult and participant assignments use `HOUSEHOLD_PRIVATE`. An organization manager may manage them. A guardian may read adult photos in their linked household but may change only the exact `household_adult` profile linked to their account. A guardian may manage linked participant photos. A controlled athlete-self account may read or update only its own participant photo when it holds the existing profile capability. Profile photos are not exposed through public-page APIs.

**4. Bind uploads to an intended slot and a scoped authorization check.** Upload requests may carry an `entityType` and `entityId`; when supplied, the backend authorizes that target before issuing the signed URL. Confirmation remains available only to the original uploader or an organization manager. Assignment again authorizes the target and rejects an asset whose requested usage slot differs from the destination slot.

**5. Public team/tournament branding follows page publication.** A published organization, team, or tournament page composes its active logo and cover into the public response. Draft or archived pages remain inaccessible through the public endpoint. Profile photos never participate in this composition.

**6. Preserve replacement history.** Replacing a logo, cover, or profile photo retires the prior assignment and archives its prior asset rather than mutating historical assignment rows. Removing media retires the active assignment. All assignment and retirement actions continue through the existing audit path.

## Consequences

- Team managers and tournament administrators can manage branding without receiving organization-wide manager rights.
- Guardians do not gain access to unrelated households or another adult's editable profile.
- The frontend uses the same signed-upload sequence as organization branding and displays safe fallback initials when no asset exists.
- Public-page responses now contain optional `logo` and `cover` descriptors with signed read URLs.
- Crop tooling, derived thumbnails, EXIF stripping, malware scanning, and deep SVG sanitization remain deferred; the existing file-size, magic-byte, decode, and dimension checks still apply.
- Phase 16 remains partial: correction requests, reusable event templates, and season rollover/archive/copy controls remain future slices.

## Alternatives Considered

- **Separate profile-photo table and upload service:** rejected because it duplicates the established media lifecycle and storage security.
- **Organization-manager-only branding:** rejected because team/tournament roles already have scoped page-edit capabilities.
- **Any household guardian may edit every adult photo:** rejected; Phase 15 defines adult profile editing as self-scoped.
- **Make participant photos public with team pages by default:** rejected because youth imagery requires an explicit public-visibility product/privacy decision.
- **Store permanent public object URLs:** rejected; the existing pipeline intentionally uses backend-issued signed read URLs.
