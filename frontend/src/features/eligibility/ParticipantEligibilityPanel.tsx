import { useRef, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ReminderButton } from "../communications/ReminderButton";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import { useParticipantEligibilityRequirements, useSubmitGuardianEvidence } from "./api";
import { guardianLegalNameSchema, type GuardianLegalNameFormValues } from "./schema";
import type { ParticipantRequirement } from "./types";

const MAX_DOCUMENT_BYTES = 15 * 1024 * 1024;

const STATUS_LABELS: Record<string, string> = {
	ACTIVE: "Submitted",
	SUPERSEDED: "Replaced by a newer submission",
	REVOKED: "Revoked",
	REJECTED: "Rejected — please resubmit",
	EXPIRED: "Expired — please resubmit",
};

function AcknowledgmentForm({ organizationId, participantId, requirementId, onDone }: { organizationId: string; participantId: string; requirementId: string; onDone: () => void }) {
	const submitEvidence = useSubmitGuardianEvidence(organizationId, participantId);
	const [submitting, setSubmitting] = useState(false);

	async function handleAcknowledge() {
		setSubmitting(true);
		try {
			await submitEvidence.mutateAsync({ requirementId, acceptanceMethod: "ACKNOWLEDGMENT_CHECKBOX" });
			onDone();
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<div className="mt-2 flex items-center gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-3">
			<p className="flex-1 text-sm text-slate-gray">By clicking below, you acknowledge you have read and agree to this requirement.</p>
			<Button type="button" onClick={handleAcknowledge} disabled={submitting}>{submitting ? "Submitting…" : "I acknowledge"}</Button>
		</div>
	);
}

function LegalNameSignForm({ organizationId, participantId, requirementId, onDone }: { organizationId: string; participantId: string; requirementId: string; onDone: () => void }) {
	const submitEvidence = useSubmitGuardianEvidence(organizationId, participantId);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<GuardianLegalNameFormValues>({
		resolver: zodResolver(guardianLegalNameSchema),
		defaultValues: { enteredLegalName: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await submitEvidence.mutateAsync({ requirementId, acceptanceMethod: "ESIGN_LEGAL_NAME", enteredLegalName: values.enteredLegalName });
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="mt-2 flex flex-wrap items-end gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-3" noValidate aria-label="Sign this requirement">
			<div className="flex flex-col gap-1">
				<label htmlFor={`sign-name-${requirementId}`} className="text-sm font-medium text-navy">Type your full legal name to sign <span aria-hidden>*</span></label>
				<input id={`sign-name-${requirementId}`} type="text" {...register("enteredLegalName")} aria-invalid={!!errors.enteredLegalName} aria-describedby={errors.enteredLegalName ? `sign-name-${requirementId}-error` : undefined} className="min-h-11 w-64 rounded-md border border-slate-gray/30 px-3 py-2" />
				{errors.enteredLegalName && <p id={`sign-name-${requirementId}-error`} role="alert" className="text-sm text-error-red">{errors.enteredLegalName.message}</p>}
			</div>
			<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Signing…" : "Sign"}</Button>
		</form>
	);
}

function DocumentEvidenceForm({ organizationId, participantId, requirementId, onDone }: { organizationId: string; participantId: string; requirementId: string; onDone: () => void }) {
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const submitEvidence = useSubmitGuardianEvidence(organizationId, participantId);
	const [file, setFile] = useState<File | null>(null);
	const [status, setStatus] = useState<"idle" | "uploading" | "error">("idle");
	const [error, setError] = useState<string | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);

	async function handleSubmit(event: React.FormEvent) {
		event.preventDefault();
		if (!file) return;
		if (file.type !== "application/pdf") {
			setStatus("error");
			setError("Only PDF files are supported.");
			return;
		}
		if (file.size > MAX_DOCUMENT_BYTES) {
			setStatus("error");
			setError("File is too large (15MB max).");
			return;
		}

		setStatus("uploading");
		setError(null);
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot: "DOCUMENT",
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
				entityType: "PARTICIPANT",
				entityId: participantId,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setStatus("error");
				setError(confirmed.rejectionReason ?? "This file could not be used.");
				return;
			}
			await submitEvidence.mutateAsync({ requirementId, acceptanceMethod: "FILE_UPLOAD", documentAssetId: requested.assetId });
			setStatus("idle");
			setFile(null);
			if (fileInputRef.current) fileInputRef.current.value = "";
			onDone();
		} catch {
			setStatus("error");
			setError("Upload failed. Please try again.");
		}
	}

	return (
		<form onSubmit={handleSubmit} className="mt-2 flex flex-wrap items-end gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-3" aria-label="Upload evidence">
			<div className="flex flex-col gap-1">
				<label htmlFor={`evidence-file-${requirementId}`} className="text-sm font-medium text-navy">PDF file</label>
				<input
					ref={fileInputRef}
					id={`evidence-file-${requirementId}`}
					type="file"
					accept="application/pdf"
					onChange={(event) => setFile(event.target.files?.[0] ?? null)}
					disabled={status === "uploading"}
					className="text-sm"
				/>
			</div>
			<Button type="submit" disabled={!file || status === "uploading"}>
				{status === "uploading" ? "Uploading…" : "Upload"}
			</Button>
			{error && <p role="alert" className="w-full text-sm text-error-red">{error}</p>}
		</form>
	);
}

