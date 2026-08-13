import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HouseholdMediaPanel } from "../HouseholdMediaPanel";
import type { HouseholdMediaItem } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";
const householdId = "22222222-2222-2222-2222-222222222222";
const assignmentId = "33333333-3333-3333-3333-333333333333";
const assetId = "44444444-4444-4444-4444-444444444444";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderPanel() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<HouseholdMediaPanel organizationId={organizationId} householdId={householdId} />
		</QueryClientProvider>,
	);
}

const photoItem: HouseholdMediaItem = {
	id: assignmentId,
	assetId,
	url: "https://signed.example.com/photo.jpg",
	contentType: "image/jpeg",
	byteSizeBytes: 204800,
	widthPx: 800,
	heightPx: 600,
	isVideo: false,
	visibility: "HOUSEHOLD_PRIVATE",
	publicationStatus: "DRAFT",
	createdAt: new Date("2026-08-01T00:00:00Z").toISOString(),
	updatedAt: new Date("2026-08-01T00:00:00Z").toISOString(),
};

describe("HouseholdMediaPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there is no media yet", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockImplementation((url: string) => {
				if (url.includes("/media") && !url.includes("release-publicly")) return Promise.resolve(jsonResponse({ items: [] }));
				return Promise.resolve(jsonResponse(null));
			}),
		);
		renderPanel();

		expect(await screen.findByText(/no photos or videos yet/i)).toBeInTheDocument();
	});

	it("uploads a photo through request/PUT/confirm/assign and shows it in the gallery", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (url === "https://signed.example.com/upload") return Promise.resolve(new Response(null, { status: 200 }));
			if (url.includes("/media/uploads/") && url.includes("/confirm")) {
				return Promise.resolve(
					jsonResponse({ assetId, status: "READY", contentType: "image/jpeg", byteSize: 204800, widthPx: 800, heightPx: 600, rejectionReason: null }),
				);
			}
			if (url.includes("/media/uploads")) {
				return Promise.resolve(
					jsonResponse({ assetId, uploadUrl: "https://signed.example.com/upload", uploadMethod: "PUT", requiredHeaders: {}, expiresAt: new Date().toISOString() }),
				);
			}
			if (url.endsWith("/media") && init?.method === "POST") {
				return Promise.resolve(jsonResponse(photoItem, 201));
			}
			if (url.endsWith("/media")) {
				const hasAssigned = fetchMock.mock.calls.some((call: unknown[]) => (call[1] as RequestInit | undefined)?.method === "POST");
				return Promise.resolve(jsonResponse({ items: hasAssigned ? [photoItem] : [] }));
			}
			return Promise.resolve(jsonResponse(null));
		});
		vi.stubGlobal("fetch", fetchMock);

		const user = userEvent.setup();
		renderPanel();
		await screen.findByText(/no photos or videos yet/i);

		const file = new File(["fake-bytes"], "photo.jpg", { type: "image/jpeg" });
		const input = screen.getByLabelText(/add a photo or video/i);
		await user.upload(input, file);

		await waitFor(() => {
			expect(screen.getByRole("list", { name: /household media/i })).toBeInTheDocument();
		});
	});

	it("releases selected media publicly after confirming the explanatory dialog", async () => {
		let released = false;
		vi.stubGlobal(
			"fetch",
			vi.fn().mockImplementation((url: string, init?: RequestInit) => {
				if (url.includes("release-publicly")) {
					released = true;
					return Promise.resolve(jsonResponse({ items: [{ ...photoItem, visibility: "PUBLIC", publicationStatus: "PUBLISHED" }] }));
				}
				if (url.endsWith("/media")) {
					return Promise.resolve(jsonResponse({ items: [released ? { ...photoItem, publicationStatus: "PUBLISHED" } : photoItem] }));
				}
				return Promise.resolve(jsonResponse(null));
			}),
		);

		const user = userEvent.setup();
		renderPanel();

		const checkbox = await screen.findByRole("checkbox");
		await user.click(checkbox);
		await user.click(screen.getByRole("button", { name: /release publicly \(1\)/i }));

		expect(await screen.findByRole("dialog", { name: /release this media publicly/i })).toBeInTheDocument();
		expect(screen.getByText(/visible outside your household/i)).toBeInTheDocument();

		await user.click(screen.getByRole("button", { name: /^release 1 item$/i }));

		await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
		expect(released).toBe(true);
	});
});
