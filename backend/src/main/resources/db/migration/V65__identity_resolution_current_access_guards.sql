-- Phase 27.5: close the Phase 27 identity-integrity loop by preventing a retired
-- merged app_user from receiving new CURRENT access. Historical attribution rows
-- remain valid and are intentionally not guarded/re-written.

create or replace function reject_current_access_for_merged_user()
returns trigger
language plpgsql
as $$
declare
    payload jsonb := to_jsonb(new);
    candidate_user_id uuid;
    is_current_access boolean := false;
begin
    if payload ->> 'user_id' is null then
        return new;
    end if;

    candidate_user_id := (payload ->> 'user_id')::uuid;

    is_current_access :=
        case tg_table_name
            when 'organization_membership' then coalesce(payload ->> 'status', '') <> 'REVOKED'
            when 'role_assignment' then coalesce(payload ->> 'status', '') = 'ACTIVE'
            when 'guardian_relationship' then coalesce(payload ->> 'status', '') = 'ACTIVE'
            when 'message_thread_member' then payload ->> 'left_at' is null
            when 'message_recipient' then coalesce((payload ->> 'in_app_visible')::boolean, false)
            when 'announcement_recipient' then coalesce((payload ->> 'in_app_visible')::boolean, false)
            else false
        end;

    if is_current_access and exists (
        select 1
        from app_user
        where id = candidate_user_id
          and merged_into_user_id is not null
    ) then
        raise exception 'merged app_user % cannot receive current access in %', candidate_user_id, tg_table_name
            using errcode = '23514';
    end if;

    return new;
end;
$$;

create trigger organization_membership_merged_user_guard
before insert or update of user_id, status, role on organization_membership
for each row execute function reject_current_access_for_merged_user();

create trigger role_assignment_merged_user_guard
before insert or update of user_id, status, context_type, resource_id, role on role_assignment
for each row execute function reject_current_access_for_merged_user();

create trigger guardian_relationship_merged_user_guard
before insert or update of user_id, status on guardian_relationship
for each row execute function reject_current_access_for_merged_user();

create trigger message_thread_member_merged_user_guard
before insert or update of user_id, left_at, member_type, household_id, participant_id, access_reason, can_reply on message_thread_member
for each row execute function reject_current_access_for_merged_user();

create trigger message_recipient_merged_user_guard
before insert or update of user_id, in_app_visible on message_recipient
for each row execute function reject_current_access_for_merged_user();

create trigger announcement_recipient_merged_user_guard
before insert or update of user_id, in_app_visible on announcement_recipient
for each row execute function reject_current_access_for_merged_user();
