# ADR-118 — Phase 37.9: mobile native document upload for eligibility

**Status:** Accepted
**Date:** 2026-08-11

## Context

Phase 31 shipped guardian document upload for `FILE_UPLOAD`-mode eligibility requirements on web only — mobile's `eligibility.tsx` special-cased this mode with a "Document upload isn't available in the app yet" message and, for guardians, a WebView deep-link out to the website. This closes that gap with a real native upload, not another WebView redirect.

## Decision

**Mirrors web's exact four-step contract**, since the backend endpoints are shared and unchanged: `POST /media/uploads` (get a presigned S3-compatible PUT URL) → raw `fetch` PUT straight to storage (bypassing `apiClient`/`apiFetch` entirely — no Authorization header, no JSON, matching `frontend/src/features/media/uploadToSignedUrl.ts`'s documented reasoning that the API never proxies file bytes) → `POST /media/uploads/{assetId}/confirm` → `POST /eligibility/.../evidence` with `acceptanceMethod: 'FILE_UPLOAD'` and the confirmed `documentAssetId`. New `mobile/src/features/media/` module (`types.ts`, `api.ts`, `uploadToSignedUrl.ts`) replicates only the two mutations mobile actually needs (`useRequestMediaUpload`/`useConfirmMediaUpload`) rather than porting web's full org-logo/product-image assignment surface, which mobile has no use for yet.

**Widened the backend's DOCUMENT upload slot to accept photographed images, not just PDF** (`UploadLimits.DOCUMENT_CONTENT_TYPES` now `{application/pdf, image/png, image/jpeg}`) — a real, if small, backend change alongside the mobile build. A guardian is far more likely to photograph a paper form (a physical exam, a birth certificate) with their phone than to already have it saved as a PDF; shipping PDF-only would have meant the "native" upload still routinely failing for the common case. `MediaUploadService`'s validation/dimension-decode pipeline already handled PNG/JPEG generically for other slots, so this was a one-line allowlist change plus a corrected test, not new logic.

**Two picker paths cover the realistic sources**: `expo-image-picker` (camera capture and photo-library selection — the primary path, matching the reasoning above) and `expo-document-picker` (an existing PDF already on the device — the secondary path, for a guardian who already scanned something). Both installed fresh; mobile had zero prior native-upload pattern of any kind (no presigned-URL flow, no picker package) to extend. `app.config.ts` gained an `expo-image-picker` plugin entry with `microphonePermission: false` (a still-photo-only feature has no use for microphone access) and Rally26-specific permission-prompt copy; `expo-document-picker` needs no plugin entry (uses the system file picker, no special permission).

**Client-side size validation before ever hitting the network** (15MB, matching the backend's own `MAX_DOCUMENT_BYTES`) avoids a pointless request-URL round-trip for an upload that will provably fail server-side anyway. Server-side rejection reasons (`FILE_TOO_LARGE`, `UNRECOGNIZED_FILE_FORMAT`, `CONTENT_TYPE_MISMATCH`, `INVALID_IMAGE`, `IMAGE_DIMENSIONS_TOO_LARGE`) are mapped to plain-language toast messages rather than surfaced as raw codes.

**Verification:** backend `UploadLimits`/`MediaUploadService` full suite green (938+ tests) after widening the content-type set; mobile `tsc --noEmit`, `expo lint`, and `expo-doctor` (18/18) all clean.

## Consequences

- A guardian can now complete a `FILE_UPLOAD` eligibility requirement entirely inside the mobile app — the WebView-redirect fallback for this specific case is retired (an athlete-self context with no household still just explains a guardian must do it, unchanged).
- The widened DOCUMENT content-type set also benefits any *other* current or future consumer of the DOCUMENT usage slot (not just eligibility) — a deliberate, low-risk broadening rather than a narrowly-scoped special case.
- `mobile/src/features/media/` is intentionally minimal (two mutations) — if a future mobile feature needs org-level media assignment (logo/cover management, say), that's new scope to add then, not something this slice already built out speculatively.
