import { useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePlatformSwagShopProducts } from "./api";

export function PlatformSwagShopPage() {
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState("");
	const [page, setPage] = useState(0);
	const products = usePlatformSwagShopProducts({ query, status, page, size: 25 });

	if (products.isLoading) return <LoadingState label="Loading Swag Shop products…" />;
	if (products.isError || !products.data) {
		return <ErrorState message="Could not load Swag Shop products." onRetry={() => products.refetch()} />;
	}

	const totalPages = Math.max(1, Math.ceil(products.data.totalElements / products.data.size));

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900">Swag Shop</h1>
				<p className="mt-1 text-slate-500">
					Search draft and active apparel products across every organization to find where a customer is stuck. Open the
					organization's own Swag Shop section (starting a support session if needed) to change status or delete a broken draft.
				</p>
			</div>

			<form
				onSubmit={(event) => { event.preventDefault(); setPage(0); setQuery(queryInput); }}
				className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4"
			>
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900">
					Search
					<input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="Product, store, or organization name" className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900">
					Status
					<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 bg-white px-3 py-2 font-normal">
						<option value="">All statuses</option>
						<option value="DRAFT">Draft</option>
						<option value="ACTIVE">Active</option>
						<option value="ARCHIVED">Archived</option>
					</select>
				</label>
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">Search</button>
			</form>

			<div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
				<table className="w-full min-w-[980px] text-left text-sm">
					<thead className="border-b border-slate-200 bg-ice-50 text-slate-500">
						<tr>
							<th className="px-4 py-3 font-medium">Organization</th>
							<th className="px-4 py-3 font-medium">Team / Store</th>
							<th className="px-4 py-3 font-medium">Product</th>
							<th className="px-4 py-3 font-medium">Status</th>
							<th className="px-4 py-3 font-medium">Variants</th>
							<th className="px-4 py-3 font-medium">Logo ready</th>
							<th className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th>
						</tr>
					</thead>
					<tbody className="divide-y divide-slate-100">
						{products.data.items.map((product) => (
							<tr key={product.productId}>
								<td className="px-4 py-3 font-semibold text-navy-900">{product.organizationName}</td>
								<td className="px-4 py-3 text-slate-600">
									{product.teamName ? `${product.teamName} · ` : ""}
									{product.storeName}
								</td>
								<td className="px-4 py-3">
									<p className="font-medium text-navy-900">{product.productName}</p>
									<p className="text-xs text-slate-500">{product.catalogSource}</p>
								</td>
								<td className="px-4 py-3"><StatusPill status={product.status} /></td>
								<td className="px-4 py-3 text-slate-600">{product.variantCount}</td>
								<td className="px-4 py-3 text-slate-600">{product.hasSwagLogo ? "Yes" : "No"}</td>
								<td className="px-4 py-3 text-right">
									<Link to={`/app/platform/organizations/${product.organizationId}`} className="rounded-md border border-slate-300 px-3 py-2 font-medium text-navy-900 hover:border-green-500 hover:text-green-700">Open organization</Link>
								</td>
							</tr>
						))}
					</tbody>
				</table>
				{products.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No Swag Shop products match these filters.</p>}
			</div>

			<div className="flex items-center justify-between text-sm text-slate-600">
				<p>{products.data.totalElements} products</p>
				<div className="flex items-center gap-2">
					<button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="rounded-md border border-slate-300 px-3 py-2 disabled:opacity-40">Previous</button>
					<span>Page {page + 1} of {totalPages}</span>
					<button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-md border border-slate-300 px-3 py-2 disabled:opacity-40">Next</button>
				</div>
			</div>
		</div>
	);
}

function StatusPill({ status }: { status: string }) {
	const classes = status === "ACTIVE" ? "bg-green-100 text-green-800" : status === "DRAFT" ? "bg-amber-100 text-amber-900" : "bg-slate-100 text-slate-700";
	return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{status}</span>;
}
