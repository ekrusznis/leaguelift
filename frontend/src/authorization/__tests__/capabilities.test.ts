import { describe, expect, it } from "vitest";
import { capabilitiesFor, hasCapability } from "../capabilities";
import type { AuthorizationContext } from "../types";

const contexts: AuthorizationContext[] = [
	{ contextType: "PERSONAL", resourceId: null, organizationId: null, label: "Jordan Ellis", role: "SELF", capabilities: ["profile.view"] },
	{
		contextType: "TEAM",
		resourceId: "team-a",
		organizationId: "org-1",
		label: "Varsity Soccer",
		role: "TEAM_MANAGER",
		capabilities: ["team.view", "team.roster.manage"],
	},
	{
		contextType: "TEAM",
		resourceId: "team-b",
		organizationId: "org-1",
		label: "JV Basketball",
		role: "COACH_READ",
		capabilities: ["team.view"],
	},
];

describe("hasCapability", () => {
	it("denies by default when contexts haven't loaded yet", () => {
		expect(hasCapability(undefined, "team.view")).toBe(false);
	});

	it("is true when any context grants the capability", () => {
		expect(hasCapability(contexts, "team.roster.manage")).toBe(true);
	});

	it("is false for a capability no context grants", () => {
		expect(hasCapability(contexts, "team.staff.manage")).toBe(false);
	});

	it("scopes the check to a specific team resource — team A's manage capability does not leak to team B", () => {
		expect(hasCapability(contexts, "team.roster.manage", { contextType: "TEAM", resourceId: "team-a" })).toBe(true);
		expect(hasCapability(contexts, "team.roster.manage", { contextType: "TEAM", resourceId: "team-b" })).toBe(false);
	});

	it("scopes the check by contextType even when resourceId is shared across context types", () => {
		expect(hasCapability(contexts, "profile.view", { contextType: "TEAM" })).toBe(false);
		expect(hasCapability(contexts, "profile.view", { contextType: "PERSONAL" })).toBe(true);
	});
});

describe("capabilitiesFor", () => {
	it("returns an empty set with no contexts", () => {
		expect(capabilitiesFor(undefined).size).toBe(0);
	});

	it("unions capabilities only from matching contexts", () => {
		const result = capabilitiesFor(contexts, "TEAM", "team-a");
		expect(result.has("team.roster.manage")).toBe(true);
		expect(result.has("team.view")).toBe(true);
		expect(result.has("profile.view")).toBe(false);
	});
});
