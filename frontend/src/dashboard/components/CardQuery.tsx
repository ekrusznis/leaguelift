import type { ReactNode } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import { LoadingState } from "../../components/states/LoadingState";
import { ErrorState } from "../../components/states/ErrorState";
import { EmptyState } from "../../components/states/EmptyState";

/**
 * Every dashboard card fires its own query and renders its own loading spinner,
 * error state (with retry), and empty state, independent of every other card on the
 * page (DESIGN-DOC.md section 10.1/10.2) — this wraps that repeated pattern once
 * instead of duplicating it in every card.
 */
export function CardQuery<T>({
	query,
	children,
	isEmpty,
	emptyTitle = "Nothing here yet",
	emptyDescription,
	loadingLabel = "Loading…",
}: {
	query: UseQueryResult<T>;
	children: (data: T) => ReactNode;
	isEmpty?: (data: T) => boolean;
	emptyTitle?: string;
	emptyDescription?: string;
	loadingLabel?: string;
}) {
	if (query.isLoading) {
		return <LoadingState label={loadingLabel} />;
	}

	if (query.isError) {
		return <ErrorState onRetry={() => query.refetch()} />;
	}

	if (query.data === undefined) {
		return null;
	}

	if (isEmpty?.(query.data)) {
		return <EmptyState title={emptyTitle} description={emptyDescription} />;
	}

	return <>{children(query.data)}</>;
}
