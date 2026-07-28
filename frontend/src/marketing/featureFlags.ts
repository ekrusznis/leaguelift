/**
 * Feature flags for incomplete or not-yet-configured sales-site capabilities
 * (LEAGUELIFT_SALES_SITE_DESIGN.md section 39.5). Flip a flag only once the
 * underlying capability is real — an incomplete feature must never be visible in
 * production.
 */
export const featureFlags = {
	/** Retired for GA — the founding-pilot announcement no longer applies. */
	pilotAnnouncementBar: false,
	/** No scheduling provider is configured yet; the internal form (Option A, section 30) is used instead. */
	demoBookingProvider: false,
	/** No OIDC tenant is configured; social buttons would be dead ends if shown. */
	socialAuthProviders: false,
	newsletter: false,
	productPreview: false,
} as const;
