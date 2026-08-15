export type ReconciliationRunStatus = "RUNNING" | "COMPLETED" | "FAILED";
export type ReconciliationSeverity = "HIGH" | "MEDIUM" | "LOW";

export interface ReconciliationRun { id: string; organizationId: string; status: ReconciliationRunStatus; issueCount: number; highCount: number; mediumCount: number; lowCount: number; startedByUserId: string; startedAt: string; completedAt: string | null }
export interface ReconciliationIssue { id: string; issueType: string; severity: ReconciliationSeverity; resourceType: string; resourceId: string | null; title: string; detail: string; actionPath: string | null; createdAt: string }
export interface ReconciliationResult { run: ReconciliationRun; issues: ReconciliationIssue[] }
export interface ReconciliationRunPage { items: ReconciliationRun[]; page: number; size: number; totalElements: number }
export interface ReconciliationIssuePage { items: ReconciliationIssue[]; page: number; size: number; totalElements: number }