function isRequirementSatisfied(item: ParticipantRequirement): boolean {
	const { evidence } = item;
	return evidence?.status === "ACTIVE" && (!evidence.expiresAt || new Date(evidence.expiresAt) > new Date());
}

function RequirementRow({ organizationId, participantId, item }: { organizationId: string; participantId: string; item: ParticipantRequirement }) {
	const [actionOpen, setActionOpen] = useState(false);
	const { requirement, evidence } = item;
	const isSatisfied = isRequirementSatisfied(item);

	return (
		<li className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div className="min-w-0 flex-1">
					<p className="break-words font-medium text-navy">{requirement.title}</p>
					<p className="whitespace-pre-wrap break-words text-sm text-slate-gray">{requirement.content}</p>
					{evidence && (
						<p className="mt-1 text-sm text-slate-gray">
							{STATUS_LABELS[evidence.status] ?? evidence.status}
							{evidence.acceptedAt ? ` · ${new Date(evidence.acceptedAt).toLocaleDateString()}` : ""}
							{evidence.expiresAt ? ` · Expires ${new Date(evidence.expiresAt).toLocaleDateString()}` : ""}
						</p>
					)}
				</div>
				<span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${isSatisfied ? "bg-victory-green/10 text-victory-green" : "bg-error-red/10 text-error-red"}`}>
					{isSatisfied ? "Complete" : "Action needed"}
				</span>
			</div>
			{!isSatisfied && requirement.mode === "STAFF_REVIEWED_EXTERNAL" && (
				<p className="mt-2 text-sm text-slate-gray">This requirement is verified by staff — no action needed here.</p>
			)}
			{!isSatisfied && requirement.mode !== "STAFF_REVIEWED_EXTERNAL" && !actionOpen && (
				<Button type="button" variant="secondary" className="mt-2" onClick={() => setActionOpen(true)}>
					{requirement.mode === "FILE_UPLOAD" ? "Upload document" : requirement.mode === "GUARDIAN_LEGAL_NAME_SIGNATURE" ? "Sign" : "Acknowledge"}
				</Button>
			)}
			{!isSatisfied && actionOpen && requirement.mode === "FILE_UPLOAD" && (
				<DocumentEvidenceForm organizationId={organizationId} participantId={participantId} requirementId={requirement.id} onDone={() => setActionOpen(false)} />
			)}
			{!isSatisfied && actionOpen && requirement.mode === "GUARDIAN_LEGAL_NAME_SIGNATURE" && (
				<LegalNameSignForm organizationId={organizationId} participantId={participantId} requirementId={requirement.id} onDone={() => setActionOpen(false)} />
			)}
			{!isSatisfied && actionOpen && (requirement.mode === "GUARDIAN_ESIGN_ACKNOWLEDGMENT" || requirement.mode === "INFORMATIONAL") && (
				<AcknowledgmentForm organizationId={organizationId} participantId={participantId} requirementId={requirement.id} onDone={() => setActionOpen(false)} />
			)}
		</li>
	);
}

export function ParticipantEligibilityPanel({
	organizationId,
	participantId,
	canManage = false,
}: {
	organizationId: string;
	participantId: string;
	canManage?: boolean;
}) {
	const { data, isLoading, isError, refetch } = useParticipantEligibilityRequirements(organizationId, participantId);

	if (isLoading) return <LoadingState label="Loading eligibility requirements…" />;
	if (isError) return <ErrorState message="Could not load eligibility requirements." onRetry={() => refetch()} />;
	if (!data || data.length === 0) {
		return <EmptyState title="No eligibility requirements apply" description="This athlete has no outstanding waivers or documents for their current teams." />;
	}

	const hasOutstanding = data.some((item) => !isRequirementSatisfied(item));

	return (
		<div className="flex flex-col gap-3">
			{canManage && hasOutstanding && (
				<ReminderButton organizationId={organizationId} resourceType="ELIGIBILITY" resourceId={participantId} label="Send eligibility reminder" />
			)}
			<ul className="flex flex-col gap-2" aria-label="Eligibility requirements">
				{data.map((item) => (
					<RequirementRow key={item.requirement.id} organizationId={organizationId} participantId={participantId} item={item} />
				))}
			</ul>
		</div>
	);
}
