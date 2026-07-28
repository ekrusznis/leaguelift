import { useState } from "react";
import { Link } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCreateHousehold, useHouseholds } from "./api";
import { createHouseholdSchema, type CreateHouseholdFormValues } from "./schema";

export function HouseholdList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useHouseholds(organizationId);
	const createHousehold = useCreateHousehold(organizationId);
	const [showForm, setShowForm] = useState(false);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateHouseholdFormValues>({
		resolver: zodResolver(createHouseholdSchema),
		defaultValues: { displayName: "", contactEmail: "", contactPhone: "", notes: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createHousehold.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${data.totalElements} household${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add household"}
				</Button>
			</div>

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4"
					noValidate
					aria-label="Create a household"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="household-name" className="text-sm font-medium text-navy">
								Display name <span aria-hidden>*</span>
							</label>
							<input
								id="household-name"
								type="text"
								placeholder="e.g. Smith Family"
								{...register("displayName")}
								aria-invalid={!!errors.displayName}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.displayName && (
								<p role="alert" className="text-sm text-error-red">
									{errors.displayName.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="household-email" className="text-sm font-medium text-navy">
								Contact email
							</label>
							<input
								id="household-email"
								type="email"
								{...register("contactEmail")}
								aria-invalid={!!errors.contactEmail}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.contactEmail && (
								<p role="alert" className="text-sm text-error-red">
									{errors.contactEmail.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="household-phone" className="text-sm font-medium text-navy">
								Contact phone
							</label>
							<input
								id="household-phone"
								type="tel"
								{...register("contactPhone")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create household"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading households…" />}
			{isError && <ErrorState message="Could not load households." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No households yet" description="Add your first household to get started." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Households">
					{data.items.map((household) => (
						<li
							key={household.id}
							className="flex items-center justify-between rounded-lg border border-slate-gray/20 bg-pure-white p-3"
						>
							<div>
								<p className="font-medium text-navy">{household.displayName}</p>
								{household.contactEmail && (
									<p className="text-sm text-slate-gray">{household.contactEmail}</p>
								)}
							</div>
							<Link
								to={`/app/organizations/${organizationId}/households/${household.id}`}
								className="rounded-md border border-slate-gray/30 px-3 py-1.5 text-sm font-medium text-navy hover:bg-ice-white"
							>
								View
							</Link>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
