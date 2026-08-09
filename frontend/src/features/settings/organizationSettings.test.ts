import { describe, expect, it } from "vitest";
import { organizationSettingsGroups } from "./organizationSettings";

describe("organizationSettingsGroups", () => {
	it("shows the complete directory to organization managers", () => {
		const groups = organizationSettingsGroups("org-1", {
			canManageOrganization: true,
			canManagePayouts: true,
		});

		expect(groups.map((group) => group.key)).toEqual([
			"organization-branding",
			"financial-credits",
			"communications",
			"events-participation",
			"commerce",
			"integrations",
			"billing",
			"administration",
		]);
		expect(groups.flatMap((group) => group.links).some((link) => link.to === "/app/organizations/org-1/billing")).toBe(true);
	});

	it("limits payout-only access to the existing payout settings surface", () => {
		const groups = organizationSettingsGroups("org-2", {
			canManageOrganization: false,
			canManagePayouts: true,
		});

		expect(groups).toHaveLength(1);
		expect(groups[0]?.key).toBe("financial-credits");
		expect(groups[0]?.links).toEqual([{ label: "Payouts", to: "/app/organizations/org-2/settings" }]);
	});

	it("returns no organization settings for users without organization management capabilities", () => {
		expect(organizationSettingsGroups("org-3", {
			canManageOrganization: false,
			canManagePayouts: false,
		})).toEqual([]);
	});

	it("does not invent Phase 30 or Phase 31 settings", () => {
		const text = JSON.stringify(organizationSettingsGroups("org-4", {
			canManageOrganization: true,
			canManagePayouts: true,
		}));
		expect(text).not.toContain("Affirm");
		expect(text).not.toContain("Zelle");
		expect(text).not.toContain("waiver");
		expect(text).not.toContain("eligibility settings");
	});
	it("keeps every organization-scoped link inside the requested organization", () => {
		const organizationId = "org-closeout-1";
		const links = organizationSettingsGroups(organizationId, {
			canManageOrganization: true,
			canManagePayouts: true,
		}).flatMap((group) => group.links);

		for (const link of links) {
			if (!link.to.startsWith("/app/organizations/")) continue;
			expect(link.to).toMatch(new RegExp(`^/app/organizations/${organizationId}(?:/|$)`));
		}
	});

	it("does not let payout-only access reach unrelated organization management routes", () => {
		const text = JSON.stringify(organizationSettingsGroups("org-closeout-2", {
			canManageOrganization: false,
			canManagePayouts: true,
		}));

		expect(text).not.toContain("/fees");
		expect(text).not.toContain("financial-operations");
		expect(text).not.toContain("/members");
		expect(text).not.toContain("/billing");
		expect(text).not.toContain("/integrations");
	});

});
