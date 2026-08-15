import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useArchiveFeeTemplate, useCreateFeeTemplate } from "./api";
import { createFeeTemplateSchema } from "./schema";
import { useFeeTemplateSearch, type FeeTemplateSearchSort } from "./searchApi";

export function FeeTemplateList({ organizationId }: { organizationId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<"ACTIVE" | "ARCHIVED" | "">("ACTIVE");
	const [sort, setSort] = useState<FeeTemplateSearchSort>("NAME_ASC");
	const { data, isLoading, isError, refetch } = useFeeTemplateSearch(organizationId, {
		page,
		size,
		q: query,
		status,
		sort,
	});
	const createTemplate = useCreateFeeTemplate(organizationId);
	const archiveTemplate = useArchiveFeeTemplate(organizationId);
	const [showForm, setShowForm] = useState(false);
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createFeeTemplateSchema>,
		unknown,
		z.output<typeof createFeeTemplateSchema>
	>({
		resolver: zodResolver(createFeeTemplateSchema),
		defaultValues: { name: "", description: "", amountDollars: 0, currency: "USD" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createTemplate.mutateAsync(values);
		reset();
		setShowForm(false);
		await refetch();
	});

	const hasFilters = status !== "ACTIVE";

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search fee templates by name or description"
				resultCount={data?.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NAME_ASC", label: "Name A–Z" },
					{ value: "NAME_DESC", label: "Name Z–A" },
					{ value: "AMOUNT_ASC", label: "Amount — low to high" },
					{ value: "AMOUNT_DESC", label: "Amount — high to low" },
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
				]}
				onSortChange={(value) => {
					setSort(value as FeeTemplateSearchSort);
					setPage(0);
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("ACTIVE");
					setSort("NAME_ASC");
					setPage(0);
				}}
				filters={
					<select
						aria-label="Filter fee templates by status"
						value={status}
						onChange={(event) => {
							setStatus(event.target.value as "ACTIVE" | "ARCHIVED" | "");
							setPage(0);
						}}
						className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
					>
						<option value="">All statuses</option>
						<option value="ACTIVE">Active</option>
						<option value="ARCHIVED">Archived</option>
					</select>
				}
				actions={
					<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>
						{showForm ? "Cancel" : "Add template"}
					</Button>
				}
			/>

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]"
					noValidate
					aria-label="Create a fee template"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="tpl-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="tpl-name"
								type="text"
								placeholder="e.g. Spring Registration"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "tpl-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && (
								<p id="tpl-name-error" role="alert" className="text-sm text-error-red">
									{errors.name.message}
								</p>
							)}
						</div>

						<div className="flex flex-col gap-1">
							<label htmlFor="tpl-amount" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Amount <span aria-hidden>*</span>
							</label>
							<div className="flex items-center gap-2">
								<select
									aria-label="Fee currency"
									{...register("currency")}
									className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-2 dark:bg-[#111827]"
								>
									<option value="USD">USD</option>
								</select>
								<input
									id="tpl-amount"
									type="number"
									min={0}
									step="0.01"
									inputMode="decimal"
									placeholder="150.00"
									{...register("amountDollars")}
									aria-invalid={!!errors.amountDollars}
									aria-describedby={errors.amountDollars ? "tpl-amount-error" : undefined}
									className="min-h-11 w-40 rounded-md border border-slate-gray/30 px-3 py-2"
								/>
							</div>
							{errors.amountDollars && (
								<p id="tpl-amount-error" role="alert" className="text-sm text-error-red">
									{errors.amountDollars.message}
								</p>
							)}
						</div>

						<div className="flex min-w-56 flex-1 flex-col gap-1">
							<label htmlFor="tpl-desc" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
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

					{createTemplate.isError && (
						<p role="alert" className="text-sm text-error-red">
							Could not create the fee template. Check the amount and try again.
						</p>
					)}

					<div className="flex justify-end gap-2">
						<Button
							type="button"
							variant="secondary"
							onClick={() => {
								reset();
								setShowForm(false);
							}}
						>
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
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No fee templates yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or status filter."
							: "Create reusable fee types to quickly charge households."
					}
				/>
			)}

			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Fee templates">
					{data.items.map((template) => (
						<li
							key={template.id}
							className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]"
						>
							<div className="min-w-0 flex-1">
								<div className="flex flex-wrap items-center gap-2">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
										{template.name}
									</p>
									{template.status === "ARCHIVED" && (
										<span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-gray dark:bg-[#0f172a]">
											Archived
										</span>
									)}
								</div>
								<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
									{formatMoneyMinorUnits(template.amountMinor, template.currency)}
									{template.description ? ` · ${template.description}` : ""}
								</p>
							</div>
							{template.status === "ACTIVE" && (
								<Button
									type="button"
									variant="secondary"
									className="shrink-0"
									onClick={() => archiveTemplate.mutate(template.id, { onSuccess: () => refetch() })}
									disabled={archiveTemplate.isPending}
								>
									Archive
								</Button>
							)}
						</li>
					))}
				</ul>
			)}

			{data && (
				<Pagination
					page={page}
					size={size}
					totalElements={data.totalElements}
					onPageChange={setPage}
					onSizeChange={(value) => {
						setSize(value);
						setPage(0);
					}}
				/>
			)}
		</div>
	);
}
