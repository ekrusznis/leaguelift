import { useAuth } from "../auth/AuthContext";

/**
 * Dashboards must answer: what needs attention, what's outstanding, what's active,
 * what changed, what to do next (DESIGN-DOC.md section 17.4). Phase 0 has no
 * financial or campaign data yet, so this is intentionally a minimal welcome screen
 * rather than a vanity chart with nothing to act on.
 */
export function DashboardPage() {
	const { user } = useAuth();

	return (
		<div className="flex flex-col gap-4">
			<h1 className="font-heading text-2xl font-bold text-navy">Welcome{user ? `, ${user.displayName}` : ""}</h1>
			<p className="max-w-prose text-slate-gray">
				This is the Phase 0 foundation shell. Once organizations, teams, fees, and
				fundraising are built out, this dashboard will surface outstanding balances,
				active campaigns, and recent activity.
			</p>
		</div>
	);
}
