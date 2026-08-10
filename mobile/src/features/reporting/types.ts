/** Matches backend/src/main/kotlin/com/rally26/reporting/web/ReportingDto.kt (ADR-105). None of these carry an isDemoData flag — the whole module predates that convention and is real end-to-end. */

export interface SourceTypeRevenueResponse {
  sourceType: string;
  amountMinor: number;
}

export interface TeamRevenueResponse {
  teamId: string | null;
  teamName: string | null;
  amountMinor: number;
}

export interface RevenueReportResponse {
  from: string;
  to: string;
  totalMinor: number;
  bySourceType: SourceTypeRevenueResponse[];
  byTeam: TeamRevenueResponse[];
}

export interface RefundResponse {
  sourceType: string;
  sourceId: string;
  amountMinor: number;
  effectiveAt: string;
}

export interface RefundsReportResponse {
  from: string;
  to: string;
  count: number;
  totalMinor: number;
  refunds: RefundResponse[];
}

export interface FeeCollectionResponse {
  feePaymentId: string;
  householdId: string;
  householdName: string;
  amountMinor: number;
  paidAt: string;
}

export interface FeeCollectionsReportResponse {
  from: string;
  to: string;
  collectedMinor: number;
  outstandingMinor: number;
  payments: FeeCollectionResponse[];
}
