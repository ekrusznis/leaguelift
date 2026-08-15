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
import { TeamRoleAssignmentsSection } from "../authorization/TeamRoleAssignmentsSection";
import { EntityBrandingPanel } from "../media/EntityBrandingPanel";
import { useArchiveTeam, useCreateTeam, useUpdateTeamTimezone } from "./api";
import { useTeamSearch } from "./searchApi";
import { createTeamSchema, type CreateTeamFormValues } from "./schema";
import { TeamColorsPanel } from "./TeamColorsPanel";

const GENDER_CATEGORY_OPTIONS = [
	{ value: "", label: "Not specified" },
	{ value: "BOYS", label: "Boys" },
	{ value: "GIRLS", label: "Girls" },
	{ value: "COED", label: "Coed" },
	{ value: "MENS", label: "Men's" },
	{ value: "WOMENS", label: "Women's" },
	{ value: "OPEN", label: "Open" },
] as const;

export function TeamList({ organizationId }: { organizationId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [sport, setSport] = useState("");
	const [season, setSeason] = useState("");
	const [genderCategory, setGenderCategory] = useState("");
	const [status, setStatus] = useState("");
	const [sort, setSort] = useState<"NAME_ASC" | "NAME_DESC" | "SPORT_ASC" | "NEWEST" | "OLDEST">("NAME_ASC");
	const { data, isLoading, isError, refetch } = useTeamSearch(organizationId, {
		page, size, q: query, sport, season, genderCategory, status, sort,
	});
	const createTeam = useCreateTeam(organizationId);
	const archiveTeam = useArchiveTeam(organizationId);
	const updateTeamTimezone = useUpdateTeamTimezone(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedTeamId, setExpandedTeamId] = useState<string | null>(null);
	const [brandingTeamId, setBrandingTeamId] = useState<string | null>(null);
	const [timezoneTeamId, setTimezoneTeamId] = useState<string | null>(null);
	const [timezoneDraft, setTimezoneDraft] = useState("");
	const [colorsTeamId, setColorsTeamId] = useState<string | null>(null);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateTeamFormValues>({
		resolver: zodResolver(createTeamSchema),
		defaultValues: { name: "", sport: "", season: "", contactEmail: "", ageGroup: "", genderCategory: "", level: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createTeam.mutateAsync(values);
		reset();
		setShowForm(false);
		await refetch();
	});
	const hasFilters = !!sport.trim() || !!season.trim() || !!genderCategory || !!status;
	const resetPage = () => setPage(0);

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => { setQuery(value); resetPage(); }}
				searchPlaceholder="Search teams by name, sport, season, age group, or level"
				resultCount={data?.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NAME_ASC", label: "Name A–Z" },
					{ value: "NAME_DESC", label: "Name Z–A" },
					{ value: "SPORT_ASC", label: "Sport" },
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
				]}
				onSortChange={(value) => { setSort(value as typeof sort); resetPage(); }}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery(""); setSport(""); setSeason(""); setGenderCategory(""); setStatus(""); setSort("NAME_ASC"); resetPage();
				}}
				actions={
					<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>
						{showForm ? "Cancel" : "Add team"}
					</Button>
				}
				filters={
					<>
						<input
							aria-label="Filter by sport"
							placeholder="Sport"
							value={sport}
							onChange={(event) => { setSport(event.target.value); resetPage(); }}
							className="min-h-11 w-32 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						/>
						<input
							aria-label="Filter by season"
							placeholder="Season"
							value={season}
							onChange={(event) => { setSeason(event.target.value); resetPage(); }}
							className="min-h-11 w-36 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						/>
						<select
							aria-label="Filter by gender"
							value={genderCategory}
							onChange={(event) => { setGenderCategory(event.target.value); resetPage(); }}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All genders</option>
							{GENDER_CATEGORY_OPTIONS.filter((option) => option.value).map((option) => (
								<option key={option.value} value={option.value}>{option.label}</option>
							))}
						</select>
						<select
							aria-label="Filter by team status"
							value={status}
							onChange={(event) => { setStatus(event.target.value); resetPage(); }}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All statuses</option>
							<option value="ACTIVE">Active</option>
							<option value="ARCHIVED">Archived</option>
						</select>
					</>
				}
			/>

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]" noValidate aria-label="Create a team">
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="team-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Name <span aria-hidden>*</span></label>
							<input id="team-name" type="text" {...register("name")} aria-invalid={!!errors.name} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
							{errors.name && <p role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-sport" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Sport <span aria-hidden>*</span></label>
							<input id="team-sport" type="text" {...register("sport")} aria-invalid={!!errors.sport} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
							{errors.sport && <p role="alert" className="text-sm text-error-red">{errors.sport.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-season" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Season</label>
							<input id="team-season" type="text" placeholder="e.g. Fall 2026" {...register("season")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-age-group" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Age group</label>
							<input id="team-age-group" type="text" placeholder="e.g. 12U" {...register("ageGroup")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-gender-category" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Gender</label>
							<select id="team-gender-category" {...register("genderCategory")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
								{GENDER_CATEGORY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
							</select>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-level" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Level</label>
							<input id="team-level" type="text" placeholder="e.g. A, Varsity" {...register("level")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>Cancel</Button>
						<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create team"}</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading teams…" />}
			{isError && <ErrorState message="Could not load teams." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No teams yet"}
					description={query.trim() || hasFilters ? "Try changing your search or filters." : "Add your first team to get started."}
				/>
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Teams">
					{data.items.map((team) => (
						<li key={team.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{team.name}</p>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
										{team.sport}{team.season ? ` · ${team.season}` : ""}{team.ageGroup ? ` · ${team.ageGroup}` : ""}
										{team.genderCategory ? ` · ${GENDER_CATEGORY_OPTIONS.find((option) => option.value === team.genderCategory)?.label ?? team.genderCategory}` : ""}
										{team.level ? ` · ${team.level}` : ""} · {team.status === "ARCHIVED" ? "Archived" : "Active"}
									</p>
								</div>
								<div className="flex shrink-0 flex-wrap items-center gap-2">
									<Link to={appPaths.teamEvents(organizationId, team.id)} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 px-4 py-2 text-sm font-medium text-navy dark:text-[#f8fafc]">Schedule</Link>
									<Link to={appPaths.teamRoster(organizationId, team.id)} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 px-4 py-2 text-sm font-medium text-navy dark:text-[#f8fafc]">Roster</Link>
									<Button type="button" variant="secondary" onClick={() => setBrandingTeamId(brandingTeamId === team.id ? null : team.id)}>{brandingTeamId === team.id ? "Hide branding" : "Branding"}</Button>
									<Button type="button" variant="secondary" onClick={() => setExpandedTeamId(expandedTeamId === team.id ? null : team.id)}>{expandedTeamId === team.id ? "Hide access" : "Manage access"}</Button>
									<Button type="button" variant="secondary" onClick={() => {
										if (timezoneTeamId === team.id) setTimezoneTeamId(null);
										else { setTimezoneTeamId(team.id); setTimezoneDraft(team.timezoneOverride ?? ""); }
									}}>{timezoneTeamId === team.id ? "Hide timezone" : "Timezone"}</Button>
									<Button type="button" variant="secondary" onClick={() => setColorsTeamId(colorsTeamId === team.id ? null : team.id)}>{colorsTeamId === team.id ? "Hide colors" : "Colors"}</Button>
									{team.status !== "ARCHIVED" && <Button type="button" variant="secondary" onClick={() => archiveTeam.mutate(team.id)} disabled={archiveTeam.isPending}>Archive</Button>}
								</div>
							</div>
							{brandingTeamId === team.id && <div className="mt-3"><EntityBrandingPanel organizationId={organizationId} entityType="TEAM" entityId={team.id} entityName={team.name} /></div>}
							{expandedTeamId === team.id && <div className="mt-3"><TeamRoleAssignmentsSection organizationId={organizationId} teamId={team.id} /></div>}
							{timezoneTeamId === team.id && (
								<div className="mt-3 flex flex-wrap items-end gap-2 rounded-md border border-slate-gray/20 bg-ice-white p-3 dark:bg-[#0f172a]">
									<div className="flex flex-col gap-1">
										<label htmlFor={`team-timezone-${team.id}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Timezone override</label>
										<input id={`team-timezone-${team.id}`} type="text" placeholder="Inherits organization default" value={timezoneDraft} onChange={(event) => setTimezoneDraft(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
									</div>
									<Button type="button" onClick={() => updateTeamTimezone.mutate({ teamId: team.id, timezone: timezoneDraft.trim() || null })} disabled={updateTeamTimezone.isPending}>Save</Button>
									{team.timezoneOverride && <Button type="button" variant="secondary" onClick={() => { setTimezoneDraft(""); updateTeamTimezone.mutate({ teamId: team.id, timezone: null }); }} disabled={updateTeamTimezone.isPending}>Clear</Button>}
								</div>
							)}
							{colorsTeamId === team.id && <div className="mt-3"><TeamColorsPanel organizationId={organizationId} team={team} /></div>}
						</li>
					))}
				</ul>
			)}
			{data && (
				<Pagination page={page} size={size} totalElements={data.totalElements} onPageChange={setPage} onSizeChange={(value) => { setSize(value); setPage(0); }} />
			)}
		</div>
	);
}
