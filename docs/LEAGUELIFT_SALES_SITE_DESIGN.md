# LeagueLift Sales Site and Authentication Design Specification

**Document status:** Implementation-ready design specification  
**Version:** 1.0  
**Product:** LeagueLift  
**Primary audience:** Claude Code, frontend agents, backend agents, designers, QA agents, and the founder  
**Related document:** `DESIGN-DOC.md`  
**Visual reference:** `docs/design/leaguelift-sales-site-concept.png`  
**Reference image dimensions:** 1672 × 941  
**Implementation target:** React, TypeScript, Vite, React Router, TanStack Query, React Hook Form, Zod, and the LeagueLift Kotlin/Spring Boot API

---

## 1. Purpose

This document defines the visual design, information architecture, content hierarchy, pages, forms, controls, responsive behavior, interaction states, accessibility requirements, analytics events, and implementation boundaries for the LeagueLift public sales site and authentication experience.

The selected visual direction is a premium, dark sports-technology design characterized by:

- Deep navy backgrounds
- Bright green conversion actions
- Select championship-gold accents
- Multi-sport action imagery
- Bold, concise typography
- Dark glass-like cards
- Clean white content sections
- High contrast
- Rounded corners
- Subtle glow and depth
- Strong visual separation between public marketing and authenticated access

This specification is the source of truth for implementation. The reference image is a visual guide, not a source of factual business claims. Text, statistics, partner logos, and feature statements in the image must be replaced with the approved content and rules in this document.

---

# 2. Product Positioning

## 2.1 Brand Name

**LeagueLift**

## 2.2 Primary Tagline

**More revenue. Lower fees. Stronger programs.**

## 2.3 Primary Product Description

LeagueLift is a revenue and payment-management platform for youth sports organizations.

It helps leagues, clubs, teams, tournaments, and booster organizations create public pages, run fundraisers, sell apparel, manage dues and fees, and apply approved sales-based credits to family balances.

## 2.4 Core Differentiator

LeagueLift does not initially replace scheduling, registration, roster-management, or team-communication systems.

Preferred positioning:

> Your sports software runs the season. LeagueLift helps fund it.

## 2.5 Primary Audiences

- League presidents
- Club directors
- Tournament directors
- Treasurers
- Booster officers
- Team managers
- Fundraising coordinators
- Merchandise coordinators
- Adult parents and guardians

## 2.6 Primary Conversion Goal

During the founding-pilot stage, the primary website conversion is:

> **Apply for the Founding Pilot**

The secondary conversion is:

> **Book a Demo**

The tertiary conversion is:

> **See How It Works**

After the pilot stage, these labels may be changed through configuration rather than hard-coded throughout the site.

---

# 3. Truthfulness and Early-Stage Rules

The reference image contains example statistics and trust marks. These must not appear as real claims unless verified.

Do not publish:

- Fake customer counts
- Fake athlete counts
- Fake average revenue gains
- Fake fee-reduction percentages
- Fake partner logos
- Fake association logos
- Fake testimonials
- Fake review scores
- Fake “trusted by” statements
- Unsupported “bank-level security” language
- Unsupported claims of direct integration
- Guaranteed fundraising results
- Claims that contributions are tax-deductible
- Claims that all listed features are already live

During the pre-pilot and pilot stages, use transparent language such as:

- “Now accepting founding-pilot applications”
- “Designed for leagues, clubs, teams, tournaments, and booster organizations”
- “Planned for the full LeagueLift platform”
- “Available to selected pilot organizations”
- “Works alongside the tools your organization already uses”
- “Results depend on organization size, participation, pricing, and campaign activity”

No public metric may be displayed without a documented source and founder approval.

---

# 4. Design Direction

## 4.1 Visual Theme

The preferred design is the dark premium option shown in the reference image.

The public homepage uses:

- Dark navy hero
- Multi-sport athlete collage
- Bright green calls to action
- Dark metric or benefit cards
- White feature section
- Dark “How It Works” section
- Strong final conversion band

The authentication page uses:

- Large visual brand panel
- Stadium or multi-sport atmosphere
- Dark form card
- Green action buttons
- Clear Sign In and Create Account navigation
- Security and adult-account messaging
- Minimal distraction

## 4.2 Emotional Goals

The design should communicate:

- Momentum
- Trust
- Energy
- Community
- Financial clarity
- Operational competence
- Multi-sport inclusivity
- Seriousness without looking corporate or cold

## 4.3 Visual Restraint

Avoid:

- Excessive neon effects
- Constant animation
- Cyberpunk styling
- Dense dashboards on the public site
- Too many gradients
- Oversized generic sports icons
- Single-sport imagery
- Childish mascots
- Cartoon styling
- Fake glass effects that reduce contrast
- Text directly over visually busy imagery without overlays

---

# 5. Design Tokens

## 5.1 Color Palette

### Brand Colors

| Token | Hex | Use |
|---|---:|---|
| `navy-950` | `#061321` | Deepest backgrounds |
| `navy-900` | `#0B1F33` | Primary navy |
| `navy-800` | `#102B46` | Cards and elevated dark surfaces |
| `navy-700` | `#173B5C` | Borders and active dark controls |
| `green-600` | `#159957` | Pressed or dark green |
| `green-500` | `#20B26B` | Primary LeagueLift green |
| `green-400` | `#3CCF83` | Hover and highlight green |
| `gold-500` | `#F4B740` | Championship gold |
| `gold-400` | `#FFC95C` | Gold hover |
| `ice-50` | `#F7F9FC` | Main light background |
| `white` | `#FFFFFF` | Cards and high-contrast text |
| `slate-700` | `#526275` | Secondary light-surface text |
| `slate-500` | `#76869A` | Muted text |
| `slate-300` | `#C9D2DD` | Light borders |
| `slate-200` | `#DDE4EC` | Input and card borders |
| `error-600` | `#C93636` | Error states |
| `warning-600` | `#BC7B00` | Warning text |
| `info-600` | `#2F6FED` | Informational states |
| `success-700` | `#117A46` | Accessible success text |

### Dark Surface Text

- Primary: `#FFFFFF`
- Secondary: `#D2DCE7`
- Muted: `#A8B7C7`
- Disabled: `#6F8295`

### Light Surface Text

- Primary: `#0B1F33`
- Secondary: `#526275`
- Muted: `#76869A`
- Disabled: `#9AA8B7`

## 5.2 Gradient Tokens

Use gradients sparingly.

