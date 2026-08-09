-- Phase 28.5 Settings Help Center closeout (ADR-091).
-- Content-only migration: no new setting, permission, provider, or payment capability.

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    (
        '28000000-0000-4000-8000-000000000001',
        'personal-settings-and-notifications',
        'Personal settings and notifications',
        'Manage your Rally26 appearance and optional notification choices without changing your role or organization access.',
        E'## Open Settings\n\nSign in and open **Settings** from the Rally26 app navigation. Personal Settings belongs to your account and is available to every authenticated Rally26 persona.\n\n## Appearance\n\nChoose **System**, **Light**, or **Dark**. System follows the light/dark preference of your current device. The choice changes presentation only; it does not change your role, team, household, or organization access.\n\n## Optional notifications\n\nYou can choose Default, On, or Off for optional in-app, email, and SMS topics. Optional in-app and email delivery normally default on, subject to existing household compatibility rules where applicable. SMS defaults off.\n\n## SMS consent\n\nOptional account SMS requires your individual consent and an explicit SMS topic setting of On. A phone number, household setting, or organization preference cannot grant consent for your account. Revoking consent stops future optional account SMS even if a topic remains stored as On.\n\n## Communications that stay on\n\nAccount verification, password/security changes, invitations, payment/order/refund receipts, and legally required eligibility or e-sign receipts when those workflows exist are transactional communications and are not controlled by optional notification settings. Safety-required guardian visibility for athlete messaging is also not removed by an optional Messages preference.\n\n## History and permissions\n\nSettings never expands what you are allowed to view. History, household, team, organization, billing, messaging, and other modules keep their existing authorization rules.',
        'Accounts and Invitations',
        'PUBLIC',
        'PUBLISHED',
        35,
        now()
    ),
    (
        '28000000-0000-4000-8000-000000000002',
        'organization-settings-directory',
        'Organization settings directory',
        'How owners and administrators use Settings to reach existing organization controls without creating a second configuration system.',
        E'## One control surface, existing domain rules\n\nFor organizations you are already authorized to manage, **Settings** groups links to the real organization-owned controls for profile and branding, financial and credit workflows, communications, events and participation, Swag Shop and commerce, integrations, billing, and history/administration.\n\nSettings is a directory over those existing modules. It does not copy organization data into a generic settings record and it does not bypass the permission checks on the destination page or backend service.\n\n## Who sees organization controls\n\nOrganization managers see the full directory for organizations they can manage. A payout-only capability receives only the existing payout/financial entry. Coaches, guardians, and athletes do not receive owner/administrator organization controls unless they separately hold the required organization capability.\n\nPlatform Administrators do not automatically receive customer Settings. Customer organization controls appear only during an already-active, reasoned Platform Support session for that organization, and the Platform Administrator remains the authenticated actor.\n\n## What is not a setting yet\n\nRally26 does not show inactive payment-provider, financing, eligibility, waiver, tax, refund, or event-default toggles simply because they are future roadmap items. A new organization setting is added only when a real domain workflow consumes it.',
        'Organization Setup',
        'OWNER_ADMIN',
        'PUBLISHED',
        35,
        now()
    );
