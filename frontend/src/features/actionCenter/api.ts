import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { ActionCenter } from "./types";

export function useActionCenter() {
	return useQuery({
		queryKey: ["me", "action-center"],
		queryFn: () => apiFetch<ActionCenter>("/me/action-center"),
		refetchInterval: 60_000,
	});
}
