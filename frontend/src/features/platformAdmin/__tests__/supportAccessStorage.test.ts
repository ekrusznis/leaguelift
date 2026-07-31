import { afterEach, describe, expect, it, vi } from "vitest";
import { clearStoredSupportAccess, readStoredSupportAccess, storeSupportAccess } from "../supportAccessStorage";
import type { PlatformSupportAccess } from "../types";

const access: PlatformSupportAccess = {
	id: "11111111-1111-1111-1111-111111111111",
	organizationId: "22222222-2222-2222-2222-222222222222",
	organizationName: "North Jersey Volleyball Club",
	reason: "Investigate a customer roster import issue",
	status: "ACTIVE",
	expiresAt: "2099-07-31T18:00:00Z",
	endedAt: null,
	createdAt: "2099-07-31T16:00:00Z",
};

afterEach(() => {
	clearStoredSupportAccess();
	vi.useRealTimers();
});

describe("platform support access storage", () => {
	it("stores the active organization-scoped session used by the API client", () => {
		storeSupportAccess(access);
		expect(readStoredSupportAccess()).toEqual(access);
	});

	it("removes an expired session instead of attaching it to organization requests", () => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date("2100-01-01T00:00:00Z"));
		storeSupportAccess(access);
		expect(readStoredSupportAccess()).toBeNull();
	});
});
