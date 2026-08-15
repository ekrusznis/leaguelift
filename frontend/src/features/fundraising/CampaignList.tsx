import { useState, type ReactNode } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits, minorUnitsToMajorInput } from "../../lib/money";
import { BoxPoolManagementPanel } from "../boxpool/BoxPoolManagementPanel";
import { ReminderButton } from "../communications/ReminderButton";
import { useTeams } from "../teams/api";
import {
	useApproveCampaign,
	useCampaignShareLink,
	useCreateCampaign,
	useFundraisingSettings,
	useRejectCampaignApproval,
	useRequestCampaignActivation,
	useUpdateCampaign,
	useUpdateCampaignStatus,
} from "./api";
import { useCampaignSearch, type CampaignSearchSort } from "./searchApi";
import { ContributionList } from "./ContributionList";
import { CAMPAIGN_TYPES, createCampaignSchema, updateCampaignSchema } from "./schema";
import type { Campaign, CampaignStatus, CampaignType, FundraiserTemplateKey } from "./types";

type Template = {
	key: FundraiserTemplateKey;
	label: string;
	description: string;
	campaignType: CampaignType;
	name?: string;
	starterDescription?: string;
	inPerson?: boolean;
};

const FUNDRAISER_TEMPLATES: Template[] = [
	{ key: "GENERAL", label: "General fundraiser", description: "A flexible donation campaign for any team or club need.", campaignType: "ORGANIZATION_GENERAL" },
	{ key: "IN_PERSON_EVENT", label: "In-person event", description: "Fundraise at a real location such as a clinic, sale, dinner, or community event.", campaignType: "SPECIAL_EVENTS", inPerson: true },
	{ key: "SPONSOR_MATCH", label: "Sponsor match", description: "Invite a local sponsor to match supporter donations toward your goal.", campaignType: "SPONSOR_SUPPORTED", name: "Community Match Challenge", starterDescription: "Every contribution helps us move closer to our goal. A community sponsor is helping amplify supporter giving." },
	{ key: "MILESTONE_CHALLENGE", label: "Milestone challenge", description: "Unlock team or coach challenges as fundraising milestones are reached.", campaignType: "SPECIAL_EVENTS", name: "Team Milestone Challenge", starterDescription: "Help us unlock fun team milestones as we work toward our fundraising goal." },
	{ key: "FUNDRAISING_CHALLENGE", label: "Team / family challenge", description: "Use Rally26 attribution links to make fundraising progress fun for families and teams.", campaignType: "TEAM_GENERAL", name: "Team Fundraising Challenge", starterDescription: "Share your Rally26 link and help the team reach our goal together." },
	{ key: "BAKE_SALE", label: "Bake sale", description: "An in-person event starter for a community bake sale.", campaignType: "SPECIAL_EVENTS", inPerson: true, name: "Bake Sale", starterDescription: "Homemade treats — proceeds support the team. Come by, donate, and help us reach our goal!" },
	{ key: "CAR_WASH", label: "Car wash", description: "An in-person event starter for a team car wash.", campaignType: "SPECIAL_EVENTS", inPerson: true, name: "Car Wash", starterDescription: "Bring your car by for a wash — all proceeds support the team. Volunteers welcome!" },
];

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

const STATUS_LABELS: Record<CampaignStatus, string> = {
	DRAFT: "Draft",
	PENDING_APPROVAL: "Awaiting owner approval",
	SCHEDULED: "Scheduled",
	ACTIVE: "Active",
	ENDED: "Ended",
	CLOSED: "Closed",
	COMPLETED: "Closed (legacy)",
	ARCHIVED: "Archived",
};

const FILTER_STATUSES: CampaignStatus[] = [
	"DRAFT",
	"PENDING_APPROVAL",
	"SCHEDULED",
	"ACTIVE",
	"ENDED",
	"CLOSED",
	"ARCHIVED",
];

function permissionsFor(campaign: Campaign, canManageOrganization: boolean) {
	return campaign.permissions ?? {
		canEdit: canManageOrganization && !["ENDED", "CLOSED", "COMPLETED", "ARCHIVED"].includes(campaign.status),
		canRequestActivation: canManageOrganization && campaign.status === "DRAFT",
		canApprove: false,
		canReturnToDraft: false,
		canClose: false,
		canArchive: false,
		canManageBoxPool: canManageOrganization,
	};
}

function toDateInput(date: Date) {
	const year = date.getFullYear();
	const month = String(date.getMonth() + 1).padStart(2, "0");
	const day = String(date.getDate()).padStart(2, "0");
	return `${year}-${month}-${day}`;
}

