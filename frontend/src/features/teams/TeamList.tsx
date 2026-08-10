import { useState } from "react";
import { Link } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { appPaths } from "../../routes/appPaths";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { TeamRoleAssignmentsSection } from "../authorization/TeamRoleAssignmentsSection";
import { EntityBrandingPanel } from "../media/EntityBrandingPanel";
import { useArchiveTeam, useCreateTeam, useTeams, useUpdateTeamTimezone } from "./api";
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
	const { data, isLoading, isError, refetch } = useTeams(organizationId);
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
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">{data ? `${data.totalElements} team${data.totalElements !== 1 ? "s" : ""}` : ""}</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add team"}
				</Button>
			</div>

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Create a team">
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="team-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="team-name"
								type="text"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "team-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && (
								<p id="team-name-error" role="alert" className="text-sm text-error-red">
									{errors.name.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-sport" className="text-sm font-medium text-navy">
								Sport <span aria-hidden>*</span>
							</label>
							<input
								id="team-sport"
								type="text"
								{...register("sport")}
								aria-invalid={!!errors.sport}
								aria-describedby={errors.sport ? "team-sport-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.sport && (
								<p id="team-sport-error" role="alert" className="text-sm text-error-red">
									{errors.sport.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-season" className="text-sm font-medium text-navy">
								Season
							</label>
							<input
								id="team-season"
								type="text"
								placeholder="e.g. Fall 2026"
								{...register("season")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-age-group" className="text-sm font-medium text-navy">
								Age group
							</label>
							<input
								id="team-age-group"
								type="text"
								placeholder="e.g. 12U"
								{...register("ageGroup")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-gender-category" className="text-sm font-medium text-navy">
								Gender
							</label>
							<select
								id="team-gender-category"
								{...register("genderCategory")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							>
								{GENDER_CATEGORY_OPTIONS.map((option) => (
									<option key={option.value} value={option.value}>
										{option.label}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="team-level" className="text-sm font-medium text-navy">
								Level
							</label>
							<input
								id="team-level"
								type="text"
								placeholder="e.g. A, Varsity"
								{...register("level")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create team"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading teams…" />}
			{isError && <ErrorState message="Could not load teams." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No teams yet" description="Add your first team to get started." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Teams">
					{data.items.map((team) => (
						<li key={team.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy">{team.name}</p>
									<p className="text-sm text-slate-gray">
										{team.sport}
										{team.season ? ` · ${team.season}` : ""}
										{team.ageGroup ? ` · ${team.ageGroup}` : ""}
										{team.genderCategory ? ` · ${GENDER_CATEGORY_OPTIONS.find((o) => o.value === team.genderCategory)?.label ?? team.genderCategory}` : ""}
										{team.level ? ` · ${team.level}` : ""}
									</p>
								</div>
								<div className="flex shrink-0 flex-wrap items-center gap-2">
									<Link to={appPaths.teamEvents(organizationId, team.id)} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 bg-pure-white px-4 py-2 text-sm font-medium text-navy hover:bg-ice-white">
										Schedule
									</Link>
									<Button
										type="button"
										variant="secondary"
										onClick={() => setBrandingTeamId(brandingTeamId === team.id ? null : team.id)}
									>
										{brandingTeamId === team.id ? "Hide branding" : "Branding"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => setExpandedTeamId(expandedTeamId === team.id ? null : team.id)}
									>
										{expandedTeamId === team.id ? "Hide access" : "Manage access"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => {
											if (timezoneTeamId === team.id) {
												setTimezoneTeamId(null);
											} else {
												setTimezoneTeamId(team.id);
												setTimezoneDraft(team.timezoneOverride ?? "");
											}
										}}
									>
										{timezoneTeamId === team.id ? "Hide timezone" : "Timezone"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => setColorsTeamId(colorsTeamId === team.id ? null : team.id)}
									>
										{colorsTeamId === team.id ? "Hide colors" : "Colors"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => archiveTeam.mutate(team.id)}
										disabled={archiveTeam.isPending}
									>
										Archive
									</Button>
								</div>
							</div>
							{brandingTeamId === team.id && (
								<div className="mt-3">
									<EntityBrandingPanel organizationId={organizationId} entityType="TEAM" entityId={team.id} entityName={team.name} />
								</div>
							)}
							{expandedTeamId === team.id && (
								<div className="mt-3">
									<TeamRoleAssignmentsSection organizationId={organizationId} teamId={team.id} />
								</div>
							)}
							{timezoneTeamId === team.id && (
								<div className="mt-3 flex flex-wrap items-end gap-2 rounded-md border border-slate-gray/20 bg-ice-white p-3">
									<div className="flex flex-col gap-1">
										<label htmlFor={`team-timezone-${team.id}`} className="text-sm font-medium text-navy">
											Timezone override
										</label>
										<input
											id={`team-timezone-${team.id}`}
											type="text"
											placeholder="Inherits organization default"
											value={timezoneDraft}
											onChange={(e) => setTimezoneDraft(e.target.value)}
											className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
										/>
									</div>
									<Button
										type="button"
										onClick={() => updateTeamTimezone.mutate({ teamId: team.id, timezone: timezoneDraft.trim() || null })}
										disabled={updateTeamTimezone.isPending}
									>
										Save
									</Button>
									{team.timezoneOverride && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => {
												setTimezoneDraft("");
												updateTeamTimezone.mutate({ teamId: team.id, timezone: null });
											}}
											disabled={updateTeamTimezone.isPending}
										>
											Clear (inherit organization default)
										</Button>
									)}
								</div>
							)}
							{colorsTeamId === team.id && (
								<div className="mt-3">
									<TeamColorsPanel organizationId={organizationId} team={team} />
								</div>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