### Hero Background

```css
background:
  radial-gradient(circle at 65% 35%, rgba(32, 178, 107, 0.18), transparent 34%),
  linear-gradient(135deg, #061321 0%, #0B1F33 58%, #102B46 100%);
```

### Final CTA

```css
background:
  radial-gradient(circle at 75% 50%, rgba(32, 178, 107, 0.20), transparent 30%),
  linear-gradient(90deg, #061321 0%, #0B1F33 62%, #0C3A2A 100%);
```

### Authentication Brand Panel

```css
background:
  linear-gradient(180deg, rgba(6, 19, 33, 0.72), rgba(6, 19, 33, 0.95)),
  url(...);
```

## 5.3 Typography

### Font Families

- Heading: `Manrope`
- Body and interface: `Inter`
- Fallback: `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`

### Desktop Type Scale

| Style | Size | Line height | Weight |
|---|---:|---:|---:|
| Display 1 | 64 px | 1.04 | 800 |
| Display 2 | 52 px | 1.08 | 800 |
| H1 | 44 px | 1.12 | 800 |
| H2 | 36 px | 1.18 | 750 |
| H3 | 28 px | 1.24 | 700 |
| H4 | 22 px | 1.30 | 700 |
| Body Large | 18 px | 1.60 | 400 |
| Body | 16 px | 1.60 | 400 |
| Body Small | 14 px | 1.55 | 400 |
| Label | 13 px | 1.40 | 600 |
| Caption | 12 px | 1.45 | 500 |

### Mobile Type Scale

- Main hero: 42 px
- H1: 36 px
- H2: 30 px
- H3: 24 px
- Body large: 17 px
- Body: 16 px

Headings may use balanced text wrapping. Do not use all-uppercase text for long headings.

## 5.4 Spacing Scale

Use an 8 px base.

```text
4, 8, 12, 16, 24, 32, 40, 48, 64, 80, 96, 120
```

Section spacing:

- Desktop: 96–120 px vertical
- Tablet: 72–88 px
- Mobile: 56–72 px

## 5.5 Border Radius

| Component | Radius |
|---|---:|
| Small control | 8 px |
| Input | 10 px |
| Button | 10 px |
| Standard card | 16 px |
| Large card | 22 px |
| Hero media | 24 px |
| Modal | 24 px |
| Pill | 999 px |

## 5.6 Borders

Dark cards:

```css
border: 1px solid rgba(201, 210, 221, 0.16);
```

Light cards:

```css
border: 1px solid #DDE4EC;
```

Green selected state:

```css
border: 1px solid rgba(32, 178, 107, 0.70);
```

## 5.7 Shadows

### Light Card

```css
box-shadow: 0 12px 34px rgba(11, 31, 51, 0.10);
```

### Dark Elevated Card

```css
box-shadow:
  0 22px 60px rgba(0, 0, 0, 0.32),
  inset 0 1px 0 rgba(255, 255, 255, 0.04);
```

### Focus Ring

```css
outline: 3px solid rgba(60, 207, 131, 0.34);
outline-offset: 2px;
```

## 5.8 Motion

- Standard transition: 160–220 ms
- Large content reveal: 300–420 ms
- Easing: `cubic-bezier(0.2, 0.8, 0.2, 1)`
- Buttons may shift upward by 1 px on hover.
- Cards may increase border brightness and move upward by 2 px.
- Respect `prefers-reduced-motion`.
- Do not autoplay dramatic video.
- Do not animate counters unless values are real.

---

# 6. Responsive Layout

## 6.1 Breakpoints

| Name | Minimum width |
|---|---:|
| Mobile | 0 |
| Small tablet | 640 px |
| Tablet | 768 px |
| Desktop | 1024 px |
| Wide desktop | 1280 px |
| Maximum content | 1440 px |

## 6.2 Content Width

```css
width: min(100% - 40px, 1360px);
margin-inline: auto;
```

Mobile horizontal padding: 20 px  
Tablet horizontal padding: 32 px  
Desktop horizontal padding: 48 px where needed

## 6.3 Responsive Rules

- Header becomes a hamburger menu below 1024 px.
- Hero becomes stacked below approximately 900 px.
- Feature bento grid becomes two columns on tablet and one column on mobile.
- Authentication split layout becomes a single form view below 900 px.
- Authentication visual panel may become a compact top brand banner on mobile.
- Tables must become cards or horizontally scrollable layouts.
- No horizontal page scrolling.
- Primary actions must remain visible and easy to tap.
- Minimum touch target: 44 × 44 px.

---

# 7. Global Information Architecture

## 7.1 Public Routes

```text
/
 /how-it-works
 /solutions
 /solutions/team-pages
 /solutions/tournament-pages
 /solutions/fundraising
 /solutions/apparel
 /solutions/dues-and-fees
 /solutions/family-credits
 /solutions/sponsorships
 /pricing
 /founding-pilot
 /about
 /contact
 /security
 /help
 /privacy
 /terms
 /accessibility
 /404
```

## 7.2 Authentication Routes

```text
/auth/sign-in
/auth/register
/auth/forgot-password
/auth/reset-password
/auth/verify-email
/auth/invitation
/auth/callback
/auth/error
```

## 7.3 Application Handoff

```text
/app
```

The sales-site implementation may show a minimal authenticated handoff page until the application dashboard is complete.

## 7.4 Public Organization Content

These pages belong to the full application but should share the public design system:

```text
/o/:organizationSlug
/o/:organizationSlug/teams/:teamSlug
/o/:organizationSlug/tournaments/:tournamentSlug
/campaigns/:campaignSlug
/stores/:storeSlug
```

---

# 8. Global Header

## 8.1 Desktop Header

Height: 72–80 px  
Position: sticky  
Background: `navy-950` with approximately 94% opacity  
Backdrop blur: 12 px  
Bottom border: subtle white alpha

### Left

- LeagueLift logo
- Logo links to `/`
- Full mark plus wordmark on desktop
- Compact mark permitted on narrower layouts

### Primary Navigation

- Product
- Solutions
- Resources
- Pricing
- About

Preferred implementation:

```text
Product
  Overview
  How It Works
  Features

Solutions
  Team Pages
  Tournament Pages
  Fundraising
  Apparel Stores
  Dues & Fees
  Family Credits
  Sponsorships

Resources
  Founding Pilot
  Help Center
  Security
  Contact
```

### Right

- `Log In` text button
- Primary CTA: `Apply for Pilot`

