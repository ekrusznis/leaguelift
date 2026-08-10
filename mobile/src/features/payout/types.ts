/** Matches backend/src/main/kotlin/com/rally26/payout/web/PayoutAccountDto.kt (ADR-105). Read-only this slice — see api.ts. */

export interface PayoutAccountResponse {
  stripeAccountId: string;
  detailsSubmitted: boolean;
  chargesEnabled: boolean;
  payoutsEnabled: boolean;
  isFullyConnected: boolean;
  updatedAt: string;
}

export interface PayoutSummaryResponse {
  eligibleMinor: number;
  heldMinor: number;
  pendingDebitsMinor: number;
  netAvailableMinor: number;
}
