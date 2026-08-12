import { Link } from "react-router-dom";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { navItemsFor } from "../registry/navRegistry";
import { visibleWidgetIds } from "../registry/widgetRegistry";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { Pill } from "../components/Pill";
import { ProgressBar } from "../components/ProgressBar";
import { CardQuery } from "../components/CardQuery";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";
import { adultAvatars, sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths } from "../../routes/appPaths";
import {
	useParentActiveFundraisers,
	useParentAthletes,
	useParentFamilySchedule,
	useParentOutstandingBalance,
	useParentOverview,
	useParentRecentOrders,
} from "../api";
import {
	ShieldIcon as GuardianIcon,
} from "../icons";
import { HouseholdDocumentsPanel } from "../../features/documents/HouseholdDocumentsPanel";
import { FamilyCreditsCard } from "../../features/credit/FamilyCreditsCard";
import { AttributionLinkPanel } from "../../features/credit/AttributionLinkPanel";
import { useState } from "react";

/** Parent/guardian dashboard, wired to real per-card API calls (DESIGN-DOC.md section 10.1/10.2). */
export function ParentDashboard({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { user } = useAuth();
	const overview = useParentOverview(organizationId, householdId);
	const athletes = useParentAthletes(organizationId, householdId);
	const familySchedule = useParentFamilySchedule(organizationId, householdId);
	const outstandingBalance = useParentOutstandingBalance(organizationId, householdId);
	const activeFundraisers = useParentActiveFundraisers(organizationId, householdId);
	const recentOrders = useParentRecentOrders(organizationId, householdId);

	// Nav/widget visibility are registries filtered by real capabilities for this
	// household (DESIGN-DOC.md section 10.2/10.3) — not hardcoded arrays. Parent
	// items are unconditional, matching this dashboard's pre-migration nav/cards
	// exactly.
	const contexts = useContexts();
	const householdCapabilities = capabilitiesFor(contexts.data, "HOUSEHOLD", householdId);
	const navItems: DashNavItem[] = navItemsFor("HOUSEHOLD", householdCapabilities, { organizationId, householdId }).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));
	const visibleWidgets = visibleWidgetIds("HOUSEHOLD", householdCapabilities);
	const eventSearch = new URLSearchParams({ householdId, returnTo: appPaths.dashboard() });

	return (
		<DashboardShell
			contextIcon={<GuardianIcon className="size-4" />}
			contextName={overview.data?.householdName ?? "Household"}
			contextRole="Guardian"
			navItems={navItems}
			userName={user?.displayName ?? "Account"}
			userAvatarSrc={adultAvatars.guardianSarah}
			promo={{
				heading: "Stay connected.",
				copy: "Enable SMS alerts for schedule updates, payments, and more.",
				linkLabel: "Manage Preferences",
				to: appPaths.household(organizationId, householdId, "profile"),
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader
				heading="Family Overview"
				description="Track schedules, fees, fundraising, and documents for all linked athletes."
				actions={
					<>
						<PrimaryButton icon="none" to={appPaths.household(organizationId, householdId, "fees")}>View Fees</PrimaryButton>
						<SecondaryLightButton to={appPaths.dashboard("parent-fundraising")}>View Fundraisers</SecondaryLightButton>
					</>
				}
			/>

			<div className="grid gap-5 lg:grid-cols-3">
				{visibleWidgets.has("parent.athletes") && (
					<CardQuery query={athletes} loadingLabel="Loading athletes…" isEmpty={(items) => items.length === 0} emptyTitle="No linked athletes yet">
						{(items) => (
							<>
								{items.map((athlete) => (
									<DashCard key={athlete.participantId} id={`parent-athlete-${athlete.participantId}`}>
										<p className="font-heading font-bold text-navy-900 dark:text-[#f8fafc]">{athlete.name}</p>
										{athlete.teamNames.length > 0 ? (
											<div className="mt-2 flex flex-wrap gap-2">
												{athlete.teamNames.map((teamName) => (
													<Pill key={teamName} tone="neutral">
														{teamName}
													</Pill>
												))}
											</div>
										) : (
											<p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]">No team assignments yet</p>
										)}
									</DashCard>
								))}
							</>
						)}
					</CardQuery>
				)}

				{visibleWidgets.has("parent.family-schedule") && (
					<DashCard id="parent-schedule" title="Family Schedule" action={{ label: "View full schedule", to: appPaths.householdEvents(organizationId, householdId) }}>
						<CardQuery query={familySchedule} loadingLabel="Loading schedule…" isEmpty={(items) => items.length === 0} emptyTitle="No upcoming events">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((event) => (
										<li key={event.id} className="flex items-center gap-3">
											<div className="flex w-12 shrink-0 flex-col items-center rounded-lg bg-ice-50 dark:bg-[#0f172a] py-1 text-xs font-semibold text-slate-500 dark:text-[#cbd5e1]">
												<span>{event.day}</span>
												<span className="font-heading text-base text-navy-900 dark:text-[#f8fafc]">{event.date}</span>
											</div>
											<div className="min-w-0 flex-1">
												<Link to={appPaths.event(organizationId, event.id, eventSearch)} className="truncate font-medium text-navy-900 dark:text-[#f8fafc] hover:text-green-600 hover:underline">{event.title}</Link>
												<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{event.subtitle}</p>
											</div>
											<div className="text-right text-xs">
												<p className="font-semibold text-navy-900 dark:text-[#f8fafc]">{event.time}</p>
												{event.tag && <p className={event.tag === "Home" ? "text-green-600" : "text-info-600"}>{event.tag}</p>}
											</div>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("parent.outstanding-balance") && (
					<DashCard
						title="Outstanding Balance"
						action={{ label: "View details", to: appPaths.household(organizationId, householdId, "fees") }}
					>
						<CardQuery query={outstandingBalance} loadingLabel="Loading balance…">
							{(data) => (
								<>
									<div className="flex items-center justify-between">
										<div>
											<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Total Due</p>
											<p className="font-heading text-3xl font-extrabold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.totalOutstandingMinor, data.currency)}</p>
										</div>
										{data.totalOutstandingMinor > 0 && <Pill tone="error">Outstanding</Pill>}
									</div>
									{data.lineItems.length > 0 ? (
										<ul className="mt-4 flex flex-col gap-2 border-t border-slate-200 dark:border-[#334155] pt-3">
											{data.lineItems.map((line, index) => (
												<li key={index} className="flex justify-between gap-3 text-sm text-slate-600 dark:text-[#cbd5e1]">
													<span className="min-w-0 flex-1 break-words">{line.description}</span>
													<span className="shrink-0 font-medium text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(line.balanceMinor, data.currency)}</span>
												</li>
											))}
										</ul>
									) : (
										<p className="mt-4 border-t border-slate-200 dark:border-[#334155] pt-3 text-sm text-slate-500 dark:text-[#cbd5e1]">No fees outstanding.</p>
									)}
								</>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("parent.family-credits") && (
					<DashCard title="Family Credits">
						<FamilyCreditsCard organizationId={organizationId} householdId={householdId} />
					</DashCard>
				)}

				{visibleWidgets.has("parent.active-fundraisers") && (
					<DashCard id="parent-fundraising" title="Active Fundraisers">
						<CardQuery query={activeFundraisers} loadingLabel="Loading fundraisers…" isEmpty={(items) => items.length === 0} emptyTitle="No active fundraisers">
							{(items) => (
								<ul className="flex flex-col gap-4">
									{items.map((fundraiser) => (
										<li key={fundraiser.campaignId}>
											<div className="flex items-center justify-between text-sm">
												<span className="font-medium text-navy-900 dark:text-[#f8fafc]">{fundraiser.name}</span>
												<Pill tone="success">Active</Pill>
											</div>
											<p className="mt-1 text-xs text-slate-500 dark:text-[#cbd5e1]">
												{formatMoneyMinorUnits(fundraiser.raisedMinor, fundraiser.currency)} raised of {formatMoneyMinorUnits(fundraiser.goalMinor, fundraiser.currency)}
												{fundraiser.isRaisedDemoData && " (demo)"}
											</p>
											<div className="mt-1">
												<ProgressBar percent={(fundraiser.raisedMinor / fundraiser.goalMinor) * 100} />
											</div>
											<FundraiserAttributionToggle organizationId={organizationId} householdId={householdId} campaignId={fundraiser.campaignId} campaignSlug={fundraiser.slug} />
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("parent.orders") && (
					<DashCard id="parent-orders" title="Recent Orders">
						<CardQuery query={recentOrders} loadingLabel="Loading orders…" isEmpty={(items) => items.length === 0} emptyTitle="No storefront orders yet">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((order) => (
										<li key={order.id} className="flex items-center justify-between gap-3 border-b border-slate-200 dark:border-[#334155] pb-3 last:border-0 last:pb-0">
											<div className="min-w-0 flex-1">
												<p className="truncate font-medium text-navy-900 dark:text-[#f8fafc]">{order.productName}</p>
												<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{order.orderNumber} · {order.orderedAt}</p>
											</div>
											<div className="shrink-0 text-right">
												<Pill tone={order.status === "DELIVERED" ? "success" : order.status === "NEEDS_ATTENTION" || order.status === "CANCELED" ? "error" : "neutral"}>{order.status}</Pill>
												{order.creditGrantedMinor != null && (
													<p className="mt-1 text-xs text-slate-500 dark:text-[#cbd5e1]">
														{order.creditStatus === "REVOKED" ? "Credit reversed" : `+${formatMoneyMinorUnits(order.creditGrantedMinor, "USD")} credit`}
													</p>
												)}
											</div>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("parent.documents") && (
					<DashCard id="parent-documents" title="Documents" action={{ label: "View documents", to: appPaths.household(organizationId, householdId, "documents") }}>
						<HouseholdDocumentsPanel organizationId={organizationId} householdId={householdId} canAcknowledge />
					</DashCard>
				)}


			</div>
		</DashboardShell>
	);
}

function FundraiserAttributionToggle({ organizationId, householdId, campaignId, campaignSlug }: { organizationId: string; householdId: string; campaignId: string; campaignSlug: string }) {
	const [showLink, setShowLink] = useState(false);

	return (
		<div className="mt-2">
			<button type="button" onClick={() => setShowLink((value) => !value)} className="text-xs font-medium text-green-600 hover:underline">
				{showLink ? "Hide my sharing link" : "Get my sharing link"}
			</button>
			{showLink && (
				<AttributionLinkPanel organizationId={organizationId} householdId={householdId} campaignId={campaignId} campaignSlug={campaignSlug} />
			)}
		</div>
	);
}