After the founding-pilot phase, the primary CTA may be configured as `Book a Demo` or `Get Started`.

## 8.2 Mobile Header

- Logo on left
- Menu button on right
- Sticky
- Menu opens full-height navy drawer
- Drawer includes all navigation and both actions
- The primary CTA is full width
- Close button receives initial focus
- Escape closes the menu
- Body scrolling is locked while open

## 8.3 Header Scroll State

At top:

- 80 px height
- Transparent or deep navy

After scrolling:

- 68–72 px height
- More opaque background
- Slight shadow
- Logo and controls remain unchanged in meaning

---

# 9. Global Footer

Use `navy-950`.

## 9.1 Footer Columns

### Brand

- LeagueLift logo
- Tagline
- One-sentence description
- Social icons remain hidden until real profiles exist

### Product

- Features
- How It Works
- Pricing
- Founding Pilot

### Solutions

- Leagues
- Clubs
- Teams
- Tournaments

### Resources

- Help
- Security
- Contact
- Status, when available

### Company

- About
- Privacy
- Terms
- Accessibility

## 9.2 Newsletter

Do not display a newsletter form unless email subscription storage and consent language are implemented.

When implemented:

- Email
- Consent note
- Submit button
- Success and error messages
- No pre-checked marketing consent

## 9.3 Footer Legal Row

- Current year
- `LeagueLift`
- Privacy
- Terms
- Accessibility
- Security

Do not include `Inc.` unless the company has been incorporated under that name.

---

# 10. Button System

## 10.1 Primary Button

Use green.

Examples:

- Apply for Pilot
- Submit Application
- Continue
- Sign In
- Create Account
- Book a Demo

States:

- Default: `green-500`
- Hover: `green-400`
- Active: `green-600`
- Disabled: 45% opacity
- Loading: spinner and preserved width

## 10.2 Secondary Dark Button

- Transparent navy
- Light border
- White text
- Hover border becomes green

Examples:

- See How It Works
- View Features
- Compare Plans

## 10.3 Secondary Light Button

- White background
- Navy border
- Navy text
- Green hover accent

## 10.4 Gold Button

Use only for selected pilot or premium emphasis.

Examples:

- Apply for Founding Pilot
- Review Pilot Benefits

Do not use gold for ordinary submit actions.

## 10.5 Text Button

Examples:

- Log In
- Forgot password?
- Learn more
- Back
- Skip for now

## 10.6 Destructive Button

- Red
- Requires confirmation for irreversible actions
- Not common on the public site

## 10.7 Icon Rules

- Right arrow for forward navigation
- External-link icon for external destinations
- Play icon only when opening actual video or guided presentation
- Loading spinner replaces or precedes the icon
- Decorative icons receive `aria-hidden="true"`

---

# 11. Form System

## 11.1 Inputs

Height: 48–52 px  
Radius: 10 px  
Label above field  
Helper text below field  
Required fields indicated with text or accessible indicator, not color alone

## 11.2 Input States

- Default
- Hover
- Focus
- Valid
- Invalid
- Disabled
- Read-only
- Loading for searchable fields

## 11.3 Validation

Use:

- React Hook Form
- Zod client schema
- Server validation as final authority

Errors:

- Appear below the relevant field
- Use plain language
- Are linked through `aria-describedby`
- Do not clear entered values unnecessarily
- Focus the first invalid field after submit
- Include a form-level summary when there are multiple errors

## 11.4 Password Controls

- Show/hide password
- Password manager support
- Allow paste
- Do not block browser-generated passwords
- Strength guidance on registration
- Do not impose arbitrary symbol-only rules unless required by the identity provider

## 11.5 Loading and Success

- Disable double submission
- Preserve button width
- Show inline loading state
- Show explicit success message
- Use idempotency protection on applicable backend actions

---

# 12. Homepage Specification

Route: `/`

## 12.1 Announcement Bar

Optional during the pilot.

Text:

> Founding-pilot applications are now open.

Action:

> Apply Now

Style:

- Dark green or gold-accent strip
- Dismissible per browser session
- Must not create fake scarcity

## 12.2 Hero

### Eyebrow

> Built for youth sports organizations

### Headline

> More revenue.  
> Lower fees.  
> **Stronger programs.**

The green emphasis should be applied to `Stronger programs.`

Alternative approved headline:

> Turn every season into more revenue.

Use one headline consistently per release.

### Supporting Copy

> LeagueLift helps youth sports organizations create public team and tournament pages, run fundraisers, sell apparel, manage dues, and apply approved sales-based credits to family fees.

### Primary CTA

> Apply for Pilot

Links to `/founding-pilot`.

### Secondary CTA

> See How It Works

Links to `/how-it-works`.

### Tertiary Action

> Log In

Available in header, not required in hero.

### Hero Visual

Use a multi-sport composition representing at least four sports.

Preferred sports:

- Soccer
- Hockey
- Baseball or softball
- Basketball
- Volleyball or lacrosse where room permits

Requirements:

- Authentic action
- Licensed imagery
- No professional team marks
- No fake LeagueLift uniforms unless clearly illustrative
- Avoid placing real children beside claims that imply endorsement
- Use a dark image overlay for readable text
- Maintain crop focal points across breakpoints

### Below-Hero Benefit Cards

Do not use fake statistics.

Use four non-numeric benefit cards:

1. **Team & Tournament Pages**  
   Public pages built to inform and convert supporters.

2. **Fundraising & Apparel**  
   Campaigns and merchandise tied to real program goals.

3. **Dues & Family Credits**  
   Clear balances with approved opportunities to reduce fees.

4. **Revenue Reporting**  
   One view of campaigns, orders, fees, and credits.

Each card includes:

- Simple line icon
- Heading
- One-sentence description
- Dark elevated styling

## 12.3 Positioning Section

Light background.

### Heading

> Your sports software runs the season. LeagueLift helps fund it.

### Supporting Copy

> Keep the tools you use for registration, scheduling, and team communication. LeagueLift adds public pages, commerce, fundraising, dues, credits, and revenue reporting.

### Comparison

#### Existing Sports Tools

- Registration
- Scheduling
- Rosters
- Communication

#### LeagueLift

- Public team and tournament pages
- Fundraising
- Apparel stores
- Dues and fee tracking
- Family credits
- Revenue reporting

Do not describe integrations as active unless implemented.

## 12.4 Feature Bento Grid

