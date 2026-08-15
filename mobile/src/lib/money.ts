/**
 * Shared money presentation/input helpers for Rally26 native clients.
 * Mirrors frontend/src/lib/money.ts. Domain/API money remains integer minor units.
 */
export function currencyFractionDigits(currency: string): number {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions().maximumFractionDigits;
}

export function formatMoneyMinorUnits(amountMinor: number, currency: string): string {
  const fractionDigits = currencyFractionDigits(currency);
  const divisor = 10 ** fractionDigits;
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(amountMinor / divisor);
}

export function minorUnitsToMajorInput(amountMinor: number, currency: string): string {
  if (!Number.isSafeInteger(amountMinor)) throw new Error('Money minor units must be a safe integer.');
  const fractionDigits = currencyFractionDigits(currency);
  if (fractionDigits === 0) return String(amountMinor);
  const sign = amountMinor < 0 ? '-' : '';
  const absolute = Math.abs(amountMinor);
  const divisor = 10 ** fractionDigits;
  const whole = Math.floor(absolute / divisor);
  const fraction = String(absolute % divisor).padStart(fractionDigits, '0');
  return `${sign}${whole}.${fraction}`;
}

export function parseMajorAmountToMinorUnits(value: string, currency: string): number | null {
  const normalized = value.trim().replaceAll(',', '');
  if (!normalized) return null;
  const match = /^([+-]?)(\d+)(?:\.(\d*))?$/.exec(normalized);
  if (!match) return null;
  const fractionDigits = currencyFractionDigits(currency);
  const suppliedFraction = match[3] ?? '';
  if (suppliedFraction.length > fractionDigits) return null;
  const paddedFraction = suppliedFraction.padEnd(fractionDigits, '0');
  const divisor = 10 ** fractionDigits;
  const whole = Number(match[2]);
  const fraction = paddedFraction ? Number(paddedFraction) : 0;
  if (!Number.isSafeInteger(whole) || !Number.isSafeInteger(fraction)) return null;
  const absoluteMinor = whole * divisor + fraction;
  if (!Number.isSafeInteger(absoluteMinor)) return null;
  return match[1] === '-' ? -absoluteMinor : absoluteMinor;
}

/**
 * Strict major-unit -> integer minor-unit conversion for API/domain boundaries.
 * Feature code should call this instead of multiplying by 100.
 */
export function majorAmountToMinorUnits(value: string | number, currency: string): number {
  const parsed = parseMajorAmountToMinorUnits(String(value), currency);
  if (parsed === null) throw new Error('Invalid money amount.');
  return parsed;
}
