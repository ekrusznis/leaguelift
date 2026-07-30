import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { ActivityFeedResponse } from "./types";

/** Cross-org activity feed (DESIGN-DOC.md section 13, Phase 7 completion) — every organization the caller belongs to, or platform-wide for a platform admin. */
export function useMyActivity() {
	return useQuery({
		queryKey: ["me", "activity"],
		queryFn: () => apiFetch<ActivityFeedResponse>("/me/activity"),
	});
}
