import { expect, test } from "@playwright/test";

/**
 * Smoke test for the Phase 0 shell against a real running dev server (backend +
 * frontend via `docker compose up` or `npm run dev` / `./gradlew bootRun`, both in
 * their local-dev-bypass configuration). Organization-isolation and checkout-flow
 * end-to-end scenarios (DESIGN-DOC.md section 22.2) get their own spec files as
 * those features are built.
 */
test("authenticated dev-mode session sees the dashboard shell", async ({ page }) => {
	await page.goto("/");
	await expect(page.getByRole("heading", { name: /welcome/i })).toBeVisible();
	await expect(page.getByRole("link", { name: /organizations/i })).toBeVisible();
});

test("organizations page is reachable and shows the create action", async ({ page }) => {
	await page.goto("/organizations");
	await expect(page.getByRole("heading", { name: /organizations/i })).toBeVisible();
	await expect(page.getByRole("button", { name: /new organization/i })).toBeVisible();
});
