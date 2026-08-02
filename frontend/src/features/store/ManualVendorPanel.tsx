import { useState, type ReactElement } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useArchiveManualVendor, useCreateManualVendor, useManualVendors, useUpdateManualVendor } from "./api";
import { manualVendorSchema, type ManualVendorFormValues } from "./schema";
import type { ManualVendor } from "./types";

const EMPTY_VALUES: ManualVendorFormValues = {
	name: "",
	contactName: "",
	contactEmail: "",
	phone: "",
	websiteUrl: "",
	notes: "",
};

export function ManualVendorPanel({ organizationId }: { organizationId: string }) {
	const vendors = useManualVendors(organizationId, true);
	const createVendor = useCreateManualVendor(organizationId);
	const updateVendor = useUpdateManualVendor(organizationId);
	const archiveVendor = useArchiveManualVendor(organizationId);
	const [editing, setEditing] = useState<ManualVendor | null>(null);
	const [showForm, setShowForm] = useState(false);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<ManualVendorFormValues>({ resolver: zodResolver(manualVendorSchema), defaultValues: EMPTY_VALUES });

	function beginCreate() {
		setEditing(null);
		reset(EMPTY_VALUES);
		setShowForm(true);
	}

	function beginEdit(vendor: ManualVendor) {
		setEditing(vendor);
		reset({
			name: vendor.name,
			contactName: vendor.contactName ?? "",
			contactEmail: vendor.contactEmail ?? "",
			phone: vendor.phone ?? "",
			websiteUrl: vendor.websiteUrl ?? "",
			notes: vendor.notes ?? "",
		});
		setShowForm(true);
	}

	const onSubmit = handleSubmit(async (values) => {
		if (editing) await updateVendor.mutateAsync({ vendorId: editing.id, values });
		else await createVendor.mutateAsync(values);
		setEditing(null);
		reset(EMPTY_VALUES);
		setShowForm(false);
	});

	return (
		<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5" aria-labelledby="manual-vendors-heading">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h3 id="manual-vendors-heading" className="font-heading text-lg font-semibold text-navy">Manual vendors</h3>
					<p className="mt-1 text-sm text-slate-gray">Record local printers, in-house fulfillment, and other non-Printify suppliers without creating fake provider IDs.</p>
				</div>
				<Button type="button" variant="secondary" onClick={showForm ? () => setShowForm(false) : beginCreate}>
					{showForm ? "Close" : "Add vendor"}
				</Button>
			</div>

			{showForm && (
				<form onSubmit={onSubmit} className="mt-4 grid gap-3 rounded-lg bg-ice-white p-4 sm:grid-cols-2" noValidate>
					<Field label="Vendor name" id="vendor-name" error={errors.name?.message} required>
						<input id="vendor-name" {...register("name")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="Contact name" id="vendor-contact" error={errors.contactName?.message}>
						<input id="vendor-contact" {...register("contactName")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="Contact email" id="vendor-email" error={errors.contactEmail?.message}>
						<input id="vendor-email" type="email" {...register("contactEmail")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="Phone" id="vendor-phone" error={errors.phone?.message}>
						<input id="vendor-phone" {...register("phone")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="HTTPS website" id="vendor-url" error={errors.websiteUrl?.message}>
						<input id="vendor-url" type="url" {...register("websiteUrl")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<Field label="Internal notes" id="vendor-notes" error={errors.notes?.message}>
						<textarea id="vendor-notes" rows={3} {...register("notes")} className="rounded-md border border-slate-gray/30 px-3 py-2" />
					</Field>
					<div className="flex justify-end gap-2 sm:col-span-2">
						<Button type="button" variant="secondary" onClick={() => setShowForm(false)}>Cancel</Button>
						<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : editing ? "Save vendor" : "Create vendor"}</Button>
					</div>
				</form>
			)}

			{vendors.isLoading && <LoadingState label="Loading manual vendors…" />}
			{vendors.isError && <ErrorState message="Could not load manual vendors." onRetry={() => vendors.refetch()} />}
			{vendors.data && vendors.data.length === 0 && !showForm && <EmptyState title="No manual vendors" description="Add a vendor when a product will be fulfilled outside Printify." />}
			{vendors.data && vendors.data.length > 0 && (
				<ul className="mt-4 grid gap-3 md:grid-cols-2" aria-label="Manual vendors">
					{vendors.data.map((vendor) => (
						<li key={vendor.id} className="rounded-lg border border-slate-gray/20 p-4">
							<div className="flex items-start justify-between gap-3">
								<div className="min-w-0">
									<p className="break-words font-medium text-navy">{vendor.name}</p>
									<p className="text-sm text-slate-gray">{vendor.contactName ?? vendor.contactEmail ?? "No contact recorded"}</p>
									<span className="mt-2 inline-flex rounded-full bg-ice-white px-2 py-1 text-xs font-medium text-slate-gray">{vendor.status}</span>
								</div>
								{vendor.status === "ACTIVE" && (
									<div className="flex shrink-0 flex-wrap gap-2">
										<Button type="button" variant="secondary" onClick={() => beginEdit(vendor)}>Edit</Button>
										<Button type="button" variant="danger" onClick={() => archiveVendor.mutate(vendor.id)} disabled={archiveVendor.isPending}>Archive</Button>
									</div>
								)}
							</div>
						</li>
					))}
				</ul>
			)}
		</section>
	);
}

function Field({ label, id, error, required = false, children }: { label: string; id: string; error?: string; required?: boolean; children: ReactElement }) {
	return (
		<div className="flex flex-col gap-1">
			<label htmlFor={id} className="text-sm font-medium text-navy">{label}{required && <span aria-hidden> *</span>}</label>
			{children}
			{error && <p id={`${id}-error`} role="alert" className="text-sm text-error-red">{error}</p>}
		</div>
	);
}
