import { useState, type ReactElement, type ReactNode } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { fileSchemaFor } from "../media/schema";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import {
	useAssignProductDesign,
	useAssignSwagLogo,
	useCreateManualProductVariant,
	useCreateProduct,
	useCreateProductVariant,
	useDeleteProduct,
	useManualVendors,
	usePrintifyBlueprints,
	usePrintifyCatalogVariants,
	usePrintifyPrintProviders,
	useProducts,
	useUpdateManualProductVariant,
	useUpdateProductStatus,
	useUpdateProductVariantStatus,
	useVariants,
} from "./api";
import { createProductSchema, createProductVariantSchema, manualProductVariantSchema } from "./schema";
import type { Product, ProductVariant } from "./types";
import { OrderList } from "./OrderList";

export function ProductManagementPanel({ organizationId, storeId, teamId = null }: { organizationId: string; storeId: string; teamId?: string | null }) {
	const products = useProducts(organizationId, storeId);
	const vendors = useManualVendors(organizationId);
	const createProduct = useCreateProduct(organizationId, storeId);
	const updateStatus = useUpdateProductStatus(organizationId, storeId);
	const deleteProduct = useDeleteProduct(organizationId, storeId);
	const [showForm, setShowForm] = useState(false);
	const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
	const [mutationError, setMutationError] = useState<string | null>(null);

	const {
		register,
		handleSubmit,
		reset,
		watch,
		formState: { errors, isSubmitting },
	} = useForm<z.input<typeof createProductSchema>, unknown, z.output<typeof createProductSchema>>({
		resolver: zodResolver(createProductSchema),
		defaultValues: { name: "", description: "", catalogSource: "PRINTIFY", manualVendorId: "", printifyBlueprintId: 0, printifyPrintPosition: "front" },
	});
	const source = watch("catalogSource");
	const blueprints = usePrintifyBlueprints(organizationId, source === "PRINTIFY");

	const onSubmit = handleSubmit(async (values) => {
		setMutationError(null);
		try {
			await createProduct.mutateAsync(values);
			reset();
			setShowForm(false);
		} catch (error) {
			setMutationError(error instanceof ApiError ? error.message : "Could not create the product.");
		}
	});

	async function activate(productId: string) {
		setMutationError(null);
		try {
			await updateStatus.mutateAsync({ productId, status: "ACTIVE" });
		} catch (error) {
			setMutationError(error instanceof ApiError ? error.message : "Could not activate the product.");
		}
	}

	async function remove(productId: string, name: string) {
		if (!window.confirm(`Delete the draft product "${name}"? This can't be undone.`)) return;
		setMutationError(null);
		try {
			await deleteProduct.mutateAsync(productId);
			if (expandedProductId === productId) setExpandedProductId(null);
		} catch (error) {
			setMutationError(error instanceof ApiError ? error.message : "Could not delete the product.");
		}
	}

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<h3 className="font-heading text-base font-semibold text-navy">Products</h3>
				<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>{showForm ? "Cancel" : "Add product"}</Button>
			</div>
			{mutationError && <p role="alert" className="text-sm text-error-red">{mutationError}</p>}

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate>
					<div className="grid gap-3 md:grid-cols-2">
						<FormField label="Catalog source" id="product-source" error={errors.catalogSource?.message}>
							<select id="product-source" {...register("catalogSource")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
								<option value="PRINTIFY">Printify</option>
								<option value="MANUAL">Manual / local vendor</option>
							</select>
						</FormField>
						<FormField label="Name" id="product-name" error={errors.name?.message} required>
							<input id="product-name" {...register("name")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</FormField>
						{source === "PRINTIFY" ? (
							<FormField label="Printify product type" id="product-blueprint" error={errors.printifyBlueprintId?.message} required>
								<select id="product-blueprint" {...register("printifyBlueprintId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
									<option value={0}>Select a product type…</option>
									{blueprints.data?.map((blueprint) => <option key={blueprint.id} value={blueprint.id}>{blueprint.title}</option>)}
								</select>
							</FormField>
						) : (
							<FormField label="Manual vendor" id="product-vendor" error={errors.manualVendorId?.message}>
								<select id="product-vendor" {...register("manualVendorId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
									<option value="">In-house / no vendor</option>
									{vendors.data?.map((vendor) => <option key={vendor.id} value={vendor.id}>{vendor.name}</option>)}
								</select>
							</FormField>
						)}
						<FormField label="Description" id="product-description" error={errors.description?.message}>
							<textarea id="product-description" rows={3} {...register("description")} className="rounded-md border border-slate-gray/30 px-3 py-2" />
						</FormField>
					</div>
					<p className="text-xs text-slate-gray">Manual products keep provider IDs empty and use the cost you enter on each variant. Order items snapshot that cost at checkout.</p>
					<div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setShowForm(false)}>Cancel</Button><Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create product"}</Button></div>
				</form>
			)}

			{products.isLoading && <LoadingState label="Loading products…" />}
			{products.isError && <ErrorState message="Could not load products." onRetry={() => products.refetch()} />}
			{products.data && products.data.items.length === 0 && !showForm && <EmptyState title="No products yet" description="Add a Printify or manually fulfilled product to this store." />}
			{products.data && products.data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Products">
					{products.data.items.map((product) => (
						<li key={product.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy">{product.name} <Badge>{product.catalogSource === "MANUAL" ? "Manual" : "Printify"}</Badge> <Badge>{product.status}</Badge>{!product.hasDesign && <Badge warning>No image</Badge>}</p>
									<p className="text-sm text-slate-gray">{product.manualVendorName ?? product.description ?? "No description"}</p>
								</div>
								<div className="flex shrink-0 flex-wrap gap-2">
									{product.status === "DRAFT" && <Button type="button" variant="secondary" onClick={() => activate(product.id)} disabled={updateStatus.isPending}>Activate</Button>}
									{product.status === "DRAFT" && <Button type="button" variant="secondary" onClick={() => remove(product.id, product.name)} disabled={deleteProduct.isPending}>Delete</Button>}
									<Button type="button" variant="secondary" onClick={() => setExpandedProductId((current) => current === product.id ? null : product.id)}>{expandedProductId === product.id ? "Hide details" : "Manage"}</Button>
								</div>
							</div>
							{expandedProductId === product.id && <ProductDetails organizationId={organizationId} storeId={storeId} teamId={teamId} product={product} />}
						</li>
					))}
				</ul>
			)}

			<div className="mt-2 border-t border-slate-gray/20 pt-4"><h3 className="mb-2 font-heading text-base font-semibold text-navy">Orders and fulfillment</h3><OrderList organizationId={organizationId} storeId={storeId} /></div>
		</div>
	);
}

function ProductDetails({ organizationId, storeId, teamId, product }: { organizationId: string; storeId: string; teamId: string | null; product: Product }) {
	return (
		<div className="mt-4 flex flex-col gap-4 border-t border-slate-gray/20 pt-4">
			<ProductDesignUpload organizationId={organizationId} storeId={storeId} productId={product.id} />
			{teamId && product.catalogSource === "PRINTIFY" && <SwagLogoAssignment organizationId={organizationId} storeId={storeId} productId={product.id} hasSwagLogo={product.hasSwagLogo} />}
			{product.catalogSource === "PRINTIFY" && product.printifyBlueprintId != null
				? <PrintifyVariantManagement organizationId={organizationId} productId={product.id} printifyBlueprintId={product.printifyBlueprintId} />
				: <ManualVariantManagement organizationId={organizationId} productId={product.id} />}
		</div>
	);
}

/** Swag Shop (DESIGN-DOC.md section 13): freezes the team's current logo onto this product so coaches/guardians can order personalized items — see ProductService.useTeamLogo. */
function SwagLogoAssignment({ organizationId, storeId, productId, hasSwagLogo }: { organizationId: string; storeId: string; productId: string; hasSwagLogo: boolean }) {
	const assignLogo = useAssignSwagLogo(organizationId, storeId);
	const [error, setError] = useState<string | null>(null);
	async function onClick() {
		setError(null);
		try {
			await assignLogo.mutateAsync(productId);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Could not use the team logo. Upload a team logo (PNG/JPEG) on the Team page first.");
		}
	}
	return (
		<div className="flex flex-col gap-1">
			<div className="flex items-center gap-2">
				<Button type="button" variant="secondary" onClick={onClick} disabled={assignLogo.isPending}>
					{assignLogo.isPending ? "Enabling…" : hasSwagLogo ? "Refresh team logo for Swag Shop" : "Use team logo for Swag Shop personalization"}
				</Button>
				{hasSwagLogo && <Badge>Swag Shop ready</Badge>}
			</div>
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
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
		if (!validation.success) { setState({ status: "error", error: validation.error.issues[0]?.message ?? "This file cannot be used." }); return; }
		setState({ status: "uploading" });
		try {
			const requested = await requestUpload.mutateAsync({ usageSlot: "PRODUCT_DESIGN", fileName: file.name, contentType: file.type, fileSizeBytes: file.size });
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") { setState({ status: "error", error: confirmed.rejectionReason ?? "This file could not be used." }); return; }
			await assignDesign.mutateAsync({ productId, assetId: requested.assetId });
			setState({ status: "idle" });
		} catch { setState({ status: "error", error: "Upload failed. Please try again." }); }
	}
	return <div className="flex flex-col gap-1"><label htmlFor={`design-upload-${productId}`} className="text-sm font-medium text-navy">Product image</label><input id={`design-upload-${productId}`} type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" onChange={(event) => handleFileSelected(event.target.files?.[0])} disabled={state.status === "uploading"} className="text-sm" />{state.status === "uploading" && <p className="text-sm text-slate-gray">Uploading…</p>}{state.status === "error" && <p role="alert" className="text-sm text-error-red">{state.error}</p>}</div>;
}

function PrintifyVariantManagement({ organizationId, productId, printifyBlueprintId }: { organizationId: string; productId: string; printifyBlueprintId: number }) {
	const variants = useVariants(organizationId, productId);
	const providers = usePrintifyPrintProviders(organizationId, printifyBlueprintId);
	const [providerId, setProviderId] = useState<number | null>(null);
	const catalogVariants = usePrintifyCatalogVariants(organizationId, printifyBlueprintId, providerId);
	const createVariant = useCreateProductVariant(organizationId, productId);
	const updateStatus = useUpdateProductVariantStatus(organizationId, productId);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<z.input<typeof createProductVariantSchema>, unknown, z.output<typeof createProductVariantSchema>>({ resolver: zodResolver(createProductVariantSchema), defaultValues: { printifyPrintProviderId: 0, printifyVariantId: 0, label: "", priceMinor: undefined } });
	const onSubmit = handleSubmit(async (values) => { await createVariant.mutateAsync(values); reset(); });
	return (
		<div className="flex flex-col gap-3"><h4 className="text-sm font-semibold text-navy">Printify variants</h4><VariantList variants={variants.data ?? []} onToggle={(variant) => updateStatus.mutate({ variantId: variant.id, isActive: !variant.isActive })} />
			<form onSubmit={onSubmit} className="grid items-end gap-3 md:grid-cols-4" noValidate>
				<FormField label="US print provider" id={`provider-${productId}`}><select id={`provider-${productId}`} {...register("printifyPrintProviderId")} onChange={(event) => setProviderId(Number(event.target.value) || null)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"><option value={0}>Select…</option>{providers.data?.map((provider) => <option key={provider.id} value={provider.id}>{provider.title}</option>)}</select></FormField>
				<FormField label="Size / color" id={`variant-${productId}`}><select id={`variant-${productId}`} {...register("printifyVariantId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"><option value={0}>Select…</option>{catalogVariants.data?.map((variant) => <option key={variant.id} value={variant.id}>{variant.title}</option>)}</select></FormField>
				<FormField label="Label" id={`label-${productId}`}><input id={`label-${productId}`} {...register("label")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<FormField label="Price (cents)" id={`price-${productId}`}><input id={`price-${productId}`} type="number" min={0} placeholder="Auto from markup rule" {...register("priceMinor")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<div className="md:col-span-4"><Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Adding…" : "Add Printify variant"}</Button>{Object.keys(errors).length > 0 && <p role="alert" className="mt-1 text-sm text-error-red">Complete all variant fields.</p>}</div>
			</form>
		</div>
	);
}

function ManualVariantManagement({ organizationId, productId }: { organizationId: string; productId: string }) {
	const variants = useVariants(organizationId, productId);
	const createVariant = useCreateManualProductVariant(organizationId, productId);
	const updateVariant = useUpdateManualProductVariant(organizationId, productId);
	const updateStatus = useUpdateProductVariantStatus(organizationId, productId);
	const [editing, setEditing] = useState<ProductVariant | null>(null);
	const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<z.input<typeof manualProductVariantSchema>, unknown, z.output<typeof manualProductVariantSchema>>({ resolver: zodResolver(manualProductVariantSchema), defaultValues: { label: "", sku: "", size: "", color: "", currency: "USD", costMinor: 0, priceMinor: 0, isActive: true } });
	function edit(variant: ProductVariant) { setEditing(variant); reset({ label: variant.label, sku: variant.sku ?? "", size: variant.size ?? "", color: variant.color ?? "", currency: variant.currency, costMinor: variant.costMinor, priceMinor: variant.priceMinor, isActive: variant.isActive }); }
	const onSubmit = handleSubmit(async (values) => { if (editing) await updateVariant.mutateAsync({ variantId: editing.id, values }); else await createVariant.mutateAsync(values); setEditing(null); reset({ label: "", sku: "", size: "", color: "", currency: "USD", costMinor: 0, priceMinor: 0, isActive: true }); });
	return (
		<div className="flex flex-col gap-3"><h4 className="text-sm font-semibold text-navy">Manual variants</h4>
			<VariantList variants={variants.data ?? []} onEdit={edit} onToggle={(variant) => updateStatus.mutate({ variantId: variant.id, isActive: !variant.isActive })} />
			<form onSubmit={onSubmit} className="grid items-end gap-3 md:grid-cols-4" noValidate>
				<FormField label="Label" id={`manual-label-${productId}`} error={errors.label?.message}><input id={`manual-label-${productId}`} {...register("label")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<FormField label="SKU" id={`manual-sku-${productId}`} error={errors.sku?.message}><input id={`manual-sku-${productId}`} {...register("sku")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<FormField label="Size" id={`manual-size-${productId}`} error={errors.size?.message}><input id={`manual-size-${productId}`} {...register("size")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<FormField label="Color" id={`manual-color-${productId}`} error={errors.color?.message}><input id={`manual-color-${productId}`} {...register("color")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<FormField label="Currency" id={`manual-currency-${productId}`} error={errors.currency?.message}><input id={`manual-currency-${productId}`} maxLength={3} {...register("currency")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2 uppercase" /></FormField>
				<FormField label="Vendor cost (cents)" id={`manual-cost-${productId}`} error={errors.costMinor?.message}><input id={`manual-cost-${productId}`} type="number" min={0} {...register("costMinor")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<FormField label="Sale price (cents)" id={`manual-price-${productId}`} error={errors.priceMinor?.message}><input id={`manual-price-${productId}`} type="number" min={0} {...register("priceMinor")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
				<div className="flex gap-2"><Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : editing ? "Save variant" : "Add manual variant"}</Button>{editing && <Button type="button" variant="secondary" onClick={() => { setEditing(null); reset(); }}>Cancel edit</Button>}</div>
			</form>
		</div>
	);
}

function VariantList({ variants, onEdit, onToggle }: { variants: ProductVariant[]; onEdit?: (variant: ProductVariant) => void; onToggle: (variant: ProductVariant) => void }) {
	if (variants.length === 0) return <p className="text-sm text-slate-gray">No variants yet.</p>;
	return (
		<ul className="flex flex-col gap-1" aria-label="Product variants">
			{variants.map((variant) => (
				<li key={variant.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-ice-white px-3 py-2 text-sm">
					{(variant.mockupFrontUrl || variant.mockupBackUrl) && (
						<span className="flex shrink-0 gap-1">
							{variant.mockupFrontUrl && <img src={variant.mockupFrontUrl} alt={`${variant.label} — front`} className="h-14 w-14 rounded-md border border-slate-gray/20 object-cover" />}
							{variant.mockupBackUrl && <img src={variant.mockupBackUrl} alt={`${variant.label} — back`} className="h-14 w-14 rounded-md border border-slate-gray/20 object-cover" />}
						</span>
					)}
					<span className="min-w-0 flex-1 break-words">{variant.label}{variant.sku && <span className="ml-2 text-xs text-slate-gray">SKU {variant.sku}</span>}</span>
					<span className="text-slate-gray">{formatMoneyMinorUnits(variant.priceMinor, variant.currency)} · cost {formatMoneyMinorUnits(variant.costMinor, variant.currency)}</span>
					<Badge>{variant.isActive ? "Active" : "Inactive"}</Badge>
					{onEdit && <Button type="button" variant="secondary" onClick={() => onEdit(variant)}>Edit</Button>}
					<Button type="button" variant="secondary" onClick={() => onToggle(variant)}>{variant.isActive ? "Deactivate" : "Activate"}</Button>
				</li>
			))}
		</ul>
	);
}

function Badge({ children, warning = false }: { children: ReactNode; warning?: boolean }) { return <span className={`ml-2 inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${warning ? "bg-championship-gold/20 text-navy" : "bg-ice-white text-slate-gray"}`}>{children}</span>; }
function FormField({ label, id, error, required = false, children }: { label: string; id: string; error?: string; required?: boolean; children: ReactElement }) { return <div className="flex flex-col gap-1"><label htmlFor={id} className="text-sm font-medium text-navy">{label}{required && <span aria-hidden> *</span>}</label>{children}{error && <p id={`${id}-error`} role="alert" className="text-sm text-error-red">{error}</p>}</div>; }
