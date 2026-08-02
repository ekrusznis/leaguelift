import { useEffect, useState, type ReactNode } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import {
	useCreateFulfillmentReprint,
	useFulfillmentHistory,
	useFulfillmentReprints,
	useManualVendors,
	useOrderFulfillment,
	useUpdateFulfillment,
	useUpdateFulfillmentReprint,
} from "./api";
import type { Fulfillment, FulfillmentReprint, FulfillmentReprintStatus, FulfillmentStatus } from "./types";

const FULFILLMENT_STATUSES: FulfillmentStatus[] = [
	"NOT_SUBMITTED",
	"DRAFT_CREATED",
	"FAILED",
	"READY",
	"IN_PRODUCTION",
	"SHIPPED",
	"DELIVERED",
	"NEEDS_ATTENTION",
	"CANCELED",
];

const REPRINT_STATUSES: FulfillmentReprintStatus[] = ["REQUESTED", "IN_PRODUCTION", "SHIPPED", "DELIVERED", "CANCELED"];

interface FulfillmentFormState {
	status: FulfillmentStatus;
	manualVendorId: string;
	vendorOrderReference: string;
	carrier: string;
	trackingNumber: string;
	trackingUrl: string;
	internalNotes: string;
	attentionReason: string;
	note: string;
}

function stateFromFulfillment(fulfillment: Fulfillment): FulfillmentFormState {
	return {
		status: fulfillment.status,
		manualVendorId: fulfillment.manualVendorId ?? "",
		vendorOrderReference: fulfillment.vendorOrderReference ?? "",
		carrier: fulfillment.carrier ?? "",
		trackingNumber: fulfillment.trackingNumber ?? "",
		trackingUrl: fulfillment.trackingUrl ?? "",
		internalNotes: fulfillment.internalNotes ?? "",
		attentionReason: fulfillment.attentionReason ?? "",
		note: "",
	};
}

