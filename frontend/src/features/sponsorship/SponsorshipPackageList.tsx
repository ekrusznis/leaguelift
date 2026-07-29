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
	useAssignSponsorLogo,
	useCreateSponsorshipPackage,
	usePackageSponsorships,
	usePublishSponsorshipPackage,
	useSponsorshipPackages,
	useUpdateSponsorshipPackageStatus,
} from "./api";
import { createSponsorshipPackageSchema, type CreateSponsorshipPackageFormValues } from "./schema";

export function SponsorshipPackageList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useSponsorshipPackages(organizationId);
	const createPackage = useCreateSponsorshipPackage(organizationId);
	const publishPackage = usePublishSponsorshipPackage(organizationId);
	const updateStatus = useUpdateSponsorshipPackageStatus(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedPackageId, setExpandedPackageId] = useState<string | null>(null);

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
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add sponsorship package"}
				</Button>
			</div>

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
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
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
								className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.priceMinor && <p role="alert" className="text-sm text-error-red">{errors.priceMinor.message}</p>}
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
							<div className="flex items-center justify-between">
								<div>
									<p className="font-medium text-navy">
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
								<div className="flex gap-2">
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
										onClick={() => setExpandedPackageId((current) => (current === sponsorshipPackage.id ? null : sponsorshipPackage.id))}
									>
										{expandedPackageId === sponsorshipPackage.id ? "Hide sponsors" : "Manage sponsors"}
									</Button>
								</div>
							</div>
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

function SponsorshipManagementPanel({ organizationId, packageId }: { organizationId: string; packageId: string }) {
	const { data, isLoading, isError, refetch } = usePackageSponsorships(organizationId, packageId);

	if (isLoading) return <LoadingState label="Loading sponsors…" />;
	if (isError) return <ErrorState message="Could not load sponsors." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="No confirmed sponsors yet" description="Confirmed sponsors will appear here once a purchase completes." />;
	}

	return (
		<ul className="flex flex-col gap-3" aria-label="Confirmed sponsors">
			{data.items.map((sponsorship) => (
				<li key={sponsorship.id} className="rounded-md bg-ice-white p-3">
					<div className="flex items-center justify-between">
						<div>
							<p className="font-medium text-navy">{sponsorship.sponsorName}</p>
							{sponsorship.sponsorContactEmail && <p className="text-sm text-slate-gray">{sponsorship.sponsorContactEmail}</p>}
						</div>
						<span className="text-sm text-slate-gray">{formatMoneyMinorUnits(sponsorship.amountMinor, sponsorship.currency)}</span>
					</div>
					<div className="mt-2">
						<SponsorLogoUpload organizationId={organizationId} packageId={packageId} sponsorId={sponsorship.sponsorId} />
					</div>
				</li>
			))}
		</ul>
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
