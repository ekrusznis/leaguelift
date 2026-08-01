# ADR-046: Phase 16 Slice 4 — Profile Correction Requests

## Status
Accepted

## Context

Phase 15 made guardians and controlled athlete accounts invitation-only and deliberately kept organization-owned participant and household fields outside unrestricted self-service editing. Phase 16 requires a correction-request workflow so a guardian, athlete, or appropriately scoped staff member can report an incorrect official profile field without receiving organization-wide mutation rights. A generic field-name/value endpoint would be unsafe: it could expose new fields accidentally, bypass domain validation, or overwrite a staff change made after the request was submitted.

## Decision

**1. Use an explicit typed allow-list.** V32 adds `profile_correction_request` for `HOUSEHOLD_ADULT` and `PARTICIPANT` targets. Supported fields are adult first name, last name, email, phone, relationship and participant first name, last name, date of birth. Adding any future field requires a migration, enum change, validation, application behavior, tests, OpenAPI, and an ADR update. Notes, status, team assignments, passwords, roles, consent, and financial fields are not accepted through this workflow.

**2. Preserve organization ownership of official fields.** Creating a request never changes the target. Only an organization OWNER/ADMINISTRATOR may approve or reject. Approval calls the existing `HouseholdService`/`ParticipantService` mutation path so organization checks and ordinary audit events still apply; the correction request also records its own immutable lifecycle audit event.

**3. Scope who may request.** A guardian may request a participant correction within a linked household and an adult correction only for the exact `household_adult` linked to their account. A controlled athlete may request an allowed correction for their own participant profile. A team-scoped user must hold `team.roster.manage` for one of the participant's active teams. Organization managers and Platform Admin support access remain allowed through existing boundaries.

**4. Prevent silent overwrite.** The request snapshots the current field value. Approval reloads the target and refuses with a conflict when the value changed after submission. Reviewers must reject or withdraw the stale request and assess the current record. A partial unique index permits only one pending request for a target/field at a time.

**5. Keep a bounded lifecycle.** Status is `PENDING`, `APPROVED`, `REJECTED`, or `WITHDRAWN`. Rejection requires a review note. The requester or an organization manager may withdraw only a pending request. Requests are not deleted, edited, or reopened.

**6. Limit household visibility.** Organization managers can review all organization requests. A linked guardian can see participant requests for their household and adult requests only for their own linked adult profile. The source request contains no password, invitation token, provider credential, financial record, or medical/educational data.

## Consequences

- Guardians and scoped staff can correct data without receiving direct write access.
- Approval is auditable and stale-safe rather than a blind patch.
- The organization UI gains a review queue; the household UI gains request forms and status history.
- Team-assignment changes remain staff-controlled and are not included because “replace, add, or remove assignment” requires a separate typed workflow rather than a string field.
- Direct guardian editing beyond profile photos remains intentionally narrow; future self-editable fields must be decided explicitly.
- Phase 16 remains partial: reusable event templates and season rollover/archive/copy controls remain future slices.

## Alternatives Considered

- **Generic target field/value endpoint:** rejected because future schema fields could become writable without a deliberate authorization decision.
- **Let guardians directly edit all fields:** rejected because team assignments and official identity data are organization-owned.
- **Apply a request even when the record changed:** rejected because it silently overwrites newer staff work.
- **Store only the proposed value:** rejected because a reviewer needs the submitted before/after context and stale-value protection.
- **Delete rejected or withdrawn requests:** rejected because the lifecycle itself is support and audit evidence.
