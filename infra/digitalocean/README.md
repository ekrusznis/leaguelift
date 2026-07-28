# DigitalOcean Deployment

Target infrastructure per DESIGN-DOC.md section 11.6:

- DigitalOcean App Platform for the `backend` and `frontend` container images.
- DigitalOcean Managed PostgreSQL (one instance per non-local environment).
- DigitalOcean Spaces for logos, artwork, and other public assets (Phase 1+).

## Status

Not yet provisioned. This is a Phase 0 placeholder. Before provisioning:

1. Write and accept ADR-008 (DigitalOcean deployment) in `docs/adr/`.
2. Decide staging vs. production account/project separation.
3. Decide managed Postgres sizing and backup/PITR configuration
   (`DESIGN-DOC.md` section 18.3).
4. Wire `.github/workflows/deploy.yml` to actually push images and trigger an
   App Platform deployment once an app spec exists here.

## Planned layout (not yet created)

```text
infra/digitalocean/
├── app-backend.yaml     # App Platform spec for the backend service
├── app-frontend.yaml    # App Platform spec for the frontend static site/service
└── README.md
```
