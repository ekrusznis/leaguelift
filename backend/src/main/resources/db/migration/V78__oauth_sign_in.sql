-- Phase 37: real Google/Apple mobile sign-in. This extends ADR-014's traditional
-- password-authentication model rather than reversing it -- a provider-authenticated
-- account still mints Rally26's own HS256 session token via the exact same
-- TokenService every password sign-in uses; there is no external session, only
-- external identity verification (a provider-signed ID token, checked against the
-- provider's own JWKS) at sign-in time. password_hash stays nullable for a
-- provider-only account, mirroring how the original OIDC-only design (V1, pre-ADR-014)
-- modeled a single external_subject column -- this time scoped per-provider so a
-- future third provider can't collide, and coexisting with password auth on the same
-- row (an account can be signed into by password AND a linked provider).

alter table app_user
    add column provider text,
    add column provider_subject text,
    add constraint app_user_provider_check check (provider is null or provider in ('GOOGLE', 'APPLE')),
    add constraint app_user_provider_subject_presence_check check (
        (provider is null and provider_subject is null) or (provider is not null and provider_subject is not null)
    );

create unique index app_user_provider_subject_key on app_user (provider, provider_subject) where provider is not null;
