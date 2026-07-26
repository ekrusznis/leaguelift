# LeagueLift Product and Engineering Design Document

**Document status:** Implementation baseline  
**Version:** 1.0  
**Date:** 2026-07-26  
**Primary audience:** Claude Code and other software-development agents, the founder, future engineers, designers, and technical reviewers  
**Repository target:** New private LeagueLift repository  
**Product stage:** Pre-pilot / initial product development  

---

## 1. Purpose of This Document

This document is the authoritative product and engineering specification for LeagueLift.

It defines:

- The product vision and business model
- The initial product scope
- The long-term feature set
- User types and permissions
- Core user journeys
- Application architecture
- Repository structure
- Backend and frontend standards
- Database and financial-design requirements
- Security and privacy boundaries
- External integration strategy
- Testing, observability, deployment, and launch requirements
- Development milestones and acceptance criteria
- Persistent instructions for AI coding agents

Agents must treat this document as the primary source of truth. When implementation details are unclear, agents must preserve the stated product intent, choose the simplest safe design, and document meaningful decisions in an Architecture Decision Record.

Agents must not silently broaden the product scope, replace the chosen technology stack, introduce microservices, collect additional youth data, or invent financial behavior.

---

# 2. Product Overview

## 2.1 Product Name

**LeagueLift**

## 2.2 Tagline

**More revenue. Lower fees. Stronger programs.**

## 2.3 Product Definition

LeagueLift is a revenue, fundraising, commerce, and fee-management platform for youth sports organizations.

It helps leagues, clubs, travel programs, recreational programs, individual teams, tournament operators, booster organizations, and multisport facilities:

- Create professional organization, team, and tournament pages
- Run organization- and team-specific fundraising campaigns
- Sell branded apparel connected to fundraising goals
- Manage adult parent and guardian accounts
- Maintain lightweight participant records
- Assign, collect, and track dues and fees
- Offer payment plans, discounts, waivers, scholarships, and credits
- Attribute supporter purchases and contributions to teams or families
- Apply approved sales-based credits against eligible sports fees
- Sell and manage local sponsorship packages
- Track revenue, costs, earnings, credits, and payouts
- Reduce administrative work for coaches, treasurers, and volunteers

LeagueLift is not initially intended to replace scheduling, team chat, registration, roster-management, or league-management products. It is the revenue and payment layer that works beside those systems.

## 2.4 Core Product Promise

LeagueLift gives youth sports organizations one place to manage the revenue surrounding their programs.

Organizations gain better tools to collect fees, raise money, sell merchandise, manage sponsors, reward families, and understand their revenue.

Families gain clearer balances, flexible payment options, fundraising tools, order visibility, and approved opportunities to reduce sports costs.

Supporters gain simple ways to contribute, purchase apparel, sponsor programs, and support the teams and organizations they care about.

---

# 3. Business Objectives

## 3.1 Short-Term Objective

Launch a sellable pilot product that can produce recurring SaaS revenue and transaction revenue while being operated by one founder.

The initial system must be credible, secure, maintainable, and narrow enough for a solo software engineer to support.

## 3.2 Long-Term Objective

Grow LeagueLift into a full company with:

- Recurring subscription revenue
- Growing payment and commerce volume
- Strong customer retention
- Repeatable onboarding and sales processes
- A supportable multi-tenant platform
- A defensible organization, family, sponsor, and transaction network
- Sufficient operational maturity to hire employees
- A credible future acquisition path

## 3.3 Revenue Model

LeagueLift may earn revenue through:

1. Monthly or annual organization subscriptions
2. Transaction fees on merchandise purchases
3. Transaction fees on fundraising contributions
4. Transaction fees on sponsorship purchases
5. Onboarding and implementation services
6. Merchandise-design services
7. Managed campaign services
8. Premium reporting or enterprise plans
9. Future integration or white-label plans

### Initial commercial assumption

The initial founding-pilot offer may start at:

- **$149 per month**
- Plus clearly disclosed transaction fees
- No setup fee for selected founding organizations
- Guided onboarding and support

Pricing must be configurable. It must not be hard-coded throughout the application.

---

# 4. Brand and Experience Direction

## 4.1 Brand Personality

LeagueLift should feel:

- Energetic
- Trustworthy
- Community-focused
- Modern
- Financially responsible
- Clear
- Helpful
- Inclusive across sports
- Professional enough for treasurers and board members
- Friendly enough for parents, coaches, and volunteers

LeagueLift should not feel:

- Childish
- Aggressive
- Exclusive to one sport
- Like professional sports media
- Like a cryptocurrency or financial-trading application
- Like a generic enterprise accounting system
- Like an unproven “AI revolution” product

## 4.2 Color System

| Token | Value | Primary use |
|---|---:|---|
| Deep Navy | `#0B1F33` | Navigation, headings, dark surfaces, trust |
| Victory Green | `#20B26B` | Primary actions, positive status, earnings |
| Championship Gold | `#F4B740` | Select highlights, premium accents, warnings |
| Ice White | `#F7F9FC` | Main application background |
| Slate Gray | `#526275` | Secondary text and icons |
| Pure White | `#FFFFFF` | Cards, forms, light surfaces |
| Error Red | `#C93636` | Errors, destructive actions, overdue states |
| Info Blue | `#2F6FED` | Informational states and links |

Do not overuse gradients. A restrained navy gradient is acceptable for public-page hero sections and major calls to action.

## 4.3 Typography

- **Headings:** Manrope
- **Body and interface:** Inter
- Use system-font fallbacks.
- Avoid unusually condensed sports fonts.
- Use strong hierarchy and generous spacing.

## 4.4 Imagery

Use authentic, licensed, candid imagery representing multiple youth sports and community participation.

Preferred subjects:

- Athletes participating in different sports
- Parents and guardians supporting teams
- Coaches and volunteers
- Tournament and community-event environments
- Branded apparel
- Local sponsors
- Diverse families and organizations

Avoid:

- Professional stadiums
- Celebrity athletes
- Fake customer logos
- AI robots
- Generic office teams
- Excessive trophy imagery
- Public close-up profile imagery of identifiable children unless properly licensed and appropriate

## 4.5 Accessibility

Target WCAG 2.2 AA where practical.

Every interface must include:

- Keyboard-accessible navigation
- Visible focus states
- Sufficient color contrast
- Semantic HTML
- Form labels
- Useful validation errors
- Screen-reader-friendly status messages
- Large touch targets
- Reduced-motion support
- Accessible dialogs and menus
- Loading, empty, success, error, and unauthorized states

---

# 5. Product Boundaries

## 5.1 What LeagueLift Is

- A multi-tenant youth-sports revenue platform
- A public-page builder for organizations, teams, tournaments, campaigns, and stores
- A fundraising and commerce platform
- A dues and fee-management system
- A family credit and discount system
- A sponsorship-management system
- A reporting and reconciliation platform

## 5.2 What LeagueLift Is Not Initially

- A complete league scheduling platform
- A team chat replacement
- A sports-registration replacement
- A competition-scoring engine
- A medical-record system
- A school-record system
- A background-check provider
- A bank
- A stored-value wallet
- A tax-advisory service
- A provider of guaranteed fundraising results
- A platform where children independently create accounts

## 5.3 Important Credit Boundary

Family credits are initially organization-approved discounts or credits that may be applied only against eligible organization fees.

Credits must not initially be:

- Withdrawable as cash
- Transferred between unrelated families
- Redeemed outside the issuing organization
- Presented as a bank balance
- Presented as a stored-value account
- Used for peer-to-peer transfers

Any expansion beyond this boundary requires a dedicated legal, payments, accounting, and architecture review.

## 5.4 Youth-Data Boundary

LeagueLift accounts are controlled by adults.

The application may maintain minimal participant records connected to adult-managed household accounts. Children do not need independent login accounts in the initial product.

Do not add medical, educational, behavioral, background-check, precise-location, or other highly sensitive youth data without an explicit product decision, privacy review, and updated design specification.

---

# 6. Target Customers

## 6.1 Primary Customer Segments

1. Recreational youth sports leagues
2. Travel clubs
3. Individual teams
4. Tournament operators
5. Booster organizations
6. Multisport facilities
7. Community sports programs
8. School-adjacent sports organizations where purchasing authority is held by an adult organization representative

