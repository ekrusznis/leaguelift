# ADR-078: Phase 25.5 Athlete-to-athlete messaging behind the SafeSport gate

Status: Accepted for implementation; **disabled by default until ADR-077 review gate is approved**.

Athletes may create direct or group conversations only with activated athletes who currently share an active team assignment. A group is capped at 20 selected teammates in v1. The creator and selected athletes are reply-capable; every currently linked guardian for every athlete member is automatically added as read-only `GUARDIAN_VISIBILITY`. No athlete action can remove guardian visibility.

Every create/send rechecks the V58 organization gate and the sender’s current team eligibility. `ALL_MESSAGING` guardian restrictions block peer messaging. Staff are not members of athlete-created threads by default; authorized safety reviewers retain the report/lock tools shipped in V57.

The new `ATHLETE_CONVERSATION` thread type is intentionally distinct from coach/family `CONVERSATION`, allowing future policy and UI rules to remain explicit. Messages remain V57 append-only safety evidence.
