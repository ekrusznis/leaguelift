import { useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { usePlatformSupportAccessList } from "./api";

export function PlatformSupportSessionsPage() {
	const [status, setStatus] = useState("");
	const [page, setPage] = useState(0);
	const sessions = usePlatformSupportAccessList({ status, page, size: 25 });

	if (sessions.isLoading) return <LoadingState label="Loading support sessions…" />;
	if (sessions.isError || !sessions.data) {
		return <ErrorState message="Could not load support sessions." onRetry={() => sessions.refetch()} />;
	}

	const totalPages = Math.max(1, Math.ceil(sessions.data.totalElements / sessions.data.size));

	return (
		<div className="flex flex-col gap-6">
			<div className="flex flex-wrap items-end justify-between gap-4">
				<div>
					<h1 className="font-heading text-2xl font-bold text-navy-900">Support Sessions</h1>
					<p className="mt-1 text-slate-500">Review reasoned, organization-scoped access by Rally26 employees.</p>
				</div>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900">
					Status
					<select
						value={status}
						onChange={(event) => { setStatus(event.target.value); setPage(0); }}
						className="min-h-11 rounded-md border border-slate-300 bg-white px-3 py-2 font-normal"
					>
						<option value="">All sessions</option>
						<option value="ACTIVE">Active</option>
						<option value="ENDED">Ended</option>
						<option value="EXPIRED">Expired</option>
					</select>
				</label>
			</div>

			<div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
				<table className="w-full min-w-[980px] text-left text-sm">
					<thead className="border-b border-slate-200 bg-ice-50 text-slate-500">
						<tr>
							<th className="px-4 py-3 font-medium">Employee</th>
							<th className="px-4 py-3 font-medium">Organization</th>
							<th className="px-4 py-3 font-medium">Reason</th>
							<th className="px-4 py-3 font-medium">Status</th>
							<th className="px-4 py-3 font-medium">Started</th>
							<th className="px-4 py-3 font-medium">Expires / ended</th>
						</tr>
					</thead>
					<tbody className="divide-y divide-slate-100">
						{sessions.data.items.map((session) => (
							<tr key={session.id}>
								<td className="px-4 py-3">
									<p className="font-semibold text-navy-900">{session.platformAdminName}</p>
									<p className="text-xs text-slate-500">{session.platformAdminEmail}</p>
								</td>
								<td className="px-4 py-3">
									<Link to={appPaths.platformOrganization(session.organizationId)} className="font-semibold text-navy-900 hover:text-green-700 hover:underline">
										{session.organizationName}
									</Link>
								</td>
								<td className="max-w-md px-4 py-3 text-slate-600"><p className="line-clamp-3">{session.reason}</p></td>
								<td className="px-4 py-3"><StatusPill status={session.status} /></td>
								<td className="px-4 py-3 text-slate-600">{formatDateTime(session.createdAt)}</td>
								<td className="px-4 py-3 text-slate-600">{formatDateTime(session.endedAt ?? session.expiresAt)}</td>
							</tr>
						))}
					</tbody>
				</table>
				{sessions.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No support sessions match this status.</p>}
			</div>

			<div className="flex items-center justify-between text-sm text-slate-600">
				<p>{sessions.data.totalElements} sessions</p>
				<div className="flex items-center gap-2">
					<button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="rounded-md border border-slate-300 bg-white px-3 py-2 disabled:opacity-40">Previous</button>
					<span>Page {page + 1} of {totalPages}</span>
					<button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-md border border-slate-300 bg-white px-3 py-2 disabled:opacity-40">Next</button>
				</div>
			</div>
		</div>
	);
}

function StatusPill({ status }: { status: "ACTIVE" | "ENDED" | "EXPIRED" }) {
	const classes = status === "ACTIVE" ? "bg-green-100 text-green-800" : status === "EXPIRED" ? "bg-amber-100 text-amber-900" : "bg-slate-100 text-slate-700";
	return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{status}</span>;
}

function formatDateTime(value: string) {
	return new Date(value).toLocaleString([], { month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" });
}