## 6.2 Primary Buyer Roles

- League president
- Club director
- Tournament director
- Treasurer
- Booster president
- Board member
- Team manager
- Fundraising coordinator
- Merchandise coordinator
- Facility operator

## 6.3 Primary End Users

- Platform administrators
- Organization owners
- Organization administrators
- Finance managers
- Team administrators
- Tournament administrators
- Adult parents and guardians
- Supporters and donors
- Sponsors
- Store customers

---

# 7. User Roles and Authorization

Authorization must be enforced by the Kotlin backend. React is never an authorization boundary.

## 7.1 Platform Roles

### Platform Administrator

Can:

- Access all organizations
- Manage platform configuration
- View platform-wide analytics
- Manage support cases
- Inspect integration failures
- Perform documented financial adjustments
- Manage feature flags
- Suspend organizations
- Review audit events
- Access carefully controlled support impersonation

### Platform Support

Future role with restricted access.

Can:

- View organization and user support context
- View non-sensitive operational records
- Resolve non-financial support cases
- Not perform payouts or ledger adjustments unless separately authorized

## 7.2 Organization Roles

### Organization Owner

Can:

- Manage the organization
- Manage administrators
- Configure billing
- Configure payment and payout settings
- Configure credit policies
- View all organization financial reports
- Transfer ownership under controlled workflow

### Organization Administrator

Can:

- Manage teams, tournaments, public pages, campaigns, stores, and users
- View organization operational reporting
- Manage parent and participant records
- Assign fees
- Manage discounts and credits subject to permissions

### Finance Manager

Can:

- View fees, payments, credits, revenue, ledger summaries, and payouts
- Issue permitted refunds or adjustments through controlled workflows
- Export financial reports
- Not change organization ownership or branding unless separately permitted

### Team Administrator

Can:

- Manage assigned teams
- Manage assigned team pages and campaigns
- View team-level fundraising and fee information
- Not automatically view unrelated teams or organization-wide private financial data

### Tournament Administrator

Can:

- Manage assigned tournaments
- Manage participating-team pages, tournament apparel, sponsor placements, and event campaigns
- View tournament-level revenue
- Not automatically access unrelated organization data

### Organization Viewer

Read-only access to explicitly permitted organization areas.

## 7.3 Household Role

### Parent or Guardian

Can:

- Manage the adult household profile
- View linked participants
- View assigned fees and payment history
- Choose payment options
- View orders and fulfillment status
- Create or share authorized family fundraising links
- View pending, available, applied, reversed, and expired credits
- Update notification preferences
- Not view another household’s information

## 7.4 Public Roles

### Supporter

Can:

- View published public pages
- Purchase available merchandise
- Make eligible contributions
- Select a team or family attribution when the campaign permits
- Purchase sponsorships where permitted
- Receive payment and order confirmations

### Sponsor

May later receive a protected sponsor portal but can initially use secure sponsor checkout and email-based fulfillment.

---

# 8. Long-Term Feature Modules

The product vision includes the modules below. Not all modules belong in the first launch.

## 8.1 Organization Management

- Organization profile
- Branding
- Sports and divisions
- Contacts
- Subscription plan
- Payment configuration
- Payout onboarding
- Organization settings
- Organization roles
- Audit history
- Data exports
- Organization suspension and archival

## 8.2 Team Management

- Team profile
- Sport
- Division or age group
- Season
- Team administrators
- Participant assignments
- Public team page
- Team fundraising
- Team store collection
- Team fees
- Team sponsors
- Team reports

## 8.3 Tournament Management

- Tournament profile
- Dates
- Venues
- Divisions
- Participating teams
- Public tournament page
- Tournament merchandise
- Preorders
- Event-day ordering
- Championship products
- Sponsor packages
- Vendor listings
- Team attribution
- Tournament reporting

## 8.4 Public Page Builder

Page types:

- Organization
- Team
- Tournament
- Fundraising campaign
- Store
- Sponsor directory

Capabilities:

- Configurable slug
- Logo
- Cover image
- Description
- Theme colors
- Contact information
- Calls to action
- Published sections
- Sponsor recognition
- Campaign widgets
- Store widgets
- Donation widgets
- QR code
- Social-sharing metadata
- Draft, preview, publish, archive workflow

## 8.5 Fundraising

Campaign types:

- Organization general fund
- Team general fund
- Travel
- Tournament fees
- Uniforms
- Equipment
- Facility improvements
- Scholarships
- Special events
- Apparel-based fundraising
- Sponsor-supported campaigns

Features:

- Goal
- Date range
- Campaign status
- Public progress
- Contribution amounts
- Team attribution
- Household attribution
- Anonymous public display
- Campaign updates
- Share links
- QR codes
- Email and social copy
- Reporting
- Refund handling
- Payment receipts

Do not describe a contribution as tax-deductible unless the organization and payment flow have been verified to support that representation.

## 8.6 Apparel and Merchandise

- Organization stores
- Team collections
- Tournament collections
- Limited-time collections
- Fundraiser-linked collections
- Product templates
- Variants
- Personalization
- Pricing
- Production cost
- Fundraising markup
- Organization earnings
- Team earnings
- Household credit rules
- Cart
- Checkout
- Order tracking
- Refund requests
- Fulfillment integration
- Shipment tracking
- Sales reporting

## 8.7 Dues and Fees

Fee types:

- Registration
- Team dues
- League dues
- Tournament
- Uniform
- Equipment
- Travel
- Facility
- Coaching
- Camp or clinic
- Custom

Features:

- Fee templates
- Fee assignments
- Household and participant association
- Due dates
- Installment plans
- Partial payment
- Automatic payment schedules
- Reminders
- Discounts
- Scholarships
- Waivers
- Credits
- Refunds
- Adjustments
- Receipts
- Outstanding-balance reports

## 8.8 Family Credits and Discounts

Credit sources:

- Merchandise purchase attribution
- Campaign contribution attribution
- Sponsorship referral
- Organization-issued promotional credit
- Approved volunteer incentive
- Manual documented adjustment

Features:

- Credit-rule configuration
- Attribution links and QR codes
- Pending credit
- Available credit
- Applied credit
- Reversed credit
- Expired credit
- Maximum limits
- Eligible-fee rules
- Refund reversal
- Audit history
- Family statement

## 8.9 Sponsorships

- Sponsor contacts
- Sponsorship package builder
- Pricing
- Quantity limits
- Exclusivity
- Package benefits
- Checkout
- Logo and artwork upload
- Approval workflow
- Placement dates
- Digital sponsor directory
- Link and QR tracking
- Renewal reminders
- Sponsor reports
- Invoices and attachments

## 8.10 Communication

- Payment reminders
- Campaign launch emails
- Fundraising reminders
- Order confirmation
- Shipping updates
- Contribution thank-you messages
- Sponsor-renewal reminders
- Notification preferences
- Templates
- Delivery events
- Failure and bounce tracking
- Future SMS integration

## 8.11 Reporting

Organization reports:

- Revenue by source
- Revenue by team
- Revenue by tournament
- Revenue by campaign
- Fee collections
- Outstanding fees
- Credits
- Product performance
- Refunds
- Platform fees
- Earnings
- Payouts

Household reports:

- Assigned fees
- Payments
- Outstanding balance
- Credits earned
- Credits applied
- Orders
- Contributions

Platform reports:

- Customers
- Active organizations
- Subscription revenue
- Gross transaction volume
- Refund and dispute rates
- Feature adoption
- Retention
- Integration health

---

# 9. Initial Delivery Strategy

The custom application will be developed while the existing Base44 site remains the temporary marketing and pilot-lead funnel.

## 9.1 Temporary Production Shape

```text
www.leaguelift.com
    Existing Base44 marketing and pilot funnel

app.leaguelift.com
    React application

api.leaguelift.com
    Kotlin/Spring Boot API

Managed PostgreSQL
    LeagueLift application data
```

The custom repository does not need to recreate the Base44 marketing site during the first development phase.

## 9.2 Architecture Strategy

Use a modular monolith:

- One Kotlin/Spring Boot backend deployment
- One React frontend deployment
- One PostgreSQL database per environment
- Clear domain modules
- Asynchronous work through an outbox and background processing
- External providers behind adapters
- No microservices until operational evidence proves a need

---

# 10. Release Scope

