# Rally26 P1 Launch-Readiness Task: Messaging / Chat Consumer-Grade UX

Review and improve Rally26 messaging/chat UI/UX across MOBILE and WEB as a P1 launch-readiness task.

## Important
- Start by reading the repo-root `LAUNCH-READINESS.md` and follow its rules.
- Do not mark this work PASS based only on code review or automated tests.
- You must run the application, exercise the messaging flows live, fix issues, and live-retest the original flows.
- Do not redesign unrelated areas of Rally26.
- Preserve all existing messaging authorization, guardian visibility, youth-safety, SafeSport, moderation, and audit behavior.
- Do not weaken backend permissions just to make the UI easier.
- Do not blindly copy GameChanger, TeamSnap, GroupMe, iMessage, etc. Use familiar consumer-chat interaction patterns while retaining Rally26 branding and product identity.

## Goal

Messaging should feel like a modern consumer messaging product embedded inside a sports-management platform.

The usability bar is:
- GameChanger
- TeamSnap
- GroupMe
- familiar iOS/Android messaging patterns

A coach or parent opening Messages should immediately understand:
1. which conversations are active,
2. who sent the latest message,
3. which messages are unread,
4. when the latest message arrived,
5. what team/group/person the conversation represents,
6. how to start a permitted new conversation,
7. how to reply,
8. whether a message failed/sent successfully.

The current Rally26 implementation is functional but visually behaves more like a SaaS list of message records than a first-class chat product.

---

## First: Audit the Current Implementation

Inspect at minimum:

### Mobile
- `mobile/src/features/messaging/MessagesListScreen.tsx`
- `mobile/src/app/messages/[threadId].tsx`
- `mobile/src/app/athlete/new-conversation.tsx`
- `mobile/src/features/messaging/api.ts`
- `mobile/src/features/messaging/types.ts`
- `mobile/src/app/(tabs)/messages.tsx`
- `mobile/src/app/parent/(tabs)/messages.tsx`
- `mobile/src/app/athlete/(tabs)/messages.tsx`
- `mobile/src/components/app-tabs.tsx`
- `mobile/src/components/parent-tabs.tsx`
- `mobile/src/components/athlete-tabs.tsx`
- `mobile/src/components/screen-header.tsx`
- `mobile/src/constants/theme.ts`

### Web
- `frontend/src/features/messaging/MessagesPage.tsx`
- `frontend/src/features/messaging/api.ts`
- `frontend/src/features/messaging/AthleteMessagingComposer.tsx`
- `frontend/src/features/messaging/MessageSafetyPanel.tsx`
- any related messaging components/routes/styles

### Backend / Product Rules
Review the messaging DTOs/controllers/services and existing ADRs before changing behavior:
- ADR-074 broadcast messaging foundation
- ADR-075 coach-family two-way messaging
- ADR-076 messaging safety/moderation
- ADR-078 athlete messaging activation
- ADR-079 messaging closeout
- ADR-102 mobile backend integration
- ADR-103 parent persona
- ADR-104 athlete persona
- any later messaging ADRs

Identify:
- what UX improvements are mobile/web-only,
- what data is already returned by the API,
- what small backend additions may be justified,
- what would require a larger product decision and should NOT be silently implemented.

---

## Messaging Inbox Redesign

Redesign the primary Messages list to feel like an actual chat inbox rather than a collection of cards.

Target interaction pattern:

```text
MESSAGES                                      Compose

[ Search conversations... ]

 All     Unread     Teams     Direct

------------------------------------------------
[avatar] 12U National                    4:26 PM
         Coach Sarah
         Practice moved to Court 3...          3
------------------------------------------------
[avatar] Jason Miller                    2:14 PM
         Sounds good, we'll be there.
------------------------------------------------
[icon]   Club Announcements              Monday
         Fall registration opens tomorrow
------------------------------------------------
```

### 1. Conversation Rows
- Prefer clean full-width rows + subtle separators over large independent cards.
- Add appropriate visual identity:
  - team avatar/logo if available,
  - participant initials/avatar if available,
  - announcement/broadcast icon,
  - group icon as fallback.
- Show thread/group/team/person name prominently.
- Show latest sender when useful.
- Show latest message preview.
- Show latest-message timestamp on the right.
- Strong but tasteful unread treatment:
  - bold title/preview,
  - unread count or dot,
  - do not let the badge overpower the row.
- Keep Rally26 orange as an accent, not as a huge visual block.

### 2. Search
Add conversation search if it can be done safely and efficiently.

Search should at least cover:
- thread title,
- team/scope name,
- participant/group name where data exists.

