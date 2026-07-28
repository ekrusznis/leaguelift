import { AthleteDashboard } from "../dashboard/roles/AthleteDashboard";
import { CoachDashboard } from "../dashboard/roles/CoachDashboard";
import { OwnerDashboard } from "../dashboard/roles/OwnerDashboard";
import { ParentDashboard } from "../dashboard/roles/ParentDashboard";
import { useDashboardContext } from "../dashboard/api";
import { LoadingState } from "../components/states/LoadingState";
import { ErrorState } from "../components/states/ErrorState";
import { UnauthorizedState } from "../components/states/UnauthorizedState";

/**
 * Routes the signed-in user to their dashboard based on a real backend role check
 * (GET /me/dashboard-context) — not a dev-only switcher (DESIGN-DOC.md section 10.1).
 * Owner/Coach need an organizationId and Parent additionally needs a householdId;
 * both come from the resolved context, not from client state.
 */
export function DashboardPage() {
	const context = useDashboardContext();

	if (context.isLoading) {
		return <LoadingState label="Loading your dashboard…" />;
	}

	if (context.isError) {
		return <ErrorState message="Could not determine your dashboard." onRetry={() => context.refetch()} />;
	}

	const data = context.data;
	if (!data) {
		return null;
	}

	switch (data.role) {
		case "OWNER":
			return data.organizationId ? <OwnerDashboard organizationId={data.organizationId} /> : <UnauthorizedState message="No organization found for your account." />;
		case "COACH":
			return data.organizationId ? <CoachDashboard organizationId={data.organizationId} /> : <UnauthorizedState message="No organization found for your account." />;
		case "PARENT":
			return data.organizationId && data.householdId ? (
				<ParentDashboard organizationId={data.organizationId} householdId={data.householdId} />
			) : (
				<UnauthorizedState message="No household found for your account." />
			);
		case "ATHLETE":
			return <AthleteDashboard />;
	}
}