## 10.1 Phase 0 — Repository and Platform Foundation

This is the first implementation target.

Must include:

- New private Git repository
- Backend and frontend projects
- Local Docker Compose
- PostgreSQL
- Flyway migrations
- Authentication integration
- Internal user provisioning
- Organizations
- Organization memberships
- Basic roles
- Request IDs
- Standard API errors
- Audit event foundation
- Transactional outbox foundation
- Health and readiness endpoints
- OpenAPI document
- CI pipeline
- Dependency update configuration
- Production Dockerfiles
- Staging and production configuration documentation
- Basic protected React application shell
- Organization creation and listing
- Tests for identity and organization isolation

## 10.2 Phase 1 — Pilot Organization Onboarding and Public Pages

Must include:

- Organization profile
- Organization type
- Sports
- Contact information
- Branding
- Logo and cover-image storage
- Adult administrator invitations
- Onboarding progress
- Team creation
- Tournament creation
- Draft and published organization pages
- Draft and published team pages
- Draft and published tournament pages
- Public slugs
- QR-code generation
- Platform-administrator organization list
- Basic analytics events
- Audit records for publishing and membership changes

## 10.3 Phase 2 — Households, Participants, Dues, and Fees

Must include:

- Adult household account
- Parent/guardian membership
- Lightweight participant records
- Participant-to-team assignment
- Fee templates
- Fee assignments
- Due dates
- Manual discounts
- Manual credits
- Outstanding balance
- Payment history model
- Parent dashboard
- Organization collections dashboard
- CSV export
- No child login accounts

The first iteration may use test or manually recorded payment records before live payment activation.

## 10.4 Phase 3 — Fundraising and Attribution

Must include:

- Organization and team campaigns
- Public campaign pages
- Campaign goals
- Date ranges
- Share links
- QR codes
- Team attribution
- Household attribution
- Contribution-intent and payment-record model
- Campaign reporting
- Credit rules
- Pending and approved credits
- Applying approved credits to eligible fees
- Reversals and audit records

Activate live payment processing only after payment architecture, terms, refunds, and reconciliation are reviewed.

## 10.5 Phase 4 — Apparel Commerce Proof

Must include:

- Product catalog
- Product variants
- Organization collections
- Team collections
- Tournament collections
- Pricing and markup
- Cart
- Checkout
- Order records
- Order items
- Fulfillment records
- Printify test integration
- Stripe test integration
- Webhook event inbox
- Idempotent processing
- Order-status notifications
- Sales attribution
- Credit calculation
- Refund reversal

Start with a small controlled product catalog.

## 10.6 Phase 5 — Financial Controls and Live Pilot

Must include:

- Immutable financial ledger
- Reconciliation
- Refunds
- Chargebacks
- Platform fees
- Production costs
- Organization earnings
- Team allocations
- Family credits
- Stripe Connect onboarding
- Selected charge model documented in an ADR
- Payout status
- Platform-admin exception handling
- Integration alerting
- Tested backup and recovery procedure
- Legal and policy review
- Live pilot release gate

## 10.7 Phase 6 — Sponsorships and Automation

- Sponsor CRM
- Sponsorship packages
- Sponsor checkout
- Artwork approval
- Placement tracking
- Renewals
- Sponsor reporting
- Campaign email automation
- Deeper integrations
- Advanced tournament workflows

## 10.8 Explicitly Deferred

Do not build during the first phases:

- Child login accounts
- Cash-withdrawable family wallets
- Peer-to-peer credit transfers
- Full team scheduling
- Team chat
- Live scoring
- Volunteer management
- Medical records
- School records
- Native mobile applications
- Kubernetes
- Microservices
- Premature accounting integrations
- Broad sports-platform integrations without pilot demand

---

# 11. Selected Technology Stack

## 11.1 Repository Style

Use one monorepo containing:

- Kotlin backend
- React frontend
- Documentation
- Infrastructure configuration
- CI workflows

## 11.2 Backend

- Kotlin
- Java 21 runtime target
- Spring Boot
- Spring MVC
- Spring Security OAuth2 Resource Server
- Bean Validation
- PostgreSQL JDBC driver
- Spring JDBC `JdbcClient` for early modules
- Flyway
- Spring Boot Actuator
- Micrometer
- Structured logging
- Testcontainers
- JUnit 5
- MockK or Mockito where needed
- jOOQ introduced before financial ledger and complex reporting

Use Gradle Kotlin DSL.

The provided starter repository used the following compatibility baseline:

- Spring Boot 4.1.0
- Kotlin 2.3.21
- Java 21
- Gradle 9.6.1

The new repository may retain these versions. Agents may update patch versions only when builds and tests pass. Major version changes require an ADR.

## 11.3 Frontend

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- React Hook Form
- Zod
- Accessible component primitives
- Tailwind CSS or a documented token-based CSS system
- Vitest
- React Testing Library
- Playwright

The provided starter used:

- React 19.2
- Vite 8.1
- TypeScript 6
- Node 24 compatibility target

Do not downgrade or change the frontend framework without an ADR.

## 11.4 Database

- PostgreSQL
- UUID primary keys
- `timestamptz`
- `jsonb` only for flexible metadata, not core relational structure
- Explicit foreign keys
- Check constraints
- Unique constraints
- Appropriate indexes
- Integer minor units for money
- ISO 4217 currency codes
- Flyway migrations for every schema change

## 11.5 Authentication

Use a managed OIDC provider initially.

The seed design uses Auth0:

- React obtains an access token
- Spring validates JWT signature, issuer, expiration, and audience
- Internal `app_user` records are provisioned from external subject identifiers
- Email is not the permanent identity key
- Database authorization uses internal memberships

Authentication provider access must be abstracted enough that a future provider change is possible.

## 11.6 Infrastructure

Initial target:

- DigitalOcean App Platform for frontend and backend
- DigitalOcean Managed PostgreSQL
- DigitalOcean Spaces for logos, artwork, and public assets
- Cloudflare for DNS and edge protections
- GitHub Actions
- Sentry for frontend and backend error monitoring
- Resend or an equivalent transactional email provider
- Stripe
- Stripe Connect
- Printify
- Managed Redis or Valkey later if required for queues, distributed locks, or caching

Do not use Kubernetes for the initial product.

---

# 12. Repository Structure

The agent should create a repository similar to:

```text
leaguelift/
├── README.md
├── DESIGN-DOC.md
├── CONTRIBUTING.md
├── LICENSE                  # private/proprietary placeholder if appropriate
├── .editorconfig
├── .gitignore
├── .env.example
├── compose.yaml
├── backend/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── Dockerfile
│   ├── src/main/kotlin/com/leaguelift/
│   │   ├── LeagueLiftApiApplication.kt
│   │   ├── common/
│   │   ├── config/
│   │   ├── identity/
│   │   ├── organization/
│   │   ├── membership/
│   │   ├── publicpage/
│   │   ├── team/
│   │   ├── tournament/
│   │   ├── household/
│   │   ├── participant/
│   │   ├── fees/
│   │   ├── fundraising/
│   │   ├── credits/
│   │   ├── catalog/
│   │   ├── store/
│   │   ├── order/
│   │   ├── payment/
│   │   ├── fulfillment/
│   │   ├── ledger/
│   │   ├── sponsorship/
│   │   ├── notification/
│   │   ├── integration/
│   │   ├── reporting/
│   │   ├── admin/
│   │   ├── audit/
│   │   └── outbox/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   ├── application-test.yml
│   │   ├── application-staging.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/
│   └── src/test/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   ├── src/
│   │   ├── app/
│   │   ├── auth/
│   │   ├── components/
│   │   ├── features/
│   │   │   ├── organizations/
│   │   │   ├── teams/
│   │   │   ├── tournaments/
│   │   │   ├── households/
│   │   │   ├── fees/
│   │   │   ├── fundraising/
│   │   │   ├── credits/
│   │   │   ├── stores/
│   │   │   ├── orders/
│   │   │   └── admin/
│   │   ├── pages/
│   │   ├── routes/
│   │   ├── lib/
│   │   ├── styles/
│   │   └── test/
│   └── public/
├── docs/
│   ├── openapi.yaml
│   ├── architecture.md
│   ├── security.md
│   ├── privacy-data-inventory.md
│   ├── launch-checklist.md
│   ├── operations-runbook.md
│   ├── ai-agent-guardrails.md
│   └── adr/
├── infra/
│   ├── digitalocean/
│   ├── cloudflare/
│   └── scripts/
└── .github/
    ├── workflows/
    │   ├── ci.yml
    │   ├── backend.yml
    │   ├── frontend.yml
    │   ├── security.yml
    │   └── deploy.yml
    └── dependabot.yml
```

