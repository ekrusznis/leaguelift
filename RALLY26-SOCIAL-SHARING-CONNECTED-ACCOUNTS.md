# Rally26 Social Sharing & Connected Accounts — Mobile-First Implementation Brief

## Goal

Build a **mobile-first social sharing system** that lets Rally26 users connect their own social accounts in **Personal Settings** and then share eligible Rally26 content directly from Events, Fundraising, Sponsorships, approved Media, and Swag Shops.

Primary providers:
- Instagram
- Facebook
- X (formerly Twitter)

Core principle:

> **Connect once → find content in Rally26 → tap Share → review → publish.**

This is especially important for mobile. Settings is where accounts are connected; the Share/Promote actions must live inside the features where the content already exists.

---

## 1. Social connections are PER USER

Do not model these as organization-wide credentials by default.

A connected social account belongs to the signed-in Rally26 user.

Examples:
- Club owner connects a Facebook Page / Instagram business account.
- Coach connects their own team-facing social account.
- Parent/guardian connects their own account to share public fundraiser, event, and swag links.
- A user in multiple Rally26 organizations keeps the same personal social connections.

Suggested relationship:

`Rally26User -> SocialConnection[]`

Suggested fields:

```text
id
user_id
provider                  // FACEBOOK, INSTAGRAM, X
provider_user_id
provider_account_id       // page/business/profile target where applicable
display_name
username
avatar_url
account_type
access_token_encrypted
refresh_token_encrypted   // where supported
token_expires_at
scopes
status                    // CONNECTED, EXPIRED, REVOKED, ERROR
last_verified_at
created_at
updated_at
```

Never expose raw provider tokens to the mobile/web client after OAuth completes.

---

## 2. Personal Settings

Rally26 already has the mobile settings screen:

`mobile/src/app/settings.tsx`

The web personal settings surface is:

`frontend/src/features/settings/SettingsPage.tsx`

Add a new section to both:

## Connected Social Accounts

Example:

```text
Connected Social Accounts

Share Rally26 events, fundraisers, approved media,
sponsors and team gear using your connected accounts.

Instagram
@rally26club
Connected                           [Manage]

Facebook
Rally26 Volleyball Club
Connected                           [Manage]

X
Not connected                       [Connect]
```

Each provider row should support:
- Connect
- Reconnect
- Manage
- Disconnect
- connected account/page/profile name
- connection status
- provider-specific setup notes when needed

Connection state follows the user across web and mobile.

---

## 3. OAuth / Connection Flow

Use official provider OAuth flows only.

Do not:
- ask for social passwords
- store provider passwords
- scrape browser sessions
- use unsupported posting automation

Suggested mobile flow:

1. User taps `Connect Instagram`, `Connect Facebook`, or `Connect X`.
2. Mobile asks Rally26 backend for an authorization URL.
3. Open provider auth using secure system-browser/auth-session handling.
4. Provider redirects to a Rally26 callback/deep link.
5. Backend exchanges authorization code for tokens.
6. Backend stores tokens encrypted.
7. Mobile refreshes `/me/social-connections`.
8. Connected account appears in Settings.

Suggested provider abstraction:

```text
SocialProvider
  authorize()
  completeAuthorization()
  refreshConnection()
  disconnect()
  getPublishingTargets()
  publish()
```

Do not tightly couple the domain model to Meta-specific structures.

---

## 4. Direct publishing + native mobile fallback

Provider capabilities vary by platform, account type, permissions, app review, and API policy.

Every share flow must therefore support:

### A. Direct Publish

Use when:
- user has a valid connected account
- Rally26 has the required provider permissions
- provider supports the requested post format

### B. Native Mobile Share

Always provide a fallback using the OS share sheet.

The share sheet can include:
- generated caption
- public Rally26 URL
- approved image/video
- generated Rally26 social card

The user should never hit a dead end because direct API publishing is unavailable.

---

## 5. Where Share / Promote actions must exist

### Events

Existing mobile surface:

`mobile/src/app/event-details.tsx`

Add:

`Share Event`

Draft may use:
- event title
- team/organization
- date/time
- public-safe location
- approved media
- public event URL

Never leak private event details.

### Fundraising

Existing mobile surface includes:

`mobile/src/app/fundraising-detail.tsx`

Add:

`Share Fundraiser`

Use:
- campaign name
- team/organization
- public goal/progress
- deadline
- approved media
- public fundraiser URL

This should be easy for parents/guardians to share.

### Sponsorships

Add:

`Promote Sponsor`

Use:
- sponsor name
- approved sponsor logo
- organization branding
- approved media
- sponsor URL

Future enhancement:

```text
Social promotion requirement
3 of 4 posts completed
```

### Media

Add:

`Share` / `Create Social Post`

ONLY if the media is approved for public/social release.