Use a light background with dark cards, matching the reference.

### Card 1: Team Pages

**Heading:** Team Pages  
**Copy:** Give each team a branded home for updates, fundraising, apparel, sponsors, and supporter links.  
**Action:** Explore Team Pages

### Card 2: Tournament Pages

**Heading:** Tournament Pages  
**Copy:** Promote an event, highlight participating teams, sell tournament apparel, and recognize sponsors.  
**Action:** Explore Tournament Pages

### Card 3: Digital Fundraising

**Heading:** Fundraising  
**Copy:** Launch organization- or team-specific campaigns with goals, sharing links, QR codes, and attribution.  
**Action:** Explore Fundraising

### Card 4: Apparel Stores

**Heading:** Apparel Stores  
**Copy:** Sell organization, team, season, and tournament merchandise without requiring large inventory purchases.  
**Action:** Explore Apparel

### Card 5: Dues & Fees

**Heading:** Dues & Fees  
**Copy:** Assign, collect, and track registration fees, team dues, tournament costs, uniforms, travel, and more.  
**Action:** Explore Dues & Fees

### Card 6: Family Credits

**Heading:** Family Credits  
**Copy:** Apply organization-approved sales and fundraising credits to eligible family fees.  
**Badge:** Planned for the full platform  
**Action:** Explore Family Credits

### Optional Card 7: Sponsorships

**Heading:** Sponsorships  
**Copy:** Create professional packages for local businesses and track fulfillment and renewals.  
**Badge:** Planned after the initial pilot

## 12.5 Founding Partner Feature Card

Match the dark and gold treatment in the reference.

### Eyebrow

> Founding Pilot

### Heading

> Help shape the first LeagueLift release.

### Benefits

- Guided onboarding
- Founding-pilot pricing
- Direct access to the founder
- Influence over early workflows
- Priority consideration for new modules

### CTA

> Apply for the Founding Pilot

### Supporting Rule

Do not claim permanent pricing unless the legal offer actually guarantees it.

## 12.6 How It Works

Dark section.

### Heading

> How LeagueLift Works

### Step 1: Build

> Create your organization, teams, tournaments, public pages, and revenue programs.

### Step 2: Share

> Promote fundraisers, apparel, fees, and sponsor opportunities through links, QR codes, email, and your existing communication tools.

### Step 3: Track

> See confirmed activity, balances, orders, credits, and campaign performance in one place.

### Step 4: Reinvest

> Use organization earnings and approved family credits to support stronger programs.

Use a connected horizontal process on desktop and stacked process on mobile.

## 12.7 Product Preview

Optional during the pilot.

Show a real screenshot or clearly marked interactive prototype of:

- Organization dashboard
- Campaign progress
- Fee balance
- Revenue summary

Do not display invented revenue totals as customer outcomes.

## 12.8 Audience Section

### Heading

> Built for the organizations that make youth sports possible.

Cards:

- Leagues
- Clubs
- Teams
- Tournaments
- Booster Organizations
- Multisport Facilities

Each card should explain one relevant use case in 20–35 words.

## 12.9 Pilot Pricing Preview

### Heading

> Start with the Founding Pilot

### Price

> Starting at $149 per month, plus applicable transaction fees.

### Includes

- Guided onboarding
- Organization profile
- Team and tournament page setup
- Initial fundraising workflow
- Initial apparel workflow as available
- Basic reporting
- Direct feedback channel

### CTA

> Review Pilot Details

### Disclaimer

> Pilot scope and availability depend on the organization’s needs, launch timing, and supported workflows.

## 12.10 FAQ

Include:

1. Does LeagueLift replace our current sports software?
2. Which sports does LeagueLift support?
3. Can we create pages for individual teams?
4. Can tournaments have their own stores and fundraising?
5. How do family credits work?
6. Are family credits cash?
7. Do children need accounts?
8. Can we sell apparel without storing inventory?
9. Are sponsorship tools available?
10. What is included in the founding pilot?
11. How does LeagueLift make money?
12. Is every feature shown available today?

Use accordion behavior.

## 12.11 Final CTA

Dark navy-green section.

### Heading

> Raise more. Manage less. Build stronger programs.

### Copy

> Apply for the LeagueLift founding pilot and help shape a better revenue platform for youth sports.

### Primary CTA

> Apply for Pilot

### Secondary CTA

> Book a Demo

---

# 13. How It Works Page

Route: `/how-it-works`

## 13.1 Hero

**Headline:** One platform for the revenue surrounding your program.  
**Copy:** Create public pages, launch campaigns, sell apparel, manage fees, and track approved family credits without replacing your scheduling or registration tools.

## 13.2 Detailed Workflow

1. Create the organization
2. Add teams and tournaments
3. Publish public pages
4. Create fundraising or apparel programs
5. Add households and assign fees
6. Attribute eligible sales or contributions
7. Apply approved credits
8. Review reports and payouts

## 13.3 “Works Alongside Your Existing Tools”

Show:

- Registration software
- Scheduling software
- Communication software
- LeagueLift revenue layer

Do not show third-party logos without permission.

## 13.4 Role-Based Experience

Cards:

- Organization leaders
- Team managers
- Parents and guardians
- Supporters
- Tournament operators

## 13.5 CTA

> Apply for Pilot

---

# 14. Solutions Overview

Route: `/solutions`

## 14.1 Hero

**Headline:** Revenue tools built for every level of youth sports.  
**Copy:** From one team to a multi-division tournament, LeagueLift gives adult administrators clear tools for public pages, fundraising, apparel, fees, credits, and reporting.

## 14.2 Solution Grid

- Team Pages
- Tournament Pages
- Fundraising
- Apparel Stores
- Dues & Fees
- Family Credits
- Sponsorships
- Reporting

Each card:

- Icon
- Heading
- 25–45 word description
- Availability badge
- Learn-more link

## 14.3 Availability Badges

- Available in Initial Release
- Pilot Workflow
- Planned
- Future

Do not imply planned modules are complete.

---

# 15. Solution Detail Template

Use a shared layout for every solution page.

## 15.1 Required Sections

1. Hero
2. Problem
3. LeagueLift approach
4. Key capabilities
5. Example workflow
6. Related users
7. Availability status
8. FAQ
9. CTA

## 15.2 Team Pages

Route: `/solutions/team-pages`

Features:

- Team branding
- Public description
- Sport, division, and season
- Fundraising links
- Apparel collection
- Sponsor recognition
- QR code
- Adult-controlled public contact options
- Draft, preview, publish, and archive

