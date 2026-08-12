/**
 * Feature flags for incomplete or not-yet-configured authenticated-app capabilities.
 * Flip a flag only once the underlying capability is real — an incomplete feature
 * must never be visible in production. Mirrors the sales-site pattern in
 * `marketing/featureFlags.ts`.
 */
export const featureFlags = {
	/** No SMS provider is configured (Twilio has no funded account yet) — every environment
	 * defaults to `rally26.sms.provider = logging`, a no-op that silently drops SMS sends.
	 * Hide SMS-facing controls until a real provider is wired up, rather than showing
	 * toggles/checkboxes that appear to work but do nothing. */
	smsNotifications: false,
} as const;
