import { useState } from "react";
import { Button } from "../../components/Button";
import { ApiError } from "../../lib/apiError";
import { useCreateProfileCorrection } from "./api";
import { PROFILE_CORRECTION_FIELD_LABELS } from "./labels";
import type { ProfileCorrectionField, ProfileCorrectionTargetType } from "./types";

interface Props {
	organizationId: string;
	householdId: string;
	targetType: ProfileCorrectionTargetType;
	targetId: string;
	targetLabel: string;
	fields: ProfileCorrectionField[];
	onDone: () => void;
}

function inputType(field: ProfileCorrectionField) {
	if (field === "ADULT_EMAIL") return "email";
	if (field === "ADULT_PHONE") return "tel";
	if (field === "PARTICIPANT_DATE_OF_BIRTH") return "date";
	return "text";
}

export function ProfileCorrectionForm({
	organizationId,
	householdId,
	targetType,
	targetId,
	targetLabel,
	fields,
	onDone,
}: Props) {
	const create = useCreateProfileCorrection(organizationId, householdId);
	const [field, setField] = useState<ProfileCorrectionField>(fields[0]!);
	const [proposedValue, setProposedValue] = useState("");
	const [reason, setReason] = useState("");

	const errorMessage = create.error instanceof ApiError ? create.error.message : create.isError ? "Could not submit the correction request." : null;

	return (
		<form
			className="mt-3 flex flex-col gap-3 rounded-lg border border-info-blue/30 bg-info-blue/5 p-4"
			onSubmit={(event) => {
				event.preventDefault();
				create.mutate(
					{ targetType, targetId, field, proposedValue, reason },
					{ onSuccess: onDone },
				);
			}}
			aria-label={`Request a correction for ${targetLabel}`}
		>
			<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
				Request an organization-reviewed change. The current profile remains unchanged until an owner or administrator approves it.
			</p>
			<div className="grid gap-3 sm:grid-cols-2">
				<div className="flex flex-col gap-1">
					<label htmlFor={`correction-field-${targetId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Field</label>
					<select
						id={`correction-field-${targetId}`}
						value={field}
						onChange={(event) => { setField(event.target.value as ProfileCorrectionField); setProposedValue(""); }}
						className="min-h-11 rounded-md border border-slate-gray/30 bg-white dark:bg-[#111827] px-3 py-2"
					>
						{fields.map((item) => <option key={item} value={item}>{PROFILE_CORRECTION_FIELD_LABELS[item]}</option>)}
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`correction-value-${targetId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Requested value</label>
					<input
						id={`correction-value-${targetId}`}
						type={inputType(field)}
						value={proposedValue}
						onChange={(event) => setProposedValue(event.target.value)}
						required
						maxLength={500}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</div>
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor={`correction-reason-${targetId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Why is this correction needed?</label>
				<textarea
					id={`correction-reason-${targetId}`}
					value={reason}
					onChange={(event) => setReason(event.target.value)}
					required
					minLength={5}
					maxLength={500}
					rows={3}
					className="rounded-md border border-slate-gray/30 px-3 py-2"
				/>
			</div>
			{errorMessage && <p role="alert" className="text-sm text-error-red">{errorMessage}</p>}
			<div className="flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={onDone}>Cancel</Button>
				<Button type="submit" disabled={create.isPending || !proposedValue.trim() || reason.trim().length < 5}>
					{create.isPending ? "Submitting…" : "Submit request"}
				</Button>
			</div>
		</form>
	);
}
