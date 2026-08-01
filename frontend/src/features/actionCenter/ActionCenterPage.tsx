import { Link } from "react-router-dom";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useActionCenter } from "./api";
import type { ActionCenterPriority } from "./types";

const PRIORITY_LABELS: Record<ActionCenterPriority, string> = {
	URGENT: "Urgent",
	HIGH: "High priority",
	NORMAL: "Action needed",
	INFO: "Review",
};

const PRIORITY_CLASSES: Record<ActionCenterPriority, string> = {
	URGENT: "bg-error-red/10 text-error-red",
	HIGH: "bg-championship-gold/20 text-navy",
	NORMAL: "bg-info-blue/10 text-info-blue",
	INFO: "bg-slate-gray/10 text-slate-gray",
};

function formatDate(value: string | null) {
	if (!value) return null;
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return null;
	return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function ActionCenterPage() {
	const actionCenter = useActionCenter();

	return (
		<div className="flex flex-col gap-6">
			<header>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">Your work queue</p>
				<h1 className="mt-1 font-heading text-3xl font-bold text-navy">Action Center</h1>
				<p className="mt-2 max-w-3xl text-slate-gray">
					LeagueLift gathers the items that need your attention from the roles and organizations you can access. The source record remains the system of truth.
				</p>
			</header>

			{actionCenter.isLoading && <LoadingState label="Loading your action center…" />}
			{actionCenter.isError && <ErrorState message="Could not load your action center." onRetry={() => actionCenter.refetch()} />}

			{actionCenter.data && (
				<>
					<div className="grid gap-3 sm:grid-cols-2">
						<div className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
							<p className="text-sm text-slate-gray">Open items</p>
							<p className="mt-1 font-heading text-3xl font-bold text-navy">{actionCenter.data.totalCount}</p>
						</div>
						<div className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
							<p className="text-sm text-slate-gray">High or urgent</p>
							<p className="mt-1 font-heading text-3xl font-bold text-error-red">{actionCenter.data.highPriorityCount}</p>
						</div>
					</div>

					{actionCenter.data.items.length === 0 ? (
						<EmptyState title="You are caught up" description="No current LeagueLift records require action from your account." />
					) : (
						<ul className="flex flex-col gap-3" aria-label="Action center items">
							{actionCenter.data.items.map((item) => {
								const date = formatDate(item.dueAt ?? item.createdAt);
								return (
									<li key={item.id} className="rounded-xl border border-slate-gray/20 bg-pure-white p-5 shadow-sm">
										<div className="flex flex-wrap items-start justify-between gap-3">
											<div className="min-w-0 flex-1">
												<span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${PRIORITY_CLASSES[item.priority]}`}>
													{PRIORITY_LABELS[item.priority]}
												</span>
												<h2 className="mt-2 font-heading text-lg font-semibold text-navy">{item.title}</h2>
												<p className="mt-1 text-sm text-slate-gray">{item.description}</p>
												{date && <p className="mt-2 text-xs text-slate-gray">Relevant date: {date}</p>}
											</div>
											<Link to={item.actionPath} className="inline-flex min-h-11 shrink-0 items-center rounded-md bg-victory-green px-4 py-2 text-sm font-medium text-pure-white hover:opacity-90">
												Open
											</Link>
										</div>
									</li>
								);
							})}
						</ul>
					)}
				</>
			)}
		</div>
	);
}