### Swag Shop

Add:
- `Share Shop`
- `Share Product`

Use:
- product/shop name
- product image
- price
- team/organization
- public shop/product URL

---

## 6. Reusable mobile Share Composer

Create one reusable composer used by Events, Fundraising, Sponsorships, Media, and Swag.

Suggested paths:

```text
mobile/src/app/social-share.tsx
mobile/src/features/social/*
```

Example:

```text
Share

Posting as
[ Instagram @rally26club  v ]

Post
--------------------------------
Tournament weekend is here! 🏐
Come support 12U National...
--------------------------------

Media
[ approved image preview ]   [Change]

Link
rally26.com/...

[ Share another way ]

                         [ Publish ]
```

If the chosen provider cannot publish the format directly:

```text
Direct publishing is unavailable for this post type.

[ Share through Instagram ]
```

Then invoke the native sharing flow.

---

## 7. Generated drafts

Users should not start with a blank composer.

Create a reusable `SocialPostDraft` from existing Rally26 data.

Suggested model:

```text
sourceType
sourceId
organizationId
teamId
title
caption
publicUrl
media[]
allowedProviders[]
```

Initially deterministic templates are fine. AI can be added later.

Example fundraiser:

```text
Help 12U National reach its season goal! 🏐

Every contribution helps support our athletes and their season.

$3,850 of $5,000 raised

Support the team:
{publicUrl}
```

The user must be able to edit the caption before publishing.

---

## 8. Public-safe URLs only

Never share an authenticated/internal Rally26 route externally.

Supported shareable content should use a public-safe destination such as:
- public fundraiser
- public swag shop/product
- public event page where enabled
- sponsor URL/public sponsor page
- public-approved media URL where intended

Explicitly distinguish:

```text
internalUrl
publicUrl
```

Only `publicUrl` may be placed in an external post.

---

## 9. Media release enforcement

This is a hard requirement.

Before media can be used in social sharing:

1. Confirm it is released for public/social use.
2. Confirm required guardian/media permission is valid.
3. Confirm it is not household-only/team-private/restricted.
4. Re-check immediately before direct publish.
5. Reject publishing if approval changed after draft creation.

Example picker:

```text
Approved for Social

✓ Team celebration
✓ Sponsor banner
✓ Club logo

Unavailable

🔒 Athlete photo — release missing
🔒 Household-only media
🔒 Team-private media
```

Enforce this in the backend as well as the client.

---

## 10. Roles and youth safety

For MVP:

- Owner / Administrator: allowed
- Coach / authorized staff: allowed for content they are allowed to promote
- Parent / Guardian: allowed to share public-safe content such as fundraisers, public events, swag, and approved media
- Athlete accounts: **do not enable connected social accounts by default**

Because Rally26 serves minors, athlete social-account connections should remain out of scope until intentionally approved.

Do not infer social publish permission merely because a user can read an object.

Consider explicit capabilities:

```text
SOCIAL_SHARE
SOCIAL_PUBLISH
ORG_SOCIAL_PROMOTE
```

---

## 11. Separate sharing permissions

Keep these concepts separate:

### SHARE_PUBLIC_CONTENT
Allows native sharing of already-public-safe Rally26 content.

### PUBLISH_SOCIAL
Allows direct publishing through a connected provider.

### MANAGE_SOCIAL_CONNECTIONS
Allows the signed-in user to connect/disconnect their personal social accounts.

The connection-management permission is user/account scoped, not organization scoped.

---

## 12. Suggested backend endpoints

Connections:

```text
GET    /api/v1/me/social-connections
POST   /api/v1/me/social-connections/{provider}/authorize
GET    /api/v1/me/social-connections/{provider}/callback
POST   /api/v1/me/social-connections/{id}/refresh
DELETE /api/v1/me/social-connections/{id}
```

Draft/publishing:

```text
POST /api/v1/social/drafts
GET  /api/v1/social/drafts/{id}
POST /api/v1/social/drafts/{id}/publish
GET  /api/v1/me/social-publishing-history
```

Example request:

```json
{
  "sourceType": "FUNDRAISER",
  "sourceId": "uuid"
}
```

Backend returns a permission-checked, public-safe draft.

---

## 13. Token security

Provider credentials are sensitive.

Requirements:
- OAuth only
- encrypt access/refresh tokens at rest
- never log token values
- never return provider tokens to normal clients
- request minimum scopes
- refresh server-side
- revoke/delete tokens on disconnect
- support expired/revoked states cleanly
- audit connect/disconnect/publish actions
- keep provider secrets out of the repo

---

## 14. Publishing history

Suggested record:

```text
id
user_id
organization_id
provider
social_connection_id
source_type
source_id
caption_snapshot
media_ids
public_url
provider_post_id
provider_post_url
status
failure_code
failure_message_safe
published_at
created_at
```

