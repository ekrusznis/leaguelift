import { BrowserAgent } from "@newrelic/browser-agent/loaders/browser-agent";
import { env } from "../lib/env";

/**
 * Workstream C (2026-08-12) — Browser/RUM monitoring, the frontend half of the
 * New Relic pass (backend APM is the Java agent, wired via Docker/docker-compose).
 * Both `licenseKey` and `applicationID` are required by New Relic's own agent —
 * `applicationID` only exists once a Browser App entity is created in the New
 * Relic UI (Browser > Add data), which hasn't happened yet as of this commit.
 * No-ops cleanly rather than throwing when either is blank, matching every other
 * blank-means-unconfigured provider key in this codebase.
 */
export function initNewRelicBrowserAgent(): void {
	if (!env.newRelicBrowserLicenseKey || !env.newRelicAppId) return;

	new BrowserAgent({
		info: { licenseKey: env.newRelicBrowserLicenseKey, applicationID: env.newRelicAppId },
		loader_config: {},
		// session_replay defaults to disabled in this package already — explicit here
		// anyway rather than relying on an upstream default, since this app handles
		// households/minors' data and recording their sessions needs its own reviewed
		// decision, not an incidental side effect of turning on error monitoring.
		init: { session_replay: { enabled: false } },
	});
}
