import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ArticleAttachmentPicker } from "../ArticleAttachmentPicker";

const articleId = "11111111-1111-1111-1111-111111111111";
const assignmentId = "22222222-2222-2222-2222-222222222222";
const assetId = "33333333-3333-3333-3333-333333333333";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderPicker(onInsert: (markdown: string) => void = vi.fn()) {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<ArticleAttachmentPicker articleId={articleId} onInsert={onInsert} />
		</QueryClientProvider>,
	);
}

const attachment = {
	id: assignmentId,
	assetId,
	url: "https://signed.example.com/diagram.png",
	contentType: "image/png",
	byteSizeBytes: 2048,
	widthPx: 400,
	heightPx: 300,
	createdAt: new Date("2026-08-13T00:00:00Z").toISOString(),
};

describe("ArticleAttachmentPicker", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no attachments", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockImplementation((url: string) => {
				if (url.endsWith("/attachments")) return Promise.resolve(jsonResponse({ items: [] }));
				return Promise.resolve(jsonResponse(null));
			}),
		);
		renderPicker();

		expect(await screen.findByText(/no attachments yet/i)).toBeInTheDocument();
	});

	it("inserts an image embed token when Insert is clicked", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockImplementation((url: string) => {
				if (url.endsWith("/attachments")) return Promise.resolve(jsonResponse({ items: [attachment] }));
				return Promise.resolve(jsonResponse(null));
			}),
		);
		const onInsert = vi.fn();
		const user = userEvent.setup();
		renderPicker(onInsert);

		await user.click(await screen.findByRole("button", { name: /insert/i }));

		expect(onInsert).toHaveBeenCalledWith(`![image/png](attachment:${assignmentId})`);
	});

	it("uploads a file through request/PUT/confirm/add", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (url === "https://signed.example.com/upload") return Promise.resolve(new Response(null, { status: 200 }));
			if (url.includes("/attachments/uploads/") && url.includes("/confirm")) {
				return Promise.resolve(jsonResponse({ assetId, status: "READY", rejectionReason: null }));
			}
			if (url.includes("/attachments/uploads")) {
				return Promise.resolve(
					jsonResponse({ assetId, uploadUrl: "https://signed.example.com/upload", uploadMethod: "PUT", requiredHeaders: {}, expiresAt: new Date().toISOString() }),
				);
			}
			if (url.endsWith("/attachments") && init?.method === "POST") {
				return Promise.resolve(jsonResponse(attachment, 201));
			}
			if (url.endsWith("/attachments")) return Promise.resolve(jsonResponse({ items: [] }));
			return Promise.resolve(jsonResponse(null));
		});
		vi.stubGlobal("fetch", fetchMock);

		const user = userEvent.setup();
		renderPicker();
		await screen.findByText(/no attachments yet/i);

		const file = new File(["fake-bytes"], "diagram.png", { type: "image/png" });
		const input = document.querySelector("input[type='file']") as HTMLInputElement;
		await user.upload(input, file);

		await waitFor(() => {
			expect(fetchMock.mock.calls.some((call: unknown[]) => (call[0] as string).endsWith("/attachments") && (call[1] as RequestInit | undefined)?.method === "POST")).toBe(true);
		});
	});
});
