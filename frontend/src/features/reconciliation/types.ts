export interface ReconciliationRun { id: string; organizationId: string; status: "RUNNING" | "COMPLETED" | "FAILED"; issueCount: number; highCount: number; mediumCount: number; lowCount: number; startedByUserId: string; startedAt: string; completedAt: string | null }
export interface ReconciliationIssue { id: string; issueType: string; severity: "HIGH" | "MEDIUM" | "LOW"; resourceType: string; resourceId: string | null; title: string; detail: string; actionPath: string | null; createdAt: string }
export interface ReconciliationResult { run: ReconciliationRun; issues: ReconciliationIssue[] }
export interface ReconciliationRunPage { items: ReconciliationRun[]; page: number; size: number; totalElements: number }
