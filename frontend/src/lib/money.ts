/**
 * Formats an integer minor-unit amount (e.g. cents) using its ISO-4217 currency
 * code. Money is never a floating-point value anywhere in Rally26
 * (DESIGN-DOC.md sections 17.2, 19.3) — this is the one place formatting happens so
 * every feature module renders money consistently.
 */
export function formatMoneyMinorUnits(amountMinor: number, currency: string): string {
	return new Intl.NumberFormat(undefined, {
		style: "currency",
		currency,
	}).format(amountMinor / 100);
}