## 15.3 Tournament Pages

Route: `/solutions/tournament-pages`

Features:

- Dates and venue
- Participating teams
- Divisions
- Tournament merchandise
- Fundraising
- Sponsors
- Vendor information
- QR-code promotion
- Post-event products
- Results or schedule links

Mark live scheduling and ticketing as future unless implemented.

## 15.4 Fundraising

Route: `/solutions/fundraising`

Features:

- Organization and team campaigns
- Goals
- Date ranges
- Share links
- QR codes
- Team attribution
- Family attribution when enabled
- Confirmed contribution reporting
- Credit-rule connection
- Refund reversals

Include:

> LeagueLift does not represent a contribution as tax-deductible unless the organization and transaction qualify.

## 15.5 Apparel

Route: `/solutions/apparel`

Features:

- Organization collections
- Team collections
- Tournament collections
- Variants
- Personalization
- Fundraising markup
- Attribution
- Order tracking
- Fulfillment status

Do not promise every product type until provider support is confirmed.

## 15.6 Dues and Fees

Route: `/solutions/dues-and-fees`

Features:

- Fee templates
- Household and participant assignments
- Due dates
- Partial payments
- Installment plans
- Discounts
- Scholarships
- Waivers
- Credits
- Receipts
- Outstanding-balance reporting

## 15.7 Family Credits

Route: `/solutions/family-credits`

Features:

- Eligible sales attribution
- Eligible contribution attribution
- Pending credit
- Available credit
- Applied credit
- Reversed credit
- Organization policies
- Limits and expiration
- Fee eligibility

Required statement:

> LeagueLift family credits are organization-approved fee credits. They are not cash accounts and are not withdrawable or transferable.

## 15.8 Sponsorships

Route: `/solutions/sponsorships`

Status: Planned after the initial pilot unless implemented.

Features:

- Sponsorship packages
- Sponsor checkout
- Logo upload
- Approval
- Placement tracking
- Renewal reminders
- Sponsor reporting

---

# 16. Pricing Page

Route: `/pricing`

## 16.1 Hero

**Headline:** Start small. Grow with your program.  
**Copy:** LeagueLift combines organization subscriptions with clearly disclosed transaction fees and optional implementation services.

## 16.2 Founding Pilot Card

### Price

> Starting at $149 per month

### Additional Fees

> Applicable transaction, payment-processing, fulfillment, and optional service fees are disclosed before launch.

### Included

- Organization account
- Adult administrator access
- Organization onboarding
- Team and tournament pages
- Initial campaign setup
- Basic reporting
- Pilot support
- Feature feedback access

### CTA

> Apply for Pilot

## 16.3 Enterprise or Large Organization Card

Use `Contact Us`, not a fabricated price.

Suitable for:

- Large leagues
- Tournament groups
- Multisport operators
- More than 1,000 participants
- Multiple legal entities
- Custom reporting needs

## 16.4 Pricing FAQ

Include:

- Is there a setup fee?
- What transaction fees apply?
- Who pays fulfillment costs?
- Are payment-processing fees included?
- Can we cancel?
- Are all features included?
- Does pricing change by athlete count?
- Can a pilot rate be retained?

Do not answer promises not yet finalized. Use clear “determined in pilot agreement” language.

---

# 17. Founding Pilot Page

Route: `/founding-pilot`

This is the primary conversion page.

## 17.1 Hero

**Headline:** Become a LeagueLift Founding Partner.  
**Copy:** Help shape a revenue platform designed for youth sports organizations while receiving guided onboarding and direct support.

## 17.2 Benefits

- Guided discovery and onboarding
- Early product access
- Direct founder feedback sessions
- Pilot-specific pricing
- Prioritized workflow support
- Opportunity to influence roadmap

## 17.3 Who Is a Good Fit

- Adult-led organization
- Clear decision-maker
- Active team, league, club, tournament, or booster program
- Existing dues, fundraising, merchandise, or sponsorship workflow
- Willingness to provide feedback
- Realistic launch window

## 17.4 Application Form

### Contact Information

- First name
- Last name
- Work email
- Phone number
- Applicant role

### Organization

- Organization name
- Organization website
- City
- State
- Organization type
- Sport or sports
- Number of teams
- Approximate number of athletes

### Current Workflow

- Current sports-management software
- Current merchandise provider or method
- Estimated annual merchandise revenue
- Estimated annual fundraising revenue
- Estimated annual sponsorship revenue
- Current method for collecting dues and fees

### Product Interest

Checkboxes:

- Team Pages
- Tournament Pages
- Fundraising
- Apparel
- Dues & Fees
- Family Credits
- Sponsorships
- Reporting

### Needs

- Biggest current revenue or administration challenge
- Desired launch month
- Additional comments

### Attribution

- How did you hear about LeagueLift?
- Hidden UTM fields
- Landing page
- Referring page

### Consent

Required:

- Consent to be contacted
- Confirmation that applicant is at least 18 years old
- Agreement to Privacy Policy

Not required:

- Marketing newsletter consent

## 17.5 Validation

- Work email format
- US phone formatting where possible without blocking international-format input
- Organization name required
- At least one sport
- At least one feature interest
- Comments limited to a reasonable length
- Currency estimates accept whole-dollar values
- Prevent rapid duplicate submissions
- Honeypot field
- Backend rate limiting

## 17.6 Submit Button

> Submit Pilot Application

## 17.7 Submission Success

Show:

- Thank-you heading
- Organization name
- Application reference number
- Statement that the application will be reviewed
- No promised response time unless operationally supported
- Return-home action
- Optional book-a-demo action only when scheduling integration exists

## 17.8 Failure State

- Preserve form data
- Friendly error
- Retry
- Contact support fallback
- No stack trace or technical details

---

# 18. About Page

Route: `/about`

## 18.1 Hero

**Headline:** Better revenue tools for the people who keep youth sports running.

## 18.2 Founding Idea

Approved copy:

> LeagueLift was created around a simple idea: youth sports organizations should have better ways to generate and manage revenue than repeatedly raising family fees or relying on already-busy volunteers.

## 18.3 Mission

> Help youth sports organizations build sustainable programs while making costs clearer and more manageable for families.

## 18.4 Values

- Stronger communities
- Clear financial reporting
- Less volunteer administration
- Responsible data use
- Adult-controlled youth information
- Honest product claims

Do not invent:

