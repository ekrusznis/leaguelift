# LeagueLift

**More revenue. Lower fees. Stronger programs.**

LeagueLift is a revenue, fundraising, commerce, and fee-management platform for youth
sports organizations. It is the revenue and payment layer that sits beside (not
replaces) scheduling, chat, registration, and league-management products.

The full product vision, scope, architecture, and AI-agent operating rules live in
[`DESIGN-DOC.md`](./DESIGN-DOC.md). That document is authoritative — read it before
making non-trivial changes.

## Current status

Phase 0, Phase 1, and Phase 2 are fully implemented; all running locally.

| Phase | What's done |
|-------|-------------|
| **Phase 0** | Repository, Spring Boot / React scaffold, PostgreSQL + Flyway, auth integration, organization CRUD, membership model, audit events, CI pipeline |
| **Phase 1** | Organization profile & onboarding checklist, administrator invitations, teams, tournaments, public pages (DRAFT → PUBLISHED, public `/p/:slug` route), organization isolation integration test, file upload/branding (organization logo/cover image, ADR-012 — real DigitalOcean Spaces credentials still needed before a real staging/prod deploy; local/test run against MinIO) |
| **Phase 2** | Households with adults, participants with team assignments, fee templates, fee assignments with status tracking, manual/offline payment recording, manual discounts/credits, org-wide collections dashboard + CSV export, real (non-demo) outstanding-balance/financial-overview data, Stripe Connect Express onboarding (ADR-005 — onboarding only, no live charge routing; real Stripe test-mode keys still needed to exercise the actual Stripe flow, everything else runs local) |

**Still to do** (approximate priority order):

- Phase 3: Fundraising campaigns, attribution, credits applied to fees, live payment processing
- Phase 4: Apparel commerce
- Phase 5: Financial controls and live pilot
- Phase 6: Sponsorships and automation
- Phase 7: Capability-based authorization model + real (non-static-preview) persona dashboards, document storage, activity feed, global search
- Phase 8: Notifications infrastructure — outbox worker, email delivery, one-way SMS (no chat/voice)
- Phase 9: Reporting module and analytics integration
- Phase 10 (post-pilot): Native mobile app, standalone registration workflows

Phases 7-10 were added 2026-07-28 while reconciling the roadmap against
`frontend/src/assets/demos/LLdiagram.png` (a marketing/vision asset, not a
picture of current or committed-near-term scope). Full team scheduling,
two-way team chat, voice calling, and a Redis caching layer remain
indefinitely out of scope — see `DESIGN-DOC.md` §14.2.

See `DESIGN-DOC.md` sections 10 and 14 for full milestone acceptance criteria, and
`docs/openapi.yaml` for the current API contract.

## Repository layout

```text
backend/    Kotlin + Spring Boot API (modular monolith)
frontend/   React + TypeScript + Vite SPA
docs/       Architecture, security, privacy, ops docs, ADRs, OpenAPI contract
infra/      DigitalOcean / Cloudflare / deployment scripts
.github/    CI workflows
```

## Prerequisites

- Java 17 (backend build target — downgraded from the originally documented 21;
  see `docs/adr/ADR-013-java-17-baseline.md`). The Gradle build includes the Foojay
  toolchain resolver, so `./gradlew` will auto-download a matching JDK if one isn't
  already installed.
- Node.js 22+ and npm
- Docker and Docker Compose
- PostgreSQL 16 (or use the provided `compose.yaml`)

## Local development

### 1. Start Postgres (and optionally the full stack)

```bash
cp .env.example .env
docker compose up -d postgres
```

### 2. Run the backend

```bash
cd backend
./gradlew bootRun
```

The API starts on `http://localhost:8080`. Health checks:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

There is no authentication bypass in any environment, including local — every
request must present a real, validly-signed JWT (see `DESIGN-DOC.md` section 7).
Authentication is traditional email/password against our own database
(`POST /api/v1/auth/register` / `POST /api/v1/auth/login`, ADR-014) — no external
identity provider to configure. To get a real session locally, either register a new
account through the frontend, or sign in as one of the four seeded dashboard-role
accounts (`backend/src/main/resources/db/seed/V9000__dev_seed_dashboard_role_users.sql`,
password `DevPassword123!` for all four — only loaded when the `local` profile's
`spring.flyway.locations` includes `classpath:db/seed`, never in staging/prod).

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app starts on `http://localhost:5173` and talks to the API at
`VITE_API_BASE_URL` (defaults to `http://localhost:8080/api/v1`). Sign-in and
registration always call the real backend endpoints — there is no mock session mode.

### 4. Run everything with Docker Compose

```bash
docker compose up --build
```

## Testing

```bash
# Backend
cd backend && ./gradlew test

# Frontend
cd frontend && npm run test
cd frontend && npm run typecheck
cd frontend && npm run lint
cd frontend && npm run build
```

Repository- and pull-request-level checks are defined in `.github/workflows/`.

## Documentation

- [`DESIGN-DOC.md`](./DESIGN-DOC.md) — authoritative product & engineering spec: architecture
  (section 5), security (7), database (8), API surface (9), dashboard UI (10), roadmap and
  launch gates (14), testing/observability/operations (18), privacy data inventory (21), and
  AI agent operating instructions (20)
- [`docs/openapi.yaml`](./docs/openapi.yaml) — API contract (foundation endpoints)
- [`docs/adr/`](./docs/adr) — Architecture Decision Records

## License

Proprietary — see [`LICENSE`](./LICENSE).
