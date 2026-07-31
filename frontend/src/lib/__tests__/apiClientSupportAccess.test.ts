import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearStoredSupportAccess, storeSupportAccess } from "../../features/platformAdmin/supportAccessStorage";
import { apiFetch, registerAccessTokenGetter } from "../apiClient";

const organizationId = "11111111-1111-4111-8111-111111111111";
const otherOrganizationId = "22222222-2222-4222-8222-222222222222";
const accessId = "33333333-3333-4333-8333-333333333333";

function activeAccess() {
	return {
		id: accessId,
		organizationId,
		organizationName: "North Jersey Volleyball Club",
		reason: "Help the owner resolve a roster import failure.",
		status: "ACTIVE" as const,
		expiresAt: "2099-07-31T18:00:00Z",
		endedAt: null,
		createdAt: "2099-07-31T16:00:00Z",
	};
}

describe("Platform Admin support access transport", () => {
	beforeEach(() => {
		clearStoredSupportAccess();
		registerAccessTokenGetter(async () => "test-token");
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
			status: 200,
			headers: { "content-type": "application/json" },
		})));
	});

	afterEach(() => {
		clearStoredSupportAccess();
		registerAccessTokenGetter(async () => null);
		vi.unstubAllGlobals();
	});

	it("attaches the session to a matching organization-first route", async () => {
		storeSupportAccess(activeAccess());

		await apiFetch(`/organizations/${organizationId}/teams`);

		expect(lastRequestHeaders().get("X-LeagueLift-Support-Access")).toBe(accessId);
	});

	it("does not attach the session to another organization", async () => {
		storeSupportAccess(activeAccess());

		await apiFetch(`/organizations/${otherOrganizationId}/teams`);

		expect(lastRequestHeaders().get("X-LeagueLift-Support-Access")).toBeNull();
	});

	it("attaches the session to a resource-first route with the matching organization query", async () => {
		storeSupportAccess(activeAccess());

		await apiFetch(`/teams/44444444-4444-4444-8444-444444444444/events?organizationId=${organizationId}`);

		expect(lastRequestHeaders().get("X-LeagueLift-Support-Access")).toBe(accessId);
	});

	it("does not send the session to platform endpoints", async () => {
		storeSupportAccess(activeAccess());

		await apiFetch("/platform/admin/organizations");

		expect(lastRequestHeaders().get("X-LeagueLift-Support-Access")).toBeNull();
	});
});

function lastRequestHeaders(): Headers {
	const fetchMock = vi.mocked(fetch);
	const init = fetchMock.mock.calls.at(-1)?.[1];
	return new Headers(init?.headers);
}