- Founder biography
- Employee count
- Funding
- Office location
- Company history
- Customer milestones

These may be added after founder approval.

---

# 19. Contact Page

Route: `/contact`

## 19.1 Contact Types

- Sales
- Pilot
- Partnership
- Support
- General

## 19.2 Form

- Name
- Email
- Organization
- Inquiry type
- Message
- Consent to be contacted
- Honeypot
- UTM fields

## 19.3 Submit Button

> Send Message

## 19.4 Success

> Thanks. Your message has been received.

Do not promise a response window unless support operations can meet it.

## 19.5 Support Alternative

Show a support email only after a real monitored mailbox exists.

---

# 20. Security Page

Route: `/security`

The page should be factual and implementation-based.

## 20.1 Sections

- Adult-controlled accounts
- Role-based authorization
- Managed authentication
- Encryption in transit
- Protected secrets
- Provider-based payment handling
- Audit events
- Backups
- Responsible disclosure contact

Do not use:

- “Unhackable”
- “Military-grade”
- “Bank-level”
- Compliance certifications not held
- Guaranteed uptime

## 20.2 CTA

> Contact Us About Security

---

# 21. Help Page

Route: `/help`

Initial version may be a structured FAQ rather than a full support center.

Categories:

- Getting Started
- Accounts
- Organizations
- Public Pages
- Fundraising
- Apparel
- Dues & Fees
- Credits
- Billing
- Privacy

Include search only if it performs real content search.

---

# 22. Legal Pages

Routes:

- `/privacy`
- `/terms`
- `/accessibility`

Use plain, readable layouts.

Requirements:

- Maximum readable width approximately 760 px
- Sticky table of contents on desktop where useful
- Updated date
- Version or revision history where appropriate
- Legal review before launch
- Do not generate claims of compliance without review

---

# 23. Authentication Experience

The authentication page should closely follow the selected reference.

## 23.1 Desktop Layout

Two-column layout:

### Left Brand Panel

Approximately 55–60% width.

Contains:

- LeagueLift logo
- Tagline
- Stadium or sports atmosphere
- Multi-sport participant image
- Large abstract upward LeagueLift mark
- Sport labels or icons
- Optional product-value statements

Do not include fake customer or partner logos.

Approved lower-panel value statements:

- Built for multiple sports
- Adult-managed access
- Role-based organization controls
- Clear fees and credits

### Right Authentication Panel

Approximately 40–45% width.

Contains:

- Dark elevated card
- Sign In and Create Account tabs
- Authentication form
- Terms and privacy links
- Help link
- Clear error handling

## 23.2 Mobile Layout

- Compact logo and tagline at top
- Brand imagery reduced to a shallow banner or removed
- Authentication card becomes full-width
- Social login buttons stack
- No visual content that pushes form below an excessive scroll distance

---

# 24. Sign-In Page

Route: `/auth/sign-in`

## 24.1 Header Tabs

- Sign In
- Create Account

Active tab:

- White text
- Green underline
- `aria-current` or selected tab state

## 24.2 Copy

### Heading

> Welcome back

### Supporting Text

> Sign in to access your LeagueLift account.

## 24.3 Fields

- Email address
- Password
- Remember me, only when supported by the identity provider
- Forgot password link

## 24.4 Primary Button

> Sign In

## 24.5 Social Authentication

Initial approved providers:

- Continue with Google
- Continue with Microsoft

Apple is optional later. Do not show provider buttons until configured.

## 24.6 Links

- Forgot password?
- Create account
- Terms
- Privacy
- Contact support

## 24.7 Errors

Examples:

- Incorrect email or password
- Account requires email verification
- Account has been disabled
- Authentication provider is temporarily unavailable
- Too many attempts
- Invitation is no longer valid

Do not reveal whether an email exists where that could create account enumeration risk.

---

# 25. Registration

Route: `/auth/register`

Use a multi-step form.

## 25.1 Step 1: Organization

Fields:

- Organization name
- Organization type
- Primary sport
- Additional sports
- Approximate organization size
- Organization website, optional
- State

Organization types:

- Recreational league
- Travel club
- Individual team
- Tournament operator
- Booster organization
- Multisport facility
- Other

Organization size:

- 1–25 participants
- 26–75
- 76–150
- 151–300
- 301–600
- 601–1,000
- More than 1,000

Button:

> Continue

## 25.2 Step 2: Your Information

Fields:

- First name
- Last name
- Work email
- Role
- Password
- Confirm password

Role options:

- Organization owner
- League or club director
- Treasurer
- Board member
- Team manager
- Tournament director
- Fundraising coordinator
- Merchandise coordinator
- Other

Consent:

- Terms and Privacy agreement
- At least 18 years old

Button:

> Create Account

## 25.3 Step 3: Confirmation

Show:

- Email verification required
- Organization name
- Verification instructions
- Resend verification email
- Change email
- Return to sign in

## 25.4 Registration Boundaries

Do not request:

- Child names
- Birth dates
- Medical information
- School information
- Player addresses
- Parent payment information
- Team roster during account creation

Organization setup occurs after registration.

---

# 26. Invitation Acceptance

Route: `/auth/invitation`

## 26.1 Valid Invitation

Show:

- Inviting organization
- Assigned role
- Inviter name when safe
- Sign in or create account
- Accept invitation

## 26.2 Expired Invitation

Show:

- Invitation expired
- Request new invitation
- Contact organization administrator

## 26.3 Wrong Email

Explain that the invitation was sent to another email without disclosing private organization information unnecessarily.

---

# 27. Forgot and Reset Password

## 27.1 Forgot Password

Route: `/auth/forgot-password`

Fields:

- Email

Button:

> Send Reset Link

Success copy must not reveal whether the account exists:

> If an account is associated with that email, password-reset instructions will be sent.

## 27.2 Reset Password

Route: `/auth/reset-password`

Fields:

- New password
- Confirm password

Button:

> Update Password

States:

- Valid token
- Expired token
- Invalid token
- Success

---

# 28. Verify Email

Route: `/auth/verify-email`

States:

- Verification in progress
- Verified successfully
- Link expired
- Link already used
- Resend verification email
- Return to sign in

---

# 29. Authentication Error Page

Route: `/auth/error`

Show:

- Plain-language heading
- Safe message
- Request or correlation ID
- Try again
- Return home
- Contact support

Never display raw OIDC or stack-trace details.

---

# 30. Demo Booking

A “Book a Demo” action should behave in one of two approved ways.

