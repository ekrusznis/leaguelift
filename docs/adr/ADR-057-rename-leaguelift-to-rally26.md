# ADR-057: Product Renamed from LeagueLift to Rally26

## Status
Accepted

## Context

`leaguelift.com` and `leaguelift.io` were both already registered by unrelated
third parties by the time ADR-008 (DigitalOcean deployment) was being executed —
the product could not launch under its original name on either extension. A
naming search prioritizing a genuinely available, non-premium `.com` (checked
directly against the live registry, not guessed) converged on **Rally26**:
"Rally" is sports-native and reads warm/approachable without being childish;
"26" makes the compound distinctive enough to dodge the extremely
squatted namespace of generic sports+SaaS two-word `.com` combinations
encountered during the search. `rally26.com` was registered and is the domain
this ADR moves the whole codebase to.

## Decision

Renamed everywhere, in one pass across the repository:

- **Domain:** `leaguelift.io` -> `rally26.com` (every reference: infra, CI,
  application config defaults, docs).
- **Backend package:** `com.leaguelift` -> `com.rally26`, including the
  directory structure under `backend/src/main/kotlin` and
  `backend/src/test/kotlin`, the Gradle `group` and `rootProject.name`
  (`leaguelift-backend` -> `rally26-backend`), and the application entry point
  class (`LeagueLiftApiApplication` -> `Rally26ApiApplication`,
  file renamed to match).
- **Container registry / image names:** `registry.digitalocean.com/leaguelift`
  -> `.../rally26`, `leaguelift-backend`/`leaguelift-frontend` images ->
  `rally26-backend`/`rally26-frontend`.
- **Infra (ADR-008):** Caddyfile, `docker-compose.prod.yml`, both GitHub Actions
  workflows, `.env.prod.example`, the backup script, and the droplet deploy
  user (`leaguelift` -> `rally26`) all updated in place — no infra target
  changed, only the name.
- **Docs:** `DESIGN-DOC.md`, `README.md`, every ADR, and `docs/openapi.yaml`
  updated in place rather than left referencing the old name, given how
  actively this repository's own documentation discipline is relied on (see
  DESIGN-DOC.md's own repeated warnings about doc/code drift) — leaving stale
  branding throughout the design doc would have been exactly the kind of drift
  that discipline exists to prevent.
- **Frontend:** brand asset files renamed (`leaguelift-logo-*.svg` ->
  `rally26-logo-*.svg`, etc.) and all references to them updated; product name
  strings throughout marketing/UI copy updated.
- **Env var prefix:** `LEAGUELIFT_SUPPORT_INBOX_EMAIL` ->
  `RALLY26_SUPPORT_INBOX_EMAIL`.

Mechanically, this was a scripted, case-preserving find/replace
(`LeagueLift`->`Rally26`, `LEAGUELIFT`->`RALLY26`, `leaguelift`->`rally26`,
with `leaguelift.io`/`leaguelift.com` special-cased to `rally26.com` ahead of
the generic word substitution so the domain didn't end up as `rally26.io`)
across 721 matched files, followed by explicit directory/file renames for the
Kotlin package and brand assets that the text substitution alone couldn't
move. This ADR — and the diff it describes — is that rename in its entirety;
there is no partial/staged rename left outstanding.

## Consequences

- No functional/behavioral change anywhere — this is a pure rename. Every
  Phase 0-19 capability described elsewhere in `DESIGN-DOC.md` is unaffected.
- The GitHub repository itself (`github.com/ekrusznis/leaguelift`) still needs
  a manual rename in GitHub's own settings — not automatable from here (see
  ADR-008's note on this sandbox's network restrictions). GitHub redirects the
  old URL automatically once renamed, so existing clones/remotes keep working,
  but updating local `git remote` URLs afterward is still recommended.
- Anyone with an existing local clone predating this ADR will see every
  tracked file as modified; there is no way to make a rename of this size
  land as a small diff.
- `rally26.com`'s DNS/TLS provisioning (via `infra-bootstrap.yml`) depends on
  the domain being actually registered and its registrar nameservers pointed
  at DigitalOcean — unchanged process from ADR-008, just the new domain name.

## Alternatives Considered

- **Keep `com.leaguelift` as the internal package name, rename only
  user-facing surfaces (domain, docs, UI copy).** Lower-risk, smaller diff —
  genuinely reasonable, since an internal package name is invisible to users.
  Rejected in favor of the full rename per explicit instruction to review and
  update the entirety of the codebase, and because a permanently mismatched
  internal namespace (`com.leaguelift` inside a product called Rally26) would
  itself become a standing point of confusion for anyone reading the source.
- **Leave historical ADRs' prose referencing "LeagueLift" unchanged, as a
  literal historical record.** Rejected — unlike a company's public git
  history (which typically shows the rename as a diff, preserving old commits
  unchanged), this codebase's own documentation is treated as always-current
  operating instructions (see DESIGN-DOC.md section 20, AI Agent Operating
  Instructions), not an immutable log. A reader following those instructions
  today should never hit a stale product name.
