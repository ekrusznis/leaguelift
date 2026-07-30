# ADR-028: Phase 10 Slice 3 — Calendar and Maps Stubs

## Status
Accepted

## Context

ADR-026/ADR-027 (slices 1-2) built the event data model, staff CRUD, and
RSVP. DESIGN-DOC.md section 14.1A/17 reserve two more provider-shaped seams
for this phase: `CalendarProvider` (standards-based `.ics`/Add-to-Calendar
output, explicitly **not** Google OAuth/calendar-write/two-way sync — that
remains a later, separate decision per section 19.3 #23) and `MapsProvider`
(a provider-neutral directions-link builder from a validated address and
optional coordinates, explicitly required to never leak private
meeting-location notes into public map metadata).

## Decision

**1. Both interfaces live in the `event` module, not `notification/`
alongside `EmailProvider`/`SmsProvider`/`AnalyticsProvider`.** Building an
`.ics` file or a directions URL is pure event-domain formatting — no
external HTTP call, no vendor credential, no account to configure. Unlike
the `notification/` seams (each with a genuine "logging stopgap vs. real
vendor" choice), there's nothing provider-shaped to abstract yet beyond the
interface itself; a future real Google Calendar OAuth integration would
still implement the same `CalendarProvider` interface, just with a second
implementation class.

**2. `MapsProvider`'s only implementation (`GoogleMapsDirectionsProvider`)
is genuinely real, not a logging stopgap — unlike every other provider in
this codebase.** Google's directions-link format
(`https://www.google.com/maps/dir/?api=1&destination=...`) needs no API key,
account, or credential at all; only Google's JS Maps SDK/Places/embed APIs
require one, and this feature uses none of those. Coordinates are preferred
over the address string when both are present (more precise, no geocoding
ambiguity); the destination is standard `URLEncoder`-escaped.

**3. `CalendarProvider`'s only implementation (`IcsCalendarProvider`) emits
every timestamp in UTC (`Z`-suffixed), never a `VTIMEZONE` block for
`Event.timezone`.** An `Instant` is already zone-independent — UTC output is
always correct, and every mainstream calendar client converts it to the
viewer's own local time automatically. A `VTIMEZONE` block would only matter
for a floating (zone-less) time, which this schema never has (`timezone` is
a required, validated field per ADR-026 decision 6). RFC 5545 line-folding
(75-octet limit, continuation lines prefixed with a single space) and text
escaping (backslash/comma/semicolon/newline) are both implemented directly —
no ICS-generation library was added for this.

**4. Neither provider ever reads `Event.meetingPoint`/`Event.directionsNotes`.**
`IcsCalendarProvider`'s `LOCATION`/`DESCRIPTION` fields are built only from
`venueName`/`area`/`address`/`description`; `GoogleMapsDirectionsProvider`
only ever receives `address`/`latitude`/`longitude` as parameters — it has
no way to leak a private note even if a caller wanted it to, since the
method signature doesn't accept those fields at all. This satisfies section
14.1A's "never expose private meeting-location notes in public map
metadata" by construction, not by a runtime check that could be forgotten.

**5. Calendar/directions endpoints reuse `EventService.get`/`listForHousehold`'s
existing read authorization exactly — no new authorization path.**
`getIcsForEvent`/`getDirections` call `get()` first (same
`event.read`/`membershipService.requireActiveMembership` check every other
single-event read already goes through) before touching either provider;
`getIcsForHousehold` calls `listForHousehold()` first (the same
`hasHouseholdCapability` check ADR-027 already established for schedule
viewing). There is no unauthenticated/public calendar or directions
endpoint in this slice — "respecting privacy/publication settings" for a
future public event page is explicitly out of scope until one exists.

**6. `EventController`'s per-event team-name resolution moved into
`EventService.displayTitleFor`, shared by the JSON API and the new
calendar/directions endpoints.** Previously a private controller helper
(`toResponseWithNames`) duplicated the team-name lookups `displayTitle()`
needs; both the existing JSON responses and the new `.ics`
output now compute the exact same title from one shared method, removing
`EventController`'s direct `TeamRepository` dependency entirely.

## Consequences

- No new database migration, no new capability, no new external
  dependency — this slice is pure application-layer code on top of what
  slices 1-2 already built.
- `GoogleMapsDirectionsProvider` hardcodes Google Maps as the destination
  service. If a future org ever needs a different maps provider (Apple
  Maps, OpenStreetMap), `MapsProvider`'s interface already supports swapping
  the implementation with zero call-site changes — the same seam pattern
  every other provider interface in this codebase follows.
- ICS output has no `VALARM` (reminder) component, no `ORGANIZER`/`ATTENDEE`
  fields, and no RSVP round-tripping (a calendar app's own "yes/no/maybe"
  reply doesn't feed back into `event_rsvp`) — none of these were asked for
  by section 14.1A's stub scope, and adding them would be speculative.
- The combined-household `.ics` endpoint is capped at 200 events
  (`ICS_SCHEDULE_LIMIT`) — generous for a real household's realistic event
  volume; revisit only if a real org's usage ever approaches it.
- Directions links are built from whatever `address`/`latitude`/`longitude`
  an event happens to have — there's no address validation/geocoding step
  in this codebase yet, so a malformed or empty address still produces a
  (possibly useless) Google Maps search rather than a hard failure. Address
  validation remains unbuilt across this entire codebase, not just here.

## Alternatives Considered

- **A third-party ICS-generation library (e.g. `biweekly` or `ical4j`)**:
  rejected — RFC 5545's actual output shape here is small and stable
  (`VCALENDAR`/`VEVENT`, a handful of properties, UTC timestamps only); a
  new dependency for ~80 lines of formatting logic isn't proportionate,
  consistent with this codebase's general "plain implementation over a new
  dependency for one narrow need" pattern (Printify/Resend/Twilio all made
  the same call for their own REST clients).
- **A `VTIMEZONE` block using `Event.timezone`, matching what a "proper"
  ICS file often includes**: rejected — see decision 3; UTC timestamps are
  simpler, always correct, and every calendar client already handles the
  conversion.
- **Placing `CalendarProvider`/`MapsProvider` in `notification/` for
  consistency with `EmailProvider`/`SmsProvider`/`AnalyticsProvider`**:
  rejected — see decision 1; those three are generic message-passing seams
  with no domain awareness, while calendar/maps output is intrinsically
  event-domain-aware (needs `Event` fields directly), so the `event` module
  is the more honest home.
- **A public, unauthenticated calendar/directions endpoint for a future
  public event page**: rejected as out of scope — no public event page
  exists yet in this codebase (visibility=PUBLIC events aren't exposed
  anywhere unauthenticated today); building public access now would be
  speculative ahead of that actual feature.
