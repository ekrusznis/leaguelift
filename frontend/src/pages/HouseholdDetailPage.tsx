import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Link, Navigate, useParams, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../components/Button";
import { EmptyState } from "../components/states/EmptyState";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import {
	useAddAdult,
	useAdults,
	useAssignTeam,
	useCreateParticipant,
	useHousehold,
	useParticipantTeams,
	useParticipants,
	useRemoveAdult,
	useRemoveFromTeam,
} from "../features/households/api";
import {
	addAdultSchema,
	createParticipantSchema,
	type CreateParticipantFormValues,
} from "../features/households/schema";
import type { Participant } from "../features/households/types";
import {
	useApplyAdjustment,
	useCreateFeeAssignment,
	useCreateFeeCheckoutSession,
	useFeeAdjustments,
	useFeeAssignments,
	useFeePaymentStatus,
	useFeePayments,
	usePaymentMethods,
	useRecordPayment,
	useUpdateFeeAssignmentStatus,
	useVoidAdjustment,
	useVoidPayment,
} from "../features/fees/api";
import {
	applyAdjustmentSchema,
	createFeeAssignmentSchema,
	recordPaymentSchema,
} from "../features/fees/schema";
import { STATUS_COLORS, STATUS_LABELS } from "../features/fees/statusLabels";
import { PaymentPlanPanel } from "../features/fees/PaymentPlanPanel";
import type { FeeAssignment, FeeAssignmentStatus } from "../features/fees/types";
import { useFeeTemplates } from "../features/fees/api";
import { useTeams } from "../features/teams/api";
import { HouseholdDocumentsPanel } from "../features/documents/HouseholdDocumentsPanel";
import { HouseholdMediaPanel } from "../features/householdMedia/HouseholdMediaPanel";
import { ParticipantEligibilityPanel } from "../features/eligibility/ParticipantEligibilityPanel";
import { useHouseholdEligibilityClearance } from "../features/eligibility/api";
import { ClearanceStatusPill } from "../features/eligibility/ClearanceStatusPill";
import type { ClearanceStatus, EligibilityClearance } from "../features/eligibility/types";
import { EventListPanel } from "../features/events/EventListPanel";
import { ProfilePhotoEditor } from "../features/media/ProfilePhotoEditor";
import { ProfileCorrectionForm } from "../features/profileCorrections/ProfileCorrectionForm";
import { HouseholdCorrectionRequestsPanel } from "../features/profileCorrections/HouseholdCorrectionRequestsPanel";
import { useContexts } from "../authorization/api";
import { hasCapability } from "../authorization/capabilities";
import { Capabilities } from "../authorization/capabilityConstants";
import { appPaths, type HouseholdSection } from "../routes/appPaths";
import { ReminderButton } from "../features/communications/ReminderButton";

function formatAmount(amountMinor: number, currency: string) {
	return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100);
}

// --- Adults Panel ---

