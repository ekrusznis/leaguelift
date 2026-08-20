# Role / Authority / Mobile Feature Matrix

Legend:

- **FULL** — may perform the feature in the stated scope
- **READ** — read/inspect only
- **SCOPED** — allowed only for assigned team/household/self context
- **RSVP** — event response only
- **DENY** — should not be usable
- **WEB** — current mobile entry intentionally uses authenticated web surface
- **GAP** — expected product capability has no current mobile implementation
- **VERIFY** — backend has mixed service-level checks; AI QA + backend tests should confirm

> Backend authorization remains the source of truth. Mobile visibility is a UX/security
> expectation, not a replacement for server checks.

| Feature family | OWNER | ADMINISTRATOR | VIEWER | TEAM_MANAGER | TEAM_EDITOR | COACH_READ | Parent/Guardian | Athlete | Platform Admin | Current Android note |
|---|---|---|---|---|---|---|---|---|---|---|
| Personal settings/help | FULL | FULL | FULL | FULL | FULL | FULL | FULL | FULL | FULL | Native for customer personas; platform lands boundary |
| Org dashboard | FULL | FULL | READ | DENY | DENY | DENY | DENY | DENY | Platform tools | Owner-style mobile route maps OWNER/ADMIN/VIEWER |
| Org reports | READ | READ | READ | DENY | DENY | DENY | DENY | DENY | Platform payments/audit views | Native Owner Reports |
| Members manage | FULL | FULL | DENY | DENY | DENY | DENY | DENY | DENY | Platform user/org tooling | Native Owner Members |
| Teams create/edit/archive | FULL | FULL | DENY | SCOPED team mgmt | SCOPED page edit | READ | DENY | own-team READ only | Platform org tools | Owner create/edit currently via WEB; coach scoped |
| Team roster manage | FULL | FULL | DENY | FULL scoped | DENY | READ | DENY | DENY teammate roster | Platform support | Coach roster native; owner inherits team-manager authority |
| Team staff manage | FULL | FULL | DENY | FULL scoped | DENY | DENY | DENY | DENY | Platform support | Native team Staff entry from Coach roster |
| Event read | FULL | FULL | scoped/read | FULL scoped | FULL scoped | READ scoped | SCOPED | SCOPED | Platform/support | Native |
| Event create/update/publish | FULL | FULL | DENY | FULL scoped | FULL scoped | DENY | DENY | DENY | Platform/support | Native event form |
| Event cancel | FULL | FULL | DENY | FULL scoped | DENY | DENY | DENY | DENY | Platform/support | Verify editor cannot cancel |
| RSVP | team/admin view | team/admin view | limited | team RSVP read | team RSVP read | DENY write | RSVP linked athlete | RSVP self | support | Native Event Details |
| Team communications | FULL | FULL | DENY | FULL scoped | FULL scoped | DENY | participant/guardian threads | SafeSport scoped | Platform support | Native messages; owner broadcasts native |
| Announcements manage | FULL | FULL | DENY | scoped when capability allows | scoped | DENY | receive | receive | Platform support | Owner manage native |
| Fees / financial ops | FULL/VERIFY | VERIFY | READ/VERIFY | fee READ scoped | DENY | DENY | own household | DENY | platform payments view | Owner native, Parent native/read + web |
| Household fee pay | owner/admin inherited | owner/admin inherited | backend currently grants fee pay in org-household fallback: review | DENY | DENY | DENY | FULL own household | DENY | platform view | High-value negative QA area |
| Family credits | FULL | FULL | DENY | DENY | DENY | DENY | FULL own household | DENY | platform view | Parent currently WEB for some flows |
| Payout management | FULL | DENY | DENY | DENY | DENY | DENY | DENY | DENY | platform payments view | Owner mobile Payout screen is primarily read-only |
| Fundraiser create | FULL | VERIFY | DENY | SCOPED | SCOPED | SCOPED create | SCOPED create | DENY | platform/support | Native fundraising |
| Fundraiser approve/activate policy | FULL | DENY | DENY | DENY | DENY | DENY | DENY | DENY | platform/support | Explicit OWNER capability |
| Swag Shop buyer order | FULL | FULL | DENY/VERIFY | SCOPED | SCOPED | DENY | own household | order READ only | platform view | Coach/Parent buyer flows; Owner management |
| Sponsorship management | FULL | VERIFY | DENY | DENY | DENY | DENY | DENY | DENY | platform/support | Native owner entry |
| Documents | FULL | FULL | DENY/VERIFY | eligibility status only | eligibility status only | eligibility status only | own household | restricted own eligibility status | platform/support | Native owner/parent; athlete eligibility native |
| Eligibility rules manage | FULL | FULL | DENY | status READ scoped | status READ scoped | status READ scoped | submit/e-sign own household | own status | platform/support | Native eligibility surfaces |
| Integrations | FULL | FULL/VERIFY | DENY | DENY | DENY | DENY | DENY | DENY | platform integration manage | Starter plan gate for SportsEngine/TeamSnap/QuickBooks |
| Subscription onboarding | FULL | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | Current main has global owner gating |
| Platform org/user/support/audit | DENY | DENY | DENY | DENY | DENY | DENY | DENY | DENY | FULL | Android customer app currently GAP |
