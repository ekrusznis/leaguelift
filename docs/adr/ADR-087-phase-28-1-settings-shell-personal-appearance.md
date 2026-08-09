# ADR-087 — Phase 28.1 Settings shell and typed personal appearance preference

**Status:** Accepted  
**Date:** 2026-08-08

## Context

Rally26 has configuration spread across identity, organization, billing, integrations, history, messaging, events, commerce, and other domain-owned modules. Phase 28 needs one discoverable Settings entry point without creating a generic settings blob or bypassing those modules' authorization rules.

The first new personal preference required by the UI is appearance.

## Decision

Slice 28.1 introduces:

- `/app/settings` for every authenticated persona;
- a typed `user_preference` table keyed one-to-one to `app_user`;
- one stable column in this slice: `appearance`, constrained to `SYSTEM`, `LIGHT`, or `DARK`;
- `SYSTEM` as the effective default without eagerly inserting a row for every existing user;
- `GET /api/v1/me/preferences` and `PATCH /api/v1/me/preferences`;
- authenticated-route appearance synchronization so the preference applies to the dashboard and routed app surfaces;
- System mode that listens to the device's current `prefers-color-scheme`;
- account information shown read-only in this slice, with identity/profile mutations continuing through their existing verified/correction workflows;
- Settings quick links to History, Integrations, and Help rather than copying those domains.

## Boundaries

- No unbounded JSON or key/value settings bucket.
- No organization setting migration in 28.1.
- No notification preference rows in 28.1; Slice 28.2 owns a separate typed notification model.
- No personal timezone preference; organization/team/tournament/event timezone authority is unchanged.
- No Settings control may broaden Phase 25 messaging safety, Phase 27 history visibility, organization capabilities, or Platform Admin support access.
- Appearance is presentation state only and does not create an audit event.

## Data model

`V66__user_preferences.sql` creates `user_preference(user_id PK/FK, appearance, created_at, updated_at)`.

The row is sparse by design: users with no row resolve to `SYSTEM`; the first explicit update inserts/upserts the row.

## Consequences

This establishes the Settings shell and typed-preference pattern that 28.2 can extend with a separate `user_notification_preference` model. Organization settings remain domain-owned and are consolidated in 28.3.