Statuses:

```text
DRAFT
PUBLISHING
PUBLISHED
FAILED
CANCELED
```

Do not duplicate restricted media just to preserve social history.

---

## 15. UX when no account is connected

If a user taps `Share Fundraiser` with no social connection:

```text
Share Fundraiser

Connect an account for direct posting, or share using
another app on your phone.

[ Connect Instagram ]
[ Connect Facebook ]
[ Connect X ]

[ Share another way ]
```

`Share another way` should invoke the native device share sheet immediately.

---

## 16. Multi-account support

Do not assume one provider always equals one destination.

A user may eventually have:
- Facebook Page A
- Facebook Page B
- Instagram account
- X account

The backend model should support multiple publishing targets even if the first UI allows one active target per provider.

---

## 17. Web parity

Mobile is the priority, but connections belong to the Rally26 user.

If a user connects Instagram on mobile, web must see the same connection.

Add `Connected Social Accounts` to the existing web Settings page and reuse the same backend connection/draft/publish APIs.

Do not create separate mobile-only OAuth records.

---

## 18. Recommended delivery order

### Slice 1 — Foundation
- DB migration/model for per-user SocialConnection
- provider abstraction
- `/me/social-connections`
- encrypted token storage
- feature flags/provider availability
- audit events

### Slice 2 — Mobile Settings
- Connected Social Accounts in `mobile/src/app/settings.tsx`
- connect/manage/disconnect states
- OAuth callback/deep-link handling
- expired/revoked UX

### Slice 3 — Native Sharing
- reusable SocialPostDraft
- reusable mobile share composer
- native OS share fallback
- Event share
- Fundraiser share
- Swag share

This provides value even before direct publishing is approved.

### Slice 4 — Media / Safety
- approved-media picker
- release validation
- publish-time revalidation
- sponsor sharing
- publishing history

### Slice 5 — Direct Providers
Enable providers independently after official API/app-review requirements are verified:
1. Facebook / Meta
2. Instagram
3. X

Do not block the whole feature on one provider.

### Slice 6 — Web Parity
- Connected Social Accounts in web Settings
- web share composer/actions
- shared publishing history

---

## 19. Acceptance criteria

- [ ] Social connections are stored per Rally26 user.
- [ ] Mobile Settings lets eligible users connect/manage/disconnect Instagram, Facebook, and X.
- [ ] Web Settings shows the same connection state.
- [ ] OAuth tokens are never persisted as Rally26-managed credentials in mobile/web client storage.
- [ ] Event detail has Share Event.
- [ ] Fundraiser detail has Share Fundraiser.
- [ ] Sponsor content has Promote Sponsor.
- [ ] Swag shop/product has Share.
- [ ] Approved media has Share/Create Social Post.
- [ ] Restricted/unreleased media cannot be shared through Rally26.
- [ ] Composer starts with a useful generated caption.
- [ ] External posts use public-safe URLs only.
- [ ] Native mobile sharing always exists as fallback.
- [ ] Connected accounts are available as direct publishing targets where supported.
- [ ] User authorization is revalidated server-side before publish.
- [ ] Media release permission is revalidated immediately before publish.
- [ ] Expired/revoked connections provide reconnect UX.
- [ ] Publish attempts are audited.
- [ ] Athlete/minor accounts cannot connect social accounts in MVP unless explicitly approved later.
- [ ] Existing feature behavior is unchanged when social sharing is not used.

---

## 20. Non-goals for initial release

Do not build a full social-management suite yet.

Initial version does NOT need:
- social inbox/comments
- follower analytics
- social listening
- competitor monitoring
- long-form content calendar
- automatic posting without user review
- social DMs
- social identity import
- athlete social-account connections

Focus on:

> **Connect once → Share from Rally26 → Review → Publish.**

---

## 21. Product language

Feature name:

**Connected Social Accounts**

Actions:
- Share
- Share Event
- Share Fundraiser
- Promote Sponsor
- Share Shop
- Share Product
- Create Social Post
- Publish
- Share another way

Product message:

> **Your season already creates the content. Rally26 helps you share it.**

---

## Agent instructions before implementation

1. Review existing backend auth/user-preference/OAuth patterns.
2. Review `mobile/src/app/settings.tsx`.
3. Review `frontend/src/features/settings/SettingsPage.tsx`.
4. Review current Event, Fundraising, Sponsorship, Media, and Swag permissions.
5. Reuse existing public-page/public-link patterns where possible.
6. Preserve guardian/media-release safety rules.
7. Implement in small slices with tests.
8. Do not claim direct provider posting is supported until the official API flow has been implemented and verified.
9. Put each provider behind independent configuration/feature flags.
10. Make native mobile sharing useful even if all direct provider integrations are disabled.
