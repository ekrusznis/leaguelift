import { Capabilities } from "../../authorization/capabilityConstants";
import type { ContextType } from "../../authorization/types";

export interface WidgetRegistryItem {
	id: string;
	/** Which context type this widget belongs to. */
	contextType: ContextType;
	/** Any one of these capabilities (in the active context) is enough to show the widget. Omit for "always shown in this context type". */
	requiredCapabilities?: string[];
}

/**
 * Data-driven widget visibility, filtered by real context/capability data. The
 * registry deliberately omits demo-only cards whose backing model is not ready and
 * capability-gates sensitive organization widgets so finance/viewer contexts do not
 * inherit owner/admin operational controls merely because they share the same shell.
 */
export const WIDGET_REGISTRY: WidgetRegistryItem[] = [
	// Owner (ORGANIZATION context)
	{ id: "owner.summary", contextType: "ORGANIZATION" },
	{ id: "owner.financial-overview", contextType: "ORGANIZATION", requiredCapabilities: [Capabilities.ORG_REPORT_VIEW] },
	{ id: "owner.team-performance", contextType: "ORGANIZATION", requiredCapabilities: [Capabilities.ORG_TEAM_MANAGE, Capabilities.ORG_MANAGE] },
	{ id: "owner.upcoming-events", contextType: "ORGANIZATION", requiredCapabilities: [Capabilities.EVENT_READ, Capabilities.ORG_EVENT_MANAGE] },
	{ id: "owner.recent-activity", contextType: "ORGANIZATION", requiredCapabilities: [Capabilities.ORG_MANAGE] },
	{ id: "owner.reports-snapshot", contextType: "ORGANIZATION", requiredCapabilities: [Capabilities.ORG_REPORT_VIEW] },
	{ id: "owner.quick-actions", contextType: "ORGANIZATION", requiredCapabilities: [Capabilities.ORG_MANAGE] },

	// Parent/Guardian (HOUSEHOLD context)
	{ id: "parent.athletes", contextType: "HOUSEHOLD" },
	{ id: "parent.family-schedule", contextType: "HOUSEHOLD" },
	{ id: "parent.outstanding-balance", contextType: "HOUSEHOLD" },
	{ id: "parent.active-fundraisers", contextType: "HOUSEHOLD" },
	{ id: "parent.documents", contextType: "HOUSEHOLD" },

	// Coach (TEAM context)
	{ id: "coach.my-teams", contextType: "TEAM" },
	{ id: "coach.team-schedule", contextType: "TEAM" },
	{ id: "coach.roster-summary", contextType: "TEAM" },
	{ id: "coach.team-page-status", contextType: "TEAM" },
	{ id: "coach.fundraising-progress", contextType: "TEAM" },

	// Athlete (ATHLETE context)
	{ id: "athlete.next-event", contextType: "ATHLETE" },
	{ id: "athlete.my-teams", contextType: "ATHLETE" },
	{ id: "athlete.this-week", contextType: "ATHLETE" },
	{ id: "athlete.recent-history", contextType: "ATHLETE" },
	{ id: "athlete.guardians", contextType: "ATHLETE" },

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