Only create domain folders when their milestone begins. Avoid empty speculative code packages when they add no value.

---

# 13. Backend Architecture

## 13.1 Modular Monolith

Each module should own:

- Domain behavior
- Application services
- Database access
- HTTP DTO mapping where appropriate
- Tests
- Events it emits
- Events it consumes

Modules may share infrastructure through explicitly named common packages. Avoid a large undifferentiated `service` or `util` package.

## 13.2 Suggested Layering

Within a domain module:

```text
domain/
    Domain models and business rules

application/
    Use cases and transaction boundaries

persistence/
    Repositories and SQL

web/
    Controllers and API DTOs

integration/
    Provider-specific adapters where relevant
```

Small modules may use a flatter structure until complexity requires additional layers.

## 13.3 API Standards

- Base path: `/api/v1`
- JSON request and response bodies
- UUID identifiers
- ISO-8601 timestamps
- Standard paginated responses
- Consistent error envelope
- Request ID in response header and errors
- OpenAPI is updated before or with implementation
- Breaking API changes require a versioning plan

Suggested error format:

```json
{
  "code": "ORGANIZATION_NOT_FOUND",
  "message": "The organization could not be found.",
  "requestId": "req_...",
  "fieldErrors": []
}
```

Do not expose stack traces, SQL, secrets, provider payloads, or internal exception names.

## 13.4 Transaction Rules

- Put business transaction boundaries in application services.
- Never make a remote provider call while holding an avoidably long database transaction.
- Write outbox events in the same transaction as the business state change.
- Process external work asynchronously when practical.
- Use idempotency keys for client and provider operations.
- Do not mutate historical financial entries.

## 13.5 Event Naming

Use lower-case dot-separated event names:

```text
organization.created
organization.updated
organization.page.published
membership.invited
membership.role_changed
team.created
tournament.created
fee.assigned
fee.payment_recorded
campaign.published
contribution.completed
credit.earned
credit.applied
credit.reversed
order.paid
order.submitted_to_fulfillment
order.shipped
order.refunded
payout.completed
```

Events must have:

- Event ID
- Event type
- Aggregate type
- Aggregate ID
- Organization ID where applicable
- Occurred timestamp
- Schema version
- Payload

---

# 14. Database Design Principles

## 14.1 General Rules

1. Every organization-owned table must include `organization_id`.
2. Every organization-owned query must verify membership or public visibility.
3. Use foreign keys.
4. Use explicit status constraints or reference tables.
5. Use immutable records for financial movements and audit history.
6. Use soft archival when history must be preserved.
7. Use database uniqueness to support idempotency.
8. Never use floating-point types for money.
9. Use `bigint` minor units and a three-character currency.
10. Keep personally identifying information to the minimum needed.

## 14.2 Core Entity Map

The full product may contain:

### Identity and Tenancy

- `app_user`
- `organization`
- `organization_membership`
- `invitation`
- `organization_setting`
- `subscription`

### Public Content

- `public_page`
- `page_section`
- `file_asset`
- `qr_code_reference`

### Sports Structure

- `team`
- `tournament`
- `tournament_team`
- `season`
- `division`

### Families and Participants

- `household`
- `household_adult`
- `participant`
- `participant_team`
- `guardian_relationship`

### Fees

- `fee_template`
- `fee_assignment`
- `payment_plan`
- `payment_schedule_item`
- `discount`
- `fee_adjustment`

### Fundraising and Credits

- `campaign`
- `campaign_attribution`
- `contribution`
- `credit_rule`
- `credit_event`
- `credit_application`

### Commerce

- `store`
- `store_collection`
- `product`
- `product_variant`
- `product_design`
- `cart`
- `cart_item`
- `order`
- `order_item`
- `fulfillment`
- `shipment`

### Payments and Finance

- `payment`
- `refund`
- `dispute`
- `ledger_entry`
- `payout`
- `reconciliation_run`

### Sponsorship

- `sponsor`
- `sponsor_contact`
- `sponsorship_package`
- `sponsorship`
- `sponsor_asset`
- `sponsor_placement`

### Platform Operations

- `webhook_event`
- `integration_connection`
- `integration_error`
- `outbox_event`
- `audit_event`
- `notification`
- `email_event`
- `feature_flag`

## 14.3 Foundation Tables

The first migration should include:

### `app_user`

- `id`
- `external_subject`
- `email`
- `display_name`
- `status`
- `created_at`
- `updated_at`

### `organization`

- `id`
- `name`
- `slug`
- `organization_type`
- `status`
- `created_at`
- `updated_at`

### `organization_membership`

- `id`
- `organization_id`
- `user_id`
- `role`
- `status`
- `created_at`
- `updated_at`
- Unique organization/user constraint

### `audit_event`

- `id`
- `actor_user_id`
- `organization_id`
- `action`
- `entity_type`
- `entity_id`
- `metadata`
- `created_at`

### `outbox_event`

- `id`
- `aggregate_type`
- `aggregate_id`
- `organization_id`
- `event_type`
- `schema_version`
- `payload`
- `status`
- `attempt_count`
- `available_at`
- `processed_at`
- `last_error`
- `created_at`

## 14.4 Public Page Model

A public page should have a stable identity separate from the organization, team, tournament, campaign, or store.

Suggested fields:

- `id`
- `organization_id`
- `page_type`
- `entity_id`
- `slug`
- `title`
- `summary`
- `status`
- `theme`
- `seo_title`
- `seo_description`
- `published_at`
- `created_at`
- `updated_at`

Statuses:

- `DRAFT`
- `PUBLISHED`
- `ARCHIVED`

A page may reference structured page sections. Do not store the entire critical business model in one unvalidated JSON blob.

## 14.5 Fee Model

### `fee_template`

Defines reusable organization fee types.

### `fee_assignment`

Represents a charge assigned to a household or participant.

Suggested fields:

- `id`
- `organization_id`
- `household_id`
- `participant_id`
- `fee_template_id`
- `description`
- `original_amount_minor`
- `currency`
- `due_date`
- `status`
- `created_at`
- `updated_at`

Statuses:

- `OPEN`
- `PARTIALLY_PAID`
- `PAID`
- `WAIVED`
- `CANCELLED`
- `REFUNDED`

Derived balances must be based on payment, credit, waiver, refund, and adjustment records rather than an editable balance field alone.

## 14.6 Credit Model

### `credit_rule`

Defines how an eligible transaction creates a credit.

Examples:

- 10% of attributed merchandise net sales
- Fixed $5 promotional credit
- 20% of attributed fundraising contribution after fees

### `credit_event`

Immutable event representing earned, reversed, expired, or adjusted credit.

Fields:

- `id`
- `organization_id`
- `household_id`
- `credit_rule_id`
- `source_type`
- `source_id`
- `event_type`
- `amount_minor`
- `currency`
- `available_at`
- `expires_at`
- `created_at`

Event types:

- `EARNED_PENDING`
- `MADE_AVAILABLE`
- `REVERSED`
- `EXPIRED`
- `MANUAL_ADJUSTMENT`

### `credit_application`

Represents applying available credit to a fee assignment.

Never overwrite the original credit event. Refunds and chargebacks create reversals.

## 14.7 Ledger Model

The financial ledger becomes the reporting source of truth before live marketplace activity.

Suggested fields:

- `id`
- `organization_id`
- `account_code`
- `entry_type`
- `amount_minor`
- `currency`
- `direction`
- `source_type`
- `source_id`
- `external_reference`
- `description`
- `effective_at`
- `created_at`

Potential entry types:

- `GROSS_SALE`
- `CONTRIBUTION`
- `SALES_TAX`
- `SHIPPING_COLLECTED`
- `PRODUCTION_COST`
- `FULFILLMENT_SHIPPING_COST`
- `PAYMENT_PROCESSING_FEE`
- `LEAGUELIFT_PLATFORM_FEE`
- `ORGANIZATION_EARNING`
- `TEAM_ALLOCATION`
- `HOUSEHOLD_CREDIT`
- `REFUND`
- `CHARGEBACK`
- `MANUAL_ADJUSTMENT`
- `TRANSFER`
- `PAYOUT`

