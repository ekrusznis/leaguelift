# QA Demo Data

`demo-data-import.csv` is a ready-to-use dataset for exercising Rally26 end-to-end
(web + mobile) against the real production backend, using the app's own real features
— it is **not** a database seed/migration. It has zero effect on anything until you
manually upload it through the app.

## What it creates

2 teams, 4 households, 5 guardians, 5 athletes — including one household (Smith) with
two guardians and two athletes on two different teams, to exercise multi-athlete/
multi-team flows on the Parent and Owner sides.

Guardian emails use the `ekrusznis+demo.<name>@gmail.com` pattern (Gmail's `+` alias
trick) so real invitation emails land in your own inbox — swap the local part before
`@gmail.com` for a different real address if you'd rather use one you control
elsewhere. Every household/team is clearly prefixed `DEMO` and every household carries
a `notes` field saying "QA demo data - safe to delete."

## Steps

1. **Register the owner account** at rally26.com (real signup — Stripe is sandboxed,
   so this is safe). Name the organization something like "Rally26 QA Demo".
2. **Import the CSV**: Organization → Integrations → CSV Import. Upload
   `demo-data-import.csv`, review the preview (should show 16 rows, all `CREATE`, zero
   errors), then confirm. This creates the teams/households/guardians/athletes as data
   records — no logins yet.
3. **Invite a coach**: Organization → Members → invite staff with a real email you
   control, then grant them a `TEAM_MANAGER` (or `TEAM_EDITOR`) role on one of the two
   demo teams (Team detail → role assignments) so they can test event/messaging
   create-flows, not just read access.
4. **Invite the guardians for login**: the CSV only creates household/guardian *data*
   records, not accounts. To actually log in as "Dana Smith" etc., send a real
   guardian invitation from the household detail page using the same email address
   already on that guardian's record (so it links to the existing person instead of
   creating a duplicate) — or a different real email if you'd rather.
5. **Invite an athlete for login**: from the household detail page, open one of the
   demo athletes (e.g. a Smith kid) and use "Invite athlete" on their participant
   card, with a real email you control. This calls a real
   `POST /participants/{id}/athlete-invitations` endpoint and grants `ATHLETE_SELF`
   on accept — the athlete must be 13+ (`MINIMUM_ATHLETE_SELF_LOGIN_AGE`) or the
   invite is rejected. (Earlier notes here said this had no exposed endpoint — that
   gap was fixed. All 5 demo athletes in this CSV are currently under 13 — edit one's
   date of birth on the participant page to 13+ before inviting.)

## Cleanup

Every record is tagged and named for easy identification. When you're done, either
archive the teams/households through the normal app UI, or leave them — they're
clearly marked as demo data and isolated to the org you created for this.
