import { useState } from "react";
import { Link } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { appPaths } from "../../routes/appPaths";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { TournamentRoleAssignmentsSection } from "../authorization/TournamentRoleAssignmentsSection";
import { EntityBrandingPanel } from "../media/EntityBrandingPanel";
import { useArchiveTournament, useCreateTournament, useTournaments, useUpdateTournamentTimezone } from "./api";
import { createTournamentSchema, type CreateTournamentFormValues } from "./schema";

type TournamentSort = "START_ASC" | "START_DESC" | "NAME_ASC" | "NAME_DESC";
type TournamentStatusFilter = "" | "ACTIVE" | "ARCHIVED";

function formatDateRange(startDate: string | null, endDate: string | null): string {
	if (!startDate && !endDate) return "";
	if (startDate && !endDate) return startDate;
	if (!startDate && endDate) return `– ${endDate}`;
	return `${startDate} – ${endDate}`;
}

export function TournamentList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useTournaments(organizationId);
	const createTournament = useCreateTournament(organizationId);
	const archiveTournament = useArchiveTournament(organizationId);
	const updateTournamentTimezone = useUpdateTournamentTimezone(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedTournamentId, setExpandedTournamentId] = useState<string | null>(null);
	const [brandingTournamentId, setBrandingTournamentId] = useState<string | null>(null);
	const [timezoneTournamentId, setTimezoneTournamentId] = useState<string | null>(null);
	const [timezoneDraft, setTimezoneDraft] = useState("");
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<TournamentStatusFilter>("");
	const [sort, setSort] = useState<TournamentSort>("START_ASC");
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateTournamentFormValues>({
		resolver: zodResolver(createTournamentSchema),
		defaultValues: { name: "", sport: "", startDate: "", endDate: "", location: "", contactEmail: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createTournament.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	const needle = query.trim().toLowerCase();
	const filtered = (data?.items ?? [])
		.filter((tournament) => {
			if (status && tournament.status !== status) return false;
			if (!needle) return true;
			return [
				tournament.name,
				tournament.sport,
				tournament.location,
				tournament.contactEmail,
			]
				.filter(Boolean)
				.some((value) => value!.toLowerCase().includes(needle));
		})
		.sort((left, right) => {
			if (sort === "NAME_ASC" || sort === "NAME_DESC") {
				const result = left.name.localeCompare(right.name);
				return sort === "NAME_ASC" ? result : -result;
			}
			const leftDate = left.startDate ?? "9999-12-31";
			const rightDate = right.startDate ?? "9999-12-31";
			const result = leftDate.localeCompare(rightDate) || left.name.localeCompare(right.name);
			return sort === "START_ASC" ? result : -result;
		});

	const totalElements = filtered.length;
	const safePage = Math.min(page, Math.max(0, Math.ceil(totalElements / size) - 1));
	const visibleItems = filtered.slice(safePage * size, safePage * size + size);
	const hasFilters = !!status;

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search tournaments by name, sport, location, or contact"
				resultCount={totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "START_ASC", label: "Start date — soonest" },
					{ value: "START_DESC", label: "Start date — latest" },
					{ value: "NAME_ASC", label: "Name A–Z" },
					{ value: "NAME_DESC", label: "Name Z–A" },
				]}
				onSortChange={(value) => {
					setSort(value as TournamentSort);
					setPage(0);
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("");
					setSort("START_ASC");
					setPage(0);
				}}
				filters={
					<select
						aria-label="Filter tournament status"
						value={status}
						onChange={(event) => {
							setStatus(event.target.value as TournamentStatusFilter);
							setPage(0);
						}}
						className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
					>
						<option value="">All statuses</option>
						<option value="ACTIVE">Active</option>
						<option value="ARCHIVED">Archived</option>
					</select>
				}
				actions={
					<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>
						{showForm ? "Cancel" : "Add tournament"}
					</Button>
				}
			/>

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Create a tournament">
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="tournament-name"
								type="text"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "tournament-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && (
								<p id="tournament-name-error" role="alert" className="text-sm text-error-red">
									{errors.name.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-sport" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Sport</label>
							<input id="tournament-sport" type="text" {...register("sport")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-start" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Start date</label>
							<input id="tournament-start" type="date" {...register("startDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-end" className="text-sm font-medium text-navy dark:text-[#f8fafc]">End date</label>
							<input
								id="tournament-end"
								type="date"
								{...register("endDate")}
								aria-invalid={!!errors.endDate}
								aria-describedby={errors.endDate ? "tournament-end-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.endDate && <p id="tournament-end-error" role="alert" className="text-sm text-error-red">{errors.endDate.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-location" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Location</label>
							<input id="tournament-location" type="text" {...register("location")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>Cancel</Button>
						<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create tournament"}</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading tournaments…" />}
			{isError && <ErrorState message="Could not load tournaments." onRetry={() => refetch()} />}
			{data && visibleItems.length === 0 && !showForm && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No tournaments yet"}
					description={query.trim() || hasFilters ? "Try changing your search or filters." : "Add your first tournament to get started."}
				/>
			)}
			{visibleItems.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Tournaments">
					{visibleItems.map((tournament) => (
						<li key={tournament.id} className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
										{tournament.name}
										<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]">
											{tournament.status}
										</span>
									</p>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
										{[tournament.sport, formatDateRange(tournament.startDate, tournament.endDate), tournament.location]
											.filter(Boolean)
											.join(" · ")}
									</p>
								</div>
								<div className="flex shrink-0 flex-wrap items-center gap-2">
									<Link to={appPaths.tournamentEvents(organizationId, tournament.id)} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 bg-pure-white dark:bg-[#111827] px-4 py-2 text-sm font-medium text-navy dark:text-[#f8fafc] hover:bg-ice-white hover:dark:bg-[#0f172a]">
										Schedule
									</Link>
									<Button type="button" variant="secondary" onClick={() => setBrandingTournamentId(brandingTournamentId === tournament.id ? null : tournament.id)}>
										{brandingTournamentId === tournament.id ? "Hide branding" : "Branding"}
									</Button>
									<Button type="button" variant="secondary" onClick={() => setExpandedTournamentId(expandedTournamentId === tournament.id ? null : tournament.id)}>
										{expandedTournamentId === tournament.id ? "Hide access" : "Manage access"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => {
											if (timezoneTournamentId === tournament.id) {
												setTimezoneTournamentId(null);
											} else {
												setTimezoneTournamentId(tournament.id);
												setTimezoneDraft(tournament.timezoneOverride ?? "");
											}
										}}
									>
										{timezoneTournamentId === tournament.id ? "Hide timezone" : "Timezone"}
									</Button>
									{tournament.status !== "ARCHIVED" && (
										<Button type="button" variant="secondary" onClick={() => archiveTournament.mutate(tournament.id)} disabled={archiveTournament.isPending}>
											Archive
										</Button>
									)}
								</div>
							</div>
							{brandingTournamentId === tournament.id && (
								<div className="mt-3">
									<EntityBrandingPanel organizationId={organizationId} entityType="TOURNAMENT" entityId={tournament.id} entityName={tournament.name} />
								</div>
							)}
							{expandedTournamentId === tournament.id && (
								<div className="mt-3">
									<TournamentRoleAssignmentsSection organizationId={organizationId} tournamentId={tournament.id} />
								</div>
							)}
							{timezoneTournamentId === tournament.id && (
								<div className="mt-3 flex flex-wrap items-end gap-2 rounded-md border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-3">
									<div className="flex flex-col gap-1">
										<label htmlFor={`tournament-timezone-${tournament.id}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Timezone override</label>
										<input
											id={`tournament-timezone-${tournament.id}`}
											type="text"
											placeholder="Inherits organization default"
											value={timezoneDraft}
											onChange={(event) => setTimezoneDraft(event.target.value)}
											className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
										/>
									</div>
									<Button
										type="button"
										onClick={() => updateTournamentTimezone.mutate({ tournamentId: tournament.id, timezone: timezoneDraft.trim() || null })}
										disabled={updateTournamentTimezone.isPending}
									>
										Save
									</Button>
									{tournament.timezoneOverride && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => {
												setTimezoneDraft("");
												updateTournamentTimezone.mutate({ tournamentId: tournament.id, timezone: null });
											}}
											disabled={updateTournamentTimezone.isPending}
										>
											Clear (inherit organization default)
										</Button>
									)}
								</div>
							)}
						</li>
					))}
				</ul>
			)}

			<Pagination
				page={safePage}
				size={size}
				totalElements={totalElements}
				onPageChange={setPage}
				onSizeChange={(nextSize) => {
					setSize(nextSize);
					setPage(0);
				}}
			/>
		</div>
	);
}