function AddAdultForm({ organizationId, householdId, onDone }: { organizationId: string; householdId: string; onDone: () => void }) {
	const addAdult = useAddAdult(organizationId, householdId);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<
		z.input<typeof addAdultSchema>,
		unknown,
		z.output<typeof addAdultSchema>
	>({
		resolver: zodResolver(addAdultSchema),
		defaultValues: { firstName: "", lastName: "", email: "", phone: "", relationship: "", isPrimary: false },
	});

	const onSubmit = handleSubmit(async (values) => {
		await addAdult.mutateAsync(values);
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Add an adult">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-first" className="text-sm font-medium text-navy dark:text-[#f8fafc]">First name <span aria-hidden>*</span></label>
					<input id="adult-first" type="text" {...register("firstName")} aria-invalid={!!errors.firstName} aria-describedby={errors.firstName ? "adult-first-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.firstName && <p id="adult-first-error" role="alert" className="text-sm text-error-red">{errors.firstName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-last" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Last name <span aria-hidden>*</span></label>
					<input id="adult-last" type="text" {...register("lastName")} aria-invalid={!!errors.lastName} aria-describedby={errors.lastName ? "adult-last-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.lastName && <p id="adult-last-error" role="alert" className="text-sm text-error-red">{errors.lastName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-email" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Email</label>
					<input id="adult-email" type="email" {...register("email")} aria-invalid={!!errors.email} aria-describedby={errors.email ? "adult-email-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.email && <p id="adult-email-error" role="alert" className="text-sm text-error-red">{errors.email.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-rel" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Relationship</label>
					<input id="adult-rel" type="text" placeholder="e.g. Parent" {...register("relationship")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				<div className="flex items-center gap-2 self-end pb-1">
					<input id="adult-primary" type="checkbox" {...register("isPrimary")} className="h-4 w-4" />
					<label htmlFor="adult-primary" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Primary contact</label>
				</div>
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={() => { reset(); onDone(); }}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Adding…" : "Add adult"}</Button>
			</div>
		</form>
	);
}

function AdultsPanel({ organizationId, householdId, canManage, canManagePhotos }: { organizationId: string; householdId: string; canManage: boolean; canManagePhotos: boolean }) {
	const { user } = useAuth();
	const currentUserEmail = user?.email.trim().toLowerCase();
	const { data, isLoading, isError, refetch } = useAdults(organizationId, householdId);
	const removeAdult = useRemoveAdult(organizationId, householdId);
	const [showForm, setShowForm] = useState(false);
	const [correctionAdultId, setCorrectionAdultId] = useState<string | null>(null);

	return (
		<section aria-label="Adults" className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Adults</h2>
				{canManage && (
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Add adult"}
					</Button>
				)}
			</div>
			{canManage && showForm && <AddAdultForm organizationId={organizationId} householdId={householdId} onDone={() => setShowForm(false)} />}
			{isLoading && <LoadingState label="Loading adults…" />}
			{isError && <ErrorState message="Could not load adults." onRetry={() => refetch()} />}
			{data && data.length === 0 && !showForm && (
				<EmptyState title="No adults on record" description="Add an adult to this household." />
			)}
			{data && data.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Adults list">
					{data.map((adult) => {
						const canRequestForAdult = canManage || (!!currentUserEmail && adult.email?.trim().toLowerCase() === currentUserEmail);
						return (
						<li key={adult.id} className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
							<ProfilePhotoEditor
								organizationId={organizationId}
								entityType="HOUSEHOLD_ADULT"
								entityId={adult.id}
								name={`${adult.firstName} ${adult.lastName}`}
								canEdit={canManage || (canManagePhotos && !!currentUserEmail && adult.email?.trim().toLowerCase() === currentUserEmail)}
							/>
							<div className="min-w-0 flex-1">
								<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
									{adult.firstName} {adult.lastName}
									{adult.isPrimary && <span className="ml-2 rounded-full bg-navy/10 px-2 py-0.5 text-xs text-navy dark:text-[#f8fafc]">Primary</span>}
								</p>
								<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
									{[adult.relationship, adult.email].filter(Boolean).join(" · ")}
								</p>
							</div>
							<div className="flex shrink-0 flex-wrap gap-2">
								{canRequestForAdult && (
									<Button type="button" variant="secondary" onClick={() => setCorrectionAdultId((id) => id === adult.id ? null : adult.id)}>
										{correctionAdultId === adult.id ? "Cancel correction" : "Request correction"}
									</Button>
								)}
								{canManage && (
									<Button type="button" variant="secondary" onClick={() => removeAdult.mutate(adult.id)} disabled={removeAdult.isPending}>
										Remove
									</Button>
								)}
							</div>
							</div>
							{correctionAdultId === adult.id && (
								<ProfileCorrectionForm
									organizationId={organizationId}
									householdId={householdId}
									targetType="HOUSEHOLD_ADULT"
									targetId={adult.id}
									targetLabel={`${adult.firstName} ${adult.lastName}`}
									fields={["ADULT_FIRST_NAME", "ADULT_LAST_NAME", "ADULT_EMAIL", "ADULT_PHONE", "ADULT_RELATIONSHIP"]}
									onDone={() => setCorrectionAdultId(null)}
								/>
							)}
						</li>
						);
					})}
				</ul>
			)}
		</section>
	);
}

// --- Participants Panel ---

function ParticipantTeamRow({ organizationId, participant, canManage }: { organizationId: string; participant: Participant; canManage: boolean }) {
	const { data: teams } = useTeams(organizationId);
	const { data: assignments, isLoading } = useParticipantTeams(organizationId, participant.id);
	const assignTeam = useAssignTeam(organizationId, participant.id);
	const removeFromTeam = useRemoveFromTeam(organizationId, participant.id);
	const [selectedTeam, setSelectedTeam] = useState("");

	const assignedTeamIds = new Set(assignments?.map((a) => a.teamId) ?? []);
	const availableTeams = teams?.items.filter((t) => !assignedTeamIds.has(t.id)) ?? [];

	return (
		<div className="mt-2 pl-4 border-l border-slate-gray/20">
			{isLoading && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Loading teams…</p>}
			{assignments && assignments.length > 0 && (
				<ul className="flex flex-wrap gap-2 mb-2">
					{assignments.map((a) => {
						const team = teams?.items.find((t) => t.id === a.teamId);
						return (
							<li key={a.id} className="flex items-center gap-1 rounded-full bg-navy/10 px-3 py-1 text-xs text-navy dark:text-[#f8fafc]">
								{team?.name ?? a.teamId}
								{canManage && (
									<button
										type="button"
										onClick={() => removeFromTeam.mutate(a.teamId)}
										className="ml-1 text-slate-gray dark:text-[#cbd5e1] hover:text-error-red"
										aria-label={`Remove from ${team?.name ?? "team"}`}
									>
										×
									</button>
								)}
							</li>
						);
					})}
				</ul>
			)}
			{canManage && availableTeams.length > 0 && (
				<div className="flex items-center gap-2">
					<select
						value={selectedTeam}
						onChange={(e) => setSelectedTeam(e.target.value)}
						className="rounded-md border border-slate-gray/30 px-2 py-1 text-sm"
						aria-label="Select a team to assign"
					>
						<option value="">Assign to team…</option>
						{availableTeams.map((t) => (
							<option key={t.id} value={t.id}>{t.name}</option>
						))}
					</select>
					<Button
						type="button"
						variant="secondary"
						disabled={!selectedTeam || assignTeam.isPending}
						onClick={() => {
							if (selectedTeam) {
								assignTeam.mutate(selectedTeam, { onSuccess: () => setSelectedTeam("") });
							}
						}}
					>
						Assign
					</Button>
				</div>
			)}
		</div>
	);
}

function AddParticipantForm({ organizationId, householdId, onDone }: { organizationId: string; householdId: string; onDone: () => void }) {
	const createParticipant = useCreateParticipant(organizationId, householdId);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<CreateParticipantFormValues>({
		resolver: zodResolver(createParticipantSchema),
		defaultValues: { firstName: "", lastName: "", dateOfBirth: "", notes: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createParticipant.mutateAsync(values);
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Add a participant">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="par-first" className="text-sm font-medium text-navy dark:text-[#f8fafc]">First name <span aria-hidden>*</span></label>
					<input id="par-first" type="text" {...register("firstName")} aria-invalid={!!errors.firstName} aria-describedby={errors.firstName ? "par-first-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.firstName && <p id="par-first-error" role="alert" className="text-sm text-error-red">{errors.firstName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="par-last" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Last name <span aria-hidden>*</span></label>
					<input id="par-last" type="text" {...register("lastName")} aria-invalid={!!errors.lastName} aria-describedby={errors.lastName ? "par-last-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.lastName && <p id="par-last-error" role="alert" className="text-sm text-error-red">{errors.lastName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="par-dob" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Date of birth</label>
					<input id="par-dob" type="date" {...register("dateOfBirth")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={() => { reset(); onDone(); }}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Adding…" : "Add participant"}</Button>
			</div>
		</form>
	);
}

/** Most-concerning-first, so a participant on multiple teams with mixed statuses shows the status that most needs attention. */
const CLEARANCE_SEVERITY: ClearanceStatus[] = ["INELIGIBLE", "EXPIRED", "UNDER_REVIEW", "DOCUMENTS_REQUIRED", "ROSTER_PENDING", "CLEARED"];

export function worstClearanceByParticipant(clearances: EligibilityClearance[]): Map<string, ClearanceStatus> {
	const byParticipant = new Map<string, ClearanceStatus>();
	for (const clearance of clearances) {
		const current = byParticipant.get(clearance.participantId);
		if (!current || CLEARANCE_SEVERITY.indexOf(clearance.status) < CLEARANCE_SEVERITY.indexOf(current)) {
			byParticipant.set(clearance.participantId, clearance.status);
		}
	}
	return byParticipant;
}

function ParticipantsPanel({ organizationId, householdId, canManage, canManagePhotos }: { organizationId: string; householdId: string; canManage: boolean; canManagePhotos: boolean }) {
	const { data, isLoading, isError, refetch } = useParticipants(organizationId, householdId);
	const clearances = useHouseholdEligibilityClearance(organizationId, householdId);
	const clearanceByParticipant = worstClearanceByParticipant(clearances.data ?? []);
	const [showForm, setShowForm] = useState(false);
	const [expandedId, setExpandedId] = useState<string | null>(null);
	const [eligibilityExpandedId, setEligibilityExpandedId] = useState<string | null>(null);
	const [correctionParticipantId, setCorrectionParticipantId] = useState<string | null>(null);

	return (
		<section aria-label="Participants" className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Participants</h2>
				{canManage && (
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Add participant"}
					</Button>
				)}
			</div>
			{canManage && showForm && <AddParticipantForm organizationId={organizationId} householdId={householdId} onDone={() => setShowForm(false)} />}
			{isLoading && <LoadingState label="Loading participants…" />}
			{isError && <ErrorState message="Could not load participants." onRetry={() => refetch()} />}
			{data && data.length === 0 && !showForm && (
				<EmptyState title="No participants yet" description="Add the athletes or players in this household." />
			)}
			{data && data.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Participants list">
					{data.map((participant) => (
						<li key={participant.id} className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<ProfilePhotoEditor
									organizationId={organizationId}
									entityType="PARTICIPANT"
									entityId={participant.id}
									name={`${participant.firstName} ${participant.lastName}`}
									canEdit={canManagePhotos}
								/>
								<div className="min-w-0 flex-1">
									<div className="flex flex-wrap items-center gap-2">
										<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
											{participant.firstName} {participant.lastName}
										</p>
										{clearanceByParticipant.has(participant.id) && (
											<ClearanceStatusPill status={clearanceByParticipant.get(participant.id)!} />
										)}
									</div>
									{participant.dateOfBirth && (
										<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Born {participant.dateOfBirth}</p>
									)}
								</div>
								<div className="flex shrink-0 flex-wrap items-center gap-2">
									<Button type="button" variant="secondary" onClick={() => setCorrectionParticipantId((id) => id === participant.id ? null : participant.id)}>
										{correctionParticipantId === participant.id ? "Cancel correction" : "Request correction"}
									</Button>
									<button
										type="button"
										className="text-sm text-azure-blue hover:underline"
										onClick={() => setExpandedId((id) => id === participant.id ? null : participant.id)}
									>
										{expandedId === participant.id ? "Hide teams" : "Teams"}
									</button>
									<button
										type="button"
										className="text-sm text-azure-blue hover:underline"
										onClick={() => setEligibilityExpandedId((id) => id === participant.id ? null : participant.id)}
									>
										{eligibilityExpandedId === participant.id ? "Hide eligibility" : "Eligibility"}
									</button>
								</div>
							</div>
							{correctionParticipantId === participant.id && (
								<ProfileCorrectionForm
									organizationId={organizationId}
									householdId={householdId}
									targetType="PARTICIPANT"
									targetId={participant.id}
									targetLabel={`${participant.firstName} ${participant.lastName}`}
									fields={["PARTICIPANT_FIRST_NAME", "PARTICIPANT_LAST_NAME", "PARTICIPANT_DATE_OF_BIRTH"]}
									onDone={() => setCorrectionParticipantId(null)}
								/>
							)}
							{expandedId === participant.id && (
								<ParticipantTeamRow organizationId={organizationId} participant={participant} canManage={canManage} />
							)}
							{eligibilityExpandedId === participant.id && (
								<div className="mt-2 border-l border-slate-gray/20 pl-4">
									<ParticipantEligibilityPanel organizationId={organizationId} participantId={participant.id} canManage={canManage} />
								</div>
							)}
						</li>
					))}
				</ul>
			)}
		</section>
	);
}

// --- Fee Payment / Adjustment Detail ---

function RecordPaymentForm({ organizationId, householdId, assignmentId, onDone }: { organizationId: string; householdId: string; assignmentId: string; onDone: () => void }) {
	const recordPayment = useRecordPayment(organizationId, householdId, assignmentId);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<
		z.input<typeof recordPaymentSchema>,
		unknown,
		z.output<typeof recordPaymentSchema>
	>({
		resolver: zodResolver(recordPaymentSchema),
		defaultValues: { amountMinor: 0, method: "CASH", paidAt: new Date().toISOString().slice(0, 10), note: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await recordPayment.mutateAsync(values);
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-3" noValidate aria-label="Record a payment">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-amount-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Amount (cents) <span aria-hidden>*</span></label>
					<input id={`payment-amount-${assignmentId}`} type="number" min={1} step={1} {...register("amountMinor")} aria-invalid={!!errors.amountMinor} aria-describedby={errors.amountMinor ? `payment-amount-${assignmentId}-error` : undefined} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.amountMinor && <p id={`payment-amount-${assignmentId}-error`} role="alert" className="text-sm text-error-red">{errors.amountMinor.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-method-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Method</label>
					<select id={`payment-method-${assignmentId}`} {...register("method")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="CASH">Cash</option>
						<option value="CHECK">Check</option>
						<option value="VENMO">Venmo</option>
						<option value="ZELLE">Zelle</option>
						<option value="OTHER">Other</option>
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-date-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Date paid</label>
					<input id={`payment-date-${assignmentId}`} type="date" {...register("paidAt")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-note-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Note</label>
					<input id={`payment-note-${assignmentId}`} type="text" {...register("note")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={onDone}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Recording…" : "Record payment"}</Button>
			</div>
		</form>
	);
}

function ApplyAdjustmentForm({ organizationId, householdId, assignmentId, onDone }: { organizationId: string; householdId: string; assignmentId: string; onDone: () => void }) {
	const applyAdjustment = useApplyAdjustment(organizationId, householdId, assignmentId);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<
		z.input<typeof applyAdjustmentSchema>,
		unknown,
		z.output<typeof applyAdjustmentSchema>
	>({
		resolver: zodResolver(applyAdjustmentSchema),
		defaultValues: { adjustmentType: "DISCOUNT", amountMinor: 0, reason: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await applyAdjustment.mutateAsync(values);
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-3" noValidate aria-label="Apply a discount or credit">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor={`adj-type-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Type</label>
					<select id={`adj-type-${assignmentId}`} {...register("adjustmentType")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="DISCOUNT">Discount</option>
						<option value="CREDIT">Credit</option>
						<option value="CORRECTION">Correction</option>
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`adj-amount-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Amount (cents) <span aria-hidden>*</span></label>
					<input id={`adj-amount-${assignmentId}`} type="number" min={1} step={1} {...register("amountMinor")} aria-invalid={!!errors.amountMinor} aria-describedby={errors.amountMinor ? `adj-amount-${assignmentId}-error` : undefined} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.amountMinor && <p id={`adj-amount-${assignmentId}-error`} role="alert" className="text-sm text-error-red">{errors.amountMinor.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`adj-reason-${assignmentId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Reason</label>
					<input id={`adj-reason-${assignmentId}`} type="text" {...register("reason")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={onDone}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Applying…" : "Apply"}</Button>
			</div>
		</form>
	);
}

function FeeDetailPanel({ organizationId, householdId, fee, canManage }: { organizationId: string; householdId: string; fee: FeeAssignment; canManage: boolean }) {
	const { data: payments, isLoading: paymentsLoading } = useFeePayments(organizationId, fee.id);
	const { data: adjustments, isLoading: adjustmentsLoading } = useFeeAdjustments(organizationId, fee.id);
	const voidPayment = useVoidPayment(organizationId, householdId, fee.id);
	const voidAdjustment = useVoidAdjustment(organizationId, householdId, fee.id);
	const [activeForm, setActiveForm] = useState<"payment" | "adjustment" | null>(null);
	const locked = fee.status === "WAIVED" || fee.status === "CANCELLED";

	function handleVoidPayment(paymentId: string) {
		const reason = window.prompt("Reason for voiding this payment?");
		if (reason && reason.trim()) {
			voidPayment.mutate({ paymentId, reason: reason.trim() });
		}
	}

	function handleVoidAdjustment(adjustmentId: string) {
		const reason = window.prompt("Reason for voiding this adjustment?");
		if (reason && reason.trim()) {
			voidAdjustment.mutate({ adjustmentId, reason: reason.trim() });
		}
	}

	return (
		<div className="mt-2 flex flex-col gap-3 border-l border-slate-gray/20 pl-4">
			<dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-4">
				<div>
					<dt className="text-slate-gray dark:text-[#cbd5e1]">Original</dt>
					<dd className="font-medium text-navy dark:text-[#f8fafc]">{formatAmount(fee.originalAmountMinor, fee.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-gray dark:text-[#cbd5e1]">Paid</dt>
					<dd className="font-medium text-navy dark:text-[#f8fafc]">{formatAmount(fee.paidMinor, fee.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-gray dark:text-[#cbd5e1]">Adjusted</dt>
					<dd className="font-medium text-navy dark:text-[#f8fafc]">{formatAmount(fee.adjustedMinor, fee.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-gray dark:text-[#cbd5e1]">Balance</dt>
					<dd className="font-semibold text-navy dark:text-[#f8fafc]">{formatAmount(fee.balanceMinor, fee.currency)}</dd>
				</div>
			</dl>

			{canManage && !locked && (
				<div className="flex gap-2">
					<Button type="button" variant="secondary" onClick={() => setActiveForm((f) => (f === "payment" ? null : "payment"))}>
						{activeForm === "payment" ? "Cancel" : "Record payment"}
					</Button>
					<Button type="button" variant="secondary" onClick={() => setActiveForm((f) => (f === "adjustment" ? null : "adjustment"))}>
						{activeForm === "adjustment" ? "Cancel" : "Apply discount/credit"}
					</Button>
				</div>
			)}
			{canManage && activeForm === "payment" && (
				<RecordPaymentForm organizationId={organizationId} householdId={householdId} assignmentId={fee.id} onDone={() => setActiveForm(null)} />
			)}
			{canManage && activeForm === "adjustment" && (
				<ApplyAdjustmentForm organizationId={organizationId} householdId={householdId} assignmentId={fee.id} onDone={() => setActiveForm(null)} />
			)}

			<PaymentPlanPanel
				organizationId={organizationId}
				householdId={householdId}
				assignmentId={fee.id}
				balanceMinor={fee.balanceMinor}
				currency={fee.currency}
				canManage={canManage && !locked}
			/>

			{paymentsLoading && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Loading payment history…</p>}
			{payments && payments.length > 0 && (
				<div>
					<h3 className="text-sm font-semibold text-navy dark:text-[#f8fafc]">Payments</h3>
					<ul className="flex flex-col gap-1">
						{payments.map((payment) => (
							<li key={payment.id} className="flex flex-wrap items-center justify-between gap-3 text-sm">
								<span className={`min-w-0 flex-1 break-words ${payment.voidedAt ? "text-slate-gray dark:text-[#cbd5e1] line-through" : "text-slate-gray dark:text-[#cbd5e1]"}`}>
									{formatAmount(payment.amountMinor, payment.currency)} · {payment.method} · {payment.paidAt}
									{payment.voidedAt ? ` · voided (${payment.voidReason})` : ""}
								</span>
								{canManage && !payment.voidedAt && (
									<button type="button" className="shrink-0 text-azure-blue hover:underline" onClick={() => handleVoidPayment(payment.id)}>
										Void
									</button>
								)}
							</li>
						))}
					</ul>
				</div>
			)}

			{adjustmentsLoading && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Loading adjustment history…</p>}
			{adjustments && adjustments.length > 0 && (
				<div>
					<h3 className="text-sm font-semibold text-navy dark:text-[#f8fafc]">Discounts &amp; Credits</h3>
					<ul className="flex flex-col gap-1">
						{adjustments.map((adjustment) => (
							<li key={adjustment.id} className="flex flex-wrap items-center justify-between gap-3 text-sm">
								<span className={`min-w-0 flex-1 break-words ${adjustment.voidedAt ? "text-slate-gray dark:text-[#cbd5e1] line-through" : "text-slate-gray dark:text-[#cbd5e1]"}`}>
									{formatAmount(adjustment.amountMinor, adjustment.currency)} · {adjustment.adjustmentType}
									{adjustment.reason ? ` · ${adjustment.reason}` : ""}
									{adjustment.voidedAt ? ` · voided (${adjustment.voidReason})` : ""}
								</span>
								{canManage && !adjustment.voidedAt && (
									<button type="button" className="shrink-0 text-azure-blue hover:underline" onClick={() => handleVoidAdjustment(adjustment.id)}>
										Void
									</button>
								)}
							</li>
						))}
					</ul>
				</div>
			)}
		</div>
	);
}

// --- Fee Assignments Panel ---

function AddFeeAssignmentForm({
	organizationId,
	householdId,
	onDone,
}: {
	organizationId: string;
	householdId: string;
	onDone: () => void;
}) {
	const { data: templates } = useFeeTemplates(organizationId);
	const { data: participants } = useParticipants(organizationId, householdId);
	const createAssignment = useCreateFeeAssignment(organizationId, householdId);

	const { register, handleSubmit, reset, setValue, formState: { errors, isSubmitting } } = useForm<
		z.input<typeof createFeeAssignmentSchema>,
		unknown,
		z.output<typeof createFeeAssignmentSchema>
	>({
		resolver: zodResolver(createFeeAssignmentSchema),
		defaultValues: { description: "", originalAmountMinor: 0, currency: "USD", dueDate: "", feeTemplateId: "", participantId: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createAssignment.mutateAsync(values);
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Assign a fee">
			<div className="flex flex-wrap gap-3">
				{templates && templates.items.length > 0 && (
					<div className="flex flex-col gap-1">
						<label htmlFor="fee-template" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Fee template</label>
						<select
							id="fee-template"
							{...register("feeTemplateId")}
							onChange={(e) => {
								const tpl = templates.items.find((t) => t.id === e.target.value);
								if (tpl) {
									setValue("description", tpl.name);
									setValue("originalAmountMinor", tpl.amountMinor);
								}
								register("feeTemplateId").onChange(e);
							}}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						>
							<option value="">Select a template (optional)</option>
							{templates.items.map((t) => (
								<option key={t.id} value={t.id}>{t.name} — {formatAmount(t.amountMinor, t.currency)}</option>
							))}
						</select>
					</div>
				)}
				<div className="flex flex-col gap-1">
					<label htmlFor="fee-desc" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Description <span aria-hidden>*</span></label>
					<input id="fee-desc" type="text" {...register("description")} aria-invalid={!!errors.description} aria-describedby={errors.description ? "fee-desc-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.description && <p id="fee-desc-error" role="alert" className="text-sm text-error-red">{errors.description.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="fee-amount" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Amount (cents) <span aria-hidden>*</span></label>
					<input id="fee-amount" type="number" min={0} step={1} {...register("originalAmountMinor")} aria-invalid={!!errors.originalAmountMinor} aria-describedby={errors.originalAmountMinor ? "fee-amount-error" : undefined} className="min-h-11 w-36 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.originalAmountMinor && <p id="fee-amount-error" role="alert" className="text-sm text-error-red">{errors.originalAmountMinor.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="fee-due" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Due date</label>
					<input id="fee-due" type="date" {...register("dueDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				{participants && participants.length > 0 && (
					<div className="flex flex-col gap-1">
						<label htmlFor="fee-participant" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Participant (optional)</label>
						<select id="fee-participant" {...register("participantId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
							<option value="">Household-wide</option>
							{participants.map((p) => (
								<option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>
							))}
						</select>
					</div>
				)}
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={() => { reset(); onDone(); }}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Adding…" : "Add fee"}</Button>
			</div>
		</form>
	);
}

/** Guardian-initiated online payment via Stripe Checkout — the counterpart to staff-only `RecordPaymentForm`. Confirmation happens via the Stripe webhook, so this only starts checkout; `FeePaymentReturnBanner` handles the return. */
function PayOnlineButton({ organizationId, assignmentId, amountMinor }: { organizationId: string; assignmentId: string; amountMinor: number }) {
	const createCheckout = useCreateFeeCheckoutSession(organizationId, assignmentId);
	const [error, setError] = useState<string | null>(null);

	const handleClick = async () => {
		setError(null);
		try {
			const returnBase = `${window.location.origin}${window.location.pathname}`;
			const result = await createCheckout.mutateAsync({
				amountMinor,
				successUrl: `${returnBase}?feeAssignmentId=${assignmentId}&feePaymentId={FEE_PAYMENT_ID}`,
				cancelUrl: `${returnBase}?feeCheckoutCanceled=1`,
			});
			window.location.href = result.checkoutUrl;
		} catch {
			setError("Could not start checkout. Please try again.");
		}
	};

	return (
		<div className="flex flex-col items-end gap-1">
			<Button type="button" variant="primary" onClick={handleClick} disabled={createCheckout.isPending}>
				{createCheckout.isPending ? "Starting checkout…" : "Pay online"}
			</Button>
			{error && <p className="text-xs text-red-600">{error}</p>}
		</div>
	);
}

/** Polls payment status after redirect back from Stripe Checkout, mirroring `ContributionReturnPanel`'s own PENDING poll — confirmation is asynchronous via the webhook, never guaranteed by the time the browser returns. */
function FeePaymentReturnBanner({
	organizationId,
	householdId,
	assignmentId,
	paymentId,
	onDismiss,
}: {
	organizationId: string;
	householdId: string;
	assignmentId: string;
	paymentId: string;
	onDismiss: () => void;
}) {
	const queryClient = useQueryClient();
	const { data: payment } = useFeePaymentStatus(organizationId, assignmentId, paymentId);

	useEffect(() => {
		if (payment?.status === "CONFIRMED") {
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "households", householdId, "fee-assignments"] });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "fee-assignments", assignmentId, "payments"] });
		}
	}, [payment?.status, organizationId, householdId, assignmentId, queryClient]);

	if (payment?.status === "CONFIRMED") {
		return (
			<div
				role="status"
				className="flex items-center justify-between gap-3 rounded-md border border-green-600/30 bg-green-50 px-3 py-2 text-sm text-green-800 dark:bg-green-900/20 dark:text-green-300"
			>
				<span>Payment confirmed — thank you!</span>
				<button type="button" className="underline" onClick={onDismiss}>
					Dismiss
				</button>
			</div>
		);
	}
	return (
		<div role="status" className="rounded-md border border-slate-gray/20 bg-slate-gray/5 px-3 py-2 text-sm text-slate-gray dark:text-[#cbd5e1]">
			Confirming your payment…
		</div>
	);
}

/** Phase 32 scaffold — Venmo/Cash App/Affirm render as visibly-present-but-disabled "Coming soon" options rather than being hidden, so families know these are planned; Zelle shows the org's handle when set. */
function PaymentMethodOptions({ organizationId }: { organizationId: string }) {
	const { data } = usePaymentMethods(organizationId);
	const otherMethods = data?.filter((m) => m.method !== "STRIPE_ONLINE") ?? [];
	if (otherMethods.length === 0) return null;

	return (
		<details className="rounded-md border border-slate-gray/20 bg-slate-gray/5 px-3 py-2 text-sm">
			<summary className="cursor-pointer font-medium text-navy dark:text-[#f8fafc]">Other ways to pay</summary>
			<ul className="mt-2 flex flex-col gap-1.5">
				{otherMethods.map((method) => (
					<li key={method.method} className="flex items-center justify-between gap-3">
						<span className={method.available ? "text-navy dark:text-[#f8fafc]" : "text-slate-gray dark:text-[#cbd5e1]"}>
							{method.displayName}
						</span>
						<span
							className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
								method.available
									? "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300"
									: "bg-slate-gray/10 text-slate-gray dark:bg-[#1f2937] dark:text-[#94a3b8]"
							}`}
						>
							{method.note ?? "Available"}
						</span>
					</li>
				))}
			</ul>
		</details>
	);
}

function FeeAssignmentsPanel({ organizationId, householdId, canManage }: { organizationId: string; householdId: string; canManage: boolean }) {
	const { data, isLoading, isError, refetch } = useFeeAssignments(organizationId, householdId);
	const { data: participants } = useParticipants(organizationId, householdId);
	const updateStatus = useUpdateFeeAssignmentStatus(organizationId, householdId);
	const [showForm, setShowForm] = useState(false);
	const [expandedFeeId, setExpandedFeeId] = useState<string | null>(null);
	const [searchParams, setSearchParams] = useSearchParams();

	const totalBalanceMinor = data?.items.reduce((sum, fee) => sum + fee.balanceMinor, 0) ?? 0;
	const currency = data?.items[0]?.currency ?? "USD";

	const returnAssignmentId = searchParams.get("feeAssignmentId");
	const returnPaymentId = searchParams.get("feePaymentId");
	const checkoutCanceled = searchParams.get("feeCheckoutCanceled") === "1";

	const dismissReturnParams = () => {
		setSearchParams(
			(prev) => {
				prev.delete("feeAssignmentId");
				prev.delete("feePaymentId");
				prev.delete("feeCheckoutCanceled");
				return prev;
			},
			{ replace: true },
		);
	};

	return (
		<section aria-label="Fee assignments" className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Fees</h2>
				{canManage && (
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Add fee"}
					</Button>
				)}
			</div>
			{returnAssignmentId && returnPaymentId && (
				<FeePaymentReturnBanner
					organizationId={organizationId}
					householdId={householdId}
					assignmentId={returnAssignmentId}
					paymentId={returnPaymentId}
					onDismiss={dismissReturnParams}
				/>
			)}
			{checkoutCanceled && (
				<div role="status" className="flex items-center justify-between gap-3 rounded-md border border-slate-gray/20 bg-slate-gray/5 px-3 py-2 text-sm text-slate-gray dark:text-[#cbd5e1]">
					<span>Checkout canceled — no charge was made.</span>
					<button type="button" className="underline" onClick={dismissReturnParams}>
						Dismiss
					</button>
				</div>
			)}
			{data && data.items.length > 0 && (
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					Household balance: <span className="font-semibold text-navy dark:text-[#f8fafc]">{formatAmount(totalBalanceMinor, currency)}</span>
				</p>
			)}
			{totalBalanceMinor > 0 && <PaymentMethodOptions organizationId={organizationId} />}
			{canManage && showForm && (
				<AddFeeAssignmentForm organizationId={organizationId} householdId={householdId} onDone={() => setShowForm(false)} />
			)}
			{isLoading && <LoadingState label="Loading fees…" />}
			{isError && <ErrorState message="Could not load fees." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No fees assigned" description="Assign a fee to track payments for this household." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Fee assignments list">
					{data.items.map((fee) => {
						const participant = participants?.find((p) => p.id === fee.participantId);
						return (
							<li key={fee.id} className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
								<div className="flex flex-wrap items-center justify-between gap-3">
									<div className="min-w-0 flex-1">
										<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{fee.description}</p>
										<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
											{formatAmount(fee.balanceMinor, fee.currency)} due of {formatAmount(fee.originalAmountMinor, fee.currency)}
											{fee.dueDate ? ` · Due ${fee.dueDate}` : ""}
											{participant ? ` · ${participant.firstName} ${participant.lastName}` : ""}
										</p>
									</div>
									<div className="flex shrink-0 items-center gap-2">
										<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[fee.status]}`}>
											{STATUS_LABELS[fee.status]}
										</span>
										{fee.balanceMinor > 0 && fee.status !== "WAIVED" && fee.status !== "CANCELLED" && (
											<PayOnlineButton organizationId={organizationId} assignmentId={fee.id} amountMinor={fee.balanceMinor} />
										)}
										{canManage && fee.balanceMinor > 0 && fee.status !== "WAIVED" && fee.status !== "CANCELLED" && (
											<ReminderButton organizationId={organizationId} resourceType="FEE_ASSIGNMENT" resourceId={fee.id} label="Send payment reminder" />
										)}
										{canManage && fee.status !== "PAID" && fee.status !== "WAIVED" && fee.status !== "CANCELLED" && (
											<select
												value=""
												onChange={(e) => {
													if (e.target.value) {
														updateStatus.mutate({ assignmentId: fee.id, status: e.target.value as FeeAssignmentStatus });
													}
												}}
												className="rounded-md border border-slate-gray/30 px-2 py-1 text-sm"
												aria-label="Update fee status"
											>
												<option value="">Update status…</option>
												<option value="WAIVED">Waive</option>
												<option value="CANCELLED">Cancel</option>
											</select>
										)}
										<button
											type="button"
											className="text-sm text-azure-blue hover:underline"
											onClick={() => setExpandedFeeId((id) => (id === fee.id ? null : fee.id))}
										>
											{expandedFeeId === fee.id ? "Hide details" : "Details"}
										</button>
									</div>
								</div>
								{expandedFeeId === fee.id && (
									<FeeDetailPanel organizationId={organizationId} householdId={householdId} fee={fee} canManage={canManage} />
								)}
							</li>
						);
					})}
				</ul>
			)}
		</section>
	);
}

// --- Main Page ---

const HOUSEHOLD_SECTIONS: Array<{ id: HouseholdSection; label: string }> = [
	{ id: "profile", label: "Household Profile" },
	{ id: "participants", label: "My Athletes" },
	{ id: "events", label: "Family Schedule" },
	{ id: "fees", label: "Fees & Payments" },
	{ id: "documents", label: "Documents" },
	{ id: "media", label: "Photos & Videos" },
	{ id: "corrections", label: "Correction Requests" },
];

function isHouseholdSection(value: string | undefined): value is HouseholdSection {
	return HOUSEHOLD_SECTIONS.some((section) => section.id === value);
}

export function HouseholdDetailPage() {
	const { organizationId, householdId, section } = useParams<{ organizationId: string; householdId: string; section?: string }>();
	const contexts = useContexts();
	const { data: household, isLoading, isError, refetch } = useHousehold(organizationId ?? "", householdId ?? "");

	if (!organizationId || !householdId) return <ErrorState message="Invalid URL." />;
	if (section && !isHouseholdSection(section)) return <Navigate to={appPaths.household(organizationId, householdId, "profile")} replace />;
	if (isLoading) return <LoadingState label="Loading household…" />;
	if (isError || !household) return <ErrorState message="Could not load this household." onRetry={() => refetch()} />;

	const activeSection: HouseholdSection = section && isHouseholdSection(section) ? section : "profile";
	const canAdminister = hasCapability(contexts.data, Capabilities.ORG_MANAGE, { contextType: "ORGANIZATION", resourceId: organizationId });
	const canManageProfilePhotos = canAdminister || hasCapability(
		contexts.data,
		Capabilities.HOUSEHOLD_PROFILE_MANAGE,
		{ contextType: "HOUSEHOLD", resourceId: householdId },
	);

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={canAdminister ? appPaths.organization(organizationId, "households") : appPaths.dashboard()} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← {canAdminister ? "Back to households" : "Back to dashboard"}
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">{household.displayName}</h1>
				{household.contactEmail && <p className="text-slate-gray dark:text-[#cbd5e1]">{household.contactEmail}</p>}
				{household.contactPhone && <p className="text-slate-gray dark:text-[#cbd5e1]">{household.contactPhone}</p>}
			</div>

			<nav aria-label="Household sections" className="flex gap-2 overflow-x-auto border-b border-slate-gray/20 pb-3">
				{HOUSEHOLD_SECTIONS.map((item) => (
					<Link key={item.id} to={appPaths.household(organizationId, householdId, item.id)} aria-current={activeSection === item.id ? "page" : undefined} className={`shrink-0 rounded-lg px-3 py-2 text-sm font-medium ${activeSection === item.id ? "bg-navy text-white" : "text-slate-gray dark:text-[#cbd5e1] hover:bg-ice-white hover:dark:bg-[#0f172a] hover:text-navy hover:dark:text-[#f8fafc]"}`}>
						{item.label}
					</Link>
				))}
			</nav>

			{activeSection === "profile" && <AdultsPanel organizationId={organizationId} householdId={householdId} canManage={canAdminister} canManagePhotos={canManageProfilePhotos} />}
			{activeSection === "participants" && <ParticipantsPanel organizationId={organizationId} householdId={householdId} canManage={canAdminister} canManagePhotos={canManageProfilePhotos} />}
			{activeSection === "events" && <EventListPanel scope={{ type: "household", organizationId, householdId }} householdId={householdId} />}
			{activeSection === "fees" && <FeeAssignmentsPanel organizationId={organizationId} householdId={householdId} canManage={canAdminister} />}
			{activeSection === "corrections" && (
				<section aria-label="Correction Requests" className="flex flex-col gap-3">
					<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Correction Requests</h2>
					<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Track requested profile changes and the organization's review decision.</p>
					<HouseholdCorrectionRequestsPanel organizationId={organizationId} householdId={householdId} />
				</section>
			)}
			{activeSection === "documents" && (
				<section aria-label="Documents" className="flex flex-col gap-3">
					<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Documents</h2>
					<HouseholdDocumentsPanel organizationId={organizationId} householdId={householdId} canManage={canAdminister} />
				</section>
			)}
			{activeSection === "media" && (
				<section aria-label="Photos & Videos" className="flex flex-col gap-3">
					<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Photos & Videos</h2>
					<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Upload photos and short video clips for your household. You can release any item publicly to allow the organization to use it for social sharing or highlight content.</p>
					<HouseholdMediaPanel organizationId={organizationId} householdId={householdId} />
				</section>
			)}
		</div>
	);
}
