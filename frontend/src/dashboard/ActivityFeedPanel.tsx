import { describeActivityAction, timeAgo } from "./activity";
import { useMyActivity } from "../features/activity/api";

/**
 * Dropdown content for the shell's bell icon (DESIGN-DOC.md section 13, Phase 7
 * completion) — the cross-org activity feed, distinct from the Owner dashboard's
 * single-organization "Recent Activity" card.
 */
export function ActivityFeedPanel() {
	const { data, isLoading, isError } = useMyActivity();

	return (
		<div className="max-h-96 w-80 overflow-y-auto rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
			<p className="px-2 py-1.5 text-xs font-semibold uppercase tracking-wide text-slate-400">Recent Activity</p>
			{isLoading && <p className="px-2 py-3 text-sm text-slate-500">Loading…</p>}
			{isError && <p className="px-2 py-3 text-sm text-error-600">Could not load activity.</p>}
			{data && data.items.length === 0 && <p className="px-2 py-3 text-sm text-slate-500">No recent activity.</p>}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col">
					{data.items.map((item) => (
						<li key={item.id} className="flex flex-col gap-0.5 rounded-lg px-2 py-2 hover:bg-ice-50">
							<div className="flex items-center justify-between gap-2">
								<span className="text-sm font-medium text-navy-900">{describeActivityAction(item.action)}</span>
								<span className="shrink-0 text-xs text-slate-400">{timeAgo(item.occurredAt)}</span>
							</div>
							{item.organizationName && <span className="text-xs text-slate-500">{item.organizationName}</span>}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
