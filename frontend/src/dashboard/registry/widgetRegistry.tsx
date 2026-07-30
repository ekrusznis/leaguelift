import type { ContextType } from "../../authorization/types";

export interface WidgetRegistryItem {
	id: string;
	/** Which context type this widget belongs to. */
	contextType: ContextType;
	/** Any one of these capabilities (in the active context) is enough to show the widget. Omit for "always shown in this context type". */
	requiredCapabilities?: string[];
}

/**
 * Data-driven widget visibility, filtered by real context/capability data rather
 * than an unconditional, hardcoded card list per dashboard component (DESIGN-DOC.md
 * section 10.3 — "widget registries are not built for any dashboard" as of Phase 7).
 * Every widget below is currently unconditional (no requiredCapabilities), matching
 * each dashboard's pre-registry behavior exactly — the design doc doesn't call for
 * hiding any widget by capability today, only gating specific *actions* within a
 * widget (handled inline at the call site via the caller's own capability set, not
 * this registry — e.g. Coach's Team Page Status "Edit Page" action). This registry's
 * value is the mechanism: adding a capability-gated widget later (e.g. a household
 * Documents card) is a one-line addition here instead of new per-dashboard plumbing.
 */
export const WIDGET_REGISTRY: WidgetRegistryItem[] = [
	// Owner (ORGANIZATION context)
	{ id: "owner.summary", contextType: "ORGANIZATION" },
	{ id: "owner.financial-overview", contextType: "ORGANIZATION" },
	{ id: "owner.attention-required", contextType: "ORGANIZATION" },
	{ id: "owner.team-performance", contextType: "ORGANIZATION" },
	{ id: "owner.upcoming-events", contextType: "ORGANIZATION" },
	{ id: "owner.recent-activity", contextType: "ORGANIZATION" },
	{ id: "owner.onboarding-progress", contextType: "ORGANIZATION" },
	{ id: "owner.reports-snapshot", contextType: "ORGANIZATION" },
	{ id: "owner.quick-actions", contextType: "ORGANIZATION" },

	// Parent/Guardian (HOUSEHOLD context)
	{ id: "parent.athletes", contextType: "HOUSEHOLD" },
	{ id: "parent.family-schedule", contextType: "HOUSEHOLD" },
	{ id: "parent.outstanding-balance", contextType: "HOUSEHOLD" },
	{ id: "parent.family-credits", contextType: "HOUSEHOLD" },
	{ id: "parent.recent-orders", contextType: "HOUSEHOLD" },
	{ id: "parent.active-fundraisers", contextType: "HOUSEHOLD" },
	{ id: "parent.required-actions", contextType: "HOUSEHOLD" },
	{ id: "parent.organization-updates", contextType: "HOUSEHOLD" },
	{ id: "parent.documents", contextType: "HOUSEHOLD" },

	// Coach (TEAM context)
	{ id: "coach.my-teams", contextType: "TEAM" },
	{ id: "coach.team-schedule", contextType: "TEAM" },
	{ id: "coach.roster-summary", contextType: "TEAM" },
	{ id: "coach.team-page-status", contextType: "TEAM" },
	{ id: "coach.fundraising-progress", contextType: "TEAM" },
	{ id: "coach.announcements", contextType: "TEAM" },
	{ id: "coach.required-actions", contextType: "TEAM" },

	// Athlete (ATHLETE context)
	{ id: "athlete.next-event", contextType: "ATHLETE" },
	{ id: "athlete.my-teams", contextType: "ATHLETE" },
	{ id: "athlete.this-week", contextType: "ATHLETE" },
	{ id: "athlete.recent-history", contextType: "ATHLETE" },
	{ id: "athlete.guardians", contextType: "ATHLETE" },
	{ id: "athlete.orders", contextType: "ATHLETE" },

	// Tournament Admin (TOURNAMENT context)
	{ id: "tournament.summary", contextType: "TOURNAMENT" },
	{ id: "tournament.page-status", contextType: "TOURNAMENT" },

	// Platform Admin (PLATFORM_ADMIN context)
	{ id: "platform.summary", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.webhook-health", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.outbox-health", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.orders", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.payments", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.payouts", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.audit", contextType: "PLATFORM_ADMIN" },
	{ id: "platform.organizations", contextType: "PLATFORM_ADMIN" },
];

export function visibleWidgetIds(contextType: ContextType, capabilities: Set<string>): Set<string> {
	return new Set(
		WIDGET_REGISTRY.filter(
			(item) =>
				item.contextType === contextType &&
				(!item.requiredCapabilities || item.requiredCapabilities.some((capability) => capabilities.has(capability))),
		).map((item) => item.id),
	);
}
