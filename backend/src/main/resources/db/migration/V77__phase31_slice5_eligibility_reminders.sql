-- Phase 31 slice 31.5 (Settings/notifications closeout): widen announcement_kind_check to add
-- ELIGIBILITY_REMINDER, giving the already-shipped "Documents & eligibility" notification
-- preference (V67, ADR-088) its first real sender -- a staff-triggered reminder for a
-- participant's outstanding eligibility requirements, matching the existing FEE_REMINDER/
-- DOCUMENT_REMINDER pattern exactly (see ReminderService.kt).

alter table announcement drop constraint announcement_kind_check;
alter table announcement add constraint announcement_kind_check check (kind in (
    'GENERAL', 'CAMPAIGN_LAUNCH', 'EVENT_REMINDER', 'FEE_REMINDER', 'DOCUMENT_REMINDER', 'ELIGIBILITY_REMINDER'
));
