import { describe, expect, it } from "vitest";
import { Capabilities } from "../../../authorization/capabilityConstants";
import { navItemsFor } from "../navRegistry";

const allOrganizationCapabilities = new Set([
	Capabilities.ORG_MANAGE,
	Capabilities.ORG_MEMBERS_MANAGE,
	Capabilities.ORG_BILLING_MANAGE,
	Capabilities.ORG_PAYOUT_MANAGE,
	Capabilities.ORG_REPORT_VIEW,
	Capabilities.ORG_TEAM_MANAGE,
	Capabilities.ORG_TOURNAMENT_MANAGE,
	Capabilities.ORG_EVENT_MANAGE,
	Capabilities.EVENT_READ,
]);

describe("dashboard navigation registry", () => {
	it("resolves every visible owner item to a concrete application route", () => {
		const items = navItemsFor("ORGANIZATION", allOrganizationCapabilities, { organizationId: "org-1" });

		expect(items.length).toBeGreaterThan(8);
		expect(items.every((item) => item.to.startsWith("/app"))).toBe(true);
		expect(items.find((item) => item.id === "owner.events")?.to).toBe("/app/organizations/org-1/events");
		expect(items.find((item) => item.id === "owner.reports")?.to).toBe("/app/organizations/org-1/reports");
		expect(items.find((item) => item.id === "owner.action-center")?.to).toBe("/app/action-center");
		expect(items.find((item) => item.id === "owner.announcements")?.to).toBe("/app/announcements");
		expect(items.find((item) => item.id === "owner.integrations")?.to).toBe("/app/integrations");
		expect(items.find((item) => item.id === "owner.organization-integrations")?.to).toBe("/app/organizations/org-1/integrations");
	});

	it("keeps a finance or viewer context focused on read-only financial areas", () => {
		const items = navItemsFor("ORGANIZATION", new Set([Capabilities.ORG_REPORT_VIEW]), { organizationId: "org-1" });

		expect(items.map((item) => item.id)).toEqual(["owner.overview", "owner.action-center", "owner.announcements", "owner.integrations", "owner.fees", "owner.reports"]);
	});

	it("does not advertise unimplemented parent credit or order destinations", () => {
		const items = navItemsFor(
			"HOUSEHOLD",
			new Set([Capabilities.EVENT_READ, Capabilities.HOUSEHOLD_FEE_VIEW, Capabilities.HOUSEHOLD_VIEW]),
			{ organizationId: "org-1", householdId: "household-1" },
		);

		expect(items.some((item) => /credit|order/i.test(item.label))).toBe(false);
		expect(items.find((item) => item.id === "parent.schedule")?.to).toBe(
			"/app/organizations/org-1/households/household-1/events",
		);
	});

	it("routes an athlete to the real participant schedule and omits orders", () => {
		const items = navItemsFor(
			"ATHLETE",
			new Set([
				Capabilities.ATHLETE_SCHEDULE_VIEW,
				Capabilities.ATHLETE_TEAM_VIEW,
				Capabilities.ATHLETE_PROFILE_VIEW,
				Capabilities.ATHLETE_GUARDIAN_VIEW,
			]),
			{ organizationId: "org-1", participantId: "participant-1" },
		);

		expect(items.find((item) => item.id === "athlete.schedule")?.to).toBe(
			"/app/organizations/org-1/participants/participant-1/events",
		);
		expect(items.some((item) => /order/i.test(item.label))).toBe(false);
	});

	it("uses the selected team for coach schedule navigation", () => {
		const items = navItemsFor(
			"TEAM",
			new Set([Capabilities.TEAM_VIEW, Capabilities.EVENT_READ]),
			{ organizationId: "org-1", teamId: "team-2" },
		);

		expect(items.find((item) => item.id === "coach.schedule")?.to).toBe(
			"/app/organizations/org-1/teams/team-2/events",
		);
	});

	it("routes platform employees to the complete support console", () => {
		const items = navItemsFor(
			"PLATFORM_ADMIN",
			new Set([
				Capabilities.PLATFORM_ORG_VIEW,
				Capabilities.PLATFORM_USER_VIEW,
				Capabilities.PLATFORM_INTEGRATION_VIEW,
				Capabilities.PLATFORM_AUDIT_VIEW,
				Capabilities.PLATFORM_SUPPORT_ACCESS,
			]),
		);

		expect(items.map((item) => item.id)).toEqual([
			"platform.overview",
			"platform.action-center",
			"platform.announcements",
			"platform.integrations",
			"platform.organizations",
			"platform.users",
			"platform.operations",
			"platform.reports",
			"platform.audit",
			"platform.support-sessions",
		]);
		expect(items.find((item) => item.id === "platform.integrations")?.to).toBe("/app/integrations");
		expect(items.find((item) => item.id === "platform.organizations")?.to).toBe("/app/platform/organizations");
		expect(items.find((item) => item.id === "platform.users")?.to).toBe("/app/platform/users");
		expect(items.find((item) => item.id === "platform.operations")?.to).toBe("/app/platform/operations");
		expect(items.find((item) => item.id === "platform.reports")?.to).toBe("/app/platform/reports");
		expect(items.find((item) => item.id === "platform.audit")?.to).toBe("/app/platform/audit");
		expect(items.find((item) => item.id === "platform.support-sessions")?.to).toBe("/app/platform/support-sessions");
		expect(items.every((item) => item.to.startsWith("/app"))).toBe(true);
	});
});
