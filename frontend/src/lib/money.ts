/**
 * Shared money presentation/input helpers for Rally26 web.
 *
 * Domain/API money remains integer minor units. Feature modules should not divide
 * by 100, call toFixed(), or manually prefix "$". Keep those conversions here so
 * web and mobile follow the same behavior.
 */
export function currencyFractionDigits(currency: string): number {
	return new Intl.NumberFormat(undefined, { style: "currency", currency }).resolvedOptions().maximumFractionDigits;
}

export function formatMoneyMinorUnits(amountMinor: number, currency: string): string {
	const fractionDigits = currencyFractionDigits(currency);
	const divisor = 10 ** fractionDigits;
	return new Intl.NumberFormat(undefined, {
		style: "currency",
		currency,
	}).format(amountMinor / divisor);
}

/** Converts integer minor units to a plain major-unit form value without a currency symbol. */
export function minorUnitsToMajorInput(amountMinor: number, currency: string): string {
	if (!Number.isSafeInteger(amountMinor)) throw new Error("Money minor units must be a safe integer.");
	const fractionDigits = currencyFractionDigits(currency);
	if (fractionDigits === 0) return String(amountMinor);
	const sign = amountMinor < 0 ? "-" : "";
	const absolute = Math.abs(amountMinor);
	const divisor = 10 ** fractionDigits;
	const whole = Math.floor(absolute / divisor);
	const fraction = String(absolute % divisor).padStart(fractionDigits, "0");
	return `${sign}${whole}.${fraction}`;
}

/**
 * Parses a plain major-unit form value into integer minor units using string/integer
 * arithmetic. Returns null for invalid input or values outside JS safe-integer range.
 */
export function parseMajorAmountToMinorUnits(value: string, currency: string): number | null {
	const normalized = value.trim().replaceAll(",", "");
	if (!normalized) return null;
	const match = /^([+-]?)(\d+)(?:\.(\d*))?$/.exec(normalized);
	if (!match) return null;
	const fractionDigits = currencyFractionDigits(currency);
	const suppliedFraction = match[3] ?? "";
	if (suppliedFraction.length > fractionDigits) return null;
	const paddedFraction = suppliedFraction.padEnd(fractionDigits, "0");
	const divisor = 10 ** fractionDigits;
	const whole = Number(match[2]);
	const fraction = paddedFraction ? Number(paddedFraction) : 0;
	if (!Number.isSafeInteger(whole) || !Number.isSafeInteger(fraction)) return null;
	const absoluteMinor = whole * divisor + fraction;
	if (!Number.isSafeInteger(absoluteMinor)) return null;
	return match[1] === "-" ? -absoluteMinor : absoluteMinor;
}
