-- Guardian/athlete self-service invitations — deliberately separate from the existing
-- org-staff `invitation` table (V2/V27): the accept target differs (a guardian_relationship
-- row or an ATHLETE_SELF role_assignment, never organization_membership), so reusing that
-- table would mean nullable columns and conditional accept logic bolted onto a working,
-- narrowly-scoped flow. Mirrors its token/hash/expiry security pattern exactly.
--
-- participant_id is required for both kinds: the athlete comes first in this product
-- ("who is this guardian a guardian OF") even though a GUARDIAN invitation's eventual
-- grant (guardian_relationship) is household-scoped, not participant-scoped — every real
-- household has at least one participant, and it's how the invite/email copy names the
-- athlete plus how authorization resolves which team's roster-manage capability applies.
-- household_adult_id is set only for GUARDIAN (find-or-created at invite time from the
-- guardian's name/email); an ATHLETE invitation grants the participant their own login
-- directly, no household_adult involved.
create table household_invitation (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization(id),
    household_id uuid not null references household(id),
    kind text not null check (kind in ('GUARDIAN', 'ATHLETE')),
    household_adult_id uuid references household_adult(id),
    participant_id uuid not null references participant(id),
    email text not null,
    status text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    invited_by_user_id uuid not null references app_user(id),
    token_hash text not null,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint household_invitation_kind_shape check (
        (kind = 'GUARDIAN' and household_adult_id is not null)
        or (kind = 'ATHLETE' and household_adult_id is null)
    )
);

create index household_invitation_token_hash_idx on household_invitation (token_hash);
create index household_invitation_household_idx on household_invitation (household_id);
create index household_invitation_participant_idx on household_invitation (participant_id);
