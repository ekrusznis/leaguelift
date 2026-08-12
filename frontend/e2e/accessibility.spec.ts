import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

/**
 * WCAG 2.1 AA runtime scan across every route that renders meaningfully without
 * a backend or an authenticated session (marketing pages, auth forms, and the
 * signed-out state of /app). Routes behind ProtectedRoute render nothing useful
 * here since there's no backend in this webServer — those need a separate
 * authenticated pass once we have seeded test accounts to sign in with.
 */
const PUBLIC_ROUTES = [
	"/",
	"/security",
	"/help",
	"/privacy",
	"/terms",
	"/accessibility",
	"/auth/sign-in",
	"/auth/register",
	"/auth/forgot-password",
	"/app",
];

for (const route of PUBLIC_ROUTES) {
	test(`${route} has no WCAG 2.1 AA violations`, async ({ page }) => {
		await page.goto(route);
		const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();

		expect(results.violations, formatViolations(results.violations)).toEqual([]);
	});
}

function formatViolations(violations: import("axe-core").Result[]): string {
	if (violations.length === 0) return "";
	return violations
		.map((violation) => {
			const targets = violation.nodes.map((node) => `    - ${node.target.join(" ")}`).join("\n");
			return `[${violation.impact}] ${violation.id}: ${violation.help}\n${targets}`;
		})
		.join("\n\n");
}