function QrCodeButton({ organizationId, url }: { organizationId: string; url: string }) {
	const shareLink = useCampaignShareLink(organizationId);
	const [show, setShow] = useState(false);
	return (
		<div className="flex flex-col items-end gap-2">
			<Button type="button" variant="secondary" onClick={() => { setShow((v) => !v); if (!shareLink.data) shareLink.mutate(url); }}>
				{show ? "Hide QR" : "QR & share"}
			</Button>
			{show && shareLink.data && (
				<div className="flex flex-col items-end gap-1 rounded-lg border border-slate-gray/20 bg-pure-white p-2 dark:bg-[#0f172a]">
					<img src={shareLink.data.qrCodeDataUri} alt="QR code linking to this fundraiser" className="size-32" />
					<input readOnly value={shareLink.data.url} className="w-60 rounded-md border border-slate-gray/30 bg-transparent px-2 py-1 text-xs" onFocus={(e) => e.currentTarget.select()} />
				</div>
			)}
		</div>
	);
}

export function CampaignList({
	organizationId,
	canCreate = false,
	canApprove = false,
	canManageOrganization = false,
}: {
	organizationId: string;
	canCreate?: boolean;
	canApprove?: boolean;
	canManageOrganization?: boolean;
}) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<CampaignStatus | "">("");
	const [campaignType, setCampaignType] = useState<CampaignType | "">("");
	const [teamId, setTeamId] = useState("");
	const [sort, setSort] = useState<CampaignSearchSort>("NEWEST");
	const campaigns = useCampaignSearch(organizationId, { page, size, q: query, status, campaignType, teamId, sort });
	const pendingCampaigns = useCampaignSearch(
		organizationId,
		{ page: 0, size: 100, status: "PENDING_APPROVAL", sort: "NEWEST" },
	);
	const settings = useFundraisingSettings(organizationId);
	const { data: teams } = useTeams(organizationId);
	const createCampaign = useCreateCampaign(organizationId);
	const requestActivation = useRequestCampaignActivation(organizationId);
	const approveCampaign = useApproveCampaign(organizationId);
	const rejectApproval = useRejectCampaignApproval(organizationId);
	const updateStatus = useUpdateCampaignStatus(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedCampaignId, setExpandedCampaignId] = useState<string | null>(null);
	const [editingCampaignId, setEditingCampaignId] = useState<string | null>(null);
	const [templateKey, setTemplateKey] = useState<FundraiserTemplateKey>("GENERAL");
	const form = useForm<z.input<typeof createCampaignSchema>, unknown, z.output<typeof createCampaignSchema>>({
		resolver: zodResolver(createCampaignSchema),
		defaultValues: {
			teamId: "",
			name: "",
			slug: "",
			description: "",
			campaignType: "ORGANIZATION_GENERAL",
			goalAmountDollars: 0,
			currency: "USD",
			startDate: toDateInput(new Date()),
			endDate: "",
			eventLocationName: "",
			eventAddress: "",
		},
	});
	const chosenTemplate = FUNDRAISER_TEMPLATES.find((item) => item.key === templateKey) ?? FUNDRAISER_TEMPLATES[0];

	function applyTemplate(template: Template) {
		setTemplateKey(template.key);
		form.setValue("campaignType", template.campaignType);
		if (template.name) form.setValue("name", template.name);
		if (template.starterDescription) form.setValue("description", template.starterDescription);
	}

	function applyDuration(days: number) {
		const start = form.getValues("startDate") ? new Date(`${form.getValues("startDate")}T12:00:00`) : new Date();
		const end = new Date(start);
		end.setDate(end.getDate() + days);
		form.setValue("startDate", toDateInput(start));
		form.setValue("endDate", toDateInput(end), { shouldValidate: true });
	}

	const onSubmit = form.handleSubmit(async (values) => {
		await createCampaign.mutateAsync({ ...values, templateKey });
		form.reset();
		setTemplateKey("GENERAL");
		setShowForm(false);
	});

	if (campaigns.isLoading) return <LoadingState label="Loading fundraisers…" />;
	if (campaigns.isError || !campaigns.data) return <ErrorState message="Could not load fundraisers." onRetry={() => campaigns.refetch()} />;

	const pending = canApprove ? (pendingCampaigns.data?.items ?? []) : [];
	const ownerApprovalRequired = settings.data?.requireOwnerApproval ?? true;
	const hasFilters = !!status || !!campaignType || !!teamId;

	return (
		<div className="flex flex-col gap-5">
			<div>
				{settings.data && (
					<p className="mb-3 text-xs text-slate-gray dark:text-[#94a3b8]">
						Activation policy: {ownerApprovalRequired ? "owner approval required for non-owner creators" : "creators may activate without owner approval"}.
					</p>
				)}
				<ListToolbar
					searchValue={query}
					onSearchChange={(value) => { setQuery(value); setPage(0); }}
					searchPlaceholder="Search fundraiser name, description, venue, or address"
					resultCount={campaigns.data.totalElements}
					sortValue={sort}
					sortOptions={[
						{ value: "NEWEST", label: "Newest" },
						{ value: "NAME_ASC", label: "Name A–Z" },
						{ value: "START_DATE_ASC", label: "Start date" },
						{ value: "END_DATE_ASC", label: "End date" },
						{ value: "RAISED_DESC", label: "Most raised" },
						{ value: "GOAL_DESC", label: "Largest goal" },
					]}
					onSortChange={(value) => { setSort(value as CampaignSearchSort); setPage(0); }}
					hasActiveFilters={hasFilters}
					onClear={() => {
						setQuery("");
						setStatus("");
						setCampaignType("");
						setTeamId("");
						setSort("NEWEST");
						setPage(0);
					}}
					actions={
						canCreate ? (
							<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
								{showForm ? "Cancel" : "Create fundraiser"}
							</Button>
						) : undefined
					}
					filters={
						<>
							<select
								aria-label="Filter fundraiser status"
								value={status}
								onChange={(event) => { setStatus(event.target.value as CampaignStatus | ""); setPage(0); }}
								className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
							>
								<option value="">All statuses</option>
								{FILTER_STATUSES.map((value) => <option key={value} value={value}>{STATUS_LABELS[value]}</option>)}
							</select>
							<select
								aria-label="Filter fundraiser type"
								value={campaignType}
								onChange={(event) => { setCampaignType(event.target.value as CampaignType | ""); setPage(0); }}
								className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
							>
								<option value="">All fundraiser types</option>
								{CAMPAIGN_TYPES.map((type) => <option key={type} value={type}>{CAMPAIGN_TYPE_LABELS[type]}</option>)}
							</select>
							<select
								aria-label="Filter fundraiser team"
								value={teamId}
								onChange={(event) => { setTeamId(event.target.value); setPage(0); }}
								className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
							>
								<option value="">All teams</option>
								{teams?.items.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
							</select>
						</>
					}
				/>
			</div>

			{canApprove && pending.length > 0 && (
				<section className="rounded-xl border border-championship-gold/50 bg-championship-gold/10 p-4" aria-label="Fundraisers awaiting owner approval">
					<h3 className="font-heading font-semibold text-navy dark:text-[#f8fafc]">Awaiting your approval ({pendingCampaigns.data?.totalElements ?? pending.length})</h3>
					<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Review these before they become public and accept contributions.</p>
					<div className="mt-3 flex flex-col gap-2">
						{pending.map((campaign) => (
							<div key={campaign.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-pure-white p-3 dark:bg-[#111827]">
								<div>
									<p className="font-medium text-navy dark:text-[#f8fafc]">{campaign.name}</p>
									<p className="text-xs text-slate-gray dark:text-[#94a3b8]">Submitted {campaign.submittedAt ? new Date(campaign.submittedAt).toLocaleString() : "for review"}</p>
								</div>
								<div className="flex flex-wrap gap-2">
									<Button type="button" variant="secondary" disabled={rejectApproval.isPending} onClick={() => rejectApproval.mutate(campaign.id)}>Return to draft</Button>
									<Button type="button" disabled={approveCampaign.isPending} onClick={() => approveCampaign.mutate(campaign.id)}>Approve & activate</Button>
								</div>
							</div>
						))}
					</div>
				</section>
			)}

			{canCreate && showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-4 rounded-xl border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]" noValidate>
					<fieldset>
						<legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Choose a fundraiser style</legend>
						<div className="mt-2 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
							{FUNDRAISER_TEMPLATES.map((template) => (
								<button key={template.key} type="button" onClick={() => applyTemplate(template)} aria-pressed={templateKey === template.key} className={`rounded-lg border px-3 py-3 text-left text-sm ${templateKey === template.key ? "border-victory-green bg-victory-green/10" : "border-slate-gray/30 bg-pure-white dark:bg-[#111827]"}`}>
									<span className="block font-medium text-navy dark:text-[#f8fafc]">{template.label}</span>
									<span className="mt-1 block text-xs text-slate-gray dark:text-[#cbd5e1]">{template.description}</span>
								</button>
							))}
						</div>
					</fieldset>
					<div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
						<FormField label="Name" error={form.formState.errors.name?.message}><input {...form.register("name")} placeholder="Spring Trip Fund" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
						<FormField label="Public URL slug" error={form.formState.errors.slug?.message}><input {...form.register("slug")} placeholder="spring-trip-fund" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
						<FormField label="Campaign type"><select {...form.register("campaignType")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">{CAMPAIGN_TYPES.map((type) => <option key={type} value={type}>{CAMPAIGN_TYPE_LABELS[type]}</option>)}</select></FormField>
						<FormField label="Team (optional)"><select {...form.register("teamId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"><option value="">Organization-wide</option>{teams?.items.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></FormField>
						<FormField label="Fundraising goal ($)" error={form.formState.errors.goalAmountDollars?.message}><input type="number" min={0} step="0.01" inputMode="decimal" {...form.register("goalAmountDollars")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
						<FormField label="Start date"><input type="date" {...form.register("startDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
						<div className="flex flex-col gap-1"><span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Duration</span><div className="flex min-h-11 flex-wrap gap-1"><Button type="button" variant="secondary" onClick={() => applyDuration(7)}>7 days</Button><Button type="button" variant="secondary" onClick={() => applyDuration(30)}>30 days</Button><Button type="button" variant="secondary" onClick={() => applyDuration(90)}>90 days</Button></div></div>
						<FormField label="End date / custom" error={form.formState.errors.endDate?.message}><input type="date" {...form.register("endDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
						{chosenTemplate.inPerson && <><FormField label="Event / venue name" error={form.formState.errors.eventLocationName?.message}><input {...form.register("eventLocationName")} placeholder="Community Center" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField><FormField label="Address" error={form.formState.errors.eventAddress?.message}><input {...form.register("eventAddress")} placeholder="123 Main St, Town, NJ" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField></>}
						<div className="md:col-span-2 xl:col-span-4"><FormField label="Description"><textarea rows={4} {...form.register("description")} className="w-full rounded-md border border-slate-gray/30 px-3 py-2" /></FormField></div>
					</div>
					{createCampaign.isError && <p role="alert" className="text-sm text-error-red">Could not create the fundraiser. Check your team access and required event location, then try again.</p>}
					<div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => { form.reset(); setShowForm(false); }}>Cancel</Button><Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? "Creating…" : "Create fundraiser"}</Button></div>
				</form>
			)}

			{campaigns.data.items.length === 0 && !showForm && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No fundraisers yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: canCreate ? "Create a fundraiser for your organization or a team." : "Your organization hasn't started a fundraiser yet."
					}
				/>
			)}

			{campaigns.data.items.length > 0 && (
				<ul className="flex flex-col gap-3" aria-label="Fundraising campaigns">
					{campaigns.data.items.map((campaign) => {
						const permissions = permissionsFor(campaign, canManageOrganization);
						return (
							<li key={campaign.id} className="rounded-xl border border-slate-gray/20 bg-pure-white p-4 dark:bg-[#111827]">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div className="min-w-0 flex-1">
										<div className="flex flex-wrap items-center gap-2">
											<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{campaign.name}</p>
											<span className="rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]">{STATUS_LABELS[campaign.status]}</span>
										</div>
										<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">{formatMoneyMinorUnits(campaign.raisedMinor, campaign.currency)} raised of {formatMoneyMinorUnits(campaign.goalAmountMinor, campaign.currency)} goal</p>
										{campaign.startDate || campaign.endDate ? <p className="mt-1 text-xs text-slate-gray dark:text-[#94a3b8]">{campaign.startDate ? `Starts ${campaign.startDate}` : "Starts immediately"}{campaign.endDate ? ` · Ends ${campaign.endDate}` : ""}</p> : null}
										{campaign.eventLocationName || campaign.eventAddress ? <p className="mt-1 text-xs text-slate-gray dark:text-[#94a3b8]">📍 {[campaign.eventLocationName, campaign.eventAddress].filter(Boolean).join(" · ")}</p> : null}
									</div>
									<div className="flex flex-wrap items-center justify-end gap-2">
										<QrCodeButton organizationId={organizationId} url={`${window.location.origin}/campaigns/${campaign.slug}`} />
										<Link to={`/app/organizations/${organizationId}/fundraising/${campaign.id}/flyer`} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 px-3 text-sm font-medium text-navy dark:text-[#f8fafc]">Print flyer</Link>
										<Button type="button" variant="secondary" onClick={() => setExpandedCampaignId((current) => current === campaign.id ? null : campaign.id)}>{expandedCampaignId === campaign.id ? "Hide contributions" : "Contributions"}</Button>
										{permissions.canEdit && <Button type="button" variant="secondary" onClick={() => setEditingCampaignId((current) => current === campaign.id ? null : campaign.id)}>{editingCampaignId === campaign.id ? "Cancel edit" : "Edit"}</Button>}
										{permissions.canRequestActivation && <Button type="button" disabled={requestActivation.isPending} onClick={() => requestActivation.mutate(campaign.id)}>{ownerApprovalRequired && !permissions.canApprove ? "Submit for approval" : "Activate"}</Button>}
										{permissions.canApprove && <Button type="button" disabled={approveCampaign.isPending} onClick={() => approveCampaign.mutate(campaign.id)}>Approve</Button>}
										{permissions.canReturnToDraft && <Button type="button" variant="secondary" onClick={() => rejectApproval.mutate(campaign.id)}>Return to draft</Button>}
										{permissions.canClose && <Button type="button" variant="secondary" onClick={() => updateStatus.mutate({ campaignId: campaign.id, status: "CLOSED" })}>Close</Button>}
										{permissions.canArchive && <Button type="button" variant="secondary" onClick={() => updateStatus.mutate({ campaignId: campaign.id, status: "ARCHIVED" })}>Archive</Button>}
										{canManageOrganization && campaign.status === "ACTIVE" && <ReminderButton organizationId={organizationId} resourceType="CAMPAIGN" resourceId={campaign.id} label="Send launch notice" />}
									</div>
								</div>
								{editingCampaignId === campaign.id && <CampaignEditForm organizationId={organizationId} campaign={campaign} onDone={() => setEditingCampaignId(null)} />}
								{permissions.canManageBoxPool && campaign.templateKey === "BOX_POOL" && <div className="mt-3 border-t border-slate-gray/20 pt-3"><p className="mb-2 text-xs text-championship-gold">Legacy paid box pool — historical campaigns only.</p><BoxPoolManagementPanel organizationId={organizationId} campaignId={campaign.id} /></div>}
								{expandedCampaignId === campaign.id && <div className="mt-3 border-t border-slate-gray/20 pt-3"><ContributionList organizationId={organizationId} campaignId={campaign.id} /></div>}
							</li>
						);
					})}
				</ul>
			)}

			<Pagination
				page={page}
				size={size}
				totalElements={campaigns.data.totalElements}
				onPageChange={setPage}
				onSizeChange={(value) => { setSize(value); setPage(0); }}
			/>
		</div>
	);
}

function CampaignEditForm({ organizationId, campaign, onDone }: { organizationId: string; campaign: Campaign; onDone: () => void }) {
	const update = useUpdateCampaign(organizationId);
	const form = useForm<z.input<typeof updateCampaignSchema>, unknown, z.output<typeof updateCampaignSchema>>({
		resolver: zodResolver(updateCampaignSchema),
		defaultValues: {
			name: campaign.name,
			description: campaign.description ?? "",
			goalAmountDollars: Number(minorUnitsToMajorInput(campaign.goalAmountMinor, campaign.currency)),
			startDate: campaign.startDate ?? "",
			endDate: campaign.endDate ?? "",
			eventLocationName: campaign.eventLocationName ?? "",
			eventAddress: campaign.eventAddress ?? "",
		},
	});
	const submit = form.handleSubmit(async (values) => {
  	await update.mutateAsync({
  		campaignId: campaign.id,
  		values,
  	});
  	onDone();
  });
	return (
		<form onSubmit={submit} className="mt-4 grid gap-3 border-t border-slate-gray/20 pt-4 md:grid-cols-2" aria-label={`Edit ${campaign.name}`}>
			<FormField label="Name" error={form.formState.errors.name?.message}><input {...form.register("name")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
			<FormField label="Goal ($)" error={form.formState.errors.goalAmountDollars?.message}><input type="number" min={0} step="0.01" {...form.register("goalAmountDollars")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
			<FormField label="Start date"><input type="date" {...form.register("startDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
			<FormField label="End date" error={form.formState.errors.endDate?.message}><input type="date" {...form.register("endDate")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
			<FormField label="Event / venue name"><input {...form.register("eventLocationName")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
			<FormField label="Address"><input {...form.register("eventAddress")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" /></FormField>
			<div className="md:col-span-2"><FormField label="Description"><textarea rows={3} {...form.register("description")} className="w-full rounded-md border border-slate-gray/30 px-3 py-2" /></FormField></div>
			{update.isError && <p role="alert" className="md:col-span-2 text-sm text-error-red">Could not update this fundraiser.</p>}
			<div className="flex justify-end gap-2 md:col-span-2"><Button type="button" variant="secondary" onClick={onDone}>Cancel</Button><Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? "Saving…" : "Save changes"}</Button></div>
		</form>
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
