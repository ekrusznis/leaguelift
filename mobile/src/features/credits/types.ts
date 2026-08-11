/** Matches backend/src/main/kotlin/com/rally26/credit/web/CreditDto.kt exactly — prefer this over the narrower dashboard-card shape (ADR-103). */
export interface FamilyCreditBalanceResponse {
  currency: string;
  availableMinor: number;
  pendingMinor: number;
  expiringSoonMinor: number;
  appliedAllTimeMinor: number;
  p2pTransferEnabled: boolean;
}

export interface FamilyCreditGrantResponse {
  id: string;
  amountMinor: number;
  remainingMinor: number;
  currency: string;
  status: string;
  sourceType: string;
  grantedAt: string;
  expiresAt: string | null;
}