Historical ledger entries are append-only. Corrections use reversing and replacement entries.

---

# 15. Core User Journeys

## 15.1 Founder or Platform Administrator Creates a Pilot Organization

1. Platform administrator signs in.
2. Creates or approves an organization.
3. Assigns an organization owner.
4. Configures pilot plan.
5. Reviews onboarding status.
6. Helps prepare branding and public pages.
7. Enables milestone-specific feature flags.
8. Reviews audit and integration status.

## 15.2 Organization Owner Onboards

1. Accepts adult administrator invitation.
2. Authenticates through the identity provider.
3. LeagueLift provisions the internal user.
4. User completes organization profile.
5. Selects organization type and sports.
6. Uploads logo and cover image.
7. Adds adult administrators.
8. Creates first team or tournament.
9. Previews public page.
10. Publishes the page.
11. Receives a public URL and QR code.

## 15.3 Team Administrator Creates a Team Page

1. Selects assigned organization.
2. Creates team.
3. Enters sport, season, division, and contact details.
4. Uploads approved assets.
5. Selects visible page sections.
6. Connects a fundraiser or store collection.
7. Previews page.
8. Publishes page.
9. Shares URL or QR code.

## 15.4 Tournament Administrator Creates a Tournament Page

1. Creates tournament.
2. Adds dates and venue information.
3. Adds participating teams.
4. Creates tournament page.
5. Adds merchandise collection.
6. Adds campaign or sponsor section.
7. Publishes page.
8. Uses QR codes in event materials.
9. Archives page after event while retaining reports.

## 15.5 Organization Assigns Fees

1. Creates fee template.
2. Selects household, participant, team, or group.
3. Sets amount and due date.
4. Optionally configures installments.
5. Reviews assignments.
6. Publishes assignments.
7. Adults receive notifications.
8. Payments, waivers, discounts, and credits update the fee position.
9. Organization views collection status.

## 15.6 Parent or Guardian Reviews Fees

1. Adult signs in.
2. Selects household.
3. Views linked participants.
4. Views fee assignments.
5. Reviews available credits.
6. Applies eligible credit.
7. Chooses permitted payment option.
8. Receives receipt.
9. Views updated status and history.

## 15.7 Organization Creates a Fundraiser

1. Creates campaign.
2. Selects organization or team ownership.
3. Sets purpose, goal, dates, and attribution policy.
4. Selects whether household attribution is allowed.
5. Selects credit rule.
6. Reviews public page.
7. Publishes campaign.
8. Shares campaign and QR code.
9. Monitors contributions and credits.
10. Closes and reports on campaign.

## 15.8 Supporter Contributes

1. Opens public campaign page.
2. Selects amount.
3. Optionally selects a team or permitted household attribution.
4. Chooses public-name or anonymous display.
5. Completes payment.
6. Receives a payment receipt.
7. Campaign progress updates after confirmed payment.
8. Eligible credit remains pending until the configured availability event.
9. Refunds or disputes reverse related credits.

## 15.9 Supporter Purchases Apparel

1. Opens a store or fundraising collection.
2. Chooses product and variant.
3. Optionally selects attribution.
4. Completes checkout.
5. Stripe confirms payment.
6. Order is recorded.
7. Fulfillment order is created asynchronously.
8. Production and shipment updates arrive through webhooks.
9. Customer receives updates.
10. Revenue, organization earnings, and eligible credits are recorded.
11. Refunds and cancellations produce reversing entries.

---

# 16. API Surface Plan

The OpenAPI contract is authoritative. Endpoints must be added milestone by milestone.

## 16.1 Foundation

```text
GET    /api/v1/public/status
POST   /api/v1/bootstrap
GET    /api/v1/me

GET    /api/v1/organizations
POST   /api/v1/organizations
GET    /api/v1/organizations/{organizationId}
PATCH  /api/v1/organizations/{organizationId}

GET    /api/v1/organizations/{organizationId}/members
POST   /api/v1/organizations/{organizationId}/invitations
PATCH  /api/v1/organizations/{organizationId}/members/{memberId}
DELETE /api/v1/organizations/{organizationId}/members/{memberId}
```

## 16.2 Teams and Tournaments

```text
GET    /api/v1/organizations/{organizationId}/teams
POST   /api/v1/organizations/{organizationId}/teams
GET    /api/v1/organizations/{organizationId}/teams/{teamId}
PATCH  /api/v1/organizations/{organizationId}/teams/{teamId}

GET    /api/v1/organizations/{organizationId}/tournaments
POST   /api/v1/organizations/{organizationId}/tournaments
GET    /api/v1/organizations/{organizationId}/tournaments/{tournamentId}
PATCH  /api/v1/organizations/{organizationId}/tournaments/{tournamentId}
```

## 16.3 Public Pages

```text
GET    /api/v1/public/pages/{slug}
GET    /api/v1/organizations/{organizationId}/pages
POST   /api/v1/organizations/{organizationId}/pages
PATCH  /api/v1/organizations/{organizationId}/pages/{pageId}
POST   /api/v1/organizations/{organizationId}/pages/{pageId}/publish
POST   /api/v1/organizations/{organizationId}/pages/{pageId}/unpublish
```

## 16.4 Households and Participants

```text
GET    /api/v1/organizations/{organizationId}/households
POST   /api/v1/organizations/{organizationId}/households
GET    /api/v1/organizations/{organizationId}/households/{householdId}
PATCH  /api/v1/organizations/{organizationId}/households/{householdId}

POST   /api/v1/organizations/{organizationId}/households/{householdId}/adults
POST   /api/v1/organizations/{organizationId}/households/{householdId}/participants
PATCH  /api/v1/organizations/{organizationId}/participants/{participantId}
```

## 16.5 Fees and Credits

```text
GET    /api/v1/organizations/{organizationId}/fee-templates
POST   /api/v1/organizations/{organizationId}/fee-templates

GET    /api/v1/organizations/{organizationId}/fee-assignments
POST   /api/v1/organizations/{organizationId}/fee-assignments
GET    /api/v1/organizations/{organizationId}/fee-assignments/{feeAssignmentId}
POST   /api/v1/organizations/{organizationId}/fee-assignments/{feeAssignmentId}/adjustments

GET    /api/v1/households/{householdId}/fees
GET    /api/v1/households/{householdId}/credits
POST   /api/v1/fee-assignments/{feeAssignmentId}/credit-applications
```

## 16.6 Fundraising

```text
GET    /api/v1/organizations/{organizationId}/campaigns
POST   /api/v1/organizations/{organizationId}/campaigns
GET    /api/v1/organizations/{organizationId}/campaigns/{campaignId}
PATCH  /api/v1/organizations/{organizationId}/campaigns/{campaignId}
POST   /api/v1/organizations/{organizationId}/campaigns/{campaignId}/publish

GET    /api/v1/public/campaigns/{slug}
POST   /api/v1/public/campaigns/{campaignId}/checkout-session
```

## 16.7 Commerce

```text
GET    /api/v1/public/stores/{slug}
GET    /api/v1/public/stores/{slug}/products

GET    /api/v1/organizations/{organizationId}/products
POST   /api/v1/organizations/{organizationId}/products
GET    /api/v1/organizations/{organizationId}/orders
GET    /api/v1/organizations/{organizationId}/orders/{orderId}

POST   /api/v1/public/carts
POST   /api/v1/public/carts/{cartId}/items
PATCH  /api/v1/public/carts/{cartId}/items/{itemId}
DELETE /api/v1/public/carts/{cartId}/items/{itemId}
POST   /api/v1/public/carts/{cartId}/checkout-session
```

## 16.8 Provider Webhooks

```text
POST /api/v1/webhooks/stripe
POST /api/v1/webhooks/printify
POST /api/v1/webhooks/email
```

Webhook endpoints must:

- Validate provider signatures
- Store the raw event safely
- Enforce unique external event IDs
- Return promptly
- Process asynchronously
- Support retries
- Record failures
- Never process the same provider event twice

---

# 17. Frontend Application Design

## 17.1 Application Areas

### Public

