import { defineConfig, devices } from "@playwright/test";

/**
 * DESIGN-DOC.md section 22.2 requires Playwright end-to-end tests, including mobile
 * viewport tests. Browser binaries are not installed by `npm install` — run
 * `npx playwright install` once locally before `npx playwright test`
 * (not runnable in the sandbox this scaffold was generated in; no browser download
 * access there).
 */
export default defineConfig({
	testDir: "./e2e",
	fullyParallel: true,
	retries: process.env.CI ? 1 : 0,
	reporter: "html",
	use: {
		baseURL: "http://localhost:5173",
		trace: "on-first-retry",
	},
	projects: [
		{ name: "chromium-desktop", use: { ...devices["Desktop Chrome"] } },
		{ name: "mobile-safari", use: { ...devices["iPhone 14"] } },
	],
	webServer: {
		command: "npm run dev",
		url: "http://localhost:5173",
		reuseExistingServer: !process.env.CI,
	},
});
