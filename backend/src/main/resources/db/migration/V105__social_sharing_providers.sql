-- Social Sharing & Connected Accounts, Slice 1 (Foundation) — registers Instagram,
-- Facebook, and X in the existing provider-neutral integration framework
-- (V40__integration_connection_foundation.sql) rather than building a parallel
-- SocialConnection schema. Same OAUTH_SCAFFOLD/NOT_CONFIGURED posture as every other
-- not-yet-activated OAuth2 provider here (TeamSnap, SportsEngine, Google Calendar):
-- visible and connectable in the UI, fails closed until a real developer app is
-- registered and its clientId/clientSecret/authorizationUri/tokenUri are configured.

alter table integration_provider_catalog drop constraint integration_provider_category_check;
alter table integration_provider_catalog add constraint integration_provider_category_check check (category in (
    'PAYMENTS', 'FULFILLMENT', 'COMMUNICATIONS', 'STORAGE', 'CALENDAR',
    'ACCOUNTING', 'SPORTS_DATA', 'MAPS', 'SOCIAL'
));

alter table integration_connection drop constraint integration_connection_category_check;
alter table integration_connection add constraint integration_connection_category_check check (category in (
    'PAYMENTS', 'FULFILLMENT', 'COMMUNICATIONS', 'STORAGE', 'CALENDAR',
    'ACCOUNTING', 'SPORTS_DATA', 'MAPS', 'SOCIAL'
));

insert into integration_provider_catalog
    (provider, display_name, category, ownership_scope, primary_auth_mode, supported_auth_modes,
     baseline_readiness, adapter_mode, description, activation_requirement, default_scopes, sort_order, visible_to_customers)
values
    ('INSTAGRAM', 'Instagram', 'SOCIAL', 'USER', 'OAUTH2', '["OAUTH2"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Share Rally26 events, fundraisers, approved media, sponsors, and team gear directly to your connected Instagram account.',
     'Requires a registered Meta developer app, Instagram Business/Creator login, approved content-publishing scopes, and app review.',
     '[]', 160, true),
    ('FACEBOOK', 'Facebook', 'SOCIAL', 'USER', 'OAUTH2', '["OAUTH2"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Share Rally26 events, fundraisers, approved media, sponsors, and team gear directly to your connected Facebook Page.',
     'Requires a registered Meta developer app, a connected Facebook Page, approved publishing scopes, and app review.',
     '[]', 170, true),
    ('X', 'X', 'SOCIAL', 'USER', 'OAUTH2', '["OAUTH2"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Share Rally26 events, fundraisers, approved media, sponsors, and team gear directly to your connected X account.',
     'Requires a registered X developer app with OAuth2 PKCE and approved tweet-write scope.',
     '[]', 180, true);
