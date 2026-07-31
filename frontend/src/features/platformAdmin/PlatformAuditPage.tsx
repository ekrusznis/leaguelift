import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { describeActivityAction, timeAgo } from "../../dashboard/activity";
import { useMyActivity } from "../activity/api";

export function PlatformAuditPage() {
	const activity = useMyActivity();
	if (activity.isLoading) return <LoadingState label="Loading platform audit…" />;
	if (activity.isError || !activity.data) return <ErrorState message="Could not load platform audit activity." onRetry={() => activity.refetch()} />;

	return (
		<div className="flex flex-col gap-6">
			<div><h1 className="font-heading text-2xl font-bold text-navy-900">Audit</h1><p className="mt-1 text-slate-500">Immutable cross-organization activity, including Platform Admin support-access starts, ends, and expirations.</p></div>
			<section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
				{activity.data.items.length === 0 ? <p className="p-6 text-sm text-slate-500">No audit activity is available.</p> : (
					<ul className="divide-y divide-slate-100">
						{activity.data.items.map((item) => (
							<li key={item.id} className="grid gap-2 px-5 py-4 sm:grid-cols-[1fr_auto]">
								<div><p className="font-semibold text-navy-900">{describeActivityAction(item.action)}</p><p className="mt-1 text-sm text-slate-500">{item.organizationName ?? "Platform"} · {item.entityType} · <span className="font-mono text-xs">{item.entityId}</span></p></div>
								<div className="text-sm text-slate-500 sm:text-right"><p>{timeAgo(item.occurredAt)}</p><p className="mt-1 text-xs">{new Date(item.occurredAt).toLocaleString()}</p></div>
							</li>
						))}
					</ul>
				)}
			</section>
		</div>
	);
}