### 3. Filters
Evaluate lightweight filters:
- All
- Unread
- Teams / Groups
- Direct

Do not expose filters that cannot be reliably derived from the current thread model.

### 4. Sorting
- Newest activity first.
- Use `lastMessageAt` when available.
- Handle threads with no messages gracefully.

### 5. Compose
Provide an obvious compose/new-conversation action for personas that are allowed to initiate messaging.

Respect persona permissions.

---

## Thread / Conversation Redesign

Target:

```text
<   [avatar] 12U National                       Info
             23 members

                    TODAY

Sarah M. • Coach
[ Practice moved to Court 3 tonight. ]
4:26 PM

                         [ Got it, thanks! ]
                                    4:27 PM

[ + ]  Message 12U National...                  Send
```

### 1. Thread Header
Show appropriate context:
- thread title,
- team/group/person,
- optional member/recipient count where available,
- thread type where useful.

Avoid internal technical terminology such as:
- `BROADCAST`
- `ATHLETE_CONVERSATION`
- `SELECTED`

Translate those to normal user-facing terms.

### 2. Message Bubbles
Keep left/right alignment.

Improve:
- spacing,
- widths,
- typography,
- consecutive-message grouping,
- sender labeling,
- timestamps.

Do not repeat the sender name unnecessarily on every consecutive message.

### 3. Date Separators
Add:
- TODAY
- YESTERDAY
- AUG 18
etc.

### 4. Message States
Determine what currently exists for:
- sending,
- success,
- failure,
- retry.

At minimum:
- prevent duplicate sends,
- provide useful pending feedback,
- visibly handle failed sends,
- allow retry where practical.

Tournament/gym connectivity can be poor, so failed-message behavior matters.

### 5. Composer
Make the composer feel like modern chat:
- persistent bottom composer,
- multiline input,
- correct keyboard avoidance,
- send enabled/disabled states,
- proper safe-area behavior,
- appropriate placeholder using the thread identity if available.

Evaluate attachment/media affordance ONLY if backend support already exists or can be safely implemented within this task.

Do not create a large new media subsystem during launch hardening.

### 6. Read-Only Threads
If `canReply=false`:
- clearly explain why the user cannot reply,
- do not simply remove the composer with no context.

Examples might include:
- "Announcements only"
- another accurate user-facing explanation based on the actual permission.

---

## Persona Review

Test messaging independently as:

1. Organization owner/admin
2. Coach/team staff
3. Parent/guardian
4. Athlete

Document exactly what each role can:
- see,
- initiate,
- reply to,
- receive,
- search,
- access.

---

## Important Product Question: Parent-Initiated Messaging

The current product appears to prevent parents from initiating conversations.

Do NOT silently change this authorization model.

Instead:
1. determine exactly how current parent/coach-family messaging works,
2. determine whether parent -> coach/staff initiation is already supported by the backend but missing in mobile/web,
3. if not supported, create a clearly documented product recommendation.

The desired product direction is likely:

### Owner / Admin
- team/group conversations
- direct conversations
- broadcasts/announcements

### Coach / Staff
- team conversations
- direct adult/family conversations
- permitted groups
- broadcasts where authorized

### Parent
- team conversations
- direct messaging with authorized coaches/staff/adults
- limited group creation if appropriate
- no unrestricted youth communication

### Athlete
- only explicitly eligible/safe contacts and teams
- preserve guardian restrictions
- preserve organization messaging approval gates
- preserve all youth-safety controls

If supporting parent -> coach initiation requires a meaningful backend/security change, STOP and document it as a proposed follow-up rather than expanding scope automatically.

---

## Web Experience

Do not simply stretch the mobile UI across desktop.

Web should use the same messaging mental model but may use a desktop layout such as:

```text
---------------------------------------------------------
| Conversations          | Active Conversation           |
|                        |                               |
| Search                 | 12U National                  |
| All Unread Teams DM    | ---------------------------   |
|                        | messages...                   |
| 12U National           |                               |
| Jason Miller           |                               |
| Club Announcements     |                               |
|                        | [ composer ]                  |
---------------------------------------------------------
```

Evaluate whether a responsive two-pane experience is appropriate on desktop/tablet.

Requirements:
- conversation selection should feel immediate,
- avoid unnecessary page navigation,
- preserve usable narrow/mobile web behavior,
- unread state must stay consistent with mobile.

---

## Real-Time / Refresh Behavior

Audit how new messages appear.

Determine whether current messaging uses:
- polling,
- query invalidation,
- push notification refresh,
- WebSocket/SSE,
- manual refresh only.

Do not introduce a major realtime architecture unless clearly justified.

However, messaging must not feel stale.

