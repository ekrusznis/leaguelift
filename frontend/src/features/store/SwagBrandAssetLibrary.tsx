import { useState } from "react";
import { Button } from "../../components/Button";
import { ApiError } from "../../lib/apiError";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { fileSchemaFor } from "../media/schema";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import {
	useArchiveSwagBrandAsset,
	useAssignProductBrandAsset,
	useCreateSwagBrandAsset,
	useRestoreSwagBrandAsset,
	useSwagBrandAssets,
	type ManagedProduct,
	type SwagBrandAssetCategory,
} from "./swagBrandAssetsApi";

const CATEGORIES: Array<{ value: SwagBrandAssetCategory; label: string }> = [
	{ value: "PRIMARY", label: "Primary" },
	{ value: "ALTERNATE", label: "Alternate" },
	{ value: "LIGHT", label: "Light-background" },
	{ value: "DARK", label: "Dark-background" },
	{ value: "WORDMARK", label: "Wordmark" },
	{ value: "MASCOT", label: "Mascot" },
	{ value: "SMALL_PRINT", label: "Small print" },
	{ value: "WIDE", label: "Wide" },
	{ value: "SEASONAL", label: "Seasonal" },
	{ value: "COMMEMORATIVE", label: "Commemorative" },
];

export function SwagBrandAssetLibrary({ organizationId, teamId }: { organizationId: string; teamId: string | null }) {
	const [showArchived, setShowArchived] = useState(false);
	const [name, setName] = useState("");
	const [category, setCategory] = useState<SwagBrandAssetCategory>("PRIMARY");
	const createTeamId = teamId;
	const [state, setState] = useState<{ status: "idle" | "uploading" | "error"; error?: string }>({ status: "idle" });
	const assets = useSwagBrandAssets(organizationId, teamId, showArchived);
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const createAsset = useCreateSwagBrandAsset(organizationId, createTeamId);
	const archiveAsset = useArchiveSwagBrandAsset(organizationId);
	const restoreAsset = useRestoreSwagBrandAsset(organizationId);

	async function upload(file: File | undefined) {
		if (!file) return;
		if (!name.trim()) { setState({ status: "error", error: "Name the brand asset before uploading." }); return; }
		const usageSlot = createTeamId ? "LOGO" : "PRODUCT_DESIGN";
		const validation = fileSchemaFor(usageSlot).safeParse(file);
		if (!validation.success) { setState({ status: "error", error: validation.error.issues[0]?.message ?? "This file cannot be used." }); return; }
		if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) { setState({ status: "error", error: "Use PNG, JPEG, or WebP. SVG is not supported for print yet." }); return; }
		setState({ status: "uploading" });
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot,
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
				...(createTeamId ? { entityType: "TEAM" as const, entityId: createTeamId } : {}),
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") { setState({ status: "error", error: confirmed.rejectionReason ?? "This file could not be used." }); return; }
			await createAsset.mutateAsync({ mediaAssetId: requested.assetId, name: name.trim(), category });
			setName("");
			setCategory("PRIMARY");
			setState({ status: "idle" });
		} catch (error) {
			setState({ status: "error", error: error instanceof ApiError ? error.message : "The brand asset could not be saved." });
		}
	}

	return (
		<section className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" aria-labelledby="swag-brand-assets-heading">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div><h3 id="swag-brand-assets-heading" className="font-heading text-base font-semibold text-navy">Swag Brand Asset Library</h3><p className="text-sm text-slate-gray">Reusable approved marks. Products keep a snapshot when an asset is selected.</p></div>
				<label className="flex items-center gap-2 text-sm text-navy"><input type="checkbox" checked={showArchived} onChange={(event) => setShowArchived(event.target.checked)} /> Show archived</label>
			</div>
			<p className="text-xs text-slate-gray">New uploads are saved to {teamId ? "this team" : "the organization"}. Team workspaces can also select active organization-wide assets.</p>
			<div className="grid items-end gap-3 md:grid-cols-3">
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">Asset name<input value={name} maxLength={120} onChange={(event) => setName(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">Asset type<select value={category} onChange={(event) => setCategory(event.target.value as SwagBrandAssetCategory)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">{CATEGORIES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">Upload PNG, JPEG, or WebP<input type="file" accept="image/png,image/jpeg,image/webp" disabled={state.status === "uploading"} onChange={(event) => upload(event.target.files?.[0])} className="text-sm" /></label>
			</div>
			{state.status === "uploading" && <p className="text-sm text-slate-gray">Uploading and validating…</p>}
			{state.status === "error" && <p role="alert" className="text-sm text-error-red">{state.error}</p>}
			{assets.isError && <p role="alert" className="text-sm text-error-red">Could not load the Swag Brand Asset Library.</p>}
			{assets.data && assets.data.length === 0 && <p className="text-sm text-slate-gray">No brand assets yet.</p>}
			{assets.data && assets.data.length > 0 && <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{assets.data.map((asset) => {
				const canChangeLifecycle = teamId === null || asset.teamId === teamId;
				return (
					<li key={asset.id} className="flex gap-3 rounded-md border border-slate-gray/20 bg-pure-white p-3">
						<img src={asset.previewUrl} alt="" className="h-16 w-16 rounded-md border border-slate-gray/20 object-contain" />
						<div className="min-w-0 flex-1"><p className="truncate font-medium text-navy">{asset.name}</p><p className="text-xs text-slate-gray">{asset.category.replaceAll("_", " ")} · {asset.teamId ? "Team" : "Organization"}</p><p className="text-xs text-slate-gray">{asset.status}</p></div>
						{canChangeLifecycle && (asset.status === "ACTIVE" ? <Button type="button" variant="secondary" onClick={() => archiveAsset.mutate(asset.id)} disabled={archiveAsset.isPending}>Archive</Button> : <Button type="button" variant="secondary" onClick={() => restoreAsset.mutate(asset.id)} disabled={restoreAsset.isPending}>Restore</Button>)}
					</li>
				);
			})}</ul>}
		</section>
	);
}

