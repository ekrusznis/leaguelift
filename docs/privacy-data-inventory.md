# Privacy Data Inventory

This inventory is updated with every milestone that introduces or changes a personal
data field, per `DESIGN-DOC.md` section 18.7. It currently reflects the Phase 0
foundation only.

| Field | Table | Purpose | Owner | Retention | Visibility | Export | Deletion | Adult or participant |
|---|---|---|---|---|---|---|---|---|
| `email` | `app_user` | Contact / identity linkage | Platform | Life of account + legal minimum | Self, org admins of shared orgs (future), platform admins | On request (future data-export flow) | On account deletion request, subject to audit-retention needs | Adult |
| `display_name` | `app_user` | Display in UI, audit trails | Platform | Life of account | Self, relevant org members | On request | On account deletion request | Adult |
| `external_subject` | `app_user` | Link to identity-provider account | Platform | Life of account | Not user-facing | Not exported | Removed on account deletion | Adult |
| `name`, `slug` | `organization` | Organization identity / public pages | Organization owner | Life of organization | Public (name/slug), depending on page publish state | Organization export (future) | Organization archival, not hard delete, per section 5 boundaries | N/A (organization, not personal) |
| `actor_user_id` | `audit_event` | Accountability for actions taken | Platform | Retained per audit/compliance needs (not user-deletable) | Platform admins only | Not exported to end users | Not deletable — audit history is immutable by design | Adult |

## Not yet collected

The Phase 0 foundation does not yet collect participant (minor) data, payment data,
household data, or fee/credit data. When those modules are built (Phase 2+), this
table must be updated before the corresponding migration ships, per
`DESIGN-DOC.md` section 5.4 (youth-data boundary) and section 18.7.

## Standing rules

- No medical, educational, behavioral, background-check, or precise-location data may
  be added without an explicit product decision, privacy review, and an update to this
  file (`DESIGN-DOC.md` section 5.4).
- Children do not have independent login accounts; any participant record is owned by
  an adult-controlled household account (Phase 2+).
