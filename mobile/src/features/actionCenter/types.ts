/** Matches backend/src/main/kotlin/com/rally26/actioncenter/domain/ActionCenterModels.kt exactly (Phase 37.10). */

export type ActionCenterPriority = 'INFO' | 'NORMAL' | 'HIGH' | 'URGENT';

export type ActionCenterType =
  | 'PROFILE_CORRECTION_REVIEW'
  | 'OVERDUE_FEES'
  | 'EVENT_REVIEW'
  | 'FULFILLMENT_EXCEPTION'
  | 'OFFLINE_FINANCIAL_REVIEW'
  | 'PAYMENT_PLAN_OVERDUE'
  | 'RECONCILIATION_REVIEW'
  | 'FEE_PAYMENT'
  | 'DOCUMENT_ACKNOWLEDGMENT'
  | 'EVENT_RSVP'
  | 'SUPPORT_CASE_RESPONSE'
  | 'PLATFORM_SUPPORT_QUEUE'
  | 'PLATFORM_DELIVERY_FAILURES';

export interface ActionCenterItem {
  id: string;
  type: ActionCenterType;
  priority: ActionCenterPriority;
  title: string;
  description: string;
  /** A web app-router path (e.g. "/app/organizations/{id}/fees") — mobile maps this to a native destination itself; see actionCenterDestination.ts. */
  actionPath: string;
  organizationId: string | null;
  contextType: string | null;
  contextId: string | null;
  dueAt: string | null;
  createdAt: string | null;
}

export interface ActionCenter {
  items: ActionCenterItem[];
  totalCount: number;
  highPriorityCount: number;
}