export function ProductBrandAssetAssignment({ organizationId, storeId, teamId, product }: { organizationId: string; storeId: string; teamId: string | null; product: ManagedProduct }) {
	const assets = useSwagBrandAssets(organizationId, teamId, false);
	const assign = useAssignProductBrandAsset(organizationId, storeId);
	const [selected, setSelected] = useState(product.swagBrandAssetId ?? "");
	const [error, setError] = useState<string | null>(null);
	async function save() {
		if (!selected) { setError("Choose a Swag Brand Asset."); return; }
		setError(null);
		try { await assign.mutateAsync({ productId: product.id, brandAssetId: selected }); }
		catch (err) { setError(err instanceof ApiError ? err.message : "Could not assign the Swag Brand Asset."); }
	}
	return (
		<div className="flex flex-col gap-2 rounded-md border border-slate-gray/20 bg-ice-white p-3">
			<label htmlFor={`brand-asset-${product.id}`} className="text-sm font-medium text-navy">Swag Brand Asset</label>
			<div className="flex flex-wrap items-center gap-2"><select id={`brand-asset-${product.id}`} value={selected} onChange={(event) => setSelected(event.target.value)} className="min-h-11 min-w-64 rounded-md border border-slate-gray/30 px-3 py-2"><option value="">Select an approved asset…</option>{assets.data?.filter((asset) => asset.status === "ACTIVE").map((asset) => <option key={asset.id} value={asset.id}>{asset.name} ({asset.category.replaceAll("_", " ")})</option>)}</select><Button type="button" variant="secondary" onClick={save} disabled={assign.isPending}>{assign.isPending ? "Saving…" : "Use asset"}</Button>{product.hasSwagBrandAsset && <span className="rounded-full bg-pure-white px-2 py-1 text-xs text-slate-gray">Snapshot assigned</span>}</div>
			<p className="text-xs text-slate-gray">Archiving the library source later will not change this product or any order history.</p>
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
		</div>
	);
}
