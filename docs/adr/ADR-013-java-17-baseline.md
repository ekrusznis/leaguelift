# ADR-013: Java 17 Instead of Java 21 Baseline

## Status
Accepted

## Context

`DESIGN-DOC.md` section 11.2 documents Java 21 as the runtime target, inherited from
the original starter repository's compatibility baseline. When first building the
Phase 0 foundation on the founder's actual development machine (Windows, IntelliJ IDEA
Community 2025.3.3), no JDK 21 was installed or discoverable, and Gradle's toolchain
auto-provisioning (added via the Foojay resolver plugin, `backend/settings.gradle.kts`)
did not resolve one either. Switching the Gradle toolchain to Java 17 built and ran
successfully.

## Decision

Target Java 17 instead of Java 21 for the backend, in `backend/build.gradle.kts`
(`JavaLanguageVersion.of(17)`), the backend `Dockerfile` (`eclipse-temurin:17-*`), and
CI (`.github/workflows/backend.yml`). Spring Boot 4.1 and Kotlin 2.3 both fully support
Java 17 (17 has been Spring Boot's minimum since 3.2), so no framework capability is
lost by this change.

## Consequences

- Local development, CI, and container builds all use the same Java 17 baseline —
  no environment drift between them.
- Any future JDK 21+-only language feature (e.g. virtual threads as a first-class
  Spring feature, certain newer standard library APIs) is off the table until this
  decision is revisited.
- If a real need for Java 21 emerges later (e.g. virtual-thread-based request
  handling under production load), revisit by installing a discoverable JDK 21 (or
  letting the Foojay resolver download one) and bump `JavaLanguageVersion` back up —
  low-risk, isolated change given no 21-only APIs are in use.

## Alternatives Considered

- **Require the founder to manually install JDK 21** — rejected for now; adds friction
  to local setup for no immediate benefit, since nothing in Phase 0/1 needs anything
  newer than 17.
- **Rely solely on the Foojay auto-download resolver instead of downgrading** — this
  is the long-term-preferable option (keeps the documented 21 baseline) and remains
  available; it just didn't resolve automatically in this environment on the first
  attempt. Worth revisiting rather than treating Java 17 as permanent.