## Option A: Internal Form

Route: `/book-demo`

Fields:

- Name
- Work email
- Organization
- Role
- Organization type
- Number of athletes
- Primary interest
- Preferred contact method
- Message

## Option B: Approved Scheduling Provider

Open an embedded or external scheduling workflow.

Requirements:

- Real configured provider
- Clear external-navigation notice
- UTM preservation where supported
- Privacy disclosure
- No empty or fake calendar

Until scheduling exists, use the pilot form rather than a broken demo flow.

---

# 31. 404 Page

Route fallback.

Content:

**Heading:** That page is out of bounds.  
**Copy:** The page may have moved, been unpublished, or no longer be available.  
**Actions:** Return Home, Explore Solutions, Contact Support

Use restrained sports language. Avoid repeated puns.

---

# 32. Shared Components

Implement reusable components:

- `SiteHeader`
- `MobileNavigation`
- `SiteFooter`
- `AnnouncementBar`
- `Hero`
- `SectionHeading`
- `PrimaryButton`
- `SecondaryButton`
- `TextButton`
- `FeatureCard`
- `BentoCard`
- `AudienceCard`
- `PilotOfferCard`
- `StepTimeline`
- `FaqAccordion`
- `FormField`
- `SelectField`
- `CheckboxField`
- `PasswordField`
- `AuthTabs`
- `SocialAuthButton`
- `StatusBadge`
- `AvailabilityBadge`
- `Toast`
- `InlineAlert`
- `Modal`
- `LoadingSpinner`
- `Skeleton`
- `EmptyState`
- `ErrorState`
- `QrCodeCard`
- `Seo`
- `PageContainer`

Components must use tokens rather than page-specific arbitrary values.

---

# 33. Imagery and Asset Requirements

## 33.1 Logo

Use a temporary or approved LeagueLift mark consisting of:

- Letter L or double-L concept
- Upward movement
- Sport-neutral construction
- White and green versions
- One-color version
- Compact icon
- Horizontal wordmark

Do not use:

- A baseball
- Basketball
- Trophy
- Whistle
- Mascot
- A mark tied to one sport

## 33.2 Required Logo Files

```text
logo-horizontal-light.svg
logo-horizontal-dark.svg
logo-mark-light.svg
logo-mark-dark.svg
favicon.svg
apple-touch-icon.png
social-share.png
```

## 33.3 Image Treatment

- Use AVIF or WebP where practical
- Provide width and height
- Use responsive `srcset`
- Lazy load below-fold images
- Do not lazy load the main hero image
- Use meaningful alt text
- Decorative visual layers should have empty alt text
- Preserve faces and sport action during crops

## 33.4 Reference Mockup

The selected reference image should be stored in:

```text
docs/design/reference/leaguelift-sales-site-concept.png
```

It must not be served as the actual website UI.

---

# 34. SEO

## 34.1 Homepage

Title:

> LeagueLift | Revenue Tools for Youth Sports Organizations

Description:

> LeagueLift helps youth sports leagues, clubs, teams, and tournaments create public pages, run fundraisers, sell apparel, manage dues, and apply approved family fee credits.

## 34.2 Rules

- Unique title and description per page
- One H1 per page
- Logical headings
- Canonical URLs
- Open Graph metadata
- Social image
- Sitemap
- Robots rules
- No indexing of auth, admin, confirmation, or private pages
- Structured organization data only when accurate
- No fake review schema
- No doorway pages generated for every sport or city without useful content

## 34.3 Public Page SEO

Organization, team, tournament, campaign, and store pages should control:

- Title
- Description
- Social image
- Canonical URL
- Publish status

Draft and archived pages must not be indexed.

---

# 35. Accessibility Requirements

- WCAG 2.2 AA target
- Correct landmark elements
- Skip-to-content link
- Keyboard navigation
- Visible focus
- Accessible menu
- Accessible tabs
- Accessible accordions
- Form labels
- Error summaries
- Reduced motion
- Contrast checks
- Alt text
- Correct heading order
- `aria-live` for asynchronous results
- Focus management after route changes
- No information conveyed only by color
- Minimum 44 px touch targets

Authentication forms must work without mouse interaction.

---

# 36. Analytics Events

Do not send personally identifying information into analytics.

## 36.1 Navigation

- `nav_item_clicked`
- `login_clicked`
- `pilot_cta_clicked`
- `demo_cta_clicked`

## 36.2 Homepage

- `hero_pilot_clicked`
- `hero_how_it_works_clicked`
- `solution_card_clicked`
- `pilot_offer_viewed`
- `faq_opened`
- `final_cta_clicked`

## 36.3 Pilot Form

- `pilot_form_viewed`
- `pilot_form_started`
- `pilot_form_step_completed`
- `pilot_form_validation_failed`
- `pilot_form_submitted`
- `pilot_form_submission_failed`

## 36.4 Authentication

Use privacy-safe events only:

- `sign_in_viewed`
- `sign_in_started`
- `sign_in_succeeded`
- `sign_in_failed`
- `registration_started`
- `registration_step_completed`
- `registration_succeeded`
- `password_reset_requested`

Do not include:

- Email
- Name
- Organization name
- Token
- Password
- Comments
- Participant information

## 36.5 Attribution

Preserve:

- `utm_source`
- `utm_medium`
- `utm_campaign`
- `utm_content`
- `utm_term`
- Landing page
- Referrer

---

# 37. Performance Requirements

Initial targets:

- Lighthouse Performance: 85 or higher on representative mobile test
- Accessibility: 95 or higher
- Best Practices: 90 or higher
- SEO: 90 or higher
- Largest Contentful Paint under 2.5 seconds on reasonable mobile conditions
- Cumulative Layout Shift under 0.1
- Interaction to Next Paint under 200 ms where practical

Implementation:

- Route-based code splitting
- Image optimization
- Font preloading only when necessary
- Use `font-display: swap`
- Avoid large animation libraries
- Avoid loading authentication SDK on pages that do not need it when architecture permits
- Cache static assets
- Use skeletons sparingly

---

# 38. Content Style Guide

## 38.1 Voice

- Direct
- Confident
- Helpful
- Community-focused
- Financially responsible
- Plain language
- Specific

## 38.2 Avoid

- “Revolutionary”
- “Game-changing”
- “Disruptive”
- “Guaranteed”
- “Effortless”
- “Passive income”
- “Bank-level” without support
- Excessive sports puns
- Excessive exclamation marks
- Claims about children’s outcomes without evidence

