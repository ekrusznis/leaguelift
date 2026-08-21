-- reject_messaging_history_mutation() (V57/V58/V60) unconditionally blocks UPDATE/DELETE
-- on message_entry (also message_moderation_event, message_safety_report,
-- message_contact_restriction, message_thread, message_thread_member, and
-- message_recipient, but those don't need this bypass — see below), to stop any
-- product/API path from tampering with messaging safety history. That guarantee is
-- correct for every normal code path, but it also silently blocked organization
-- closure (V103) for any organization with messaging history at all — discovered via
-- OrganizationDeletionCascadeIntegrationTest, not anticipated by the original
-- FK-graph analysis (this is a trigger, not a constraint).
--
-- Founder direction this session: closure doesn't get to hard-delete this history
-- (the append-only guarantee stands) — message_entry/message_recipient are redacted
-- in place instead (organization/persistence/OrganizationDeletionExecutorRepository.kt's
-- redactMessagingHistory), which needs an UPDATE on message_entry.body specifically.
-- Only message_entry's own trigger blocks UPDATE (the others only block DELETE, which
-- redaction never attempts), so this bypass flag only actually matters for that one
-- table, set narrowly (transaction-scoped, never set by any product/API code path) for
-- this one terminal, Owner-authorized operation.
create or replace function reject_messaging_history_mutation() returns trigger as $$
begin
    if current_setting('rally26.bypass_messaging_append_only', true) = 'on' then
        return coalesce(new, old);
    end if;
    raise exception 'Messaging safety history is append-only';
end;
$$ language plpgsql;