At minimum verify:
- sending updates the thread immediately,
- thread preview updates,
- unread counts update,
- opening the conversation marks messages read,
- returning to inbox reflects that change,
- received push/deep-link navigation opens the proper thread,
- manual/refetch behavior works.

---

## Data / API Review

Current mobile thread models already appear to include:
- `threadType`
- `title`
- `scopeName`
- `unreadCount`
- `lastMessageAt`
- `lastMessagePreview`
- `recipientCount`
- `messageCount`
- `canReply`
- `senderDisplayName` on messages

Use existing data first.

If UX needs additional lightweight fields such as:
- `lastSenderDisplayName`
- participant display data
- avatar/team logo reference

evaluate whether adding those server-side improves efficiency instead of making N+1 client calls.

Avoid frontend hacks and N+1 API requests.

---

## Accessibility

Verify:
- minimum touch target sizes,
- readable contrast,
- Dynamic Type / font scaling where supported,
- VoiceOver/TalkBack labels for compose/send/unread/actions,
- keyboard navigation on web,
- focus states,
- screen-reader-friendly unread semantics.

---

## Performance

Test inboxes with:
- 0 conversations
- 1 conversation
- 20 conversations
- 100+ conversations
- long titles
- long previews
- high unread counts
- mixed team/direct/broadcast thread types

Use FlatList/virtualized rendering correctly on mobile.

Avoid expensive per-row queries.

---

## Live QA

This task is NOT complete until live-tested.

Run:
- backend
- frontend
- mobile

Use realistic seeded/test accounts for all four personas.

Exercise at minimum:

### Inbox
- empty state
- loaded state
- unread conversations
- read conversations
- search
- filters if implemented
- ordering
- long content
- dark mode
- light mode

### Thread
- open conversation
- send message
- receive/refresh message
- mark read
- multi-message grouping
- keyboard behavior
- failed-send behavior
- read-only thread
- navigation back to inbox

### Personas
- owner/admin
- coach
- parent
- athlete

### Platforms
- desktop web
- narrow/mobile web
- physical or representative Android build
- physical or representative iOS build if available

Pay special attention to:
- keyboard covering composer,
- safe-area overlap,
- bottom tabs,
- scroll positioning,
- Android back behavior,
- long messages,
- failed network,
- duplicate send,
- push/deep-link entry into a thread.

---

## Tests

Add/update appropriate tests for:
- conversation sorting,
- unread rendering,
- filter behavior,
- sender/preview display,
- date grouping,
- message grouping,
- composer states,
- permissions/read-only behavior,
- relevant API transformations.

Do not over-rely on snapshots.

---

## Readiness Document

Update `LAUNCH-READINESS.md`.

Add a P1 item titled similar to:

`Messaging / Chat Consumer-Grade UX`

Include explicit PASS criteria covering:
- modern conversation inbox,
- latest sender/message/time,
- unread state,
- search/filter behavior where implemented,
- conversation grouping/date separators,
- polished composer,
- failure/retry behavior,
- persona authorization,
- parent messaging decision documented,
- responsive web experience,
- Android/iOS keyboard/safe-area validation,
- live browser/device testing.

Record every issue found using the readiness document's existing finding/status format.

Do not mark PASS until live verification is complete.

---

## Scope Control

Prioritize high-value UX improvements.

### P1 / Implement Now
- inbox redesign
- thread polish
- timestamps
- unread behavior
- date/message grouping
- composer polish
- responsive web messaging
- search if straightforward
- useful filters if straightforward
- error/send states
- persona/read-only clarity
- accessibility
- live QA

### Defer Unless Already Supported and Trivial
- GIFs
- stickers
- reactions
- typing indicators
- read receipts per individual
- voice messages
- video calls
- disappearing messages
- advanced presence
- complex attachments/media systems
- major realtime backend rewrite

Do NOT turn this into a P2/P3 feature expansion.

---

## Deliverable

When finished, provide:

1. Summary of the original UX problems.
2. Exact files changed.
3. Mobile improvements made.
4. Web improvements made.
5. Backend/API changes, if any.
6. Persona/permission findings.
7. Explicit recommendation on parent -> coach/staff conversation initiation.
8. Tests added/updated.
9. Live QA performed, including devices/browsers/personas.
10. Remaining P1 blockers.
11. Deferred P2/P3 enhancements.
12. `LAUNCH-READINESS.md` section/status updated.
13. Final GO / NO-GO assessment specifically for Messaging UX.

## Success Criterion

A new Rally26 user familiar with GameChanger, TeamSnap, GroupMe, iMessage, or similar apps should open Messages and immediately know how to use it without training, while Rally26 retains stronger youth-safety and organization controls.
