-- Replaces the planned external-IdP (OIDC/Auth0) identity model with traditional
-- email/password authentication checked against this database (superseding
-- DESIGN-DOC.md section 11.5/18.1 and ADR-002 — see their updated text). app_user
-- rows are no longer provisioned from an external subject; they are created
-- directly by POST /api/v1/auth/register and authenticated by
-- POST /api/v1/auth/login.

alter table app_user
    drop constraint app_user_external_subject_key,
    drop column external_subject,
    add column password_hash text;
