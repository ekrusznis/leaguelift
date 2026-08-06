-- Phase 24 slice 24.2 (DESIGN-DOC.md section 14.1G): Swag Shop Path 2 adds a
-- CENTER_FRONT logo placement and a curated small/standard/large logo-size
-- preset. Both stay preset-driven, not freeform coordinates/scale values.

alter table order_item drop constraint order_item_personalization_placement_check;
alter table order_item add constraint order_item_personalization_placement_check check (
    personalization_placement is null
    or personalization_placement in ('LEFT_CHEST', 'RIGHT_CHEST', 'CENTER_FRONT', 'BACK')
);

alter table order_item add column personalization_logo_size text;
alter table order_item add constraint order_item_personalization_logo_size_check check (
    personalization_logo_size is null
    or personalization_logo_size in ('SMALL', 'STANDARD', 'LARGE')
);
