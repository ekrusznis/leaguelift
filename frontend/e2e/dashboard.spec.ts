import { expect, test } from "@playwright/test";

/**
 * Smoke test for the Phase 0 shell against a real running dev server (backend +
 * frontend via `docker compose up` or `npm run dev` / `./gradlew bootRun`). There is
 * no auth bypass in any environment (ADR-014) — this predates real authentication
 * and needs updating to log in first (a real account or a seeded dashboard-role
 * fixture, `db/seed/V9000__dev_seed_dashboard_role_users.sql`) before it will pass.
 * Organization-isolation and checkout-flow end-to-end scenarios (DESIGN-DOC.md
 * section 22.2) get their own spec files as those features are built.
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
