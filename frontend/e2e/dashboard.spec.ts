import { expect, test } from "@playwright/test";

/**
 * Phase 14 dashboard/connectivity smoke tests against the current route map.
 */
test("dashboard route redirects to sign-in with a next param when not authenticated", async ({ page }) => {
	await page.goto("/app");
	await expect(page).toHaveURL(/\/auth\/sign-in\?next=%2Fapp$/);
	await expect(page.getByRole("heading", { name: /welcome back/i })).toBeVisible();
});

test("organizations route redirects to sign-in with a next param when not authenticated", async ({ page }) => {
	await page.goto("/app/organizations");
	await expect(page).toHaveURL(/\/auth\/sign-in\?next=%2Fapp%2Forganizations$/);
	await expect(page.getByRole("heading", { name: /welcome back/i })).toBeVisible();
});
