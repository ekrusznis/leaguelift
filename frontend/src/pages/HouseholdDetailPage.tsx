import { useState } from "react";
import { Link, useParams } from "react-router-dom";
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
	useFeeAdjustments,
	useFeeAssignments,
	useFeePayments,
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
import type { FeeAssignment, FeeAssignmentStatus } from "../features/fees/types";
import { useFeeTemplates } from "../features/fees/api";
import { useTeams } from "../features/teams/api";

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
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Add an adult">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-first" className="text-sm font-medium text-navy">First name <span aria-hidden>*</span></label>
					<input id="adult-first" type="text" {...register("firstName")} aria-invalid={!!errors.firstName} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.firstName && <p role="alert" className="text-sm text-error-red">{errors.firstName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-last" className="text-sm font-medium text-navy">Last name <span aria-hidden>*</span></label>
					<input id="adult-last" type="text" {...register("lastName")} aria-invalid={!!errors.lastName} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.lastName && <p role="alert" className="text-sm text-error-red">{errors.lastName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-email" className="text-sm font-medium text-navy">Email</label>
					<input id="adult-email" type="email" {...register("email")} aria-invalid={!!errors.email} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.email && <p role="alert" className="text-sm text-error-red">{errors.email.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="adult-rel" className="text-sm font-medium text-navy">Relationship</label>
					<input id="adult-rel" type="text" placeholder="e.g. Parent" {...register("relationship")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				<div className="flex items-center gap-2 self-end pb-1">
					<input id="adult-primary" type="checkbox" {...register("isPrimary")} className="h-4 w-4" />
					<label htmlFor="adult-primary" className="text-sm font-medium text-navy">Primary contact</label>
				</div>
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={() => { reset(); onDone(); }}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Adding…" : "Add adult"}</Button>
			</div>
		</form>
	);
}

function AdultsPanel({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { data, isLoading, isError, refetch } = useAdults(organizationId, householdId);
	const removeAdult = useRemoveAdult(organizationId, householdId);
	const [showForm, setShowForm] = useState(false);

	return (
		<section aria-label="Adults" className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h2 className="font-heading text-lg font-semibold text-navy">Adults</h2>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add adult"}
				</Button>
			</div>
			{showForm && <AddAdultForm organizationId={organizationId} householdId={householdId} onDone={() => setShowForm(false)} />}
			{isLoading && <LoadingState label="Loading adults…" />}
			{isError && <ErrorState message="Could not load adults." onRetry={() => refetch()} />}
			{data && data.length === 0 && !showForm && (
				<EmptyState title="No adults on record" description="Add an adult to this household." />
			)}
			{data && data.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Adults list">
					{data.map((adult) => (
						<li key={adult.id} className="flex items-center justify-between rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div>
								<p className="font-medium text-navy">
									{adult.firstName} {adult.lastName}
									{adult.isPrimary && <span className="ml-2 rounded-full bg-navy/10 px-2 py-0.5 text-xs text-navy">Primary</span>}
								</p>
								<p className="text-sm text-slate-gray">
									{[adult.relationship, adult.email].filter(Boolean).join(" · ")}
								</p>
							</div>
							<Button type="button" variant="secondary" onClick={() => removeAdult.mutate(adult.id)} disabled={removeAdult.isPending}>
								Remove
							</Button>
						</li>
					))}
				</ul>
			)}
		</section>
	);
}

// --- Participants Panel ---

function ParticipantTeamRow({ organizationId, participant }: { organizationId: string; participant: Participant }) {
	const { data: teams } = useTeams(organizationId);
	const { data: assignments, isLoading } = useParticipantTeams(organizationId, participant.id);
	const assignTeam = useAssignTeam(organizationId, participant.id);
	const removeFromTeam = useRemoveFromTeam(organizationId, participant.id);
	const [selectedTeam, setSelectedTeam] = useState("");

	const assignedTeamIds = new Set(assignments?.map((a) => a.teamId) ?? []);
	const availableTeams = teams?.items.filter((t) => !assignedTeamIds.has(t.id)) ?? [];

	return (
		<div className="mt-2 pl-4 border-l border-slate-gray/20">
			{isLoading && <p className="text-sm text-slate-gray">Loading teams…</p>}
			{assignments && assignments.length > 0 && (
				<ul className="flex flex-wrap gap-2 mb-2">
					{assignments.map((a) => {
						const team = teams?.items.find((t) => t.id === a.teamId);
						return (
							<li key={a.id} className="flex items-center gap-1 rounded-full bg-navy/10 px-3 py-1 text-xs text-navy">
								{team?.name ?? a.teamId}
								<button
									type="button"
									onClick={() => removeFromTeam.mutate(a.teamId)}
									className="ml-1 text-slate-gray hover:text-error-red"
									aria-label={`Remove from ${team?.name ?? "team"}`}
								>
									×
								</button>
							</li>
						);
					})}
				</ul>
			)}
			{availableTeams.length > 0 && (
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
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Add a participant">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="par-first" className="text-sm font-medium text-navy">First name <span aria-hidden>*</span></label>
					<input id="par-first" type="text" {...register("firstName")} aria-invalid={!!errors.firstName} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.firstName && <p role="alert" className="text-sm text-error-red">{errors.firstName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="par-last" className="text-sm font-medium text-navy">Last name <span aria-hidden>*</span></label>
					<input id="par-last" type="text" {...register("lastName")} aria-invalid={!!errors.lastName} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.lastName && <p role="alert" className="text-sm text-error-red">{errors.lastName.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="par-dob" className="text-sm font-medium text-navy">Date of birth</label>
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

function ParticipantsPanel({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { data, isLoading, isError, refetch } = useParticipants(organizationId, householdId);
	const [showForm, setShowForm] = useState(false);
	const [expandedId, setExpandedId] = useState<string | null>(null);

	return (
		<section aria-label="Participants" className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h2 className="font-heading text-lg font-semibold text-navy">Participants</h2>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add participant"}
				</Button>
			</div>
			{showForm && <AddParticipantForm organizationId={organizationId} householdId={householdId} onDone={() => setShowForm(false)} />}
			{isLoading && <LoadingState label="Loading participants…" />}
			{isError && <ErrorState message="Could not load participants." onRetry={() => refetch()} />}
			{data && data.length === 0 && !showForm && (
				<EmptyState title="No participants yet" description="Add the athletes or players in this household." />
			)}
			{data && data.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Participants list">
					{data.map((participant) => (
						<li key={participant.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex items-center justify-between">
								<div>
									<p className="font-medium text-navy">
										{participant.firstName} {participant.lastName}
									</p>
									{participant.dateOfBirth && (
										<p className="text-sm text-slate-gray">Born {participant.dateOfBirth}</p>
									)}
								</div>
								<button
									type="button"
									className="text-sm text-azure-blue hover:underline"
									onClick={() => setExpandedId((id) => id === participant.id ? null : participant.id)}
								>
									{expandedId === participant.id ? "Hide teams" : "Teams"}
								</button>
							</div>
							{expandedId === participant.id && (
								<ParticipantTeamRow organizationId={organizationId} participant={participant} />
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
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-3" noValidate aria-label="Record a payment">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-amount-${assignmentId}`} className="text-sm font-medium text-navy">Amount (cents) <span aria-hidden>*</span></label>
					<input id={`payment-amount-${assignmentId}`} type="number" min={1} step={1} {...register("amountMinor")} aria-invalid={!!errors.amountMinor} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.amountMinor && <p role="alert" className="text-sm text-error-red">{errors.amountMinor.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-method-${assignmentId}`} className="text-sm font-medium text-navy">Method</label>
					<select id={`payment-method-${assignmentId}`} {...register("method")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="CASH">Cash</option>
						<option value="CHECK">Check</option>
						<option value="VENMO">Venmo</option>
						<option value="ZELLE">Zelle</option>
						<option value="OTHER">Other</option>
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-date-${assignmentId}`} className="text-sm font-medium text-navy">Date paid</label>
					<input id={`payment-date-${assignmentId}`} type="date" {...register("paidAt")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`payment-note-${assignmentId}`} className="text-sm font-medium text-navy">Note</label>
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
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-3" noValidate aria-label="Apply a discount or credit">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor={`adj-type-${assignmentId}`} className="text-sm font-medium text-navy">Type</label>
					<select id={`adj-type-${assignmentId}`} {...register("adjustmentType")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="DISCOUNT">Discount</option>
						<option value="CREDIT">Credit</option>
						<option value="CORRECTION">Correction</option>
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`adj-amount-${assignmentId}`} className="text-sm font-medium text-navy">Amount (cents) <span aria-hidden>*</span></label>
					<input id={`adj-amount-${assignmentId}`} type="number" min={1} step={1} {...register("amountMinor")} aria-invalid={!!errors.amountMinor} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.amountMinor && <p role="alert" className="text-sm text-error-red">{errors.amountMinor.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`adj-reason-${assignmentId}`} className="text-sm font-medium text-navy">Reason</label>
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

function FeeDetailPanel({ organizationId, householdId, fee }: { organizationId: string; householdId: string; fee: FeeAssignment }) {
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
					<dt className="text-slate-gray">Original</dt>
					<dd className="font-medium text-navy">{formatAmount(fee.originalAmountMinor, fee.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-gray">Paid</dt>
					<dd className="font-medium text-navy">{formatAmount(fee.paidMinor, fee.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-gray">Adjusted</dt>
					<dd className="font-medium text-navy">{formatAmount(fee.adjustedMinor, fee.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-gray">Balance</dt>
					<dd className="font-semibold text-navy">{formatAmount(fee.balanceMinor, fee.currency)}</dd>
				</div>
			</dl>

			{!locked && (
				<div className="flex gap-2">
					<Button type="button" variant="secondary" onClick={() => setActiveForm((f) => (f === "payment" ? null : "payment"))}>
						{activeForm === "payment" ? "Cancel" : "Record payment"}
					</Button>
					<Button type="button" variant="secondary" onClick={() => setActiveForm((f) => (f === "adjustment" ? null : "adjustment"))}>
						{activeForm === "adjustment" ? "Cancel" : "Apply discount/credit"}
					</Button>
				</div>
			)}
			{activeForm === "payment" && (
				<RecordPaymentForm organizationId={organizationId} householdId={householdId} assignmentId={fee.id} onDone={() => setActiveForm(null)} />
			)}
			{activeForm === "adjustment" && (
				<ApplyAdjustmentForm organizationId={organizationId} householdId={householdId} assignmentId={fee.id} onDone={() => setActiveForm(null)} />
			)}

			{paymentsLoading && <p className="text-sm text-slate-gray">Loading payment history…</p>}
			{payments && payments.length > 0 && (
				<div>
					<h3 className="text-sm font-semibold text-navy">Payments</h3>
					<ul className="flex flex-col gap-1">
						{payments.map((payment) => (
							<li key={payment.id} className="flex items-center justify-between text-sm">
								<span className={payment.voidedAt ? "text-slate-gray line-through" : "text-slate-gray"}>
									{formatAmount(payment.amountMinor, payment.currency)} · {payment.method} · {payment.paidAt}
									{payment.voidedAt ? ` · voided (${payment.voidReason})` : ""}
								</span>
								{!payment.voidedAt && (
									<button type="button" className="text-azure-blue hover:underline" onClick={() => handleVoidPayment(payment.id)}>
										Void
									</button>
								)}
							</li>
						))}
					</ul>
				</div>
			)}

			{adjustmentsLoading && <p className="text-sm text-slate-gray">Loading adjustment history…</p>}
			{adjustments && adjustments.length > 0 && (
				<div>
					<h3 className="text-sm font-semibold text-navy">Discounts &amp; Credits</h3>
					<ul className="flex flex-col gap-1">
						{adjustments.map((adjustment) => (
							<li key={adjustment.id} className="flex items-center justify-between text-sm">
								<span className={adjustment.voidedAt ? "text-slate-gray line-through" : "text-slate-gray"}>
									{formatAmount(adjustment.amountMinor, adjustment.currency)} · {adjustment.adjustmentType}
									{adjustment.reason ? ` · ${adjustment.reason}` : ""}
									{adjustment.voidedAt ? ` · voided (${adjustment.voidReason})` : ""}
								</span>
								{!adjustment.voidedAt && (
									<button type="button" className="text-azure-blue hover:underline" onClick={() => handleVoidAdjustment(adjustment.id)}>
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
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Assign a fee">
			<div className="flex flex-wrap gap-3">
				{templates && templates.items.length > 0 && (
					<div className="flex flex-col gap-1">
						<label htmlFor="fee-template" className="text-sm font-medium text-navy">Fee template</label>
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
					<label htmlFor="fee-desc" className="text-sm font-medium text-navy">Description <span aria-hidden>*</span></label>
					<input id="fee-desc" type="text" {...register("description")} aria-invalid={!!errors.description} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.description && <p role="alert" className="text-sm text-error-red">{errors.description.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="fee-amount" className="text-sm font-medium text-navy">Amount (cents) <span aria-hidden>*</span></label>
					<input id="fee-amount" type="number" min={0} step={1} {...register("originalAmountMinor")} aria-invalid={!!errors.originalAmountMinor} className="min-h-11 w-36 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.originalAmountMinor && <p role="alert" className="text-sm text-error-red">{errors.originalAmountMinor.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="fee-due" className="text-sm font-medium text-navy">Due date</label>
					<input id="fee-due" type="date" {...register("dueDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</div>
				{participants && participants.length > 0 && (
					<div className="flex flex-col gap-1">
						<label htmlFor="fee-participant" className="text-sm font-medium text-navy">Participant (optional)</label>
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

function FeeAssignmentsPanel({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { data, isLoading, isError, refetch } = useFeeAssignments(organizationId, householdId);
	const { data: participants } = useParticipants(organizationId, householdId);
	const updateStatus = useUpdateFeeAssignmentStatus(organizationId, householdId);
	const [showForm, setShowForm] = useState(false);
	const [expandedFeeId, setExpandedFeeId] = useState<string | null>(null);

	const totalBalanceMinor = data?.items.reduce((sum, fee) => sum + fee.balanceMinor, 0) ?? 0;
	const currency = data?.items[0]?.currency ?? "USD";

	return (
		<section aria-label="Fee assignments" className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h2 className="font-heading text-lg font-semibold text-navy">Fees</h2>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add fee"}
				</Button>
			</div>
			{data && data.items.length > 0 && (
				<p className="text-sm text-slate-gray">
					Household balance: <span className="font-semibold text-navy">{formatAmount(totalBalanceMinor, currency)}</span>
				</p>
			)}
			{showForm && (
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
							<li key={fee.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
								<div className="flex items-center justify-between">
									<div>
										<p className="font-medium text-navy">{fee.description}</p>
										<p className="text-sm text-slate-gray">
											{formatAmount(fee.balanceMinor, fee.currency)} due of {formatAmount(fee.originalAmountMinor, fee.currency)}
											{fee.dueDate ? ` · Due ${fee.dueDate}` : ""}
											{participant ? ` · ${participant.firstName} ${participant.lastName}` : ""}
										</p>
									</div>
									<div className="flex items-center gap-2">
										<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[fee.status]}`}>
											{STATUS_LABELS[fee.status]}
										</span>
										{fee.status !== "PAID" && fee.status !== "WAIVED" && fee.status !== "CANCELLED" && (
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
									<FeeDetailPanel organizationId={organizationId} householdId={householdId} fee={fee} />
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

export function HouseholdDetailPage() {
	const { organizationId, householdId } = useParams<{ organizationId: string; householdId: string }>();
	const { data: household, isLoading, isError, refetch } = useHousehold(organizationId ?? "", householdId ?? "");

	if (!organizationId || !householdId) {
		return <ErrorState message="Invalid URL." />;
	}
	if (isLoading) {
		return <LoadingState label="Loading household…" />;
	}
	if (isError || !household) {
		return <ErrorState message="Could not load this household." onRetry={() => refetch()} />;
	}

	return (
		<div className="flex flex-col gap-8">
			<div>
				<Link
					to={`/app/organizations/${organizationId}`}
					className="mb-2 inline-block text-sm text-azure-blue hover:underline"
				>
					← Back to organization
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy">{household.displayName}</h1>
				{household.contactEmail && (
					<p className="text-slate-gray">{household.contactEmail}</p>
				)}
				{household.contactPhone && (
					<p className="text-slate-gray">{household.contactPhone}</p>
				)}
			</div>

			<AdultsPanel organizationId={organizationId} householdId={householdId} />
			<ParticipantsPanel organizationId={organizationId} householdId={householdId} />
			<FeeAssignmentsPanel organizationId={organizationId} householdId={householdId} />
		</div>
	);
}