export function FulfillmentOperationsPanel({
	organizationId,
	storeId,
	orderId,
}: {
	organizationId: string;
	storeId: string;
	orderId: string;
}) {
	const fulfillment = useOrderFulfillment(organizationId, orderId);
	const history = useFulfillmentHistory(organizationId, orderId);
	const reprints = useFulfillmentReprints(organizationId, orderId);
	const vendors = useManualVendors(organizationId);
	const updateFulfillment = useUpdateFulfillment(organizationId, storeId, orderId);
	const createReprint = useCreateFulfillmentReprint(organizationId, orderId);
	const [form, setForm] = useState<FulfillmentFormState | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [success, setSuccess] = useState<string | null>(null);
	const [reprintReason, setReprintReason] = useState("");
	const [reprintReference, setReprintReference] = useState("");
	const [reprintNotes, setReprintNotes] = useState("");

	useEffect(() => {
		if (fulfillment.data) setForm(stateFromFulfillment(fulfillment.data));
	}, [fulfillment.data]);

	if (fulfillment.isLoading) return <LoadingState label="Loading fulfillment operations…" />;
	if (fulfillment.isError) return <ErrorState message="Could not load fulfillment details." onRetry={() => fulfillment.refetch()} />;
	if (!fulfillment.data || !form) {
		return <EmptyState title="No fulfillment record" description="A fulfillment record is created when payment confirmation is received." />;
	}

	async function saveFulfillment() {
		if (!form) return;
		const currentForm = form;
		setError(null);
		setSuccess(null);
		try {
			const updated = await updateFulfillment.mutateAsync({
				status: currentForm.status,
				manualVendorId: currentForm.manualVendorId || null,
				vendorOrderReference: currentForm.vendorOrderReference.trim() || null,
				carrier: currentForm.carrier.trim() || null,
				trackingNumber: currentForm.trackingNumber.trim() || null,
				trackingUrl: currentForm.trackingUrl.trim() || null,
				internalNotes: currentForm.internalNotes.trim() || null,
				attentionReason: currentForm.attentionReason.trim() || null,
				note: currentForm.note.trim(),
			});
			setForm(stateFromFulfillment(updated));
			setSuccess("Fulfillment updated.");
		} catch (cause) {
			setError(cause instanceof ApiError ? cause.message : "Could not update fulfillment.");
		}
	}

	async function requestReprint() {
		setError(null);
		setSuccess(null);
		try {
			await createReprint.mutateAsync({
				reason: reprintReason.trim(),
				vendorOrderReference: reprintReference.trim(),
				internalNotes: reprintNotes.trim(),
			});
			setReprintReason("");
			setReprintReference("");
			setReprintNotes("");
			setSuccess("Replacement or reprint request recorded.");
		} catch (cause) {
			setError(cause instanceof ApiError ? cause.message : "Could not create the replacement request.");
		}
	}

	return (
		<div className="mt-4 flex flex-col gap-5 border-t border-slate-gray/20 pt-4">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<div>
					<h4 className="font-heading text-base font-semibold text-navy">Fulfillment operations</h4>
					<p className="text-sm text-slate-gray">
						{fulfillment.data.source === "PRINTIFY" ? "Printify-origin fulfillment" : "Manual fulfillment"}
						{fulfillment.data.manualVendorName ? ` · ${fulfillment.data.manualVendorName}` : ""}
					</p>
				</div>
				<StatusBadge status={fulfillment.data.status} />
			</div>

			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
			{success && <p role="status" className="text-sm text-green-700">{success}</p>}

			<div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
				<Field label="Operational status" id={`fulfillment-status-${orderId}`}>
					<select
						id={`fulfillment-status-${orderId}`}
						value={form.status}
						onChange={(event) => setForm({ ...form, status: event.target.value as FulfillmentStatus })}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					>
						{FULFILLMENT_STATUSES.map((status) => <option key={status} value={status}>{formatStatus(status)}</option>)}
					</select>
				</Field>
				<Field label="Manual vendor" id={`fulfillment-vendor-${orderId}`}>
					<select
						id={`fulfillment-vendor-${orderId}`}
						value={form.manualVendorId}
						onChange={(event) => setForm({ ...form, manualVendorId: event.target.value })}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					>
						<option value="">No manual vendor</option>
						{vendors.data?.map((vendor) => <option key={vendor.id} value={vendor.id}>{vendor.name}</option>)}
					</select>
				</Field>
				<Field label="Vendor order reference" id={`fulfillment-reference-${orderId}`}>
					<input id={`fulfillment-reference-${orderId}`} value={form.vendorOrderReference} onChange={(event) => setForm({ ...form, vendorOrderReference: event.target.value })} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Carrier" id={`fulfillment-carrier-${orderId}`}>
					<input id={`fulfillment-carrier-${orderId}`} value={form.carrier} onChange={(event) => setForm({ ...form, carrier: event.target.value })} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Tracking number" id={`fulfillment-tracking-${orderId}`}>
					<input id={`fulfillment-tracking-${orderId}`} value={form.trackingNumber} onChange={(event) => setForm({ ...form, trackingNumber: event.target.value })} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Tracking URL" id={`fulfillment-tracking-url-${orderId}`}>
					<input id={`fulfillment-tracking-url-${orderId}`} type="url" value={form.trackingUrl} onChange={(event) => setForm({ ...form, trackingUrl: event.target.value })} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Attention reason" id={`fulfillment-attention-${orderId}`}>
					<input id={`fulfillment-attention-${orderId}`} value={form.attentionReason} onChange={(event) => setForm({ ...form, attentionReason: event.target.value })} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" aria-describedby={`fulfillment-attention-help-${orderId}`} />
					<p id={`fulfillment-attention-help-${orderId}`} className="text-xs text-slate-gray">Required when the status is Needs attention.</p>
				</Field>
				<Field label="Internal notes" id={`fulfillment-notes-${orderId}`}>
					<textarea id={`fulfillment-notes-${orderId}`} rows={3} value={form.internalNotes} onChange={(event) => setForm({ ...form, internalNotes: event.target.value })} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Change note" id={`fulfillment-change-note-${orderId}`}>
					<textarea id={`fulfillment-change-note-${orderId}`} rows={3} value={form.note} onChange={(event) => setForm({ ...form, note: event.target.value })} className="rounded-md border border-slate-gray/30 px-3 py-2" aria-describedby={`fulfillment-change-help-${orderId}`} />
					<p id={`fulfillment-change-help-${orderId}`} className="text-xs text-slate-gray">Explain the operational update. This is preserved in history.</p>
				</Field>
			</div>
			<div className="flex justify-end">
				<Button type="button" onClick={saveFulfillment} disabled={updateFulfillment.isPending || form.note.trim().length < 3}>
					{updateFulfillment.isPending ? "Saving…" : "Save fulfillment update"}
				</Button>
			</div>

			<section aria-labelledby={`fulfillment-history-heading-${orderId}`}>
				<h5 id={`fulfillment-history-heading-${orderId}`} className="text-sm font-semibold text-navy">Status history</h5>
				{history.isLoading && <LoadingState label="Loading fulfillment history…" />}
				{history.isError && <ErrorState message="Could not load fulfillment history." onRetry={() => history.refetch()} />}
				{history.data && history.data.length === 0 && <p className="mt-2 text-sm text-slate-gray">No history has been recorded.</p>}
				{history.data && history.data.length > 0 && (
					<ol className="mt-2 flex flex-col gap-2">
						{history.data.map((entry) => (
							<li key={entry.id} className="rounded-md bg-ice-white p-3 text-sm">
								<div className="flex flex-wrap items-center justify-between gap-2">
									<span className="font-medium text-navy">{entry.previousStatus ? `${formatStatus(entry.previousStatus)} → ` : ""}{formatStatus(entry.newStatus)}</span>
									<time className="text-xs text-slate-gray" dateTime={entry.createdAt}>{formatTimestamp(entry.createdAt)}</time>
								</div>
								<p className="mt-1 text-slate-gray">{entry.note}</p>
							</li>
						))}
					</ol>
				)}
			</section>

			<section aria-labelledby={`reprint-heading-${orderId}`} className="rounded-lg border border-slate-gray/20 p-4">
				<h5 id={`reprint-heading-${orderId}`} className="text-sm font-semibold text-navy">Replacements and reprints</h5>
				<p className="mt-1 text-sm text-slate-gray">Create a durable replacement record without changing the original order or ledger.</p>
				<div className="mt-3 grid gap-3 md:grid-cols-3">
					<Field label="Reason" id={`reprint-reason-${orderId}`}>
						<input id={`reprint-reason-${orderId}`} value={reprintReason} onChange={(event) => setReprintReason(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="Vendor reference" id={`reprint-reference-${orderId}`}>
						<input id={`reprint-reference-${orderId}`} value={reprintReference} onChange={(event) => setReprintReference(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="Internal notes" id={`reprint-notes-${orderId}`}>
						<input id={`reprint-notes-${orderId}`} value={reprintNotes} onChange={(event) => setReprintNotes(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
				</div>
				<div className="mt-3 flex justify-end">
					<Button type="button" variant="secondary" onClick={requestReprint} disabled={createReprint.isPending || reprintReason.trim().length < 3}>
						{createReprint.isPending ? "Recording…" : "Record replacement request"}
					</Button>
				</div>
				{reprints.isLoading && <LoadingState label="Loading replacements…" />}
				{reprints.isError && <ErrorState message="Could not load replacement records." onRetry={() => reprints.refetch()} />}
				{reprints.data && reprints.data.length === 0 && <p className="mt-3 text-sm text-slate-gray">No replacements or reprints have been recorded.</p>}
				{reprints.data && reprints.data.length > 0 && (
					<ul className="mt-3 flex flex-col gap-3" aria-label="Replacement and reprint records">
						{reprints.data.map((reprint) => <ReprintEditor key={reprint.id} organizationId={organizationId} orderId={orderId} reprint={reprint} />)}
					</ul>
				)}
			</section>
		</div>
	);
}

function ReprintEditor({ organizationId, orderId, reprint }: { organizationId: string; orderId: string; reprint: FulfillmentReprint }) {
	const update = useUpdateFulfillmentReprint(organizationId, orderId);
	const [status, setStatus] = useState(reprint.status);
	const [vendorOrderReference, setVendorOrderReference] = useState(reprint.vendorOrderReference ?? "");
	const [carrier, setCarrier] = useState(reprint.carrier ?? "");
	const [trackingNumber, setTrackingNumber] = useState(reprint.trackingNumber ?? "");
	const [trackingUrl, setTrackingUrl] = useState(reprint.trackingUrl ?? "");
	const [internalNotes, setInternalNotes] = useState(reprint.internalNotes ?? "");
	const [error, setError] = useState<string | null>(null);

	async function save() {
		setError(null);
		try {
			await update.mutateAsync({
				reprintId: reprint.id,
				values: { status, vendorOrderReference, carrier, trackingNumber, trackingUrl, internalNotes },
			});
		} catch (cause) {
			setError(cause instanceof ApiError ? cause.message : "Could not update this replacement record.");
		}
	}

	return (
		<li className="rounded-md bg-ice-white p-3">
			<div className="flex flex-wrap items-start justify-between gap-2">
				<div>
					<p className="font-medium text-navy">{reprint.reason}</p>
					<p className="text-xs text-slate-gray">Requested {formatTimestamp(reprint.createdAt)}</p>
				</div>
				<StatusBadge status={status} />
			</div>
			{error && <p role="alert" className="mt-2 text-sm text-error-red">{error}</p>}
			<div className="mt-3 grid gap-3 md:grid-cols-3">
				<Field label="Status" id={`reprint-status-${reprint.id}`}>
					<select id={`reprint-status-${reprint.id}`} value={status} onChange={(event) => setStatus(event.target.value as FulfillmentReprintStatus)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						{REPRINT_STATUSES.map((value) => <option key={value} value={value}>{formatStatus(value)}</option>)}
					</select>
				</Field>
				<Field label="Vendor reference" id={`reprint-vendor-${reprint.id}`}>
					<input id={`reprint-vendor-${reprint.id}`} value={vendorOrderReference} onChange={(event) => setVendorOrderReference(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Carrier" id={`reprint-carrier-${reprint.id}`}>
					<input id={`reprint-carrier-${reprint.id}`} value={carrier} onChange={(event) => setCarrier(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Tracking number" id={`reprint-tracking-${reprint.id}`}>
					<input id={`reprint-tracking-${reprint.id}`} value={trackingNumber} onChange={(event) => setTrackingNumber(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Tracking URL" id={`reprint-url-${reprint.id}`}>
					<input id={`reprint-url-${reprint.id}`} type="url" value={trackingUrl} onChange={(event) => setTrackingUrl(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
				<Field label="Internal notes" id={`reprint-internal-${reprint.id}`}>
					<input id={`reprint-internal-${reprint.id}`} value={internalNotes} onChange={(event) => setInternalNotes(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</Field>
			</div>
			<div className="mt-3 flex justify-end"><Button type="button" variant="secondary" onClick={save} disabled={update.isPending}>{update.isPending ? "Saving…" : "Save replacement"}</Button></div>
		</li>
	);
}

function Field({ label, id, children }: { label: string; id: string; children: ReactNode }) {
	return <div className="flex flex-col gap-1"><label htmlFor={id} className="text-sm font-medium text-navy">{label}</label>{children}</div>;
}

function StatusBadge({ status }: { status: FulfillmentStatus | FulfillmentReprintStatus }) {
	const critical = status === "FAILED" || status === "NEEDS_ATTENTION";
	const positive = status === "SHIPPED" || status === "DELIVERED";
	return (
		<span className={`shrink-0 rounded-full px-2 py-1 text-xs font-medium ${critical ? "bg-error-red/10 text-error-red" : positive ? "bg-victory-green/10 text-green-700" : "bg-slate-gray/10 text-slate-gray"}`}>
			{formatStatus(status)}
		</span>
	);
}

function formatStatus(status: string) {
	return status.toLowerCase().replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase());
}

function formatTimestamp(value: string) {
	return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
