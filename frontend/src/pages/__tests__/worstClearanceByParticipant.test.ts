import { describe, expect, it } from "vitest";
import type { EligibilityClearance } from "../../features/eligibility/types";
import { worstClearanceByParticipant } from "../HouseholdDetailPage";

function clearance(participantId: string, status: EligibilityClearance["status"], teamId = "team-1"): EligibilityClearance {
	return { participantId, teamId, status, unmetRequirementCount: 0, computedAt: null };
}

describe("worstClearanceByParticipant", () => {
	it("picks the most-concerning status when a participant is on multiple teams", () => {
		const result = worstClearanceByParticipant([
			clearance("p1", "CLEARED", "team-1"),
			clearance("p1", "INELIGIBLE", "team-2"),
		]);

		expect(result.get("p1")).toBe("INELIGIBLE");
	});

	it("ranks EXPIRED above still-in-progress statuses", () => {
		const result = worstClearanceByParticipant([
			clearance("p1", "DOCUMENTS_REQUIRED", "team-1"),
			clearance("p1", "EXPIRED", "team-2"),
			clearance("p1", "UNDER_REVIEW", "team-3"),
		]);

		expect(result.get("p1")).toBe("EXPIRED");
	});

	it("keeps CLEARED when it's the only status", () => {
		const result = worstClearanceByParticipant([clearance("p1", "CLEARED")]);

		expect(result.get("p1")).toBe("CLEARED");
	});

	it("returns an empty map for no clearances", () => {
		expect(worstClearanceByParticipant([]).size).toBe(0);
	});

	it("keeps each participant's own worst status independently", () => {
		const result = worstClearanceByParticipant([
			clearance("p1", "CLEARED"),
			clearance("p2", "INELIGIBLE"),
		]);

		expect(result.get("p1")).toBe("CLEARED");
		expect(result.get("p2")).toBe("INELIGIBLE");
	});
});