- Published organization page
- Published team page
- Published tournament page
- Published campaign page
- Published store
- Product detail
- Cart and checkout redirect
- Payment result
- Order lookup through secure token where appropriate

### Authenticated Organization Portal

- Dashboard
- Organization onboarding
- Branding
- Teams
- Tournaments
- Public pages
- Households
- Participants
- Fees
- Campaigns
- Stores and products
- Orders
- Credits
- Sponsors
- Reports
- Settings
- Members

### Parent or Guardian Portal

- Household dashboard
- Participants
- Fees
- Payment history
- Credits
- Fundraising links
- Orders
- Notifications
- Profile

### Platform Administration

- Organizations
- Users
- Pilot status
- Subscriptions
- Orders
- Payments
- Payouts
- Integrations
- Webhook failures
- Outbox failures
- Audit events
- Feature flags
- Support

## 17.2 Frontend Rules

- Use generated or typed API clients based on OpenAPI where practical.
- Do not invent endpoints in React.
- Use TanStack Query for server state.
- Keep local UI state separate from server state.
- Use React Hook Form and Zod for forms.
- Backend validation remains authoritative.
- All routes need loading, error, empty, unauthorized, and success behavior.
- Every destructive operation requires confirmation.
- Financial values must be formatted from integer minor units.
- Never expose provider secret keys.
- Never infer authorization only from route visibility.
- Public pages must be mobile-first.
- Organization tables must support search, pagination, and filters where volume warrants it.

## 17.3 App Navigation

Initial organization navigation:

```text
Overview
Organization
Teams
Tournaments
Pages
Households
Fees
Fundraising
Store
Orders
Reports
Members
Settings
```

Only show milestone-enabled modules. Use feature flags to prevent incomplete modules from appearing in production.

## 17.4 Dashboard Principles

Dashboards must answer:

- What needs attention?
- What money is outstanding?
- What campaigns are active?
- What changed recently?
- What should the user do next?

Avoid vanity charts without decisions or actions.

---

# 18. Authentication and Security

## 18.1 Authentication

- Managed OIDC provider
- Authorization Code Flow with PKCE for React
- JWT access tokens for API
- Issuer and audience validation
- Short-lived tokens
- Exact callback and origin configuration
- MFA for platform administrators
- Internal user record based on external subject

## 18.2 Authorization

- Every protected backend operation checks organization membership and role.
- Return `404` instead of revealing inaccessible resource existence where appropriate.
- Platform access is a separate permission, not inferred from email or frontend state.
- Support impersonation, if later added, must require a reason and create an audit event.
- Financial adjustments require elevated permission and immutable audit records.

## 18.3 Public Endpoints

Protect with:

- Input validation
- Rate limits
- Bot protection where needed
- Idempotency keys for submissions
- Minimal public responses
- No record enumeration
- No private IDs in predictable sequences
- Secure asset upload rules

## 18.4 Secrets

Store secrets in deployment environment variables or a secret manager.

Never commit:

- Database passwords
- Auth provider client secrets
- Stripe secret keys
- Stripe webhook secrets
- Printify tokens
- Email provider keys
- Sentry auth tokens
- Cloudflare tokens

`VITE_` variables are public browser configuration and must never contain secrets.

## 18.5 File Uploads

- Use signed upload URLs where practical.
- Restrict MIME types and file sizes.
- Generate unique storage keys.
- Do not trust filenames.
- Scan or validate uploaded files as the threat model requires.
- Keep private files private.
- Store metadata and ownership in PostgreSQL.
- Do not allow public overwrite of existing objects.

## 18.6 Security Headers

Configure:

- Content Security Policy
- Strict Transport Security
- `X-Content-Type-Options`
- Frame restrictions
- Referrer Policy
- Permissions Policy
- Secure cookies where used

## 18.7 Privacy

Create and maintain `docs/privacy-data-inventory.md`.

For every personal field, document:

- Purpose
- Owner
- Retention
- Visibility
- Export behavior
- Deletion behavior
- Whether it belongs to an adult or participant

Avoid logging:

- Access tokens
- Payment details
- Full provider payloads containing personal data
- Sensitive form contents
- Child data
- Secrets

---

# 19. Payment and Financial Design

## 19.1 Provider Responsibilities

Stripe should remain the source of truth for:

- Payment intent and charge status
- Refund status
- Disputes
- Connected account onboarding
- Transfers and payouts

LeagueLift maintains synchronized records, provider IDs, and an internal immutable ledger.

Printify remains the source of truth for:

- Provider product identifiers
- Production status
- Fulfillment status
- Shipment tracking

LeagueLift stores synchronized operational records and historical cost snapshots.

## 19.2 Payment Architecture Decision

Do not activate live multi-party payment routing until an ADR selects and documents the Stripe Connect charge model.

The ADR must evaluate:

- Destination charges
- Separate charges and transfers
- Direct charges
- Refund responsibility
- Dispute responsibility
- Negative balances
- Production-cost funding
- Platform fees
- Tax responsibilities
- Organization payout timing

## 19.3 Money Rules

- Store values as `bigint` minor units.
- Store currency separately.
- Never use `float` or `double`.
- Use a Money value object in Kotlin.
- Currency arithmetic requires matching currency.
- Rounding rules must be explicit.
- Pricing and allocation calculations require dedicated unit tests.
- Store transaction-time cost snapshots.

## 19.4 Reconciliation

Before live pilot:

- Match Stripe payment records to LeagueLift payments.
- Match Printify costs to orders.
- Match ledger entries to source records.
- Identify missing, duplicate, or conflicting events.
- Provide a platform-admin exception queue.
- Support daily automated reconciliation.
- Preserve reconciliation-run history.

## 19.5 Refunds and Chargebacks

- Never delete paid orders.
- Record refund requests and provider results.
- Create reversing ledger entries.
- Reverse related credits when appropriate.
- Handle partial refunds.
- Preserve original allocations.
- Notify affected organization users.
- Track dispute status.

---

# 20. Integration Architecture

Every external provider must be behind an interface.

Suggested adapters:

```text
IdentityProvider
PaymentProvider
ConnectedAccountProvider
FulfillmentProvider
EmailProvider
FileStorageProvider
AnalyticsProvider
```

## 20.1 Webhook Inbox

`webhook_event` fields:

- `id`
- `provider`
- `external_event_id`
- `event_type`
- `payload`
- `payload_hash`
- `signature_verified`
- `processing_status`
- `attempt_count`
- `related_entity_type`
- `related_entity_id`
- `received_at`
- `processed_at`
- `last_error`

Unique constraint:

```text
(provider, external_event_id)
```

## 20.2 Outbox

Use outbox events for:

- Email
- Fulfillment submission
- Notifications
- Analytics forwarding
- Reconciliation requests
- Campaign reminders
- Provider synchronization

Worker requirements:

- Claim work safely
- Retry with backoff
- Record errors
- Dead-letter after configured attempts
- Allow controlled reprocessing
- Provide admin visibility
- Remain idempotent

---

# 21. Notifications

Initial channels:

- Transactional email
- In-app notifications

Future:

- SMS
- Push notification

Notification events:

- Invitation
- Onboarding reminder
- Fee assigned
- Fee due
- Payment received
- Payment failed
- Credit earned
- Credit available
- Credit applied
- Campaign published
- Contribution received
- Order received
- Order shipped
- Refund completed
- Sponsor renewal

Email content must not contain unnecessary participant information.

---

# 22. Testing Strategy

## 22.1 Backend

Required:

- Unit tests for domain rules
- Repository integration tests with Testcontainers and PostgreSQL
- Controller/API tests
- Security tests
- Organization-isolation tests
- Migration tests
- Webhook signature tests
- Idempotency tests
- Outbox retry tests
- Money-calculation tests
- Credit reversal tests
- Ledger reconciliation tests
- Provider contract tests using stubs or sandboxes

## 22.2 Frontend

Required:

- Component tests
- Form tests
- Route protection tests
- Loading and error-state tests
- Accessibility checks
- Playwright end-to-end tests
- Mobile viewport tests
- Organization-isolation scenarios
- Checkout redirect and return tests in provider test mode

## 22.3 Critical Test Scenarios

