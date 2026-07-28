import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { Pill } from "../components/Pill";
import { ProgressBar } from "../components/ProgressBar";
import { IconBadge } from "../components/IconBadge";
import { CardQuery } from "../components/CardQuery";
import { PrimaryButton, SecondaryLightButton, TextButton } from "../../marketing/components/buttons";
import { adultAvatars, sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { formatMoneyMinorUnits } from "../../lib/money";
import {
	useParentActiveFundraisers,
	useParentAthletes,
	useParentFamilyCredits,
	useParentFamilySchedule,
	useParentOrganizationUpdates,
	useParentOutstandingBalance,
	useParentOverview,
	useParentRecentOrders,
	useParentRequiredActions,
} from "../api";
import {
	CalendarIcon,
	DollarIcon,
	GiftIcon,
	HeartHandshakeIcon,
	HomeIcon,
	MegaphoneIcon,
	PackageIcon,
	ShieldIcon as GuardianIcon,
	UserIcon,
	UsersIcon,
} from "../icons";

const NAV_ITEMS: DashNavItem[] = [
	{ icon: <HomeIcon className="size-5" />, label: "Overview", active: true },
	{ icon: <UsersIcon className="size-5" />, label: "My Athletes" },
	{ icon: <CalendarIcon className="size-5" />, label: "Schedule" },
	{ icon: <DollarIcon className="size-5" />, label: "Fees & Payments" },
	{ icon: <GiftIcon className="size-5" />, label: "Credits" },
	{ icon: <HeartHandshakeIcon className="size-5" />, label: "Fundraising" },
	{ icon: <PackageIcon className="size-5" />, label: "Orders" },
	{ icon: <UserIcon className="size-5" />, label: "Profile" },
];

/** Parent/guardian dashboard, wired to real per-card API calls (DESIGN-DOC.md section 10.1/10.2). */
export function ParentDashboard({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { user } = useAuth();
	const overview = useParentOverview(organizationId, householdId);
	const athletes = useParentAthletes(organizationId, householdId);
	const familySchedule = useParentFamilySchedule(organizationId, householdId);
	const outstandingBalance = useParentOutstandingBalance(organizationId, householdId);
	const familyCredits = useParentFamilyCredits(organizationId, householdId);
	const activeFundraisers = useParentActiveFundraisers(organizationId, householdId);
	const recentOrders = useParentRecentOrders(organizationId, householdId);
	const requiredActions = useParentRequiredActions(organizationId, householdId);
	const organizationUpdates = useParentOrganizationUpdates(organizationId, householdId);

	return (
		<DashboardShell
			contextIcon={<GuardianIcon className="size-4" />}
			contextName={overview.data?.householdName ?? "Household"}
			contextRole="Guardian"
			navItems={NAV_ITEMS}
			showSearch
			userName={user?.displayName ?? "Account"}
			userAvatarSrc={adultAvatars.guardianSarah}
			promo={{
				heading: "Stay connected.",
				copy: "Enable SMS alerts for schedule updates, payments, and more.",
				linkLabel: "Manage Preferences",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader
				heading="Family Overview"
				description="Track schedules, fees, credits, and orders for all linked athletes."
				actions={
					<>
						<PrimaryButton icon="none">Pay Fees</PrimaryButton>
						<SecondaryLightButton>Share Fundraiser</SecondaryLightButton>
					</>
				}
			/>

			<div className="grid gap-5 lg:grid-cols-3">
				<CardQuery query={athletes} loadingLabel="Loading athletes…" isEmpty={(items) => items.length === 0} emptyTitle="No linked athletes yet">
					{(items) => (
						<>
							{items.map((athlete) => (
								<DashCard key={athlete.participantId}>
									<p className="font-heading font-bold text-navy-900">{athlete.name}</p>
									{athlete.teamNames.length > 0 ? (
										<div className="mt-2 flex flex-wrap gap-2">
											{athlete.teamNames.map((teamName) => (
												<Pill key={teamName} tone="neutral">
													{teamName}
												</Pill>
											))}
										</div>
									) : (
										<p className="mt-2 text-xs text-slate-500">No team assignments yet</p>
									)}
								</DashCard>
							))}
						</>
					)}
				</CardQuery>

				<DashCard title="Family Schedule" action={{ label: "View full schedule" }}>
					<CardQuery query={familySchedule} loadingLabel="Loading schedule…" isEmpty={(items) => items.length === 0} emptyTitle="No upcoming events">
						{(items) => (
							<ul className="flex flex-col gap-3">
								{items.map((event) => (
									<li key={event.id} className="flex items-center gap-3">
										<div className="flex w-12 shrink-0 flex-col items-center rounded-lg bg-ice-50 py-1 text-xs font-semibold text-slate-500">
											<span>{event.day}</span>
											<span className="font-heading text-base text-navy-900">{event.date}</span>
										</div>
										<div className="min-w-0 flex-1">
											<p className="truncate font-medium text-navy-900">{event.title}</p>
											<p className="text-xs text-slate-500">{event.subtitle}</p>
										</div>
										<div className="text-right text-xs">
											<p className="font-semibold text-navy-900">{event.time}</p>
											{event.tag && <p className={event.tag === "Home" ? "text-green-600" : "text-info-600"}>{event.tag}</p>}
										</div>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Outstanding Balance" action={{ label: "View details & pay" }}>
					<CardQuery query={outstandingBalance} loadingLabel="Loading balance…">
						{(data) => (
							<>
								<div className="flex items-center justify-between">
									<div>
										<p className="text-xs text-slate-500">Total Due</p>
										<p className="font-heading text-3xl font-extrabold text-navy-900">{formatMoneyMinorUnits(data.totalOutstandingMinor, data.currency)}</p>
									</div>
									{data.totalOutstandingMinor > 0 && <Pill tone="error">Outstanding</Pill>}
								</div>
								{data.lineItems.length > 0 ? (
									<ul className="mt-4 flex flex-col gap-2 border-t border-slate-200 pt-3">
										{data.lineItems.map((line, index) => (
											<li key={index} className="flex justify-between text-sm text-slate-600">
												<span>{line.description}</span>
												<span className="font-medium text-navy-900">{formatMoneyMinorUnits(line.amountMinor, data.currency)}</span>
											</li>
										))}
									</ul>
								) : (
									<p className="mt-4 border-t border-slate-200 pt-3 text-sm text-slate-500">No fees outstanding.</p>
								)}
								{data.isApproximate && <p className="mt-3 text-xs text-slate-400">Approximate — not yet net of partial payments.</p>}
							</>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Family Credits" action={{ label: "View credits history" }}>
					<CardQuery query={familyCredits} loadingLabel="Loading credits…">
						{(data) => (
							<>
								<ul className="flex flex-col gap-3">
									<li className="flex items-center justify-between">
										<span className="flex items-center gap-2 text-sm text-slate-600">
											<GiftIcon className="size-4 text-gold-500" /> Pending Credits
										</span>
										<span className="font-semibold text-navy-900">{formatMoneyMinorUnits(data.pendingMinor, data.currency)}</span>
									</li>
									<li className="flex items-center justify-between">
										<span className="flex items-center gap-2 text-sm text-slate-600">
											<GiftIcon className="size-4 text-green-600" /> Available Credits
										</span>
										<span className="font-semibold text-green-600">{formatMoneyMinorUnits(data.availableMinor, data.currency)}</span>
									</li>
									<li className="flex items-center justify-between">
										<span className="flex items-center gap-2 text-sm text-slate-600">
											<GiftIcon className="size-4 text-info-600" /> Applied This Season
										</span>
										<span className="font-semibold text-navy-900">{formatMoneyMinorUnits(data.appliedThisSeasonMinor, data.currency)}</span>
									</li>
								</ul>
								{data.isDemoData && <p className="mt-3 text-xs text-slate-400">Demo data — credit rules aren't built yet.</p>}
							</>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Recent Orders" action={{ label: "View all orders" }}>
					<CardQuery query={recentOrders} loadingLabel="Loading orders…" isEmpty={(items) => items.length === 0} emptyTitle="No orders yet">
						{(items) => (
							<ul className="flex flex-col gap-3">
								{items.map((order) => (
									<li key={order.id} className="flex items-center gap-3">
										<span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-ice-50 text-slate-400">
											<PackageIcon className="size-5" />
										</span>
										<div className="min-w-0 flex-1">
											<p className="truncate text-sm font-medium text-navy-900">{order.productName}</p>
											<p className="text-xs text-slate-500">
												{order.orderNumber} · {order.orderedAt}
											</p>
										</div>
										<Pill tone="info">{order.status}</Pill>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Active Fundraisers" action={{ label: "View all" }}>
					<CardQuery query={activeFundraisers} loadingLabel="Loading fundraisers…" isEmpty={(items) => items.length === 0} emptyTitle="No active fundraisers">
						{(items) => (
							<ul className="flex flex-col gap-4">
								{items.map((fundraiser) => (
									<li key={fundraiser.campaignId}>
										<div className="flex items-center justify-between text-sm">
											<span className="font-medium text-navy-900">{fundraiser.name}</span>
											<TextButton className="text-green-600">Share</TextButton>
										</div>
										<p className="mt-1 text-xs text-slate-500">
											{formatMoneyMinorUnits(fundraiser.raisedMinor, fundraiser.currency)} raised of {formatMoneyMinorUnits(fundraiser.goalMinor, fundraiser.currency)}
											{fundraiser.isRaisedDemoData && " (demo)"}
										</p>
										<div className="mt-1">
											<ProgressBar percent={(fundraiser.raisedMinor / fundraiser.goalMinor) * 100} />
										</div>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Required Actions" action={{ label: "View all" }}>
					<CardQuery query={requiredActions} loadingLabel="Loading…" isEmpty={(items) => items.length === 0} emptyTitle="Nothing pending">
						{(items) => (
							<ul className="flex flex-col gap-3">
								{items.map((action) => (
									<li key={action.id} className="flex items-center gap-3">
										<IconBadge icon={<DollarIcon className="size-4" />} tone={action.tone === "warning" ? "warning" : action.tone === "error" ? "error" : "info"} />
										<div className="min-w-0 flex-1">
											<p className="truncate text-sm font-medium text-navy-900">{action.title}</p>
											<p className="truncate text-xs text-slate-500">{action.subtitle}</p>
										</div>
										<span className="shrink-0 text-xs font-semibold text-error-600">{action.dueLabel}</span>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Organization Updates" action={{ label: "View all" }}>
					<CardQuery query={organizationUpdates} loadingLabel="Loading updates…" isEmpty={(items) => items.length === 0} emptyTitle="No updates">
						{(items) => (
							<ul className="flex flex-col gap-4">
								{items.map((update) => (
									<li key={update.id} className="flex items-start gap-3">
										<IconBadge icon={<MegaphoneIcon className="size-4" />} tone="info" />
										<div>
											<p className="text-sm font-medium text-navy-900">{update.title}</p>
											<p className="text-xs text-slate-500">{update.body}</p>
											<p className="mt-0.5 text-xs text-slate-400">{update.postedLabel}</p>
										</div>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>
			</div>
		</DashboardShell>
	);
}
