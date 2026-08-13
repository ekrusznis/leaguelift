import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { BoxPoolManagementPanel } from "../boxpool/BoxPoolManagementPanel";
import { useTeams } from "../teams/api";
import { useCampaignShareLink, useCampaigns, useCreateCampaign, usePublishCampaign } from "./api";
import { ContributionList } from "./ContributionList";
import { ReminderButton } from "../communications/ReminderButton";
import { CAMPAIGN_TYPES, createCampaignSchema } from "./schema";
import type { CampaignType, FundraiserTemplateKey } from "./types";

const FUNDRAISER_TEMPLATES: {
	key: FundraiserTemplateKey | "";
	label: string;
	description: string;
	campaignType?: CampaignType;
	name?: string;
	description_?: string;
}[] = [
	{ key: "", label: "Blank campaign", description: "Start from a blank form." },
	{ key: "BOX_POOL", label: "Sports box pool", description: "A grid of claimable boxes tied to a game — set up the pool after creating the campaign." },
	{
		key: "BAKE_SALE",
		label: "Bake sale",
		description: "Pre-fills a starter name/description you can edit.",
		campaignType: "SPECIAL_EVENTS",
		name: "Bake Sale",
		description_: "Homemade treats — proceeds support the team. Let us know if you'd like to bake or volunteer!",
	},
	{
		key: "CAR_WASH",
		label: "Car wash",
		description: "Pre-fills a starter name/description you can edit.",
		campaignType: "SPECIAL_EVENTS",
		name: "Car Wash",
		description_: "Bring your car by for a wash — all proceeds support the team. Volunteers welcome!",
	},
];

function QrCodeButton({ organizationId, url }: { organizationId: string; url: string }) {
	const shareLink = useCampaignShareLink(organizationId);
	const [show, setShow] = useState(false);
	return (
		<div className="flex flex-col items-end gap-2">
			<Button
				type="button"
				variant="secondary"
				onClick={() => {
					setShow((v) => !v);
					if (!shareLink.data) shareLink.mutate(url);
				}}
			>
				{show ? "Hide QR code" : "QR code"}
			</Button>
			{show && shareLink.data && (
				<div className="flex flex-col items-end gap-1">
					<img src={shareLink.data.qrCodeDataUri} alt="QR code linking to this campaign" className="size-32" />
					<input readOnly value={shareLink.data.url} className="w-56 rounded-md border border-slate-gray/30 px-2 py-1 text-xs" onFocus={(e) => e.currentTarget.select()} />
				</div>
			)}
		</div>
	);
}

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

/**
 * Reused as-is (not a separate route) by Coach/Parent's mobile WebView "Fundraising"
 * entry — `canManage` mirrors the same prop convention every other org section panel
 * already uses (households, teams, documents). A coach/parent has real read access to
 * the campaign list/contributions/share-link (backend only requires active
 * membership, not manager role — `CampaignService.list`/`buildShareLink`,
 * `ContributionService.listConfirmed`), just not create/publish/box-pool-setup.
 */
