import { expect, test } from "@playwright/test";

/**
 * Phase 14 dashboard/connectivity smoke tests against the current route map.
 */
test("dashboard route shows the sign-in prompt when not authenticated", async ({ page }) => {
	await page.goto("/app");
	await expect(page).toHaveURL(/\/app(?:#.*)?$/);
	await expect(page.getByRole("alert")).toContainText(/please sign in to continue/i);
});

test("organizations route shows the sign-in prompt when not authenticated", async ({ page }) => {
	await page.goto("/app/organizations");
	await expect(page).toHaveURL(/\/app\/organizations$/);
	await expect(page.getByRole("alert")).toContainText(/please sign in to continue/i);
});
