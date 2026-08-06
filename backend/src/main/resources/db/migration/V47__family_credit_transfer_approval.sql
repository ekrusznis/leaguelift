-- P2P family credit transfers (V46) executed immediately on request, with no
-- organization review step — founder direction added mid-Phase-23: a transfer
-- must be held for organization owner/manager approval before credit actually
-- moves, mirroring the existing profile_correction_request PENDING/APPROVED/
-- REJECTED review pattern (reviewed_by_user_id/review_note/reviewed_at).
alter table family_credit_transfer
    add column status               text not null default 'PENDING',
    add column reviewed_by_user_id  uuid references app_user (id),
    add column review_note          text,
    add column reviewed_at          timestamptz;

alter table family_credit_transfer
    add constraint family_credit_transfer_status_check
        check (status in ('PENDING', 'APPROVED', 'REJECTED'));

create index family_credit_transfer_status_idx on family_credit_transfer (organization_id, status);