export function CampaignList({ organizationId, canManage = true }: { organizationId: string; canManage?: boolean }) {
	const { data, isLoading, isError, refetch } = useCampaigns(organizationId);
	const { data: teams } = useTeams(organizationId);
	const createCampaign = useCreateCampaign(organizationId);
	const publishCampaign = usePublishCampaign(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedCampaignId, setExpandedCampaignId] = useState<string | null>(null);
	const [templateKey, setTemplateKey] = useState<FundraiserTemplateKey | "">("");

	const {
		register,
		handleSubmit,
		reset,
		setValue,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createCampaignSchema>,
		unknown,
		z.output<typeof createCampaignSchema>
	>({
		resolver: zodResolver(createCampaignSchema),
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

	function applyTemplate(key: FundraiserTemplateKey | "") {
		setTemplateKey(key);
		const template = FUNDRAISER_TEMPLATES.find((t) => t.key === key);
		if (template?.name) setValue("name", template.name);
		if (template?.description_) setValue("description", template.description_);
		if (template?.campaignType) setValue("campaignType", template.campaignType);
	}

	const onSubmit = handleSubmit(async (values) => {
		await createCampaign.mutateAsync({ ...values, templateKey: templateKey || null });
		reset();
		setTemplateKey("");
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					{data ? `${data.totalElements} campaign${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				{canManage && (
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Add campaign"}
					</Button>
				)}
			</div>

			{canManage && showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4"
					noValidate
					aria-label="Create a fundraising campaign"
				>
					<fieldset className="flex flex-col gap-2">
						<legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Start from a template</legend>
						<div className="flex flex-wrap gap-2">
							{FUNDRAISER_TEMPLATES.map((template) => (
								<button
									key={template.label}
									type="button"
									onClick={() => applyTemplate(template.key)}
									aria-pressed={templateKey === template.key}
									className={`rounded-lg border px-3 py-2 text-left text-sm ${templateKey === template.key ? "border-victory-green bg-victory-green/10" : "border-slate-gray/30"}`}
								>
									<span className="block font-medium text-navy dark:text-[#f8fafc]">{template.label}</span>
									<span className="block text-xs text-slate-gray dark:text-[#cbd5e1]">{template.description}</span>
								</button>
							))}
						</div>
					</fieldset>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="campaign-name"
								type="text"
								placeholder="e.g. Spring Trip Fund"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "campaign-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && <p id="campaign-name-error" role="alert" className="text-sm text-error-red">{errors.name.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-slug" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Public URL slug <span aria-hidden>*</span>
							</label>
							<input
								id="campaign-slug"
								type="text"
								placeholder="spring-trip-fund"
								{...register("slug")}
								aria-invalid={!!errors.slug}
								aria-describedby={errors.slug ? "campaign-slug-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.slug && <p id="campaign-slug-error" role="alert" className="text-sm text-error-red">{errors.slug.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-type" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
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
							<label htmlFor="campaign-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
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
							<label htmlFor="campaign-goal" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
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
								aria-describedby={errors.goalAmountMinor ? "campaign-goal-error" : undefined}
								className="min-h-11 w-44 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.goalAmountMinor && <p id="campaign-goal-error" role="alert" className="text-sm text-error-red">{errors.goalAmountMinor.message}</p>}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="campaign-start" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
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
							<label htmlFor="campaign-end" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								End date
							</label>
							<input
								id="campaign-end"
								type="date"
								{...register("endDate")}
								aria-invalid={!!errors.endDate}
								aria-describedby={errors.endDate ? "campaign-end-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.endDate && <p id="campaign-end-error" role="alert" className="text-sm text-error-red">{errors.endDate.message}</p>}
						</div>
						<div className="flex min-w-[16rem] flex-1 flex-col gap-1">
							<label htmlFor="campaign-desc" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
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
				<EmptyState
					title="No campaigns yet"
					description={canManage ? "Create a fundraising campaign for your organization or a team." : "Your organization hasn't started a fundraiser yet."}
				/>
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Fundraising campaigns">
					{data.items.map((campaign) => (
						<li key={campaign.id} className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
										{campaign.name}
										<span className="ml-2 rounded-full bg-ice-white dark:bg-[#0f172a] px-2 py-0.5 text-xs font-medium text-slate-gray dark:text-[#cbd5e1]">
											{campaign.status}
										</span>
									</p>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
										{formatMoneyMinorUnits(campaign.raisedMinor, campaign.currency)} raised of{" "}
										{formatMoneyMinorUnits(campaign.goalAmountMinor, campaign.currency)} goal &middot; /{campaign.slug}
									</p>
								</div>
								<div className="flex shrink-0 items-center gap-2">
									<QrCodeButton organizationId={organizationId} url={`${window.location.origin}/campaigns/${campaign.slug}`} />
									<Button
										type="button"
										variant="secondary"
										onClick={() => setExpandedCampaignId((current) => (current === campaign.id ? null : campaign.id))}
									>
										{expandedCampaignId === campaign.id ? "Hide contributions" : "View contributions"}
									</Button>
									{canManage && campaign.status === "ACTIVE" && (
										<ReminderButton organizationId={organizationId} resourceType="CAMPAIGN" resourceId={campaign.id} label="Send launch notice" />
									)}
									{canManage && campaign.status === "DRAFT" && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => publishCampaign.mutate(campaign.id)}
											disabled={publishCampaign.isPending}
										>
											Publish
										</Button>
									)}
								</div>
							</div>
							{canManage && campaign.templateKey === "BOX_POOL" && (
								<div className="mt-3 border-t border-slate-gray/20 pt-3">
									<BoxPoolManagementPanel organizationId={organizationId} campaignId={campaign.id} />
								</div>
							)}
							{expandedCampaignId === campaign.id && (
								<div className="mt-3 border-t border-slate-gray/20 pt-3">
									<ContributionList organizationId={organizationId} campaignId={campaign.id} />
								</div>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
