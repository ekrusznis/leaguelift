import { useState } from "react";
import { Link } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCreateStore, useStores, useUpdateStoreStatus } from "./api";
import { createStoreSchema, type CreateStoreFormValues } from "./schema";
import { ManualVendorPanel } from "./ManualVendorPanel";
import { ProductManagementPanel } from "./ProductManagementPanel";

export function StoreList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useStores(organizationId);
	const createStore = useCreateStore(organizationId);
	const updateStatus = useUpdateStoreStatus(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedStoreId, setExpandedStoreId] = useState<string | null>(null);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateStoreFormValues>({
		resolver: zodResolver(createStoreSchema),
		defaultValues: { teamId: "", name: "", slug: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createStore.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<ManualVendorPanel organizationId={organizationId} />
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${data.totalElements} Swag Shop${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add Swag Shop"}
				</Button>
			</div>

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4"
					noValidate
					aria-label="Create a Swag Shop"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="store-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="store-name"
								type="text"
								placeholder="e.g. Team Swag Shop"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "store-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p id="store-name-error" role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="store-slug" className="text-sm font-medium text-navy">
								Public URL slug <span aria-hidden>*</span>
							</label>
							<input
								id="store-slug"
								type="text"
								placeholder="team-swag-shop"
								{...register("slug")}
								aria-invalid={!!errors.slug}
								aria-describedby={errors.slug ? "store-slug-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.slug && <p id="store-slug-error" role="alert" className="text-sm text-error-red">{errors.slug.message}</p>}
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create Swag Shop"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading Swag Shops…" />}
			{isError && <ErrorState message="Could not load Swag Shops." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No Swag Shops yet" description="Create a Swag Shop to sell apparel for your organization or a team." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Swag Shops">
					{data.items.map((store) => (
						<li key={store.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy">
										{store.name}
										<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">
											{store.status}
										</span>
									</p>
									<p className="text-sm text-slate-gray">/{store.slug}</p>
								</div>
								<div className="flex shrink-0 flex-wrap gap-2">
									{store.status === "ACTIVE" && (
										<Link to={`/swag-shop/${store.slug}`} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 bg-pure-white px-4 py-2 text-sm font-medium text-navy hover:bg-ice-white">
											View Swag Shop
										</Link>
									)}
									{store.status === "DRAFT" && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => updateStatus.mutate({ storeId: store.id, status: "ACTIVE" })}
											disabled={updateStatus.isPending}
										>
											Activate
										</Button>
									)}
									<Button
										type="button"
										variant="secondary"
										onClick={() => setExpandedStoreId((current) => (current === store.id ? null : store.id))}
									>
										{expandedStoreId === store.id ? "Hide products" : "Manage products"}
									</Button>
								</div>
							</div>
							{expandedStoreId === store.id && (
								<div className="mt-4 border-t border-slate-gray/20 pt-4">
									<ProductManagementPanel organizationId={organizationId} storeId={store.id} teamId={store.teamId} />
								</div>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
