-- Help Center content audit (Track 4, 2026-08-13): closes three genuine coverage gaps
-- found by walking this session's newly shipped, user-facing workflows against
-- docs/help/HELP-CENTER-COVERAGE.md — Swag Shop reorder-availability handling, the
-- event source-update redesign, and the new household media center. Content-only
-- migration, matching the established pattern (V38-V42, V68) of shipping a feature's
-- help article in the same phase as the feature itself.

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    (
        '86000000-0000-4000-8000-000000000001',
        'swag-shop-reordering',
        'Reordering a Swag Shop item',
        'How Reorder works from your order history, and what happens if the vendor no longer carries the exact item you ordered before.',
        E'## Reordering from your order history\n\nOn the Swag Shop order page, your past orders are listed with a **Reorder** button for each item. Selecting Reorder prefills the apparel type, size/color, athlete, and any name/number personalization from that past order so you do not have to re-enter it. Review the prefilled choices, then submit the order like any other Swag Shop purchase.\n\nAn item shows **No longer available** instead of Reorder when the store no longer offers it at all.\n\n## If the vendor no longer carries the exact item\n\nEven when Reorder is offered, Rally26 checks with the fulfillment vendor at checkout time to confirm the exact size/color combination is still produced. Most of the time this check passes silently and your order goes through immediately.\n\nIf the vendor has stopped carrying that exact combination since your last order, you will see a dialog titled **This item is no longer available** explaining that the vendor no longer carries this exact apparel type. Select **Choose a different option** to close the dialog, then pick an available size/color, or a different apparel type, from the store.\n\n## What this does not do\n\nThis check does not automatically substitute a different size/color or vendor for you — Rally26 never changes what you ordered without your confirmation. If nothing in the current store matches what you are looking for, contact your organization about options.',
        'Stores and Orders',
        'GUARDIAN',
        'PUBLISHED',
        10,
        now()
    ),
    (
        '86000000-0000-4000-8000-000000000002',
        'updating-an-event-from-its-source',
        'Updating an event from its calendar or roster source',
        'Events imported from an ICS calendar feed or CSV import no longer update themselves automatically — here is how to review and apply a source change.',
        E'## Source-linked events\n\nAn event created by an ICS calendar feed sync or a CSV schedule import stays linked to that source. Unlike earlier behavior, Rally26 no longer silently overwrites a source-linked event''s fields the next time the source is checked. Every field on a source-linked event — including title, times, venue, and opponent — can also be edited directly at any time, the same as a fully manual event.\n\n## Reviewing an available update\n\nWhen the source data for a linked event changes (for example, the opponent''s calendar moves a game time), Rally26 detects the difference the next time it checks that source, but does not apply it automatically. Open the event and look for a gold **An update is available from this event''s source** banner. Select **Review update** to see exactly which fields changed and their old and new values.\n\n## Applying or ignoring an update\n\nIn the review dialog, select **Apply update** to accept the new values from the source — this overwrites any local edits you made to those specific fields, so review the before/after values first. Select **Cancel** to leave the event exactly as it is; the pending update stays available to apply later, and checking the source again will not create a duplicate pending update for the same change.\n\nEvery applied source update is recorded in the organization''s audit history with the exact fields changed and their before/after values.\n\n## Disconnecting an event from its source\n\nIf you no longer want a specific event checked against its source at all, open the event and select **Detach from source**. This is permanent for that event — future source checks will no longer affect it, and no further update banner will appear.',
        'Events and RSVP',
        'OWNER_ADMIN',
        'PUBLISHED',
        40,
        now()
    ),
    (
        '86000000-0000-4000-8000-000000000003',
        'household-media-center',
        'Household media center: photos, video, and releasing media publicly',
        'Guardians can upload photos and short video clips for their household, then choose to release specific items publicly for the organization to use.',
        E'## Uploading photos and video\n\nFrom your household, open the **Photos & Videos** tab. Select a photo (PNG, JPEG, or WebP) or a short video clip (MP4 or MOV) to upload. Photos can be up to 15MB; video can be up to 250MB. Uploaded items are private to your household by default — only your household''s guardians and your organization''s managers can see them.\n\n## Releasing media publicly\n\nTo let your organization use a photo or video for social sharing or a highlight reel, select one or more items using the checkboxes (or **Select all**), then select **Release publicly**. A dialog explains what public release means before you confirm — it is shown every time you use this action, not just the first time, since this affects photos and video of minors. Releasing an item is not reversible: once shared, it may already be saved or reposted elsewhere even if you remove it from Rally26 afterward.\n\n## Removing an item\n\nSelect **Remove** on any item in your household''s media list to take it out of the media center. Removing an item that was already released publicly does not retract copies the organization has already used or shared.\n\n## Who can do this\n\nAny guardian linked to the household can upload, release, and remove household media — this does not require organization manager access. Organization managers can also see and manage a household''s media the same way a guardian can.',
        'Teams and Families',
        'GUARDIAN',
        'PUBLISHED',
        50,
        now()
    );
