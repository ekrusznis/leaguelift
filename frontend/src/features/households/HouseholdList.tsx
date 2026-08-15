import { useState } from "react";
import { Link } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useTeamSearch } from "../teams/searchApi";
import { useCreateHousehold } from "./api";
import { useHouseholdSearch } from "./searchApi";
import { createHouseholdSchema, type CreateHouseholdFormValues } from "./schema";

export function HouseholdList({ organizationId }: { organizationId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState("");
	const [teamId, setTeamId] = useState("");
	const [sort, setSort] = useState<"NAME_ASC" | "NAME_DESC" | "NEWEST" | "OLDEST">("NAME_ASC");
	const { data, isLoading, isError, refetch } = useHouseholdSearch(organizationId, { page, size, q: query, status, teamId, sort });
	const teams = useTeamSearch(organizationId, { page: 0, size: 100, status: "ACTIVE", sort: "NAME_ASC" });
	const createHousehold = useCreateHousehold(organizationId);
	const [showForm, setShowForm] = useState(false);
	const {
		register, handleSubmit, reset, formState: { errors, isSubmitting },
	} = useForm<CreateHouseholdFormValues>({
		resolver: zodResolver(createHouseholdSchema),
		defaultValues: { displayName: "", contactEmail: "", contactPhone: "", notes: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createHousehold.mutateAsync(values);
		reset();
		setShowForm(false);
		await refetch();
	});
	const hasFilters = !!status || !!teamId;

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => { setQuery(value); setPage(0); }}
				searchPlaceholder="Search household, parent email, or athlete"
				resultCount={data?.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NAME_ASC", label: "Name A–Z" },
					{ value: "NAME_DESC", label: "Name Z–A" },
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
				]}
				onSortChange={(value) => { setSort(value as typeof sort); setPage(0); }}
				hasActiveFilters={hasFilters}
				onClear={() => { setQuery(""); setStatus(""); setTeamId(""); setSort("NAME_ASC"); setPage(0); }}
				actions={<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>{showForm ? "Cancel" : "Add household"}</Button>}
				filters={
					<>
						<select aria-label="Filter households by team" value={teamId} onChange={(event) => { setTeamId(event.target.value); setPage(0); }} className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]">
							<option value="">All teams</option>
							{teams.data?.items.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
						</select>
						<select aria-label="Filter household status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]">
							<option value="">All statuses</option>
							<option value="ACTIVE">Active</option>
							<option value="ARCHIVED">Archived</option>
						</select>
					</>
				}
			/>

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]" noValidate aria-label="Create a household">
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="household-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Display name <span aria-hidden>*</span></label>
							<input id="household-name" type="text" placeholder="e.g. Smith Family" {...register("displayName")} aria-invalid={!!errors.displayName} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
							{errors.displayName && <p role="alert" className="text-sm text-error-red">{errors.displayName.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="household-email" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Contact email</label>
							<input id="household-email" type="email" {...register("contactEmail")} aria-invalid={!!errors.contactEmail} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
							{errors.contactEmail && <p role="alert" className="text-sm text-error-red">{errors.contactEmail.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="household-phone" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Contact phone</label>
							<input id="household-phone" type="tel" {...register("contactPhone")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>Cancel</Button>
						<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create household"}</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading households…" />}
			{isError && <ErrorState message="Could not load households." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No households yet"}
					description={query.trim() || hasFilters ? "Try changing your search or filters." : "Add your first household to get started."}
				/>
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Households">
					{data.items.map((household) => (
						<li key={household.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
							<div className="min-w-0 flex-1">
								<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{household.displayName}</p>
								{household.contactEmail && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{household.contactEmail}</p>}
								<p className="text-xs font-semibold uppercase tracking-wide text-slate-gray dark:text-[#94a3b8]">{household.status.toLowerCase()}</p>
							</div>
							<Link to={`/app/organizations/${organizationId}/households/${household.id}`} className="shrink-0 rounded-md border border-slate-gray/30 px-3 py-1.5 text-sm font-medium text-navy dark:text-[#f8fafc]">View</Link>
						</li>
					))}
				</ul>
			)}
			{data && <Pagination page={page} size={size} totalElements={data.totalElements} onPageChange={setPage} onSizeChange={(value) => { setSize(value); setPage(0); }} />}
		</div>
	);
}
