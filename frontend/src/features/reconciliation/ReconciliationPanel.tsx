import { useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { useReconciliationIssues, useReconciliationRuns, useRunReconciliation } from "./api";
import type { ReconciliationRunStatus, ReconciliationSeverity } from "./types";

const inputClass = "min-h-11 rounded-md border border-slate-gray/30 bg-pure-white dark:bg-[#111827] px-3 py-2 text-navy dark:text-[#f8fafc]";

export function ReconciliationPanel({ organizationId }: { organizationId: string }) {
	const run = useRunReconciliation(organizationId);
	const [issueQuery, setIssueQuery] = useState("");
	const [issueSeverity, setIssueSeverity] = useState<ReconciliationSeverity | "">("");
	const [issueSort, setIssueSort] = useState<"newest" | "oldest">("newest");
	const [issuePage, setIssuePage] = useState(0);
	const [runStatus, setRunStatus] = useState<ReconciliationRunStatus | "">("");
	const [runSort, setRunSort] = useState<"newest" | "oldest">("newest");
	const [runPage, setRunPage] = useState(0);
	const latestRunQuery = useReconciliationRuns(organizationId, { page: 0, size: 1, sort: "newest" });
	const latestRun = latestRunQuery.data?.items[0] ?? null;
	const issues = useReconciliationIssues(organizationId, latestRun?.id ?? null, {
		query: issueQuery,
		severity: issueSeverity,
		sort: issueSort,
		page: issuePage,
		size: 20,
	});
	const runs = useReconciliationRuns(organizationId, { status: runStatus, sort: runSort, page: runPage, size: 10 });
	const issuePageCount = Math.max(1, Math.ceil((issues.data?.totalElements ?? 0) / 20));
	const runPageCount = Math.max(1, Math.ceil((runs.data?.totalElements ?? 0) / 10));

	return <section className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5" aria-labelledby="reconciliation-heading">
		<div className="flex flex-wrap items-start justify-between gap-3"><div><h3 id="reconciliation-heading" className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Reconciliation</h3><p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Compare provider references, source records, ledger entries, fulfillment, offline verification, and fee-plan balances.</p></div><div className="flex gap-2"><Link to={appPaths.helpArticle("running-financial-reconciliation")} className="min-h-11 rounded-md border border-slate-gray/30 px-4 py-2 text-sm font-medium text-navy dark:text-[#f8fafc]">How it works</Link><Button type="button" onClick={() => run.mutate()} disabled={run.isPending}>{run.isPending ? "Running…" : "Run reconciliation"}</Button></div></div>
		{run.isError && <p role="alert" className="mt-3 text-sm text-error-red">{run.error.message}</p>}
		{latestRunQuery.isLoading && <LoadingState label="Loading latest reconciliation run…" />}
		{latestRunQuery.isError && <ErrorState message="Could not load the latest reconciliation run." onRetry={() => latestRunQuery.refetch()} />}
		{latestRun ? <div className="mt-4">
			<p className="text-sm text-navy dark:text-[#f8fafc]"><strong>{latestRun.issueCount} issues</strong> · {latestRun.highCount} high · {latestRun.mediumCount} medium · {latestRun.lowCount} low · {new Date(latestRun.startedAt).toLocaleString()}</p>
			<div className="mt-3 flex flex-wrap gap-2">
				<input className={inputClass} value={issueQuery} onChange={(event) => { setIssueQuery(event.target.value); setIssuePage(0); }} placeholder="Search reconciliation issues" aria-label="Search reconciliation issues" />
				<select className={inputClass} value={issueSeverity} onChange={(event) => { setIssueSeverity(event.target.value as ReconciliationSeverity | ""); setIssuePage(0); }} aria-label="Issue severity"><option value="">All severities</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select>
				<select className={inputClass} value={issueSort} onChange={(event) => { setIssueSort(event.target.value as "newest" | "oldest"); setIssuePage(0); }} aria-label="Sort reconciliation issues"><option value="newest">Newest</option><option value="oldest">Oldest</option></select>
			</div>
			{issues.isLoading && <LoadingState label="Loading reconciliation issues…" />}
			{issues.isError && <ErrorState message="Could not load reconciliation issues." onRetry={() => issues.refetch()} />}
			{issues.data?.items.length ? <ul className="mt-3 grid gap-2">{issues.data.items.map((issue) => <li key={issue.id} className="rounded-md border border-slate-gray/20 p-3 text-sm"><div className="flex flex-wrap justify-between gap-2"><strong className="text-navy dark:text-[#f8fafc]">{issue.title}</strong><span className={issue.severity === "HIGH" ? "font-semibold text-error-red" : "text-slate-gray dark:text-[#cbd5e1]"}>{issue.severity}</span></div><p className="mt-1 text-slate-gray dark:text-[#cbd5e1]">{issue.detail}</p>{issue.actionPath && <Link to={issue.actionPath} className="mt-2 inline-flex text-info-blue hover:underline">Review record</Link>}</li>)}</ul> : null}
			{issues.data?.items.length === 0 && <EmptyState title={issueQuery || issueSeverity ? "No matching exceptions" : "No exceptions found"} description={issueQuery || issueSeverity ? "Try changing your search or severity filter." : "The latest run found no supported reconciliation issues."} />}
			{issues.data && issues.data.totalElements > issues.data.size && <div className="mt-3 flex items-center justify-between gap-3 text-sm text-slate-gray dark:text-[#cbd5e1]"><span>Page {issuePage + 1} of {issuePageCount} · {issues.data.totalElements} issues</span><div className="flex gap-2"><Button type="button" variant="secondary" disabled={issuePage === 0} onClick={() => setIssuePage((page) => Math.max(0, page - 1))}>Previous</Button><Button type="button" variant="secondary" disabled={issuePage + 1 >= issuePageCount} onClick={() => setIssuePage((page) => page + 1)}>Next</Button></div></div>}
		</div> : !latestRunQuery.isLoading && !latestRunQuery.isError ? <EmptyState title="No reconciliation run yet" description="Run reconciliation to create a durable snapshot of current exceptions." /> : null}
		<div className="mt-6 border-t border-slate-gray/20 pt-4">
			<div className="flex flex-wrap items-end justify-between gap-3"><h4 className="font-semibold text-navy dark:text-[#f8fafc]">Run history</h4><div className="flex gap-2"><select className={inputClass} value={runStatus} onChange={(event) => { setRunStatus(event.target.value as ReconciliationRunStatus | ""); setRunPage(0); }} aria-label="Reconciliation run status"><option value="">All statuses</option><option value="RUNNING">Running</option><option value="COMPLETED">Completed</option><option value="FAILED">Failed</option></select><select className={inputClass} value={runSort} onChange={(event) => { setRunSort(event.target.value as "newest" | "oldest"); setRunPage(0); }} aria-label="Sort reconciliation runs"><option value="newest">Newest</option><option value="oldest">Oldest</option></select></div></div>
			{runs.isLoading && <LoadingState label="Loading reconciliation history…" />}
			{runs.isError && <ErrorState message="Could not load reconciliation history." onRetry={() => runs.refetch()} />}
			{runs.data?.items.length ? <ul className="mt-3 grid gap-2">{runs.data.items.map((item) => <li key={item.id} className="rounded-md bg-ice-white dark:bg-[#0f172a] p-3 text-sm text-slate-gray dark:text-[#cbd5e1]"><strong className="text-navy dark:text-[#f8fafc]">{item.status}</strong> · {item.issueCount} issue{item.issueCount === 1 ? "" : "s"} · {new Date(item.startedAt).toLocaleString()}</li>)}</ul> : null}
			{runs.data?.items.length === 0 && <EmptyState title="No reconciliation runs" description={runStatus ? "No runs match this status." : "Run reconciliation to create the first snapshot."} />}
			{runs.data && runs.data.totalElements > runs.data.size && <div className="mt-3 flex items-center justify-between gap-3 text-sm text-slate-gray dark:text-[#cbd5e1]"><span>Page {runPage + 1} of {runPageCount} · {runs.data.totalElements} runs</span><div className="flex gap-2"><Button type="button" variant="secondary" disabled={runPage === 0} onClick={() => setRunPage((page) => Math.max(0, page - 1))}>Previous</Button><Button type="button" variant="secondary" disabled={runPage + 1 >= runPageCount} onClick={() => setRunPage((page) => page + 1)}>Next</Button></div></div>}
		</div>
	</section>;
}
