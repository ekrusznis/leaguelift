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
	useApproveSponsorship,
	useAssignSponsorLogo,
	useCreateSponsorshipPackage,
	usePackageSponsorships,
	usePendingReviewSponsorships,
	usePublishSponsorshipPackage,
	useRefundSponsorship,
	useRejectSponsorship,
	useShareLinkQrCode,
	useSponsorshipInvoice,
	useSponsorshipPackages,
	useUpdateSponsor,
	useUpdateSponsorshipPackageStatus,
} from "./api";
import { createSponsorshipPackageSchema, updateSponsorSchema, type CreateSponsorshipPackageFormValues, type UpdateSponsorFormValues } from "./schema";
import type { Sponsorship } from "./types";

export function SponsorshipPackageList({
	organizationId,
	organizationSlug,
}: {
	organizationId: string;
	/** Needed to build the public sponsorship page's shareable URL/QR code (Phase 6 remainder, ADR-019). The "Share" panel is unavailable without it. */
	organizationSlug?: string;
}) {
	const { data, isLoading, isError, refetch } = useSponsorshipPackages(organizationId);
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
		defaultValues: { name: "", description: "", priceMinor: 0, maxQuantity: "", exclusive: false, placementStartDate: "", placementEndDate: "" },
	});

	const onSubmit = handleSubmit(async (values: CreateSponsorshipPackageFormValues) => {
		await createPackage.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${data.totalElements} sponsorship package${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<div className="flex gap-2">
					<Button type="button" variant="secondary" onClick={() => setShowPendingReview((v) => !v)}>
						{showPendingReview ? "Hide pending review" : "Review pending sponsorships"}
					</Button>
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Add sponsorship package"}
					</Button>
				</div>
			</div>

			{showPendingReview && (
				<div className="rounded-lg border border-slate-gray/20 bg-ice-white p-4">
					<PendingReviewQueue organizationId={organizationId} />
				</div>
			)}

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4"
					noValidate
					aria-label="Create a sponsorship package"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="sponsorship-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="sponsorship-name"
								type="text"
								placeholder="e.g. Gold Sponsor"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "sponsorship-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p id="sponsorship-name-error" role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="sponsorship-price" className="text-sm font-medium text-navy">
								Price (cents) <span aria-hidden>*</span>
							</label>
							<input
								id="sponsorship-price"
								type="number"
								min={0}
								step={1}
								{...register("priceMinor")}
								aria-invalid={!!errors.priceMinor}
								aria-describedby={errors.priceMinor ? "sponsorship-price-error" : undefined}
								className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.priceMinor && <p id="sponsorship-price-error" role="alert" className="text-sm text-error-red">{errors.priceMinor.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="sponsorship-max-quantity" className="text-sm font-medium text-navy">
								Max quantity
							</label>
							<input
								id="sponsorship-max-quantity"
								type="number"
								min={1}
								step={1}
								placeholder="Uncapped"
								{...register("maxQuantity")}
								aria-invalid={!!errors.maxQuantity}
								className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<label className="flex items-center gap-2 self-end pb-2 text-sm text-navy">
							<input type="checkbox" {...register("exclusive")} className="size-4" />
							Exclusive (only one sponsor)
						</label>
						<div className="flex min-w-[16rem] flex-1 flex-col gap-1">
							<label htmlFor="sponsorship-desc" className="text-sm font-medium text-navy">
								Description
							</label>
							<input
								id="sponsorship-desc"
								type="text"
								{...register("description")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="sponsorship-start" className="text-sm font-medium text-navy">
								Placement start
							</label>
							<input
								id="sponsorship-start"
								type="date"
								{...register("placementStartDate")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="sponsorship-end" className="text-sm font-medium text-navy">
								Placement end
							</label>
							<input
								id="sponsorship-end"
								type="date"
								{...register("placementEndDate")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
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
				<EmptyState title="No sponsorship packages yet" description="Create a package to start accepting sponsors for your organization." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Sponsorship packages">
					{data.items.map((sponsorshipPackage) => (
						<li key={sponsorshipPackage.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy">
										{sponsorshipPackage.name}
										<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">
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
									<p className="text-sm text-slate-gray">
										{formatMoneyMinorUnits(sponsorshipPackage.priceMinor, sponsorshipPackage.currency)}
										{" · "}
										{sponsorshipPackage.confirmedCount} confirmed
										{sponsorshipPackage.maxQuantity ? ` of ${sponsorshipPackage.maxQuantity}` : ""}
									</p>
								</div>
								<div className="flex shrink-0 gap-2">
									{sponsorshipPackage.status === "DRAFT" && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => publishPackage.mutate(sponsorshipPackage.id)}
											disabled={publishPackage.isPending}
										>
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
									<Button
										type="button"
										variant="secondary"
										onClick={() => setShareLinkPackageId((current) => (current === sponsorshipPackage.id ? null : sponsorshipPackage.id))}
									>
										{shareLinkPackageId === sponsorshipPackage.id ? "Hide share link" : "Share"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => setExpandedPackageId((current) => (current === sponsorshipPackage.id ? null : sponsorshipPackage.id))}
									>
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
		</div>
	);
}

function reviewStatusBadgeClass(reviewStatus: Sponsorship["reviewStatus"]): string {
	if (reviewStatus === "APPROVED") return "bg-victory-green/10 text-victory-green";
	if (reviewStatus === "REJECTED") return "bg-error-red/10 text-error-red";
	return "bg-gold-500/10 text-gold-600";
}

function SponsorshipManagementPanel({ organizationId, packageId }: { organizationId: string; packageId: string }) {
	const { data, isLoading, isError, refetch } = usePackageSponsorships(organizationId, packageId);
	const refundSponsorship = useRefundSponsorship(organizationId);
	const [invoiceSponsorshipId, setInvoiceSponsorshipId] = useState<string | null>(null);
	const [editingSponsorId, setEditingSponsorId] = useState<string | null>(null);

	if (isLoading) return <LoadingState label="Loading sponsors…" />;
	if (isError) return <ErrorState message="Could not load sponsors." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="No confirmed sponsors yet" description="Confirmed sponsors will appear here once a purchase completes." />;
	}

	return (
		<ul className="flex flex-col gap-3" aria-label="Confirmed sponsors">
			{data.items.map((sponsorship) => (
				<li key={sponsorship.id} className="rounded-md bg-ice-white p-3">
					<div className="flex flex-wrap items-center justify-between gap-3">
						<div className="min-w-0 flex-1">
							<p className="break-words font-medium text-navy">
								{sponsorship.sponsorName}
								<span className={`ml-2 rounded-full px-2 py-0.5 text-xs font-medium ${reviewStatusBadgeClass(sponsorship.reviewStatus)}`}>
									{sponsorship.reviewStatus === "PENDING_REVIEW" ? "Pending review" : sponsorship.reviewStatus}
								</span>
								{sponsorship.paymentSource === "OFFLINE" && (
									<span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">Recorded offline</span>
								)}
								{sponsorship.status === "REFUNDED" && (
									<span className="ml-2 rounded-full bg-slate-gray/10 px-2 py-0.5 text-xs font-medium text-slate-gray">Refunded</span>
								)}
							</p>
							{sponsorship.sponsorContactEmail && <p className="text-sm text-slate-gray">{sponsorship.sponsorContactEmail}</p>}
						</div>
						<span className="shrink-0 text-sm text-slate-gray">{formatMoneyMinorUnits(sponsorship.amountMinor, sponsorship.currency)}</span>
					</div>
					<div className="mt-2 flex flex-wrap items-center gap-2">
						<SponsorLogoUpload organizationId={organizationId} packageId={packageId} sponsorId={sponsorship.sponsorId} />
						<Button
							type="button"
							variant="secondary"
							onClick={() => setInvoiceSponsorshipId((current) => (current === sponsorship.id ? null : sponsorship.id))}
						>
							{invoiceSponsorshipId === sponsorship.id ? "Hide invoice" : "View invoice"}
						</Button>
						<Button
							type="button"
							variant="secondary"
							onClick={() => setEditingSponsorId((current) => (current === sponsorship.sponsorId ? null : sponsorship.sponsorId))}
						>
							{editingSponsorId === sponsorship.sponsorId ? "Hide contact details" : "Edit contact details"}
						</Button>
						{sponsorship.status === "CONFIRMED" && sponsorship.paymentSource === "STRIPE" && (
							<Button
								type="button"
								variant="secondary"
								disabled={refundSponsorship.isPending}
								onClick={() => {
									if (window.confirm(`Refund ${sponsorship.sponsorName}'s sponsorship? This charges back the original payment via Stripe.`)) {
										refundSponsorship.mutate(sponsorship.id);
									}
								}}
							>
								Refund
							</Button>
						)}
					</div>
					{refundSponsorship.isError && refundSponsorship.variables === sponsorship.id && (
						<p role="alert" className="mt-1 text-sm text-error-red">
							Could not issue the refund. Please try again.
						</p>
					)}
					{invoiceSponsorshipId === sponsorship.id && (
						<div className="mt-3 border-t border-slate-gray/20 pt-3">
							<SponsorshipInvoicePanel organizationId={organizationId} sponsorshipId={sponsorship.id} />
						</div>
					)}
					{editingSponsorId === sponsorship.sponsorId && (
						<div className="mt-3 border-t border-slate-gray/20 pt-3">
							<SponsorContactForm organizationId={organizationId} packageId={packageId} sponsorId={sponsorship.sponsorId} />
						</div>
					)}
				</li>
			))}
		</ul>
	);
}

function SponsorshipInvoicePanel({ organizationId, sponsorshipId }: { organizationId: string; sponsorshipId: string }) {
	const { data, isLoading, isError } = useSponsorshipInvoice(organizationId, sponsorshipId, true);

	if (isLoading) return <LoadingState label="Loading invoice…" />;
	if (isError || !data) return <ErrorState message="Could not load the invoice." />;

	return (
		<dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm" aria-label="Sponsorship invoice">
			<dt className="text-slate-gray">Organization</dt>
			<dd className="text-navy">{data.organizationName}</dd>
			<dt className="text-slate-gray">Sponsor</dt>
			<dd className="text-navy">{data.sponsorName}{data.sponsorCompanyName ? ` (${data.sponsorCompanyName})` : ""}</dd>
			<dt className="text-slate-gray">Package</dt>
			<dd className="text-navy">{data.packageName}</dd>
			<dt className="text-slate-gray">Amount</dt>
			<dd className="text-navy">{formatMoneyMinorUnits(data.amountMinor, data.currency)}</dd>
			<dt className="text-slate-gray">Status</dt>
			<dd className="text-navy">{data.status}</dd>
			<dt className="text-slate-gray">Date</dt>
			<dd className="text-navy">{data.confirmedAt ? new Date(data.confirmedAt).toLocaleDateString() : "—"}</dd>
		</dl>
	);
}

function SponsorContactForm({ organizationId, packageId, sponsorId }: { organizationId: string; packageId: string; sponsorId: string }) {
	const updateSponsor = useUpdateSponsor(organizationId, packageId);
	const {
		register,
		handleSubmit,
		formState: { isSubmitting },
	} = useForm<z.input<typeof updateSponsorSchema>, unknown, z.output<typeof updateSponsorSchema>>({
		resolver: zodResolver(updateSponsorSchema),
		defaultValues: { name: "", contactEmail: "", phone: "", companyName: "", notes: "" },
	});

	const onSubmit = handleSubmit(async (values: UpdateSponsorFormValues) => {
		await updateSponsor.mutateAsync({ sponsorId, values });
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-2" aria-label="Edit sponsor contact details">
			{updateSponsor.isError && (
				<p role="alert" className="text-sm text-error-red">
					Could not save these details. Please try again.
				</p>
			)}
			{updateSponsor.isSuccess && <p className="text-sm text-victory-green">Saved.</p>}
			<div className="flex flex-wrap gap-2">
				<div className="flex flex-col gap-1">
					<label htmlFor={`sponsor-phone-${sponsorId}`} className="text-xs font-medium text-navy">
						Phone
					</label>
					<input id={`sponsor-phone-${sponsorId}`} type="tel" {...register("phone")} className="min-h-9 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" />
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor={`sponsor-company-${sponsorId}`} className="text-xs font-medium text-navy">
						Company name
					</label>
					<input
						id={`sponsor-company-${sponsorId}`}
						type="text"
						{...register("companyName")}
						className="min-h-9 rounded-md border border-slate-gray/30 px-2 py-1 text-sm"
					/>
				</div>
				<div className="flex min-w-[14rem] flex-1 flex-col gap-1">
					<label htmlFor={`sponsor-notes-${sponsorId}`} className="text-xs font-medium text-navy">
						Notes
					</label>
					<input id={`sponsor-notes-${sponsorId}`} type="text" {...register("notes")} className="min-h-9 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" />
				</div>
			</div>
			<div>
				<Button type="submit" disabled={isSubmitting}>
					{isSubmitting ? "Saving…" : "Save contact details"}
				</Button>
			</div>
		</form>
	);
}

/** The org-admin approval queue (Phase 6 remainder, ADR-019) — every confirmed sponsorship, across all packages, still awaiting a review decision. */
function PendingReviewQueue({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = usePendingReviewSponsorships(organizationId, true);
	const approve = useApproveSponsorship(organizationId);
	const reject = useRejectSponsorship(organizationId);

	if (isLoading) return <LoadingState label="Loading sponsorships awaiting review…" />;
	if (isError) return <ErrorState message="Could not load pending sponsorships." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="Nothing awaiting review" description="Confirmed sponsorships awaiting approval will appear here." />;
	}

	return (
		<ul className="flex flex-col gap-2" aria-label="Sponsorships awaiting review">
			{data.items.map((sponsorship) => (
				<li key={sponsorship.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-pure-white p-3">
					<div className="min-w-0 flex-1">
						<p className="break-words font-medium text-navy">{sponsorship.sponsorName}</p>
						<p className="text-sm text-slate-gray">{formatMoneyMinorUnits(sponsorship.amountMinor, sponsorship.currency)}</p>
					</div>
					<div className="flex shrink-0 gap-2">
						<Button type="button" disabled={approve.isPending} onClick={() => approve.mutate(sponsorship.id)}>
							Approve
						</Button>
						<Button
							type="button"
							variant="secondary"
							disabled={reject.isPending}
							onClick={() => {
								if (window.confirm(`Reject ${sponsorship.sponsorName}'s sponsorship? This refunds the original payment via Stripe.`)) {
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
	);
}

/** A shareable QR code + plain URL for the org's public sponsorship page (Phase 6 remainder, ADR-019) — for an org admin to hand to a prospective sponsor or print in event materials. No click-through tracking. */
function ShareLinkPanel({ organizationId, organizationSlug }: { organizationId: string; organizationSlug?: string }) {
	if (!organizationSlug) {
		return <p className="text-sm text-slate-gray">A share link isn&rsquo;t available until this organization has a public slug.</p>;
	}
	const url = `${window.location.origin}/sponsors/${organizationSlug}`;
	const { data, isLoading, isError } = useShareLinkQrCode(organizationId, url, true);

	return (
		<div className="flex flex-wrap items-center gap-4">
			{isLoading && <LoadingState label="Generating QR code…" />}
			{isError && <ErrorState message="Could not generate a QR code." />}
			{data && <img src={data.qrCodeDataUri} alt={`QR code linking to ${url}`} className="size-32 rounded-md border border-slate-gray/20" />}
			<div className="flex flex-col gap-1">
				<p className="text-sm text-slate-gray">Share this link with a prospective sponsor, or print the QR code in event materials.</p>
				<div className="flex items-center gap-2">
					<input readOnly aria-label="Shareable sponsorship link" value={url} className="min-h-9 w-64 rounded-md border border-slate-gray/30 px-2 py-1 text-sm" onFocus={(e) => e.currentTarget.select()} />
					<Button type="button" variant="secondary" onClick={() => navigator.clipboard?.writeText(url)}>
						Copy
					</Button>
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
			<label htmlFor={`sponsor-logo-${sponsorId}`} className="text-sm font-medium text-navy">
				Sponsor logo
			</label>
			<input
				id={`sponsor-logo-${sponsorId}`}
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
