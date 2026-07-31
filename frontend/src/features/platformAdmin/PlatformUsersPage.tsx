import { useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePlatformAdminUsers } from "./api";

export function PlatformUsersPage() {
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState("");
	const [page, setPage] = useState(0);
	const users = usePlatformAdminUsers({ query, status, page, size: 25 });

	if (users.isLoading) return <LoadingState label="Loading users…" />;
	if (users.isError || !users.data) return <ErrorState message="Could not load platform users." onRetry={() => users.refetch()} />;
	const totalPages = Math.max(1, Math.ceil(users.data.totalElements / users.data.size));

	return (
		<div className="flex flex-col gap-6">
			<div><h1 className="font-heading text-2xl font-bold text-navy-900">Users</h1><p className="mt-1 text-slate-500">Platform-wide identity and organization-membership directory. Passwords and tokens are never displayed.</p></div>
			<form onSubmit={(event) => { event.preventDefault(); setPage(0); setQuery(queryInput); }} className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4">
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900">Search<input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="Name or email" className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal" /></label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900">Status<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 bg-white px-3 py-2 font-normal"><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option></select></label>
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">Search</button>
			</form>
			<div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
				<table className="w-full min-w-[900px] text-left text-sm">
					<thead className="border-b border-slate-200 bg-ice-50 text-slate-500"><tr><th className="px-4 py-3 font-medium">User</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Platform role</th><th className="px-4 py-3 font-medium">Organizations & roles</th><th className="px-4 py-3 font-medium">Created</th></tr></thead>
					<tbody className="divide-y divide-slate-100">
						{users.data.items.map((user) => (
							<tr key={user.userId}>
								<td className="px-4 py-3"><p className="font-semibold text-navy-900">{user.displayName}</p><p className="text-xs text-slate-500">{user.email}</p><p className="mt-1 font-mono text-[11px] text-slate-400">{user.userId}</p></td>
								<td className="px-4 py-3"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${user.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-amber-100 text-amber-900"}`}>{user.status}</span></td>
								<td className="px-4 py-3">{user.platformAdmin ? <span className="rounded-full bg-navy-900 px-2.5 py-1 text-xs font-semibold text-white">PLATFORM_ADMIN</span> : <span className="text-slate-400">—</span>}</td>
								<td className="px-4 py-3 text-slate-600">
									{user.organizationMemberships.length > 0 ? (
										<ul className="flex flex-col gap-1.5">
											{user.organizationMemberships.map((membership) => (
												<li key={membership.organizationId}>
													<Link to={`/app/platform/organizations/${membership.organizationId}`} className="font-medium text-azure-blue hover:underline">{membership.organizationName}</Link>
													<span className="ml-2 text-xs text-slate-500">{membership.role}</span>
												</li>
											))}
										</ul>
									) : "None"}
								</td>
								<td className="px-4 py-3 text-slate-600">{new Date(user.createdAt).toLocaleDateString()}</td>
							</tr>
						))}
					</tbody>
				</table>
				{users.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No users match these filters.</p>}
			</div>
			<div className="flex items-center justify-between text-sm text-slate-600"><p>{users.data.totalElements} users</p><div className="flex items-center gap-2"><button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="rounded-md border border-slate-300 px-3 py-2 disabled:opacity-40">Previous</button><span>Page {page + 1} of {totalPages}</span><button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-md border border-slate-300 px-3 py-2 disabled:opacity-40">Next</button></div></div>
		</div>
	);
}
