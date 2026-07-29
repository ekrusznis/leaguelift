import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OrganizationBrandingPanel } from "../OrganizationBrandingPanel";

const organizationId = "11111111-1111-1111-1111-111111111111";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function urlAndMethod(input: RequestInfo | URL, init?: RequestInit): [string, string] {
	return [typeof input === "string" ? input : input.toString(), init?.method ?? "GET"];
}

describe("OrganizationBrandingPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty logo and cover state with no assignments", async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ items: [] }));
		vi.stubGlobal("fetch", fetchMock);

		renderWithProviders(<OrganizationBrandingPanel organizationId={organizationId} organizationName="Riverside Soccer" />);

		expect(await screen.findByLabelText(/^logo$/i)).toBeInTheDocument();
		expect(screen.getByText(/no cover/i)).toBeInTheDocument();
	});

	it("uploads, confirms, and assigns a logo end to end", async () => {
		let assignedLogoUrl: string | null = null;
		const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
			const [url, method] = urlAndMethod(input, init);

			if (url.includes("/media/assignments") && method === "GET") {
				return jsonResponse({
					items: assignedLogoUrl
						? [
								{
									usageSlot: "LOGO",
									assetId: "asset-1",
									url: assignedLogoUrl,
									altText: null,
									contentType: "image/png",
									widthPx: 10,
									heightPx: 10,
									byteSizeBytes: 100,
									visibility: "ORGANIZATION_PRIVATE",
									publicationStatus: "PRIVATE",
									updatedAt: new Date().toISOString(),
								},
							]
						: [],
				});
			}
			if (url.endsWith("/media/uploads") && method === "POST") {
				return jsonResponse(
					{
						assetId: "asset-1",
						uploadUrl: "https://minio.local/put-url",
						uploadMethod: "PUT",
						requiredHeaders: { "Content-Type": "image/png" },
						expiresAt: new Date().toISOString(),
					},
					201,
				);
			}
			if (url.includes("minio.local/put-url") && method === "PUT") {
				return new Response(null, { status: 200 });
			}
			if (url.endsWith("/confirm") && method === "POST") {
				return jsonResponse({
					assetId: "asset-1",
					status: "READY",
					contentType: "image/png",
					byteSize: 100,
					widthPx: 10,
					heightPx: 10,
					rejectionReason: null,
				});
			}
			if (url.endsWith("/media/assignments/LOGO") && method === "PUT") {
				assignedLogoUrl = "https://minio.local/read-url";
				return jsonResponse({
					usageSlot: "LOGO",
					assetId: "asset-1",
					url: assignedLogoUrl,
					altText: null,
					contentType: "image/png",
					widthPx: 10,
					heightPx: 10,
					byteSizeBytes: 100,
					visibility: "ORGANIZATION_PRIVATE",
					publicationStatus: "PRIVATE",
					updatedAt: new Date().toISOString(),
				});
			}
			throw new Error(`Unexpected fetch: ${method} ${url}`);
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationBrandingPanel organizationId={organizationId} organizationName="Riverside Soccer" />);

		const logoInput = await screen.findByLabelText(/^logo$/i);
		const file = new File([new Uint8Array([1, 2, 3])], "logo.png", { type: "image/png" });
		await user.upload(logoInput, file);

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining("/media/assignments/LOGO"),
				expect.objectContaining({ method: "PUT" }),
			),
		);
		expect(await screen.findByAltText(/riverside soccer logo/i)).toBeInTheDocument();
	});

	it("shows the rejection reason when the backend rejects the upload", async () => {
		const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
			const [url, method] = urlAndMethod(input, init);

			if (url.includes("/media/assignments") && method === "GET") {
				return jsonResponse({ items: [] });
			}
			if (url.endsWith("/media/uploads") && method === "POST") {
				return jsonResponse(
					{
						assetId: "asset-2",
						uploadUrl: "https://minio.local/put-url-2",
						uploadMethod: "PUT",
						requiredHeaders: { "Content-Type": "image/png" },
						expiresAt: new Date().toISOString(),
					},
					201,
				);
			}
			if (url.includes("minio.local/put-url-2") && method === "PUT") {
				return new Response(null, { status: 200 });
			}
			if (url.endsWith("/confirm") && method === "POST") {
				return jsonResponse({
					assetId: "asset-2",
					status: "REJECTED",
					contentType: null,
					byteSize: null,
					widthPx: null,
					heightPx: null,
					rejectionReason: "CONTENT_TYPE_MISMATCH",
				});
			}
			throw new Error(`Unexpected fetch: ${method} ${url}`);
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationBrandingPanel organizationId={organizationId} organizationName="Riverside Soccer" />);

		const logoInput = await screen.findByLabelText(/^logo$/i);
		const file = new File([new Uint8Array([1, 2, 3])], "logo.png", { type: "image/png" });
		await user.upload(logoInput, file);

		expect(await screen.findByText(/content_type_mismatch/i)).toBeInTheDocument();
	});

	it("rejects an oversized file client-side without making an upload request", async () => {
		// The <input accept="..."> attribute already stops a mismatched MIME type from
		// reaching onChange in a real browser (and user-event models that), so this
		// exercises the size-limit branch of fileSchemaFor instead — accept doesn't
		// filter by size.
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ items: [] }));
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationBrandingPanel organizationId={organizationId} organizationName="Riverside Soccer" />);

		const logoInput = await screen.findByLabelText(/^logo$/i);
		const oversizedFile = new File([new Uint8Array(11 * 1024 * 1024)], "logo.png", { type: "image/png" });
		await user.upload(logoInput, oversizedFile);

		expect(await screen.findByText(/too large/i)).toBeInTheDocument();
		const uploadCalls = fetchMock.mock.calls.filter((call) => {
			const input = call[0] as RequestInfo | URL;
			return (typeof input === "string" ? input : input.toString()).endsWith("/media/uploads");
		});
		expect(uploadCalls.length).toBe(0);
	});
});
