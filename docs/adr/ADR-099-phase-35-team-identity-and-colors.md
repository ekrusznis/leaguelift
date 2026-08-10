# ADR-099 — Phase 35 structured team identity and team colors

**Status:** Accepted
**Date:** 2026-08-10

## Context

`Team` had only a free-text `name` and `sport`, with a unique constraint on `(organization_id, name)` (V3) — a club naming both its 10U and 12U squads "Pirates" could not create the second team. Age/gender/level distinctions lived entirely inside an opaque name string with no way to query or filter by them.

Separately, `Team` had no color concept at all, despite clubs identifying visually by color as much as by name. This phase was pulled ahead of Phase 31 (Athlete Eligibility) in actual execution order per the founder's execution-order note in `DESIGN-DOC.md` §14.1 — see [[rally26-phase-execution-reorder]].

## Decision

**Structured identity** (V74 migration): added `age_group`/`level` as organization-defined free text (naming conventions genuinely differ by sport/region — no hardcoded global list) and `gender_category` as a real closed-set enum (`BOYS`, `GIRLS`, `COED`, `MENS`, `WOMENS`, `OPEN`). Widened the identity constraint from `(organization_id, name)` to `(organization_id, sport, age_group, gender_category, level, name)`. No backfill needed — no migration seeds team rows and existing rows get null for all three, a safe degenerate case of the widened constraint.

**Team colors** (same migration): added nullable `primary_color`/`secondary_color` (`char(7)` hex, checked against `^#[0-9A-Fa-f]{6}$`). Null means "inherit Rally26's default brand color" (Deep Navy `#0B1F33` / Victory Green `#20B26B`) — the same convention `timezone_override` already uses, resolved via `Team.resolvedPrimaryColor`/`resolvedSecondaryColor` computed properties rather than a repository-level lookup chain (unlike timezone, there's no org-level color to fall back through — just the team's own value or the hardcoded brand default). New `PATCH /organizations/{organizationId}/teams/{teamId}/colors` endpoint mirrors the existing timezone-override endpoint's explicit-set-not-coalesce semantics: `null` actually clears back to the default, distinct from "leave unchanged."

**Call-site scope decision:** `SeasonRolloverService` (duplicating a team into a new season) and `OnboardingImportService` (CSV team import) both gained three new required constructor parameters from the `TeamService`/`TeamRepository` signature change. Both now pass `null` for age group/gender/level rather than threading real values through — season rollover doesn't currently expose the source team's identity fields to copy, and CSV import has no matching columns. Carrying these through is a reasonable future enhancement but was not built speculatively in this phase.

**Retired file-import preview scope note is unrelated** — no change to that decision here; this ADR only touches `Team`.

**Live color preview:** per founder direction mid-implementation, `TeamColorsPanel.tsx` (new component, toggled from `TeamList.tsx` alongside the existing Branding/Timezone buttons) includes a live-rendered preview mockup — a stylized header bar and event card using the currently-edited (not-yet-saved) colors — so an owner/coach can see the effect before committing, rather than only a swatch preview.

**Public surface wiring:** the Swag Shop storefront (`GET /public/stores/{slug}`) and the public team page (`GET /public/pages/{slug}`, `PageType.TEAM`) both now resolve and return the team's colors, consumed by `PublicStoreView.tsx`/`PublicPageView.tsx` as `--team-color-1`/`--team-color-2` CSS custom properties. The spec referenced "public event pages"; no dedicated public/unauthenticated event-RSVP page exists in the frontend today (RSVP lives only in the authenticated `EventDetailPage.tsx`) — the public team page (`PublicPageView.tsx`) is the closest existing concept and is what this phase wires colors into instead of inventing a new public route.

## Consequences

- A club can now create multiple teams sharing a sport and even a display name, distinguished by age group/gender/level — the same-name collision is fixed.
- A team with no colors set renders using Rally26's default brand colors everywhere colors are consumed; setting one or both colors overrides only what's explicitly set.
- `SeasonRolloverService`/`OnboardingImportService` don't yet carry identity fields through their team-creation paths — flagged as a real, scoped-out follow-up, not silently dropped.
- The `TeamRepository.findNameMatches` CSV-import duplicate-name heuristic (used only by `OnboardingImportService`) queries by name alone with no age/gender/level filtering — now that two teams can legitimately share a name, this heuristic may over-flag as a possible duplicate. Not fixed this phase; a pre-existing heuristic, not a hard blocker.
- Backend `BUILD SUCCESSFUL`, frontend typecheck clean, frontend suite at its confirmed pre-existing baseline with no regressions.
