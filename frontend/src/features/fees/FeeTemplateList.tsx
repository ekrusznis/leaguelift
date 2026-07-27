import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useArchiveFeeTemplate, useCreateFeeTemplate, useFeeTemplates } from "./api";
import { createFeeTemplateSchema, type CreateFeeTemplateFormValues } from "./schema";

function formatAmount(amountMinor: number, currency: string) {
	return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100);
}

export function FeeTemplateList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useFeeTemplates(organizationId);
	const createTemplate = useCreateFeeTemplate(organizationId);
	const archiveTemplate = useArchiveFeeTemplate(organizationId);
	const [showForm, setShowForm] = useState(false);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateFeeTemplateFormValues>({
		resolver: zodResolver(createFeeTemplateSchema),
		defaultValues: { name: "", description: "", amountMinor: 0, currency: "USD" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createTemplate.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${data.totalElements} template${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add template"}
				</Button>
			</div>

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4"
					noValidate
					aria-label="Create a fee template"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="tpl-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="tpl-name"
								type="text"
								placeholder="e.g. Spring Registration"
								{...register("name")}
								aria-invalid={!!errors.name}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tpl-amount" className="text-sm font-medium text-navy">
								Amount (cents) <span aria-hidden>*</span>
							</label>
							<input
								id="tpl-amount"
								type="number"
								min={0}
								step={1}
								placeholder="e.g. 15000 = $150.00"
								{...register("amountMinor")}
								aria-invalid={!!errors.amountMinor}
								className="min-h-11 w-40 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.amountMinor && <p role="alert" className="text-sm text-error-red">{errors.amountMinor.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tpl-desc" className="text-sm font-medium text-navy">
								Description
							</label>
							<input
								id="tpl-desc"
								type="text"
								{...register("description")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create template"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading fee templates…" />}
			{isError && <ErrorState message="Could not load fee templates." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No fee templates yet" description="Create reusable fee types to quickly charge households." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Fee templates">
					{data.items.map((tpl) => (
						<li
							key={tpl.id}
							className="flex items-center justify-between rounded-lg border border-slate-gray/20 bg-pure-white p-3"
						>
							<div>
								<p className="font-medium text-navy">{tpl.name}</p>
								<p className="text-sm text-slate-gray">
									{formatAmount(tpl.amountMinor, tpl.currency)}
									{tpl.description ? ` · ${tpl.description}` : ""}
								</p>
							</div>
							<Button
								type="button"
								variant="secondary"
								onClick={() => archiveTemplate.mutate(tpl.id)}
								disabled={archiveTemplate.isPending}
							>
								Archive
							</Button>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
