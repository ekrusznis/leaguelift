import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
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
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Create an eligibility requirement">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="req-title" className="text-sm font-medium text-navy">Title <span aria-hidden>*</span></label>
					<input id="req-title" type="text" placeholder="e.g. Season Liability Waiver" {...register("title")} aria-invalid={!!errors.title} aria-describedby={errors.title ? "req-title-error" : undefined} className="min-h-11 w-64 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.title && <p id="req-title-error" role="alert" className="text-sm text-error-red">{errors.title.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-mode" className="text-sm font-medium text-navy">How guardians satisfy it</label>
					<select id="req-mode" {...register("mode")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						{Object.entries(MODE_LABELS).map(([value, label]) => (
							<option key={value} value={value}>{label}</option>
						))}
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-sport" className="text-sm font-medium text-navy">Sport (optional)</label>
					<input id="req-sport" type="text" placeholder="Applies to all sports if blank" {...register("sport")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-season" className="text-sm font-medium text-navy">Season (optional)</label>
					<input id="req-season" type="text" placeholder="Applies to all seasons if blank" {...register("season")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				{teams && teams.items.length > 0 && (
					<div className="flex flex-col gap-1">
						<label htmlFor="req-team" className="text-sm font-medium text-navy">Team (optional)</label>
						<select id="req-team" {...register("teamId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
							<option value="">Organization-wide</option>
							{teams.items.map((team) => (
								<option key={team.id} value={team.id}>{team.name}</option>
							))}
						</select>
					</div>
				)}
				<div className="flex flex-col gap-1">
					<label htmlFor="req-effective" className="text-sm font-medium text-navy">Effective from <span aria-hidden>*</span></label>
					<input id="req-effective" type="date" {...register("effectiveStart")} aria-invalid={!!errors.effectiveStart} aria-describedby={errors.effectiveStart ? "req-effective-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.effectiveStart && <p id="req-effective-error" role="alert" className="text-sm text-error-red">{errors.effectiveStart.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="req-expiration" className="text-sm font-medium text-navy">Expires</label>
					<select id="req-expiration" {...register("expirationRule")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="NEVER">Never</option>
						<option value="FIXED_DAYS_FROM_ACCEPTANCE">A fixed number of days after acceptance</option>
						<option value="SEASON_END">At season end</option>
						<option value="ORG_CONFIGURED">Organization-configured</option>
					</select>
				</div>
				{expirationRule === "FIXED_DAYS_FROM_ACCEPTANCE" && (
					<div className="flex flex-col gap-1">
						<label htmlFor="req-expiration-days" className="text-sm font-medium text-navy">Days until expiration <span aria-hidden>*</span></label>
						<input id="req-expiration-days" type="number" min={1} step={1} {...register("expirationDays")} aria-invalid={!!errors.expirationDays} aria-describedby={errors.expirationDays ? "req-expiration-days-error" : undefined} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
						{errors.expirationDays && <p id="req-expiration-days-error" role="alert" className="text-sm text-error-red">{errors.expirationDays.message}</p>}
					</div>
				)}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor="req-content" className="text-sm font-medium text-navy">Waiver / requirement text <span aria-hidden>*</span></label>
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
		<form onSubmit={onSubmit} className="mt-2 flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-3" noValidate aria-label={`New version of ${requirement.title}`}>
			<p className="text-sm text-slate-gray">Material changes to wording require guardians to re-accept. The prior version is archived, never overwritten.</p>
			<div className="flex flex-col gap-1">
				<label htmlFor={`version-title-${requirement.id}`} className="text-sm font-medium text-navy">Title</label>
				<input id={`version-title-${requirement.id}`} type="text" {...register("title")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.title && <p role="alert" className="text-sm text-error-red">{errors.title.message}</p>}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor={`version-content-${requirement.id}`} className="text-sm font-medium text-navy">Waiver / requirement text</label>
				<textarea id={`version-content-${requirement.id}`} rows={6} {...register("content")} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.content && <p role="alert" className="text-sm text-error-red">{errors.content.message}</p>}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor={`version-effective-${requirement.id}`} className="text-sm font-medium text-navy">Effective from</label>
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

	const activeRequirements = data?.filter((r) => r.status === "ACTIVE") ?? [];

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${activeRequirements.length} active requirement${activeRequirements.length !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "New requirement"}
				</Button>
			</div>

			{showForm && <CreateRequirementForm organizationId={organizationId} onDone={() => setShowForm(false)} />}

			{isLoading && <LoadingState label="Loading eligibility requirements…" />}
			{isError && <ErrorState message="Could not load eligibility requirements." onRetry={() => refetch()} />}
			{data && activeRequirements.length === 0 && !showForm && (
				<EmptyState title="No eligibility requirements yet" description="Create a waiver or document requirement guardians must satisfy before their athlete is roster-eligible." />
			)}
			{activeRequirements.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Eligibility requirements">
					{activeRequirements.map((requirement) => {
						const team = teams?.items.find((t) => t.id === requirement.teamId);
						return (
							<li key={requirement.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
								<div className="flex flex-wrap items-center justify-between gap-3">
									<div className="min-w-0 flex-1">
										<p className="break-words font-medium text-navy">
											{requirement.title} <span className="text-xs text-slate-gray">v{requirement.version}</span>
										</p>
										<p className="text-sm text-slate-gray">
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
		</div>
	);
}
