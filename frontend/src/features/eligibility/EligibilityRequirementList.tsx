import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useTeams } from "../teams/api";
import {
	useArchiveEligibilityRequirement,
	useCreateEligibilityRequirement,
	useCreateEligibilityRequirementVersion,
	useEligibilityRequirements,
} from "./api";
import { createEligibilityRequirementSchema, createEligibilityRequirementVersionSchema } from "./schema";
import type { EligibilityRequirement, RequirementMode } from "./types";

const MODE_LABELS: Record<RequirementMode, string> = {
	GUARDIAN_ESIGN_ACKNOWLEDGMENT: "Guardian acknowledgment (checkbox)",
	GUARDIAN_LEGAL_NAME_SIGNATURE: "Guardian e-sign (legal name)",
	FILE_UPLOAD: "Document upload",
	STAFF_REVIEWED_EXTERNAL: "Staff-reviewed external evidence",
	INFORMATIONAL: "Informational only",
};

type RequirementSort = "EFFECTIVE_DESC" | "EFFECTIVE_ASC" | "TITLE_ASC" | "TITLE_DESC";

function CreateRequirementForm({ organizationId, onDone }: { organizationId: string; onDone: () => void }) {
	const { data: teams } = useTeams(organizationId);
	const createRequirement = useCreateEligibilityRequirement(organizationId);
	const {
		register,
		handleSubmit,
		reset,
		watch,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createEligibilityRequirementSchema>,
		unknown,
		z.output<typeof createEligibilityRequirementSchema>
	>({
		resolver: zodResolver(createEligibilityRequirementSchema),
		defaultValues: {
			title: "",
			mode: "GUARDIAN_ESIGN_ACKNOWLEDGMENT",
			content: "",
			sport: "",
			season: "",
			teamId: "",
			effectiveStart: new Date().toISOString().slice(0, 10),
			expirationRule: "NEVER",
		},
	});
	const expirationRule = watch("expirationRule");

	const onSubmit = handleSubmit(async (values) => {
		await createRequirement.mutateAsync({
			title: values.title,
			mode: values.mode,
			content: values.content,
			sport: values.sport || undefined,
			season: values.season || undefined,
			teamId: values.teamId || undefined,
			effectiveStart: values.effectiveStart,
			expirationRule: values.expirationRule,
			expirationDays: values.expirationDays,
		});
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Create an eligibility requirement">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="req-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Title <span aria-hidden>*</span></label>
					<input id="req-title" type="text" placeholder="e.g. Season Liability Waiver" {...register("title")} aria-invalid={!!errors.title} aria-describedby={errors.title ? "req-title-error" : undefined} className="min-h-11 w-64 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.title && <p id="req-title-error" role="alert" className="text-sm text-error-red">{errors.title.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-mode" className="text-sm font-medium text-navy dark:text-[#f8fafc]">How guardians satisfy it</label>
					<select id="req-mode" {...register("mode")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						{Object.entries(MODE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-sport" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Sport (optional)</label>
					<input id="req-sport" type="text" placeholder="Applies to all sports if blank" {...register("sport")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-season" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Season (optional)</label>
					<input id="req-season" type="text" placeholder="Applies to all seasons if blank" {...register("season")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				{teams && teams.items.length > 0 && (
					<div className="flex flex-col gap-1">
						<label htmlFor="req-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Team (optional)</label>
						<select id="req-team" {...register("teamId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
							<option value="">Organization-wide</option>
							{teams.items.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
						</select>
					</div>
				)}
				<div className="flex flex-col gap-1">
					<label htmlFor="req-effective" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Effective from <span aria-hidden>*</span></label>
					<input id="req-effective" type="date" {...register("effectiveStart")} aria-invalid={!!errors.effectiveStart} aria-describedby={errors.effectiveStart ? "req-effective-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.effectiveStart && <p id="req-effective-error" role="alert" className="text-sm text-error-red">{errors.effectiveStart.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-expiration" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Expires</label>
					<select id="req-expiration" {...register("expirationRule")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="NEVER">Never</option>
						<option value="FIXED_DAYS_FROM_ACCEPTANCE">A fixed number of days after acceptance</option>
						<option value="SEASON_END">At season end</option>
						<option value="ORG_CONFIGURED">Organization-configured</option>
					</select>
				</div>
				{expirationRule === "FIXED_DAYS_FROM_ACCEPTANCE" && (
					<div className="flex flex-col gap-1">
						<label htmlFor="req-expiration-days" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Days until expiration <span aria-hidden>*</span></label>
						<input id="req-expiration-days" type="number" min={1} step={1} {...register("expirationDays")} aria-invalid={!!errors.expirationDays} aria-describedby={errors.expirationDays ? "req-expiration-days-error" : undefined} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
						{errors.expirationDays && <p id="req-expiration-days-error" role="alert" className="text-sm text-error-red">{errors.expirationDays.message}</p>}
					</div>
				)}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor="req-content" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Waiver / requirement text <span aria-hidden>*</span></label>
				<textarea id="req-content" rows={6} {...register("content")} aria-invalid={!!errors.content} aria-describedby={errors.content ? "req-content-error" : undefined} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.content && <p id="req-content-error" role="alert" className="text-sm text-error-red">{errors.content.message}</p>}
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={() => { reset(); onDone(); }}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create requirement"}</Button>
			</div>
		</form>
	);
}

function NewVersionForm({ organizationId, requirement, onDone }: { organizationId: string; requirement: EligibilityRequirement; onDone: () => void }) {
	const createVersion = useCreateEligibilityRequirementVersion(organizationId);
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<z.infer<typeof createEligibilityRequirementVersionSchema>>({
		resolver: zodResolver(createEligibilityRequirementVersionSchema),
		defaultValues: {
			title: requirement.title,
			content: requirement.content,
			effectiveStart: new Date().toISOString().slice(0, 10),
		},
	});

	const onSubmit = handleSubmit(async (values) => {
		await createVersion.mutateAsync({ requirementId: requirement.id, ...values });
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="mt-2 flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-3" noValidate aria-label={`New version of ${requirement.title}`}>
			<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Material changes to wording require guardians to re-accept. The prior version is archived, never overwritten.</p>
			<div className="flex flex-col gap-1">
				<label htmlFor={`version-title-${requirement.id}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Title</label>
				<input id={`version-title-${requirement.id}`} type="text" {...register("title")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.title && <p role="alert" className="text-sm text-error-red">{errors.title.message}</p>}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor={`version-content-${requirement.id}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Waiver / requirement text</label>
				<textarea id={`version-content-${requirement.id}`} rows={6} {...register("content")} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.content && <p role="alert" className="text-sm text-error-red">{errors.content.message}</p>}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor={`version-effective-${requirement.id}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Effective from</label>
				<input id={`version-effective-${requirement.id}`} type="date" {...register("effectiveStart")} className="min-h-11 w-48 rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.effectiveStart && <p role="alert" className="text-sm text-error-red">{errors.effectiveStart.message}</p>}
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={() => { reset(); onDone(); }}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Publishing…" : "Publish new version"}</Button>
			</div>
		</form>
	);
}

export function EligibilityRequirementList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useEligibilityRequirements(organizationId);
	const archiveRequirement = useArchiveEligibilityRequirement(organizationId);
	const { data: teams } = useTeams(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [versioningId, setVersioningId] = useState<string | null>(null);
	const [query, setQuery] = useState("");
	const [mode, setMode] = useState<RequirementMode | "">("");
	const [sort, setSort] = useState<RequirementSort>("EFFECTIVE_DESC");
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);

	const teamNameById = new Map((teams?.items ?? []).map((team) => [team.id, team.name]));
	const needle = query.trim().toLowerCase();
	const activeRequirements = (data ?? [])
		.filter((requirement) => requirement.status === "ACTIVE")
		.filter((requirement) => {
			if (mode && requirement.mode !== mode) return false;
			if (!needle) return true;
			const teamName = requirement.teamId ? teamNameById.get(requirement.teamId) : "Organization-wide";
			return [
				requirement.title,
				requirement.content,
				requirement.sport,
				requirement.season,
				teamName,
				MODE_LABELS[requirement.mode],
			]
				.filter(Boolean)
				.some((value) => value!.toLowerCase().includes(needle));
		})
		.sort((left, right) => {
			if (sort === "TITLE_ASC" || sort === "TITLE_DESC") {
				const result = left.title.localeCompare(right.title);
				return sort === "TITLE_ASC" ? result : -result;
			}
			const result = left.effectiveStart.localeCompare(right.effectiveStart) || left.title.localeCompare(right.title);
			return sort === "EFFECTIVE_ASC" ? result : -result;
		});

	const totalElements = activeRequirements.length;
	const safePage = Math.min(page, Math.max(0, Math.ceil(totalElements / size) - 1));
	const visibleRequirements = activeRequirements.slice(safePage * size, safePage * size + size);
	const hasFilters = !!mode;

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search requirements by title, sport, season, team, or mode"
				resultCount={totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "EFFECTIVE_DESC", label: "Effective date — newest" },
					{ value: "EFFECTIVE_ASC", label: "Effective date — oldest" },
					{ value: "TITLE_ASC", label: "Title A–Z" },
					{ value: "TITLE_DESC", label: "Title Z–A" },
				]}
				onSortChange={(value) => {
					setSort(value as RequirementSort);
					setPage(0);
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setMode("");
					setSort("EFFECTIVE_DESC");
					setPage(0);
				}}
				filters={
					<select
						aria-label="Filter eligibility requirement mode"
						value={mode}
						onChange={(event) => {
							setMode(event.target.value as RequirementMode | "");
							setPage(0);
						}}
						className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
					>
						<option value="">All requirement modes</option>
						{Object.entries(MODE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
					</select>
				}
				actions={
					<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>
						{showForm ? "Cancel" : "New requirement"}
					</Button>
				}
			/>

			{showForm && <CreateRequirementForm organizationId={organizationId} onDone={() => setShowForm(false)} />}

			{isLoading && <LoadingState label="Loading eligibility requirements…" />}
			{isError && <ErrorState message="Could not load eligibility requirements." onRetry={() => refetch()} />}
			{data && visibleRequirements.length === 0 && !showForm && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No eligibility requirements yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: "Create a waiver or document requirement guardians must satisfy before their athlete is roster-eligible."
					}
				/>
			)}
			{visibleRequirements.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Eligibility requirements">
					{visibleRequirements.map((requirement) => {
						const team = teams?.items.find((item) => item.id === requirement.teamId);
						return (
							<li key={requirement.id} className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
								<div className="flex flex-wrap items-center justify-between gap-3">
									<div className="min-w-0 flex-1">
										<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
											{requirement.title} <span className="text-xs text-slate-gray dark:text-[#cbd5e1]">v{requirement.version}</span>
										</p>
										<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
											{MODE_LABELS[requirement.mode]}
											{" · "}
											{[requirement.sport, requirement.season, team?.name].filter(Boolean).join(" · ") || "Organization-wide"}
											{" · Effective "}
											{requirement.effectiveStart}
										</p>
									</div>
									<div className="flex shrink-0 flex-wrap gap-2">
										<Button type="button" variant="secondary" onClick={() => setVersioningId((id) => id === requirement.id ? null : requirement.id)}>
											{versioningId === requirement.id ? "Cancel" : "New version"}
										</Button>
										<Button type="button" variant="secondary" onClick={() => archiveRequirement.mutate(requirement.id)} disabled={archiveRequirement.isPending}>
											Archive
										</Button>
									</div>
								</div>
								{versioningId === requirement.id && (
									<NewVersionForm organizationId={organizationId} requirement={requirement} onDone={() => setVersioningId(null)} />
								)}
							</li>
						);
					})}
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
