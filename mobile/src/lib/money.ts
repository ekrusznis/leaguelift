/**
 * Formats an integer minor-unit amount (e.g. cents) using its ISO-4217 currency
 * code. Money is never a floating-point value anywhere in Rally26 (DESIGN-DOC.md
 * §17.2, §19.3) — mirrors frontend/src/lib/money.ts exactly.
 */
export function formatMoneyMinorUnits(amountMinor: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(amountMinor / 100);
}
