import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { ReconciliationResult, ReconciliationRunPage } from "./types";
const latestKey = (id: string) => ["organizations", id, "reconciliation", "latest"] as const;
const runsKey = (id: string) => ["organizations", id, "reconciliation", "runs"] as const;
export function useLatestReconciliation(id: string) { return useQuery({ queryKey: latestKey(id), queryFn: () => apiFetch<ReconciliationResult | undefined>(`/organizations/${id}/reconciliation-runs/latest`), enabled: !!id }); }
export function useReconciliationRuns(id: string) { return useQuery({ queryKey: runsKey(id), queryFn: () => apiFetch<ReconciliationRunPage>(`/organizations/${id}/reconciliation-runs`), enabled: !!id }); }
export function useRunReconciliation(id: string) { const client = useQueryClient(); return useMutation({ mutationFn: () => apiFetch<ReconciliationResult>(`/organizations/${id}/reconciliation-runs`, { method: "POST" }), onSuccess: () => { client.invalidateQueries({ queryKey: latestKey(id) }); client.invalidateQueries({ queryKey: runsKey(id) }); } }); }
