-- Owner-initiated organization-ownership handoff by email — deliberately separate from
-- the existing org-staff `invitation` table (V2/V27): that table's INVITABLE_ROLES never
-- includes OWNER (MembershipService.grantMembership explicitly refuses it, "ownership
-- transfer is a separate controlled workflow"), and accept needs to demote the current
-- owner atomically, which the generic invitation-accept flow has no concept of. Mirrors
-- the existing token/hash/expiry/email-match security pattern exactly (V99).
--
-- The invitee may or may not already be an organization member — MembershipService
-- .finalizeOwnershipTransfer handles both cases on accept.
create table ownership_transfer_invitation (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization(id),
    email text not null,
    status text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    invited_by_user_id uuid not null references app_user(id),
    token_hash text not null,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index ownership_transfer_invitation_token_hash_idx on ownership_transfer_invitation (token_hash);
create index ownership_transfer_invitation_organization_idx on ownership_transfer_invitation (organization_id);

-- Only one pending ownership-transfer invitation per organization at a time — a second
-- invite would ordinarily just be "invite someone else instead," achieved by revoking
-- the pending one first, not by stacking two live invitations.
create unique index ownership_transfer_invitation_one_pending_per_org
    on ownership_transfer_invitation (organization_id)
    where status = 'PENDING';