1. User from Organization A cannot read Organization B.
2. Team administrator cannot access an unrelated team.
3. Public visitor cannot read private household data.
4. Duplicate webhook does not duplicate payment, order, credit, or ledger entries.
5. Refund reverses the correct credit.
6. Partial refund reverses proportionally according to documented rules.
7. Payment failure does not create available credit.
8. Fulfillment failure does not erase the paid order.
9. Historical ledger entries cannot be edited.
10. Test profile authentication bypass cannot run in production.
11. Public pages expose only approved fields.
12. Archived pages are no longer publicly accessible.
13. Money arithmetic never uses floating point.
14. Migration from a clean database succeeds.
15. Migration from the latest prior release succeeds.

---

# 23. Observability and Operations

## 23.1 Health

Provide:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

Readiness should reflect critical dependencies where appropriate without leaking details publicly.

## 23.2 Logging

Use structured logs containing:

- Timestamp
- Level
- Service
- Environment
- Request ID
- User ID where safe
- Organization ID where safe
- Event name
- Error code

Do not log tokens, secrets, raw card information, or unnecessary personal data.

## 23.3 Metrics

Track:

- Request rate and latency
- Error rate
- Authentication failures
- Database pool usage
- Outbox backlog
- Webhook backlog
- Webhook failures
- Email delivery failures
- Checkout starts and completions
- Order failures
- Reconciliation exceptions

## 23.4 Alerts

Initial alerts:

- API unavailable
- Readiness failures
- Elevated 5xx rate
- Database capacity
- Outbox backlog
- Repeated webhook failures
- Reconciliation mismatch
- Email provider failure
- Fulfillment submission failure

## 23.5 Backups

- Managed PostgreSQL automated backups
- Point-in-time recovery where available
- Regular CSV or secure export for critical business records
- Object-storage versioning where appropriate
- Documented restoration test
- Backup restoration tested before live payment launch

---

# 24. Environments

Required:

- Local
- Test
- Staging
- Production

Each environment must have separate:

- Database
- Auth application or tenant configuration
- Stripe mode and keys
- Printify shop or test configuration where available
- Email configuration
- Storage namespace
- Sentry environment
- Secrets

Production must never use the local authentication bypass.

Seed data must be clearly marked and must not appear in production unless deliberately created.

---

# 25. CI/CD

## 25.1 Pull Request Checks

Backend:

- Compile
- Unit tests
- Integration tests
- Static analysis
- Dependency scan
- Migration validation
- Build container

Frontend:

- Install with lockfile
- Type check
- Unit tests
- Lint
- Build
- Accessibility smoke checks
- Build container

Repository:

- Secret scan
- License/dependency review
- OpenAPI validation
- Markdown link check where practical

## 25.2 Deployment

Recommended:

1. Merge to main.
2. Build immutable container images.
3. Deploy to staging.
4. Run migrations.
5. Run smoke tests.
6. Require manual approval for production during pilot.
7. Deploy backend with readiness checks.
8. Deploy frontend.
9. Run production smoke tests.
10. Monitor errors and metrics.

Database migrations should be forward-compatible. Do not assume destructive rollback.

---

# 26. Development Standards

## 26.1 Kotlin

- Prefer immutable data.
- Use explicit nullability.
- Use constructor injection.
- Keep controllers thin.
- Put business rules in domain/application services.
- Use sealed classes or enums for controlled states.
- Use value objects for Money, Slug, Email, and external IDs where useful.
- Avoid generic `Map<String, Any>` for core domain behavior.
- Use typed errors.
- Use transactions deliberately.
- Add KDoc for non-obvious public APIs and financial behavior.

## 26.2 SQL

- Name constraints and indexes.
- Include organization filters.
- Review query plans for reporting and high-volume endpoints.
- Avoid hidden N+1 patterns.
- Use database constraints to protect invariants.
- Use migration filenames that explain the change.
- Never rewrite an already-applied production migration.

## 26.3 TypeScript and React

- Strict TypeScript.
- Avoid `any`.
- Prefer feature-based modules.
- Keep API calls in a shared client layer.
- Use semantic components.
- Do not duplicate server truth into global stores unnecessarily.
- Use query keys consistently.
- Handle cancellation and stale data.
- Centralize formatting for money, dates, statuses, and names.
- Never place secrets in frontend code.

## 26.4 Documentation

Update with each milestone:

- `DESIGN-DOC.md`
- `docs/openapi.yaml`
- Relevant ADR
- Setup instructions
- Environment-variable reference
- Launch checklist
- Data inventory
- Operational runbook

---

# 27. AI Agent Operating Instructions

These instructions are mandatory for Claude and all coding agents.

## 27.1 Before Coding

1. Read this entire design document.
2. Read the repository README.
3. Read `docs/openapi.yaml`.
4. Read existing ADRs.
5. Inspect the current code before proposing changes.
6. Identify the active milestone.
7. Produce a concise implementation plan.
8. Identify schema, API, UI, security, and test changes.
9. Do not ask the user to repeat information already present here.
10. Do not begin unrelated future modules.

## 27.2 During Coding

1. Implement one vertical slice at a time.
2. Keep the backend a modular monolith.
3. Do not invent API endpoints without updating OpenAPI.
4. Do not place authorization logic only in React.
5. Add backend membership checks to every organization-owned query.
6. Add tests with every behavior change.
7. Use Flyway for every database change.
8. Never store money as floating point.
9. Never edit historical ledger or credit events.
10. Never process the same webhook twice.
11. Never expose secrets.
12. Never create child accounts without a new approved design decision.
13. Never add sensitive participant fields casually.
14. Keep external providers behind adapters.
15. Use feature flags for incomplete production features.
16. Preserve loading, empty, error, unauthorized, and success states.
17. Maintain accessibility.
18. Avoid unrelated refactors.
19. Avoid adding libraries unless they clearly reduce risk or complexity.
20. Record material architecture changes in an ADR.

## 27.3 Before Completing a Task

1. Run relevant backend tests.
2. Run frontend type checks and tests.
3. Run integration tests.
4. Verify migrations.
5. Verify organization isolation.
6. Verify no secrets were committed.
7. Verify OpenAPI matches behavior.
8. Verify error states.
9. Verify mobile behavior where relevant.
10. Update documentation.
11. Summarize changes, tests, and remaining limitations.

## 27.4 Agent Prohibitions

Agents must not:

- Replace Kotlin with Node, Python, or another backend
- Replace PostgreSQL
- Introduce microservices
- Introduce Kubernetes
- Add a new global-state library without justification
- Build child login accounts
- Represent credits as cash
- Claim donations are tax deductible
- Create fake transactions in production
- Process live payments without launch approval
- Store raw payment-card details
- Store secrets in source control
- Trust provider webhooks without signature validation
- Delete financial history
- Bypass role checks for convenience
- silently change pricing or allocation rules
- use AI-generated placeholder claims as real customer evidence

---

# 28. Architecture Decision Records

Create ADRs for material choices.

Initial ADRs:

- ADR-001: Modular monolith
- ADR-002: Managed OIDC authentication
- ADR-003: PostgreSQL and Flyway
- ADR-004: JDBC first, jOOQ before finance/reporting
- ADR-005: Stripe Connect charge model
- ADR-006: Transactional outbox
- ADR-007: Immutable ledger and credit events
- ADR-008: DigitalOcean deployment
- ADR-009: Adult-controlled household accounts
- ADR-010: Family credits as non-withdrawable fee credits
- ADR-011: Public-page content model
- ADR-012: File storage and upload security

ADR format:

```markdown
# ADR-NNN: Title

## Status
Proposed | Accepted | Superseded

## Context

## Decision

## Consequences

## Alternatives Considered
```

---

# 29. Initial Acceptance Criteria

## 29.1 Repository Foundation

- New private repository exists.
- README contains local setup.
- `DESIGN-DOC.md` is committed.
- Backend and frontend build.
- Docker Compose starts PostgreSQL, backend, and frontend.
- CI passes.
- No secrets are committed.
- `.env.example` documents required variables.

## 29.2 Authentication

- Production profile validates OIDC JWT issuer and audience.
- Local bypass is isolated to local profile.
- Current user can be provisioned.
- Invalid or missing production token is rejected.
- Platform-admin permission is not inferred from email.
- Tests cover authentication configuration.

## 29.3 Organizations

- Authenticated user can create an organization.
- Creator receives owner membership.
- User can list only accessible organizations.
- User cannot access another organization.
- Slugs are unique and normalized.
- Create and membership actions are audited.
- OpenAPI documents endpoints.

