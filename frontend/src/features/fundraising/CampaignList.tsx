import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, type Resolver } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useTeams } from "../teams/api";
import { useCampaigns, useCreateCampaign, usePublishCampaign } from "./api";
import { CAMPAIGN_TYPES, createCampaignSchema, type CreateCampaignFormValues } from "./schema";
import type { CampaignType } from "./types";

const CAMPAIGN_TYPE_LABELS: Record<CampaignType, string> = {
	ORGANIZATION_GENERAL: "Organization general fund",
	TEAM_GENERAL: "Team general fund",
	TRAVEL: "Travel",
	TOURNAMENT_FEES: "Tournament fees",
	UNIFORMS: "Uniforms",
	EQUIPMENT: "Equipment",
	FACILITY_IMPROVEMENTS: "Facility improvements",
	SCHOLARSHIPS: "Scholarships",
	SPECIAL_EVENTS: "Special events",
	APPAREL_BASED: "Apparel-based",
	SPONSOR_SUPPORTED: "Sponsor-supported",
};

export function CampaignList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useCampaigns(organizationId);
	const { data: teams } = useTeams(organizationId);
	const createCampaign = useCreateCampaign(organizationId);
	const publishCampaign = usePublishCampaign(organizationId);
	const [showForm, setShowForm] = useState(false);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateCampaignFormValues>({
		// z.coerce.number() (goalAmountMinor) makes zodResolver's inferred input type
		// diverge from CreateCampaignFormValues's output type — the same known
		// zod/react-hook-form generic mismatch as FeeTemplateList's amountMinor field.
		resolver: zodResolver(createCampaignSchema) as Resolver<CreateCampaignFormValues>,
		defaultValues: {
			teamId: "",
			name: "",
			slug: "",
			description: "",
			campaignType: "ORGANIZATION_GENERAL",
			goalAmountMinor: 0,
			currency: "USD",
			startDate: "",
			endDate: "",
		},
	});

	const onSubmit = handleSubmit(async (values) => {
		await createCampaign.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${data.totalElements} campaign${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add campaign"}
				</Button>
			</div>

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4"
					noValidate
					aria-label="Create a fundraising campaign"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="campaign-name"
								type="text"
								placeholder="e.g. Spring Trip Fund"
								{...register("name")}
								aria-invalid={!!errors.name}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-slug" className="text-sm font-medium text-navy">
								Public URL slug <span aria-hidden>*</span>
							</label>
							<input
								id="campaign-slug"
								type="text"
								placeholder="spring-trip-fund"
								{...register("slug")}
								aria-invalid={!!errors.slug}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.slug && <p role="alert" className="text-sm text-error-red">{errors.slug.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-type" className="text-sm font-medium text-navy">
								Campaign type
							</label>
							<select
								id="campaign-type"
								{...register("campaignType")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							>
								{CAMPAIGN_TYPES.map((type) => (
									<option key={type} value={type}>
										{CAMPAIGN_TYPE_LABELS[type]}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-team" className="text-sm font-medium text-navy">
								Team (optional)
							</label>
							<select
								id="campaign-team"
								{...register("teamId")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							>
								<option value="">Organization-wide</option>
								{teams?.items.map((team) => (
									<option key={team.id} value={team.id}>
										{team.name}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-goal" className="text-sm font-medium text-navy">
								Goal (cents) <span aria-hidden>*</span>
							</label>
							<input
								id="campaign-goal"
								type="number"
								min={0}
								step={1}
								placeholder="e.g. 400000 = $4,000.00"
								{...register("goalAmountMinor")}
								aria-invalid={!!errors.goalAmountMinor}
								className="min-h-11 w-44 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.goalAmountMinor && <p role="alert" className="text-sm text-error-red">{errors.goalAmountMinor.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-start" className="text-sm font-medium text-navy">
								Start date
							</label>
							<input
								id="campaign-start"
								type="date"
								{...register("startDate")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-end" className="text-sm font-medium text-navy">
								End date
							</label>
							<input
								id="campaign-end"
								type="date"
								{...register("endDate")}
								aria-invalid={!!errors.endDate}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.endDate && <p role="alert" className="text-sm text-error-red">{errors.endDate.message}</p>}
						</div>
						<div className="flex min-w-[16rem] flex-1 flex-col gap-1">
							<label htmlFor="campaign-desc" className="text-sm font-medium text-navy">
								Description
							</label>
							<input
								id="campaign-desc"
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
							{isSubmitting ? "Creating…" : "Create campaign"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading campaigns…" />}
			{isError && <ErrorState message="Could not load campaigns." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No campaigns yet" description="Create a fundraising campaign for your organization or a team." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Fundraising campaigns">
					{data.items.map((campaign) => (
						<li
							key={campaign.id}
							className="flex items-center justify-between rounded-lg border border-slate-gray/20 bg-pure-white p-3"
						>
							<div>
								<p className="font-medium text-navy">
									{campaign.name}
									<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">
										{campaign.status}
									</span>
								</p>
								<p className="text-sm text-slate-gray">
									{formatMoneyMinorUnits(campaign.raisedMinor, campaign.currency)} raised of{" "}
									{formatMoneyMinorUnits(campaign.goalAmountMinor, campaign.currency)} goal &middot; /{campaign.slug}
								</p>
							</div>
							{campaign.status === "DRAFT" && (
								<Button
									type="button"
									variant="secondary"
									onClick={() => publishCampaign.mutate(campaign.id)}
									disabled={publishCampaign.isPending}
								>
									Publish
								</Button>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
