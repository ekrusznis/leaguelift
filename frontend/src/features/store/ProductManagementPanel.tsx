import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { fileSchemaFor } from "../media/schema";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import {
	useAssignProductDesign,
	useCreateProduct,
	useCreateProductVariant,
	usePrintifyBlueprints,
	usePrintifyCatalogVariants,
	usePrintifyPrintProviders,
	useProducts,
	useUpdateProductStatus,
	useVariants,
} from "./api";
import { createProductSchema, createProductVariantSchema } from "./schema";
import { OrderList } from "./OrderList";

export function ProductManagementPanel({ organizationId, storeId }: { organizationId: string; storeId: string }) {
	const { data, isLoading, isError, refetch } = useProducts(organizationId, storeId);
	const { data: blueprints } = usePrintifyBlueprints(organizationId);
	const createProduct = useCreateProduct(organizationId, storeId);
	const updateStatus = useUpdateProductStatus(organizationId, storeId);
	const [showForm, setShowForm] = useState(false);
	const [expandedProductId, setExpandedProductId] = useState<string | null>(null);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createProductSchema>,
		unknown,
		z.output<typeof createProductSchema>
	>({
		resolver: zodResolver(createProductSchema),
		defaultValues: { name: "", description: "", printifyBlueprintId: 0, printifyPrintPosition: "front" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createProduct.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<h3 className="font-heading text-base font-semibold text-navy">Products</h3>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add product"}
				</Button>
			</div>

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="product-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="product-name"
								type="text"
								placeholder="e.g. Team Hoodie"
								{...register("name")}
								aria-invalid={!!errors.name}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="product-blueprint" className="text-sm font-medium text-navy">
								Product type <span aria-hidden>*</span>
							</label>
							<select
								id="product-blueprint"
								{...register("printifyBlueprintId")}
								aria-invalid={!!errors.printifyBlueprintId}
								className="min-h-11 min-w-48 rounded-md border border-slate-gray/30 px-3 py-2"
							>
								<option value={0}>Select a product type…</option>
								{blueprints?.map((blueprint) => (
									<option key={blueprint.id} value={blueprint.id}>
										{blueprint.title}
									</option>
								))}
							</select>
							{errors.printifyBlueprintId && <p role="alert" className="text-sm text-error-red">{errors.printifyBlueprintId.message}</p>}
						</div>
						<div className="flex min-w-[16rem] flex-1 flex-col gap-1">
							<label htmlFor="product-desc" className="text-sm font-medium text-navy">
								Description
							</label>
							<input
								id="product-desc"
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
							{isSubmitting ? "Creating…" : "Create product"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading products…" />}
			{isError && <ErrorState message="Could not load products." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No products yet" description="Add a product to start selling apparel from this store." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Products">
					{data.items.map((product) => (
						<li key={product.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex items-center justify-between">
								<div>
									<p className="font-medium text-navy">
										{product.name}
										<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">
											{product.status}
										</span>
										{!product.hasDesign && (
											<span className="ml-2 rounded-full bg-gold-500/10 px-2 py-0.5 text-xs font-medium text-gold-600">
												No design yet
											</span>
										)}
									</p>
									{product.description && <p className="text-sm text-slate-gray">{product.description}</p>}
								</div>
								<div className="flex gap-2">
									{product.status === "DRAFT" && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => updateStatus.mutate({ productId: product.id, status: "ACTIVE" })}
											disabled={updateStatus.isPending}
										>
											Activate
										</Button>
									)}
									<Button
										type="button"
										variant="secondary"
										onClick={() => setExpandedProductId((current) => (current === product.id ? null : product.id))}
									>
										{expandedProductId === product.id ? "Hide details" : "Manage"}
									</Button>
								</div>
							</div>
							{expandedProductId === product.id && (
								<div className="mt-4 flex flex-col gap-4 border-t border-slate-gray/20 pt-4">
									<ProductDesignUpload organizationId={organizationId} storeId={storeId} productId={product.id} />
									<ProductVariantManagement organizationId={organizationId} productId={product.id} printifyBlueprintId={product.printifyBlueprintId} />
								</div>
							)}
						</li>
					))}
				</ul>
			)}

			<div className="mt-2 border-t border-slate-gray/20 pt-4">
				<h3 className="mb-2 font-heading text-base font-semibold text-navy">Orders</h3>
				<OrderList organizationId={organizationId} storeId={storeId} />
			</div>
		</div>
	);
}

function ProductDesignUpload({ organizationId, storeId, productId }: { organizationId: string; storeId: string; productId: string }) {
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const assignDesign = useAssignProductDesign(organizationId, storeId);
	const [state, setState] = useState<{ status: "idle" | "uploading" | "error"; error?: string }>({ status: "idle" });

	async function handleFileSelected(file: File | undefined) {
		if (!file) return;
		const validation = fileSchemaFor("PRODUCT_DESIGN").safeParse(file);
		if (!validation.success) {
			setState({ status: "error", error: validation.error.issues[0]?.message ?? "This file cannot be used." });
			return;
		}
		setState({ status: "uploading" });
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot: "PRODUCT_DESIGN",
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setState({ status: "error", error: confirmed.rejectionReason ?? "This file could not be used." });
				return;
			}
			await assignDesign.mutateAsync({ productId, assetId: requested.assetId });
			setState({ status: "idle" });
		} catch {
			setState({ status: "error", error: "Upload failed. Please try again." });
		}
	}

	return (
		<div className="flex flex-col gap-1">
			<label htmlFor={`design-upload-${productId}`} className="text-sm font-medium text-navy">
				Design image
			</label>
			<input
				id={`design-upload-${productId}`}
				type="file"
				accept="image/png,image/jpeg,image/webp,image/svg+xml"
				onChange={(event) => handleFileSelected(event.target.files?.[0])}
				disabled={state.status === "uploading"}
				className="text-sm"
			/>
			{state.status === "uploading" && <p className="text-sm text-slate-gray">Uploading…</p>}
			{state.status === "error" && <p role="alert" className="text-sm text-error-red">{state.error}</p>}
		</div>
	);
}

