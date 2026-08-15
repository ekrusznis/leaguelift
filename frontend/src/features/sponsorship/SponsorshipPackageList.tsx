import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
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
import { appPaths } from "../../routes/appPaths";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { fileSchemaFor } from "../media/schema";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import {
	useApproveSponsorship,
	useAssignSponsorLogo,
	useCreateSponsorshipPackage,
	usePublishSponsorshipPackage,
	useRejectSponsorship,
	useShareLinkQrCode,
	useSponsorshipInvoice,
	useUpdateSponsor,
	useUpdateSponsorshipPackageStatus,
} from "./api";
import {
	useSponsorshipPackageSearch,
	useSponsorshipSearch,
	type SponsorshipPackageSearchSort,
	type SponsorshipSearchItem,
	type SponsorshipSearchSort,
} from "./searchApi";
import {
	createSponsorshipPackageSchema,
	updateSponsorSchema,
	type CreateSponsorshipPackageFormValues,
	type UpdateSponsorFormValues,
} from "./schema";
import type { Sponsorship, SponsorshipPackageStatus, SponsorshipReviewStatus } from "./types";

export function SponsorshipPackageList({
	organizationId,
	organizationSlug,
}: {
	organizationId: string;
	organizationSlug?: string;
}) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<SponsorshipPackageStatus | "">("");
	const [exclusiveOnly, setExclusiveOnly] = useState(false);
	const [sort, setSort] = useState<SponsorshipPackageSearchSort>("NEWEST");
	const { data, isLoading, isError, refetch } = useSponsorshipPackageSearch(organizationId, {
		page,
		size,
		q: query,
		status,
		exclusive: exclusiveOnly ? true : undefined,
		sort,
	});
	const createPackage = useCreateSponsorshipPackage(organizationId);
	const publishPackage = usePublishSponsorshipPackage(organizationId);
	const updateStatus = useUpdateSponsorshipPackageStatus(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedPackageId, setExpandedPackageId] = useState<string | null>(null);
	const [shareLinkPackageId, setShareLinkPackageId] = useState<string | null>(null);
	const [showPendingReview, setShowPendingReview] = useState(false);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createSponsorshipPackageSchema>,
		unknown,
		z.output<typeof createSponsorshipPackageSchema>
	>({
		resolver: zodResolver(createSponsorshipPackageSchema),
		defaultValues: {
			name: "",
			description: "",
			priceDollars: 0,
			currency: "USD",
			maxQuantity: "",
			exclusive: false,
			placementStartDate: "",
			placementEndDate: "",
		},
	});

	const onSubmit = handleSubmit(async (values: CreateSponsorshipPackageFormValues) => {
		await createPackage.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	const hasFilters = !!status || exclusiveOnly;

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search sponsorship packages"
				resultCount={data?.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
					{ value: "NAME_ASC", label: "Name A–Z" },
					{ value: "NAME_DESC", label: "Name Z–A" },
					{ value: "PRICE_ASC", label: "Price — low to high" },
					{ value: "PRICE_DESC", label: "Price — high to low" },
					{ value: "SPONSORS_DESC", label: "Most sponsors" },
				]}
				onSortChange={(value) => {
					setSort(value as SponsorshipPackageSearchSort);
					setPage(0);
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("");
					setExclusiveOnly(false);
					setSort("NEWEST");
					setPage(0);
				}}
				filters={
					<>
						<select
							aria-label="Filter sponsorship package status"
							value={status}
							onChange={(event) => {
								setStatus(event.target.value as SponsorshipPackageStatus | "");
								setPage(0);
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All statuses</option>
							<option value="DRAFT">Draft</option>
							<option value="PUBLISHED">Published</option>
							<option value="ARCHIVED">Archived</option>
						</select>
						<label className="flex min-h-11 items-center gap-2 rounded-lg border border-slate-300 px-3 text-sm font-medium text-navy dark:border-[#334155] dark:text-[#f8fafc]">
							<input
								type="checkbox"
								checked={exclusiveOnly}
								onChange={(event) => {
									setExclusiveOnly(event.target.checked);
									setPage(0);
								}}
							/>
							Exclusive only
						</label>
					</>
				}
				actions={
					<>
						<Button type="button" variant="secondary" onClick={() => setShowPendingReview((value) => !value)}>
							{showPendingReview ? "Hide pending review" : "Review pending sponsorships"}
						</Button>
						<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>
							{showForm ? "Cancel" : "Add sponsorship package"}
						</Button>
					</>
				}
			/>

			{showPendingReview && (
				<div className="rounded-lg border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]">
					<PendingReviewQueue organizationId={organizationId} />
				</div>
			)}

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]"
					noValidate
					aria-label="Create a sponsorship package"
				>
					<div className="flex flex-wrap gap-3">
						<FormField label="Name" error={errors.name?.message}>
							<input
								type="text"
								placeholder="e.g. Gold Sponsor"
								{...register("name")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</FormField>
						<FormField label="Price" error={errors.priceDollars?.message}>
							<div className="flex gap-2">
								<select
									aria-label="Sponsorship package currency"
									{...register("currency")}
									className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-2 dark:bg-[#111827]"
								>
									<option value="USD">USD</option>
								</select>
								<input
									type="number"
									min={0}
									step="0.01"
									inputMode="decimal"
									placeholder="500.00"
									{...register("priceDollars")}
									className="min-h-11 w-36 rounded-md border border-slate-gray/30 px-3 py-2"
								/>
							</div>
						</FormField>
						<FormField label="Max quantity">
							<input
								type="number"
								min={1}
								step={1}
								placeholder="Uncapped"
								{...register("maxQuantity")}
								className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</FormField>
						<label className="flex items-center gap-2 self-end pb-2 text-sm text-navy dark:text-[#f8fafc]">
							<input type="checkbox" {...register("exclusive")} className="size-4" />
							Exclusive (only one sponsor)
						</label>
						<FormField label="Description">
							<input
								type="text"
								{...register("description")}
								className="min-h-11 min-w-64 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</FormField>
						<FormField label="Placement start">
							<input type="date" {...register("placementStartDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</FormField>
						<FormField label="Placement end">
							<input type="date" {...register("placementEndDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</FormField>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create package"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading sponsorship packages…" />}
			{isError && <ErrorState message="Could not load sponsorship packages." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No sponsorship packages yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: "Create a package to start accepting sponsors for your organization."
					}
				/>
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Sponsorship packages">
					{data.items.map((sponsorshipPackage) => (
						<li key={sponsorshipPackage.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
										{sponsorshipPackage.name}
										<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]">
											{sponsorshipPackage.status}
										</span>
										{sponsorshipPackage.exclusive && (
											<span className="ml-2 rounded-full bg-gold-500/10 px-2 py-0.5 text-xs font-medium text-gold-600">
												Exclusive
											</span>
										)}
										{sponsorshipPackage.soldOut && (
											<span className="ml-2 rounded-full bg-error-red/10 px-2 py-0.5 text-xs font-medium text-error-red">
												Sold out
											</span>
										)}
									</p>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
										{formatMoneyMinorUnits(sponsorshipPackage.priceMinor, sponsorshipPackage.currency)}
										{" · "}
										{sponsorshipPackage.confirmedCount} confirmed
										{sponsorshipPackage.maxQuantity ? ` of ${sponsorshipPackage.maxQuantity}` : ""}
									</p>
								</div>
								<div className="flex shrink-0 flex-wrap gap-2">
									{sponsorshipPackage.status === "DRAFT" && (
										<Button type="button" variant="secondary" onClick={() => publishPackage.mutate(sponsorshipPackage.id)} disabled={publishPackage.isPending}>
											Publish
										</Button>
									)}
									{sponsorshipPackage.status === "PUBLISHED" && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => updateStatus.mutate({ packageId: sponsorshipPackage.id, status: "ARCHIVED" })}
											disabled={updateStatus.isPending}
										>
											Archive
										</Button>
									)}
									<Button type="button" variant="secondary" onClick={() => setShareLinkPackageId((current) => current === sponsorshipPackage.id ? null : sponsorshipPackage.id)}>
										{shareLinkPackageId === sponsorshipPackage.id ? "Hide share link" : "Share"}
									</Button>
									<Button type="button" variant="secondary" onClick={() => setExpandedPackageId((current) => current === sponsorshipPackage.id ? null : sponsorshipPackage.id)}>
										{expandedPackageId === sponsorshipPackage.id ? "Hide sponsors" : "Manage sponsors"}
									</Button>
								</div>
							</div>
							{shareLinkPackageId === sponsorshipPackage.id && (
								<div className="mt-4 border-t border-slate-gray/20 pt-4">
									<ShareLinkPanel organizationId={organizationId} organizationSlug={organizationSlug} />
								</div>
							)}
							{expandedPackageId === sponsorshipPackage.id && (
								<div className="mt-4 border-t border-slate-gray/20 pt-4">
									<SponsorshipManagementPanel organizationId={organizationId} packageId={sponsorshipPackage.id} />
								</div>
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
					onSizeChange={(value) => { setSize(value); setPage(0); }}
				/>
			)}
		</div>
	);
}

function FormField({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
	return (
		<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
			{label}
			{children}
			{error && <span className="text-xs font-normal text-error-red">{error}</span>}
		</label>
	);
}

function reviewStatusBadgeClass(reviewStatus: Sponsorship["reviewStatus"]): string {
	if (reviewStatus === "APPROVED") return "bg-victory-green/10 text-victory-green";
	if (reviewStatus === "REJECTED") return "bg-error-red/10 text-error-red";
	return "bg-gold-500/10 text-gold-600";
}

function SponsorshipManagementPanel({ organizationId, packageId }: { organizationId: string; packageId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<"CONFIRMED" | "REFUNDED" | "">("");
	const [reviewStatus, setReviewStatus] = useState<SponsorshipReviewStatus | "">("");
	const [paymentSource, setPaymentSource] = useState<"STRIPE" | "OFFLINE" | "">("");
	const [sort, setSort] = useState<SponsorshipSearchSort>("NEWEST");
	const { data, isLoading, isError, refetch } = useSponsorshipSearch(organizationId, {
		page,
		size,
		q: query,
		packageId,
		status,
		reviewStatus,
		paymentSource,
		sort,
	});
	const [invoiceSponsorshipId, setInvoiceSponsorshipId] = useState<string | null>(null);
	const [editingSponsorId, setEditingSponsorId] = useState<string | null>(null);
	const hasFilters = !!status || !!reviewStatus || !!paymentSource;

	if (isLoading) return <LoadingState label="Loading sponsors…" />;
	if (isError || !data) return <ErrorState message="Could not load sponsors." onRetry={() => refetch()} />;

	return (
		<div className="flex flex-col gap-3">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => { setQuery(value); setPage(0); }}
				searchPlaceholder="Search sponsor, company, email, or ID"
				resultCount={data.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
					{ value: "SPONSOR_ASC", label: "Sponsor A–Z" },
					{ value: "AMOUNT_DESC", label: "Amount — high to low" },
					{ value: "AMOUNT_ASC", label: "Amount — low to high" },
					{ value: "REVIEW_STATUS_ASC", label: "Review status" },
				]}
				onSortChange={(value) => { setSort(value as SponsorshipSearchSort); setPage(0); }}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("");
					setReviewStatus("");
					setPaymentSource("");
					setSort("NEWEST");
					setPage(0);
				}}
				filters={
					<>
						<select aria-label="Filter sponsorship payment status" value={status} onChange={(event) => { setStatus(event.target.value as typeof status); setPage(0); }} className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 dark:border-[#334155] dark:bg-[#0f172a]">
							<option value="">All payment statuses</option>
							<option value="CONFIRMED">Confirmed</option>
							<option value="REFUNDED">Refunded</option>
						</select>
						<select aria-label="Filter sponsorship review status" value={reviewStatus} onChange={(event) => { setReviewStatus(event.target.value as SponsorshipReviewStatus | ""); setPage(0); }} className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 dark:border-[#334155] dark:bg-[#0f172a]">
							<option value="">All review statuses</option>
							<option value="PENDING_REVIEW">Pending review</option>
							<option value="APPROVED">Approved</option>
							<option value="REJECTED">Rejected</option>
						</select>
						<select aria-label="Filter sponsorship payment source" value={paymentSource} onChange={(event) => { setPaymentSource(event.target.value as typeof paymentSource); setPage(0); }} className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 dark:border-[#334155] dark:bg-[#0f172a]">
							<option value="">All payment sources</option>
							<option value="STRIPE">Online card</option>
							<option value="OFFLINE">Recorded offline</option>
						</select>
					</>
				}
			/>

			{data.items.length === 0 ? (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No confirmed sponsors yet"}
					description={query.trim() || hasFilters ? "Try changing your search or filters." : "Confirmed sponsors will appear here once a purchase completes."}
				/>
			) : (
				<ul className="flex flex-col gap-3" aria-label="Confirmed sponsors">
					{data.items.map((sponsorship) => (
						<SponsorshipRow
							key={sponsorship.id}
							organizationId={organizationId}
							packageId={packageId}
							sponsorship={sponsorship}
							invoiceOpen={invoiceSponsorshipId === sponsorship.id}
							contactOpen={editingSponsorId === sponsorship.sponsorId}
							onToggleInvoice={() => setInvoiceSponsorshipId((current) => current === sponsorship.id ? null : sponsorship.id)}
							onToggleContact={() => setEditingSponsorId((current) => current === sponsorship.sponsorId ? null : sponsorship.sponsorId)}
						/>
					))}
				</ul>
			)}

			<Pagination page={page} size={size} totalElements={data.totalElements} onPageChange={setPage} onSizeChange={(value) => { setSize(value); setPage(0); }} />
		</div>
	);
}

function SponsorshipRow({
	organizationId,
	packageId,
	sponsorship,
	invoiceOpen,
	contactOpen,
	onToggleInvoice,
	onToggleContact,
}: {
	organizationId: string;
	packageId: string;
	sponsorship: SponsorshipSearchItem;
	invoiceOpen: boolean;
	contactOpen: boolean;
	onToggleInvoice: () => void;
	onToggleContact: () => void;
}) {
	return (
		<li className="rounded-md bg-ice-white p-3 dark:bg-[#0f172a]">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div className="min-w-0 flex-1">
					<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
						{sponsorship.sponsorName}
						{sponsorship.sponsorCompanyName && <span className="ml-1 text-sm font-normal text-slate-gray">({sponsorship.sponsorCompanyName})</span>}
						<span className={`ml-2 rounded-full px-2 py-0.5 text-xs font-medium ${reviewStatusBadgeClass(sponsorship.reviewStatus)}`}>
							{sponsorship.reviewStatus === "PENDING_REVIEW" ? "Pending review" : sponsorship.reviewStatus}
						</span>
						{sponsorship.paymentSource === "OFFLINE" && <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">Recorded offline</span>}
						{sponsorship.status === "REFUNDED" && <span className="ml-2 rounded-full bg-slate-gray/10 px-2 py-0.5 text-xs font-medium text-slate-gray dark:text-[#cbd5e1]">Refunded</span>}
					</p>
					{sponsorship.sponsorContactEmail && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{sponsorship.sponsorContactEmail}</p>}
				</div>
				<span className="shrink-0 text-sm text-slate-gray dark:text-[#cbd5e1]">{formatMoneyMinorUnits(sponsorship.amountMinor, sponsorship.currency)}</span>
			</div>
			<div className="mt-2 flex flex-wrap items-center gap-2">
				<SponsorLogoUpload organizationId={organizationId} packageId={packageId} sponsorId={sponsorship.sponsorId} />
				<Button type="button" variant="secondary" onClick={onToggleInvoice}>{invoiceOpen ? "Hide invoice" : "View invoice"}</Button>
				<Button type="button" variant="secondary" onClick={onToggleContact}>{contactOpen ? "Hide contact details" : "Edit contact details"}</Button>
				{sponsorship.status === "CONFIRMED" && sponsorship.paymentSource === "STRIPE" && (
					<Link className="min-h-11 rounded-md border border-slate-gray/30 bg-pure-white px-4 py-2 text-sm font-medium text-navy hover:bg-ice-white dark:bg-[#111827] dark:text-[#f8fafc] dark:hover:bg-[#0f172a]" to={`${appPaths.organization(organizationId, "financial-operations")}?targetType=SPONSORSHIP&targetId=${sponsorship.id}`}>
						Preview refund
					</Link>
				)}
			</div>
			{invoiceOpen && <div className="mt-3 border-t border-slate-gray/20 pt-3"><SponsorshipInvoicePanel organizationId={organizationId} sponsorshipId={sponsorship.id} /></div>}
			{contactOpen && <div className="mt-3 border-t border-slate-gray/20 pt-3"><SponsorContactForm organizationId={organizationId} packageId={packageId} sponsorId={sponsorship.sponsorId} /></div>}
		</li>
	);
}

function SponsorshipInvoicePanel({ organizationId, sponsorshipId }: { organizationId: string; sponsorshipId: string }) {
	const { data, isLoading, isError } = useSponsorshipInvoice(organizationId, sponsorshipId, true);
	if (isLoading) return <LoadingState label="Loading invoice…" />;
	if (isError || !data) return <ErrorState message="Could not load the invoice." />;
	return (
		<dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm" aria-label="Sponsorship invoice">
			<dt className="text-slate-gray dark:text-[#cbd5e1]">Organization</dt><dd>{data.organizationName}</dd>
			<dt className="text-slate-gray dark:text-[#cbd5e1]">Sponsor</dt><dd>{data.sponsorName}{data.sponsorCompanyName ? ` (${data.sponsorCompanyName})` : ""}</dd>
			<dt className="text-slate-gray dark:text-[#cbd5e1]">Package</dt><dd>{data.packageName}</dd>
			<dt className="text-slate-gray dark:text-[#cbd5e1]">Amount</dt><dd>{formatMoneyMinorUnits(data.amountMinor, data.currency)}</dd>
			<dt className="text-slate-gray dark:text-[#cbd5e1]">Status</dt><dd>{data.status}</dd>
			<dt className="text-slate-gray dark:text-[#cbd5e1]">Date</dt><dd>{data.confirmedAt ? new Date(data.confirmedAt).toLocaleDateString() : "—"}</dd>
		</dl>
	);
}

function SponsorContactForm({ organizationId, packageId, sponsorId }: { organizationId: string; packageId: string; sponsorId: string }) {
	const updateSponsor = useUpdateSponsor(organizationId, packageId);
	const { register, handleSubmit, formState: { isSubmitting } } = useForm<z.input<typeof updateSponsorSchema>, unknown, z.output<typeof updateSponsorSchema>>({
		resolver: zodResolver(updateSponsorSchema),
		defaultValues: { name: "", contactEmail: "", phone: "", companyName: "", notes: "" },
	});
	const onSubmit = handleSubmit(async (values: UpdateSponsorFormValues) => {
		await updateSponsor.mutateAsync({ sponsorId, values });
	});
	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-2" aria-label="Edit sponsor contact details">
			{updateSponsor.isError && <p role="alert" className="text-sm text-error-red">Could not save these details. Please try again.</p>}
			{updateSponsor.isSuccess && <p role="status" className="text-sm text-success-700 dark:text-success-400">Saved.</p>}
			<div className="flex flex-wrap gap-2">
				<input aria-label="Sponsor phone" type="tel" placeholder="Phone" {...register("phone")} className="min-h-9 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" />
				<input aria-label="Sponsor company" type="text" placeholder="Company name" {...register("companyName")} className="min-h-9 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" />
				<input aria-label="Sponsor notes" type="text" placeholder="Notes" {...register("notes")} className="min-h-9 min-w-56 flex-1 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" />
			</div>
			<div><Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Save contact details"}</Button></div>
		</form>
	);
}

function PendingReviewQueue({ organizationId }: { organizationId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [paymentSource, setPaymentSource] = useState<"STRIPE" | "OFFLINE" | "">("");
	const [sort, setSort] = useState<SponsorshipSearchSort>("OLDEST");
	const { data, isLoading, isError, refetch } = useSponsorshipSearch(
		organizationId,
		{ page, size, q: query, reviewStatus: "PENDING_REVIEW", paymentSource, sort },
		true,
	);
	const approve = useApproveSponsorship(organizationId);
	const reject = useRejectSponsorship(organizationId);

	if (isLoading) return <LoadingState label="Loading sponsorships awaiting review…" />;
	if (isError || !data) return <ErrorState message="Could not load pending sponsorships." onRetry={() => refetch()} />;

	return (
		<div className="flex flex-col gap-3">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => { setQuery(value); setPage(0); }}
				searchPlaceholder="Search sponsor, company, package, or email"
				resultCount={data.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "OLDEST", label: "Oldest awaiting review" },
					{ value: "NEWEST", label: "Newest" },
					{ value: "SPONSOR_ASC", label: "Sponsor A–Z" },
					{ value: "AMOUNT_DESC", label: "Amount — high to low" },
					{ value: "PACKAGE_ASC", label: "Package A–Z" },
				]}
				onSortChange={(value) => { setSort(value as SponsorshipSearchSort); setPage(0); }}
				hasActiveFilters={!!paymentSource}
				onClear={() => { setQuery(""); setPaymentSource(""); setSort("OLDEST"); setPage(0); }}
				filters={
					<select aria-label="Filter pending sponsorship payment source" value={paymentSource} onChange={(event) => { setPaymentSource(event.target.value as typeof paymentSource); setPage(0); }} className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 dark:border-[#334155] dark:bg-[#0f172a]">
						<option value="">All payment sources</option>
						<option value="STRIPE">Online card</option>
						<option value="OFFLINE">Recorded offline</option>
					</select>
				}
			/>
			{data.items.length === 0 ? (
				<EmptyState
					title={query.trim() || paymentSource ? "No results found" : "Nothing awaiting review"}
					description={query.trim() || paymentSource ? "Try changing your search or filters." : "Confirmed sponsorships awaiting approval will appear here."}
				/>
			) : (
				<ul className="flex flex-col gap-2" aria-label="Sponsorships awaiting review">
					{data.items.map((sponsorship) => (
						<li key={sponsorship.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-pure-white p-3 dark:bg-[#111827]">
							<div className="min-w-0 flex-1">
								<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{sponsorship.sponsorName}</p>
								<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
									{sponsorship.packageName} · {formatMoneyMinorUnits(sponsorship.amountMinor, sponsorship.currency)}
								</p>
							</div>
							<div className="flex shrink-0 gap-2">
								<Button type="button" disabled={approve.isPending} onClick={() => approve.mutate(sponsorship.id)}>Approve</Button>
								<Button
									type="button"
									variant="secondary"
									disabled={reject.isPending}
									onClick={() => {
										if (window.confirm(`Reject ${sponsorship.sponsorName}'s sponsorship? Online payments are refunded through the original payment method.`)) {
											reject.mutate(sponsorship.id);
										}
									}}
								>
									Reject &amp; refund
								</Button>
							</div>
						</li>
					))}
				</ul>
			)}
			<Pagination page={page} size={size} totalElements={data.totalElements} onPageChange={setPage} onSizeChange={(value) => { setSize(value); setPage(0); }} />
		</div>
	);
}

function ShareLinkPanel({ organizationId, organizationSlug }: { organizationId: string; organizationSlug?: string }) {
	const url = organizationSlug ? `${window.location.origin}/sponsors/${organizationSlug}` : "";
	const { data, isLoading, isError } = useShareLinkQrCode(organizationId, url, Boolean(organizationSlug));
	if (!organizationSlug) return <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">A share link isn&rsquo;t available until this organization has a public slug.</p>;
	return (
		<div className="flex flex-wrap items-center gap-4">
			{isLoading && <LoadingState label="Generating QR code…" />}
			{isError && <ErrorState message="Could not generate a QR code." />}
			{data && <img src={data.qrCodeDataUri} alt={`QR code linking to ${url}`} className="size-32 rounded-md border border-slate-gray/20" />}
			<div className="flex flex-col gap-1">
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Share this link with a prospective sponsor, or print the QR code in event materials.</p>
				<div className="flex items-center gap-2">
					<input readOnly aria-label="Shareable sponsorship link" value={url} className="min-h-9 w-64 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" onFocus={(event) => event.currentTarget.select()} />
					<Button type="button" variant="secondary" onClick={() => navigator.clipboard?.writeText(url)}>Copy</Button>
				</div>
			</div>
		</div>
	);
}

function SponsorLogoUpload({ organizationId, packageId, sponsorId }: { organizationId: string; packageId: string; sponsorId: string }) {
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const assignLogo = useAssignSponsorLogo(organizationId, packageId);
	const [state, setState] = useState<{ status: "idle" | "uploading" | "error"; error?: string }>({ status: "idle" });
	async function handleFileSelected(file: File | undefined) {
		if (!file) return;
		const validation = fileSchemaFor("SPONSOR_LOGO").safeParse(file);
		if (!validation.success) {
			setState({ status: "error", error: validation.error.issues[0]?.message ?? "This file cannot be used." });
			return;
		}
		setState({ status: "uploading" });
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot: "SPONSOR_LOGO",
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
			await assignLogo.mutateAsync({ sponsorId, assetId: requested.assetId });
			setState({ status: "idle" });
		} catch {
			setState({ status: "error", error: "Upload failed. Please try again." });
		}
	}
	return (
		<div className="flex flex-col gap-1">
			<label htmlFor={`sponsor-logo-${sponsorId}`} className="text-sm font-medium text-navy dark:text-[#f8fafc]">Sponsor logo</label>
			<input
				id={`sponsor-logo-${sponsorId}`}
				type="file"
				accept="image/png,image/jpeg,image/webp,image/svg+xml"
				onChange={(event) => handleFileSelected(event.target.files?.[0])}
				disabled={state.status === "uploading"}
				className="text-sm"
			/>
			{state.status === "uploading" && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Uploading…</p>}
			{state.status === "error" && <p role="alert" className="text-sm text-error-red">{state.error}</p>}
		</div>
	);
}
