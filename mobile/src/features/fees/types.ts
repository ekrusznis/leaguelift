/** Matches backend/src/main/kotlin/com/rally26/fee/web/FeeDto.kt and dashboard/web/ParentDashboardDto.kt (ADR-103). */

export interface FeeLineItem {
  description: string;
  balanceMinor: number;
  status: string;
  dueDate: string | null;
}

export interface OutstandingBalance {
  totalOutstandingMinor: number;
  currency: string;
  lineItems: FeeLineItem[];
}

export interface FeeAssignmentResponse {
  id: string;
  organizationId: string;
  householdId: string;
  participantId: string | null;
  feeTemplateId: string | null;
  description: string;
  originalAmountMinor: number;
  currency: string;
  dueDate: string | null;
  status: string;
  paidMinor: number;
  adjustedMinor: number;
  balanceMinor: number;
  createdAt: string;
  updatedAt: string;
}

export interface FeePaymentResponse {
  id: string;
  feeAssignmentId: string;
  amountMinor: number;
  currency: string;
  method: string;
  paidAt: string;
  note: string | null;
  recordedByUserId: string;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
}
