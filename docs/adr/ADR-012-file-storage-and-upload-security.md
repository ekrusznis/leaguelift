# ADR-012: File Storage and Upload Security

## Status
Accepted

## Context

Phase 1's "file upload/branding" slice (`DESIGN-DOC.md` section 11, organization
logo/cover image) was the last code-side blocker on the Pilot Organization Launch gate
(section 14.4). No media pipeline existed at all — no tables, no backend module, no
upload UI; `frontend/src/dashboard/demoAssets.ts` and its consuming components
(`Avatar`, `TeamLogo`, `CardBackground`, `ProductThumbnail`) were a deliberately
throwaway demo-only static layer, already shaped to match the real target API per
section 11.1/11.2 so this slice wouldn't require component rewrites.

The first real slice, built 2026-07-28, covers organization logo + cover image only —
team/tournament logos are an intentional fast-follow reusing the same tables and
endpoints. Explicitly deferred out of this slice: multiple derived image variants per
asset, malware scanning, EXIF stripping, and SVG sanitization beyond MIME/magic-byte
checks. No async worker exists yet in this codebase (the outbox consumer is Phase 8,
unbuilt), so this slice's validation is synchronous.

## Decision

1. **S3-compatible object storage behind one seam.** MinIO for local dev/test (via
   `compose.yaml`), DigitalOcean Spaces for staging/prod — same S3 API, so
   `media/infra/SpacesClient.kt` (backed by AWS SDK v2, itself wrapping `S3Client`/
   `S3Presigner`) never changes between environments, only `leaguelift.spaces.*`
   endpoint/credentials config does. Path-style access is used everywhere since both
   providers support it identically.
2. **Signed-URL upload pattern.** The browser requests a presigned PUT URL and uploads
   directly to object storage; the Spring API never proxies file bytes (section 11.3).
3. **Reads are always backend-issued signed GET URLs, never public-read ACLs**, even
   for assignments whose computed `visibility` is `PUBLIC`. Section 11.3 states
   "originals always private" as a hard rule with no exception, and this slice
   produces no derived "variant" object to safely front with a CDN. Tradeoff:
   no long-lived cacheable URL until variant generation ships; acceptable at pilot
   scale (a 15-minute-lived signed URL refetched per page load).
4. **Synchronous validation inside the confirm-upload request**, not an async worker.
   Acceptable at pilot-scale file sizes (≤15MB) and keeps this slice independent of
   Phase 8 (notifications/outbox-worker infrastructure, not yet built). Downside: a
   slower confirm request (bounded by file size plus one `GetObject` round trip) and no
   automatic retry on a transient storage failure beyond the client re-calling confirm.
5. **Defense-in-depth content-type validation.** A presigned PUT's `Content-Type`
   binding only stops a *header* mismatch — a client can still send bytes that don't
   match what it declared. `MediaUploadService.confirmUpload` re-derives the real
   type via magic-byte sniffing (`UploadLimits.detectContentType`) and an `ImageIO`
   decode/dimension check, and rejects on any mismatch, corrupt image data, or an
   over-10,000px dimension.
6. **No versioning within one `media_asset`.** Re-uploading a logo/cover creates a new
   asset row; the assignment swaps to point at it; the prior asset is marked
   `ARCHIVED` (its object is left in storage, not deleted — flagged as a known gap for
   a future ops cleanup job, not built now).
7. **`media_variant` table exists in the `V9__media.sql` schema but nothing writes to
   it yet** — created now so a future variant-generation writer needs no additional
   migration.
8. **Deferred, not blocked**: multi-variant resizing, malware scanning, EXIF stripping,
   deep SVG sanitization (SVGs are text-sniffed for a top-level `<svg` tag and size-capped
   at 2MB, nothing more).

## Consequences

- Local development requires no external account or real credentials — `docker compose
  up -d postgres minio minio-init` plus running the backend on the host is sufficient to
  exercise the full upload pipeline end-to-end (verified manually against the real local
  stack: request → PUT → confirm → assign → signed GET returned the byte-identical file).
- Moving to real DigitalOcean Spaces in staging/prod is a configuration change only
  (`leaguelift.spaces.*` env vars), not a code change.
- Every public-facing image read costs one backend round trip to mint a fresh signed
  URL — acceptable now, but should be revisited (via variant generation + a public CDN
  prefix for published variants) before public page traffic makes that meaningfully
  expensive.
- Rejected/orphaned objects are not cleaned up from storage automatically — low risk at
  pilot scale (private bucket, inert bytes) but should get a scheduled cleanup job
  before this matters for storage cost.
- Team/tournament logo uploads, real-time content moderation (`APPROVED` publication
  status), and async processing all reuse this same schema/pattern when their
  milestones begin — no re-architecture expected.

## Alternatives Considered

- **Proxy uploads through the Spring API** — rejected: memory/bandwidth cost per
  upload, and contradicts section 11.3's explicit signed-URL requirement.
- **Public-read bucket ACL for logos** — rejected: contradicts the "originals always
  private" hard rule in section 11.3; the correct place for a public, CDN-fronted
  artifact is a published *variant*, not the original.
- **Async processing via the outbox worker** — rejected for this slice: no worker
  exists yet (Phase 8 is unbuilt), and synchronous processing is simpler and
  sufficient at this file-size/volume scale. `media.asset.ready` and
  `media.assignment.published` outbox events are still written in the same
  transaction as the state change, so a future Phase 8 consumer can pick this up
  without a data-model change.