## 29.4 Reliability

- Request IDs appear in responses.
- Errors use standard envelope.
- Health, liveness, and readiness endpoints work.
- Flyway migration runs on clean database.
- Outbox table exists.
- Audit table exists.
- Production logs do not expose stack traces to clients.

## 29.5 Frontend

- Public landing shell loads.
- Sign-in works in production configuration.
- Local development mode works without production credentials.
- Protected routes redirect or show unauthorized state.
- Organization list and creation work.
- Loading, empty, error, and success states exist.
- Keyboard navigation works.
- Mobile layout is usable.

---

# 30. Product Milestone Acceptance Criteria

## 30.1 Organization and Team Pages

A pilot organization can:

- Complete profile
- Upload logo
- Create team
- Create tournament
- Create corresponding public pages
- Preview pages
- Publish pages
- Receive working public URLs
- Generate QR codes
- Unpublish or archive pages
- Control which contact information is public

## 30.2 Dues and Fees

An authorized adult administrator can:

- Create fee template
- Assign fee
- View household balance
- Record payment or adjustment
- Apply manual discount
- Apply approved credit
- Export collections
- See audit history

A guardian can:

- View only their household
- View fees
- View payments
- View credits
- View participants
- Not access another household

## 30.3 Fundraising

An administrator can:

- Create campaign
- Configure attribution
- Publish campaign
- Share link and QR code
- View confirmed contributions
- View pending and available credits
- Close campaign

A supporter can:

- View campaign
- Choose permitted attribution
- Complete test-mode checkout
- Receive confirmation
- Not access private family information

## 30.4 Apparel

A supporter can:

- View products
- Select variant
- Add to cart
- Complete test checkout
- Receive order record
- Receive fulfillment status

The system:

- Does not duplicate orders on repeated webhook
- Stores transaction-time cost
- Calculates configured allocations
- Creates pending credits
- Reverses credits for refunds

---

# 31. Launch Gates

## 31.1 Base-Layer Internal Launch

Required:

- CI green
- Local setup documented
- Staging environment working
- Authentication verified
- Organization isolation verified
- Backups configured
- Error monitoring configured
- Support email working

## 31.2 Pilot Organization Launch

Required:

- Organization onboarding
- Public pages
- Role management
- Audit history
- Privacy policy and terms reviewed
- Adult-account boundary stated
- Support process documented
- Pilot feature flags configured
- Mobile test completed

## 31.3 Live Payments Launch

Required:

- Stripe test suite completed
- Stripe Connect ADR accepted
- Webhook signature verification
- Idempotency verified
- Immutable ledger implemented
- Refund and dispute workflows
- Reconciliation dashboard
- Production secrets configured
- Payment terms and refund policies reviewed
- Accounting and legal review completed
- Backup restoration tested
- Incident response runbook created
- One controlled live transaction and refund rehearsed

## 31.4 Live Fulfillment Launch

Required:

- Printify test orders successful
- Product and variant mapping
- Cost snapshots
- Shipment webhook processing
- Cancellation and reprint handling
- Customer-notification workflow
- Fulfillment exception queue
- Support process

---

# 32. Non-Functional Requirements

## 32.1 Performance

Initial targets:

- Public page usable on mobile broadband
- API p95 response below 500 ms for normal non-provider requests under pilot load
- Paginate list endpoints
- Optimize images
- Avoid blocking provider calls in user requests when asynchronous processing is appropriate

## 32.2 Availability

- Managed database
- Health checks
- Automated deployment rollback for application images
- No destructive migration rollback assumption
- Background retries
- Provider failure isolation

## 32.3 Maintainability

- One repository
- One backend deployment
- Clear modules
- Strong tests around money and permissions
- OpenAPI contract
- ADRs
- Limited dependency count
- Automated updates and scans

## 32.4 Scalability

The architecture should support early growth through:

- Horizontal backend scaling
- Stateless API instances
- Managed PostgreSQL scaling
- Background workers
- Object storage
- Paginated queries
- Provider webhook queues

Do not optimize for millions of users before pilot evidence.

---

# 33. Open Questions Requiring Future Decisions

The following decisions should remain explicit rather than being guessed by agents:

1. Final LeagueLift domain and trademark clearance
2. Auth0 versus another OIDC provider before production
3. Stripe Connect charge model
4. Whether LeagueLift or organizations are merchant of record for each flow
5. Tax calculation and remittance approach
6. Exact refund policy
7. Exact credit percentages and availability delays
8. Whether household attribution uses public names, codes, or private links
9. How participant imports will work
10. Whether tournament operators can operate independently of a parent organization
11. Production Printify shop model
12. Fulfillment-provider fallback
13. Subscription-plan tiers
14. Support impersonation policy
15. Data retention and deletion schedule
16. Future accounting integrations
17. Whether family fee credits may cross seasons
18. Whether credits expire
19. Whether manual offline payments are permitted
20. Whether sponsorship purchases use the same payment account as merchandise

Agents should implement configurable abstractions where practical, but must not create unnecessary frameworks for unresolved questions.

---

# 34. First Claude Agent Assignment

The first Claude agent should perform the following work:

## Step 1: Repository Creation

- Create a new private repository named `leaguelift`.
- Add this file as `DESIGN-DOC.md`.
- Add a clear README.
- Create the monorepo structure.
- Copy or recreate the useful foundation from the provided `leaguelift-starter` repository.
- Do not copy generated build outputs or secrets.
- Preserve the chosen Kotlin, Spring Boot, PostgreSQL, React, and Vite stack.

## Step 2: Foundation Review

- Review starter architecture and dependencies.
- Identify any incompatibilities.
- Create ADR-001 through ADR-004.
- Create an initial implementation plan.
- Do not implement future commerce modules.

## Step 3: Backend Foundation

- Create Spring Boot application.
- Configure profiles.
- Configure JWT validation.
- Implement internal user provisioning.
- Implement organizations and memberships.
- Implement request IDs and standard errors.
- Add Flyway foundation migration.
- Add audit and outbox foundations.
- Add health endpoints.
- Add tests.

## Step 4: Frontend Foundation

- Create React application.
- Configure managed authentication.
- Add local development mode.
- Add protected layout.
- Add dashboard.
- Add organization list and creation.
- Add design tokens.
- Add accessible loading, empty, error, and unauthorized states.
- Add tests.

## Step 5: Contracts and Operations

- Create `docs/openapi.yaml`.
- Create DigitalOcean deployment documentation.
- Create GitHub Actions.
- Create Dockerfiles and Compose.
- Create environment-variable documentation.
- Create launch checklist.
- Create AI-agent guardrails.
- Confirm all tests pass.

## Step 6: Deliverable Report

Return:

- Repository structure
- Implemented features
- Test results
- Environment variables
- Known limitations
- Recommended next vertical slice
- Any ADRs requiring founder approval

---

# 35. Recommended First Vertical Slices After Foundation

Develop in this order:

1. Organization onboarding
2. File uploads and branding
3. Team creation
4. Tournament creation
5. Public-page draft and publish
6. Adult administrator invitations
7. Platform-administrator console
8. Household and participant model
9. Fee templates and assignments
10. Parent dashboard
11. Fundraising campaign
12. Test-mode payments
13. Credit rules
14. Apparel catalog and store
15. Fulfillment test integration
16. Immutable ledger
17. Stripe Connect
18. Live controlled pilot

Each slice must include:

- Migration
- Domain behavior
- Repository
- API
- OpenAPI update
- Authorization
- React UI
- Error states
- Tests
- Audit events
- Documentation

---

# 36. Final Product Principle

LeagueLift should grow from a focused revenue platform, not from an attempt to replicate every youth-sports product at once.

The implementation should prioritize:

1. Secure organization isolation
2. Clear public pages
3. Trustworthy fee and credit calculations
4. Reliable fundraising and commerce
5. Transparent financial reporting
6. Low administrative burden
7. Adult-controlled youth data
8. Simple pilot operations
9. Repeatable onboarding
10. Evidence-driven expansion

When there is a conflict between speed and irreversible financial or privacy risk, choose the safer design.

When there is a conflict between completeness and a testable pilot workflow, choose the smallest complete workflow.

When there is a conflict between agent convenience and this document, follow this document.
