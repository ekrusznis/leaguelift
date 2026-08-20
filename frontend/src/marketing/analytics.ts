/**
 * Privacy-safe event tracking (RALLY26_SALES_SITE_DESIGN.md section 36). No
 * analytics provider is wired up yet, so this only logs in development — swap the
 * implementation here when one is chosen, without touching call sites.
 */
export type AnalyticsEvent =
	| "nav_item_clicked"
	| "login_clicked"
	| "primary_cta_clicked"
	| "demo_cta_clicked"
	| "hero_cta_clicked"
	| "hero_how_it_works_clicked"
	| "solution_card_clicked"
	| "guided_onboarding_viewed"
	| "faq_opened"
	| "final_cta_clicked"
	| "final_cta_contact_clicked"
	| "founding_org_cta_clicked"
	| "founding_org_cta_clicked_footer"
	| "sales_form_viewed"
	| "sales_form_started"
	| "sales_form_step_completed"
	| "sales_form_validation_failed"
	| "sales_form_submitted"
	| "sales_form_submission_failed"
	| "sign_in_viewed"
	| "sign_in_started"
	| "sign_in_succeeded"
	| "sign_in_failed"
	| "registration_started"
	| "registration_step_completed"
	| "registration_succeeded"
	| "password_reset_requested";

export type AnalyticsProperties = Record<string, string | number | boolean | undefined>;

export function track(event: AnalyticsEvent, properties?: AnalyticsProperties): void {
	if (import.meta.env.DEV) {
		// eslint-disable-next-line no-console
		console.debug(`[analytics] ${event}`, properties ?? {});
	}
}

const UTM_KEYS = ["utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term"] as const;

export type AttributionData = Record<(typeof UTM_KEYS)[number], string | undefined> & {
	landingPage: string;
	referrer: string;
};

/**
 * Captures UTM parameters and the first-touch landing page once per browser session
 * (section 36.5) so the sales-request form can submit them without re-reading the
 * URL after the visitor has navigated elsewhere on the site.
 */
export function captureAttribution(): AttributionData {
	const stored = sessionStorage.getItem("ll_attribution");
	if (stored) {
		return JSON.parse(stored) as AttributionData;
	}

	const params = new URLSearchParams(window.location.search);
	const attribution: AttributionData = {
		utm_source: params.get("utm_source") ?? undefined,
		utm_medium: params.get("utm_medium") ?? undefined,
		utm_campaign: params.get("utm_campaign") ?? undefined,
		utm_content: params.get("utm_content") ?? undefined,
		utm_term: params.get("utm_term") ?? undefined,
		landingPage: window.location.pathname,
		referrer: document.referrer,
	};

	sessionStorage.setItem("ll_attribution", JSON.stringify(attribution));
	return attribution;
}