## 38.3 Preferred Terms

Use:

- Youth sports organization
- Adult administrator
- Parent or guardian
- Participant
- Household
- Organization-approved credit
- Fundraising campaign
- Organization earnings
- Public page

Avoid using `wallet` for family credits in early releases.

---

# 39. Implementation Architecture

## 39.1 Frontend

- React
- TypeScript strict mode
- Vite
- React Router
- TanStack Query
- React Hook Form
- Zod
- Token-based styling
- Accessible component primitives
- Vitest
- React Testing Library
- Playwright

## 39.2 Content

Initial public content should live in typed frontend configuration or markdown/MDX where helpful.

Do not introduce a CMS before content-editing demand exists.

## 39.3 API Integration

Public forms should submit to the Kotlin API.

Endpoints should be defined in `docs/openapi.yaml`.

Frontend agents must not invent endpoint contracts.

## 39.4 Authentication

Use the managed OIDC provider selected in the main design document.

The UI should use provider SDK abstractions and not implement password storage.

## 39.5 Feature Flags

Use flags for:

- Pilot announcement
- Demo booking
- Registration availability
- Social authentication providers
- Family Credits page
- Sponsorship page
- Newsletter
- Public product preview

Incomplete features must not be visible in production.

---

# 40. Error, Empty, and Loading States

Every interactive page must implement:

- Initial loading
- Empty state
- Inline validation
- Network error
- Server error
- Unauthorized
- Forbidden
- Not found
- Success
- Retry where safe

## 40.1 Form Server Error

Approved copy:

> We could not complete that request. Your information has not been lost. Please try again.

## 40.2 Authentication Error

Approved copy:

> We could not sign you in. Check your information or try again.

Avoid exposing provider error codes publicly.

## 40.3 Temporary Availability

Approved copy:

> LeagueLift is temporarily unavailable. Please try again shortly.

---

# 41. Testing Requirements

## 41.1 Visual

- Desktop at 1440 px
- Laptop at 1280 px
- Tablet at 768 px
- Mobile at 390 px
- Mobile at 320 px
- High zoom
- Dark and light browser UI where relevant

## 41.2 Functional

- All navigation links
- Mobile menu
- CTA routing
- Pilot form validation
- Contact form validation
- Sign-in
- Registration steps
- Password reset
- Email verification
- Invitation acceptance
- External scheduling behavior
- 404

## 41.3 Accessibility

- Keyboard-only
- Screen-reader smoke test
- Automated axe checks
- Focus order
- Menu and modal focus traps
- Error associations
- Contrast

## 41.4 Security

- No secrets in frontend
- No sensitive analytics data
- No open redirect
- Safe callback handling
- Public form rate limiting
- Anti-automation controls
- Secure link handling
- Correct no-index rules

---

# 42. Agent Instructions

These instructions are mandatory.

## 42.1 Before Implementation

1. Read this file.
2. Read `DESIGN-DOC.md`.
3. Inspect the reference image.
4. Inspect existing frontend code and tokens.
5. Review `docs/openapi.yaml`.
6. Identify which routes are in the current milestone.
7. Provide a concise implementation plan.
8. Do not build deferred pages without approval.

## 42.2 During Implementation

1. Recreate the visual direction, not the image pixel-for-pixel.
2. Do not copy unsupported example statistics.
3. Use the approved palette and type scale.
4. Build reusable components.
5. Use semantic HTML.
6. Implement responsive states.
7. Implement accessibility.
8. Use typed forms.
9. Use real routes and working controls.
10. Hide unimplemented actions with feature flags.
11. Do not create fake testimonials or logos.
12. Do not invent backend endpoints.
13. Do not request participant information in public sales forms.
14. Do not place authentication secrets in browser variables.
15. Preserve loading, error, success, and empty states.
16. Add tests.

## 42.3 Before Completion

1. Run type checking.
2. Run unit tests.
3. Run Playwright flows.
4. Run accessibility checks.
5. Test mobile navigation.
6. Verify all buttons.
7. Verify no fake claims.
8. Verify metadata.
9. Verify no private pages are indexed.
10. Update screenshots where approved.
11. Summarize implemented routes and remaining gaps.

---

# 43. Initial Implementation Order

## Iteration 1: Foundation

- Design tokens
- Fonts
- Logo placement
- Buttons
- Header
- Mobile menu
- Footer
- Page container
- SEO component
- Error states

## Iteration 2: Homepage

- Hero
- Non-numeric benefit cards
- Positioning
- Feature grid
- Founding pilot card
- How It Works
- Audience cards
- Pricing preview
- FAQ
- Final CTA

## Iteration 3: Authentication

- Desktop split layout
- Mobile auth layout
- Sign-in
- Social auth placeholders behind flags
- Forgot password
- Reset password
- Verification
- Auth error
- Invitation acceptance

## Iteration 4: Registration

- Three-step form
- Validation
- Terms and adult confirmation
- Confirmation state
- Analytics

## Iteration 5: Conversion Pages

- Founding Pilot
- Contact
- Demo booking
- Pricing

## Iteration 6: Content Pages

- How It Works
- Solutions
- Solution details
- About
- Security
- Help
- Legal
- 404

## Iteration 7: QA and Optimization

- Responsive review
- Accessibility
- Performance
- SEO
- Analytics
- Cross-browser
- Production feature flags

---

# 44. Acceptance Criteria

The sales site is ready for staging when:

- The visual direction clearly matches the premium dark LeagueLift concept.
- The homepage works on mobile and desktop.
- Every public navigation item has a real destination or is hidden.
- The primary pilot CTA works.
- Pilot application submissions are validated and persisted.
- Authentication pages are integrated with the selected identity provider.
- Registration is adult-focused and does not collect child data.
- No fake metrics, logos, testimonials, or integration claims are present.
- Forms provide accessible validation and success states.
- Legal and security links are present.
- Authentication and private pages are no-indexed.
- Lighthouse and accessibility targets are reasonably met.
- Playwright covers the primary conversion and auth flows.
- The site can be deployed independently from the authenticated application.
- The reference image is stored in documentation but not used as the production page.

---

# 45. Final Visual Principle

The site should look as confident and polished as an established sports-technology company while speaking honestly as an early-stage product.

The visual system may be bold.

The claims must remain conservative.

The interactions must feel complete.

The path from interest to pilot application or account access must always be obvious.
