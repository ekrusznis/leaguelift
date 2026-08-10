-- Phase 29.3 / ADR-094: durable QuickBooks request identity and retry/readback metadata.
-- This migration does not activate QuickBooks and does not permit provider writes.

alter table quickbooks_export_item
    drop constraint quickbooks_export_item_status_check;

alter table quickbooks_export_item
    add column provider_entity_type text,
    add column operation_kind text,
    add column operation_key char(64),
    add column intuit_request_id varchar(50),
    add column attempt_count integer not null default 0,
    add column last_http_status integer,
    add column last_fault_type text,
    add column last_fault_code text,
    add column last_intuit_tid text,
    add column retry_disposition text,
    add column retry_not_before timestamptz,
    add column last_attempt_at timestamptz;

alter table quickbooks_export_item
    add constraint quickbooks_export_item_status_check check (status in (
        'PENDING', 'PLANNED', 'WRITE_DISABLED', 'SENT', 'READBACK_REQUIRED',
        'RETRY_SCHEDULED', 'EXPORTED', 'SKIPPED', 'FAILED'
    )),
    add constraint quickbooks_export_item_operation_kind_check
        check (operation_kind is null or operation_kind in ('CREATE', 'UPDATE', 'DELETE')),
    add constraint quickbooks_export_item_attempt_count_check check (attempt_count >= 0),
    add constraint quickbooks_export_item_http_status_check
        check (last_http_status is null or last_http_status between 100 and 599),
    add constraint quickbooks_export_item_request_id_length_check
        check (intuit_request_id is null or char_length(intuit_request_id) between 1 and 50),
    add constraint quickbooks_export_item_operation_key_format_check
        check (operation_key is null or operation_key ~ '^[0-9a-f]{64}$'),
    add constraint quickbooks_export_item_retry_disposition_check check (
        retry_disposition is null or retry_disposition in (
            'DO_NOT_RETRY', 'REFRESH_AUTH', 'RETRY_SAME_REQUEST_AFTER_DELAY',
            'READBACK_REQUIRED', 'READBACK_THEN_RETRY_SAME_REQUEST',
            'REFRESH_ENTITY_THEN_REBUILD', 'REFRESH_REFERENCE_DATA', 'MANUAL_REVIEW'
        )
    ),
    add constraint quickbooks_export_item_operation_identity_check check (
        (operation_key is null and intuit_request_id is null and operation_kind is null and provider_entity_type is null)
        or
        (operation_key is not null and intuit_request_id is not null and operation_kind is not null and provider_entity_type is not null)
    );

create unique index quickbooks_export_item_intuit_request_id_unique_idx
    on quickbooks_export_item (intuit_request_id)
    where intuit_request_id is not null;

comment on column quickbooks_export_item.operation_key is
    'Stable Rally26 business-operation identity. Payload changes do not silently reuse an already-planned operation.';

comment on column quickbooks_export_item.intuit_request_id is
    'Deterministic QuickBooks requestid for the exact canonical payload; limited to Intuit''s 50-character non-batch maximum.';

comment on column quickbooks_export_item.last_intuit_tid is
    'Sanitized Intuit trace identifier retained for support/debugging; never an access token or credential.';

comment on column quickbooks_export_item.retry_disposition is
    'Safe next action after provider/transport failure. Ambiguous financial writes require readback before retry.';