function ProductVariantManagement({
	organizationId,
	productId,
	printifyBlueprintId,
}: {
	organizationId: string;
	productId: string;
	printifyBlueprintId: number;
}) {
	const { data: variants } = useVariants(organizationId, productId);
	const { data: providers } = usePrintifyPrintProviders(organizationId, printifyBlueprintId);
	const [printProviderId, setPrintProviderId] = useState<number | null>(null);
	const { data: catalogVariants } = usePrintifyCatalogVariants(organizationId, printifyBlueprintId, printProviderId);
	const createVariant = useCreateProductVariant(organizationId, productId);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createProductVariantSchema>,
		unknown,
		z.output<typeof createProductVariantSchema>
	>({
		resolver: zodResolver(createProductVariantSchema),
		defaultValues: { printifyPrintProviderId: 0, printifyVariantId: 0, label: "", priceMinor: 0 },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createVariant.mutateAsync(values);
		reset();
	});

	return (
		<div className="flex flex-col gap-3">
			<h4 className="text-sm font-semibold text-navy">Variants</h4>
			{variants && variants.length > 0 && (
				<ul className="flex flex-col gap-1" aria-label="Product variants">
					{variants.map((variant) => (
						<li key={variant.id} className="flex items-center justify-between rounded-md bg-ice-white px-3 py-2 text-sm">
							<span>{variant.label}</span>
							<span className="text-slate-gray">
								{formatMoneyMinorUnits(variant.priceMinor, variant.currency)} (cost {formatMoneyMinorUnits(variant.costMinor, variant.currency)})
							</span>
						</li>
					))}
				</ul>
			)}
			<form onSubmit={onSubmit} className="flex flex-wrap items-end gap-3" noValidate aria-label="Add a variant">
				<div className="flex flex-col gap-1">
					<label htmlFor={`provider-${productId}`} className="text-sm font-medium text-navy">
						US print provider
					</label>
					<select
						id={`provider-${productId}`}
						{...register("printifyPrintProviderId")}
						onChange={(event) => setPrintProviderId(Number(event.target.value) || null)}
						className="min-h-11 min-w-48 rounded-md border border-slate-gray/30 px-3 py-2"
					>
						<option value={0}>Select a provider…</option>
						{providers?.map((provider) => (
							<option key={provider.id} value={provider.id}>
								{provider.title} ({provider.location.city ?? provider.location.region ?? "US"})
							</option>
						))}
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`variant-${productId}`} className="text-sm font-medium text-navy">
						Size / color
					</label>
					<select
						id={`variant-${productId}`}
						{...register("printifyVariantId")}
						className="min-h-11 min-w-40 rounded-md border border-slate-gray/30 px-3 py-2"
					>
						<option value={0}>Select…</option>
						{catalogVariants?.map((catalogVariant) => (
							<option key={catalogVariant.id} value={catalogVariant.id}>
								{catalogVariant.title}
							</option>
						))}
					</select>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`label-${productId}`} className="text-sm font-medium text-navy">
						Label
					</label>
					<input
						id={`label-${productId}`}
						type="text"
						placeholder="e.g. M / Navy"
						{...register("label")}
						className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`price-${productId}`} className="text-sm font-medium text-navy">
						Price (cents)
					</label>
					<input
						id={`price-${productId}`}
						type="number"
						min={0}
						step={1}
						{...register("priceMinor")}
						className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</div>
				<Button type="submit" disabled={isSubmitting}>
					{isSubmitting ? "Adding…" : "Add variant"}
				</Button>
			</form>
			{(errors.printifyPrintProviderId || errors.printifyVariantId || errors.label || errors.priceMinor) && (
				<p role="alert" className="text-sm text-error-red">
					Choose a provider, size/color, label, and price.
				</p>
			)}
		</div>
	);
}
