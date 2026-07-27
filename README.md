# LeagueLift

**More revenue. Lower fees. Stronger programs.**

LeagueLift is a revenue, fundraising, commerce, and fee-management platform for youth
sports organizations. It is the revenue and payment layer that sits beside (not
replaces) scheduling, chat, registration, and league-management products.

The full product vision, scope, architecture, and AI-agent operating rules live in
[`DESIGN-DOC.md`](./DESIGN-DOC.md). That document is authoritative — read it before
making non-trivial changes.

## Current status

**Phase 0 (foundation) is built and running locally; Phase 1's first vertical slice
(organization onboarding — profile, sports/contact info, administrator invitations,
member management, onboarding checklist) is implemented.** See
[`docs/launch-checklist.md`](./docs/launch-checklist.md) for what's implemented and
what's next, and section 35 of `DESIGN-DOC.md` for the recommended order of upcoming
vertical slices.

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

The `local` Spring profile enables a development authentication bypass — a fixed
internal test user — so the frontend and API can be exercised without a configured
Auth0 tenant. This bypass is hard-disabled outside the `local` profile
(see `docs/security.md`).

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app starts on `http://localhost:5173` and talks to the API at
`VITE_API_BASE_URL` (defaults to `http://localhost:8080/api/v1`). With
`VITE_AUTH_DEV_MODE=true` (the default in `.env.example`), the frontend uses an
in-memory mock session instead of redirecting to Auth0, matching the backend's local
bypass.

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

- [`DESIGN-DOC.md`](./DESIGN-DOC.md) — authoritative product & engineering spec
- [`docs/architecture.md`](./docs/architecture.md) — module boundaries and request flow
- [`docs/security.md`](./docs/security.md) — authn/authz, secrets, headers
- [`docs/privacy-data-inventory.md`](./docs/privacy-data-inventory.md) — personal data inventory
- [`docs/openapi.yaml`](./docs/openapi.yaml) — API contract (foundation endpoints)
- [`docs/operations-runbook.md`](./docs/operations-runbook.md) — on-call / operations
- [`docs/launch-checklist.md`](./docs/launch-checklist.md) — gate-by-gate launch readiness
- [`docs/ai-agent-guardrails.md`](./docs/ai-agent-guardrails.md) — rules for AI coding agents
- [`docs/adr/`](./docs/adr) — Architecture Decision Records

## License

Proprietary — see [`LICENSE`](./LICENSE).
