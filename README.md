# Rally26

**More revenue. Lower fees. Stronger programs.**

Rally26 is a revenue, fundraising, commerce, and fee-management platform for youth
sports organizations. It is the revenue and payment layer that sits beside (not
replaces) scheduling, chat, registration, and league-management products.

The full product vision, scope, architecture, and AI-agent operating rules live in
[`DESIGN-DOC.md`](./DESIGN-DOC.md). That document is authoritative — read it before
making non-trivial changes.

## Features

Organization and team management, household/roster tracking, fee assignment and
collection, fundraising campaigns, apparel commerce (Swag Shop), sponsorships,
in-app messaging, eligibility/compliance tracking, notifications (email/SMS),
platform-admin support tooling, and a native Android/iOS companion app — all
backed by real Stripe billing and subscription tiers (Free/Starter/Club/League).

Full team scheduling, two-way team chat, voice calling, and a Redis caching layer
are intentionally out of scope — see `DESIGN-DOC.md` §14.2.

See `DESIGN-DOC.md` for the authoritative product spec, phase-by-phase build
history, and API/database reference. See `LAUNCH-READINESS.md` for current
pre-launch QA status. `docs/openapi.yaml` has the API contract.

## Repository layout

```text
backend/    Kotlin + Spring Boot API (modular monolith)
frontend/   React + TypeScript + Vite SPA
mobile/     Expo / React Native app (Android + iOS)
qa/         Firebase App Testing agent (Android) QA pack
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

### 4. Run the mobile app

```bash
cd mobile
npm install
npx expo start
```

Set `EXPO_PUBLIC_API_BASE_URL`/`EXPO_PUBLIC_FRONTEND_BASE_URL` in `mobile/.env.local`
(see `.env.example`) — an Android emulator reaches the host machine at `10.0.2.2`, a
physical device needs a real LAN/tunnel address, never `localhost`. EAS build profiles
(`mobile/eas.json`) bake these in at build time; `preview`/`production` point at the
real deployed API.

### 5. Run everything with Docker Compose

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

# Mobile
cd mobile && npm run typecheck
cd mobile && npm run lint
```

Repository- and pull-request-level checks are defined in `.github/workflows/`.

## Documentation

- [`DESIGN-DOC.md`](./DESIGN-DOC.md) — authoritative product & engineering spec: architecture
  (section 5), security (7), database (8), API surface (9), dashboard UI (10), roadmap and
  launch gates (14), testing/observability/operations (18), privacy data inventory (21), and
  AI agent operating instructions (20)
- [`LAUNCH-READINESS.md`](./LAUNCH-READINESS.md) — pre-launch QA checklist, findings log, and go/no-go criteria
- [`docs/openapi.yaml`](./docs/openapi.yaml) — API contract (foundation endpoints)
- [`docs/adr/`](./docs/adr) — Architecture Decision Records
- [`qa/README.md`](./qa/README.md) — Firebase App Testing agent (Android) QA pack

## License

Proprietary — see [`LICENSE`](./LICENSE).
