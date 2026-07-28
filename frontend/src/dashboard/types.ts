export type DashboardRole = "OWNER" | "COACH" | "PARENT" | "ATHLETE";

export interface DashboardContext {
	role: DashboardRole;
	organizationId: string | null;
	householdId: string | null;
}

export interface ScheduleItem {
	id: string;
	day: string;
	date: string;
	title: string;
	subtitle: string;
	time: string;
	tag: string | null;
}

export interface RequiredActionItem {
	id: string;
	tone: string;
	title: string;
	subtitle: string;
	dueLabel: string;
}

export interface OrderSummary {
	id: string;
	productName: string;
	orderNumber: string;
	orderedAt: string;
	status: string;
}

export interface ActivityItem {
	id: string;
	action: string;
	entityType: string;
	entityId: string;
	occurredAt: string;
}

// --- Owner ---

export interface OwnerSummary {
	organizationName: string;
	activeTeams: number;
	participants: number;
	households: number;
	upcomingTournaments: number;
}

export interface FinancialOverview {
	isDemoData: boolean;
	currency: string;
	feesAssignedMinor: number;
	feesCollectedMinor: number;
	outstandingMinor: number;
	fundraisingMinor: number;
	apparelSalesMinor: number;
	pendingPayoutMinor: number;
}

export interface AttentionItem {
	id: string;
	tone: string;
	title: string;
	subtitle: string;
	count: number;
}

export interface TeamPerformanceRow {
	teamId: string;
	name: string;
	sport: string;
	participants: number;
	status: string;
	isFundraisingDemoData: boolean;
	fundraisingRaisedMinor: number | null;
	fundraisingGoalMinor: number | null;
}

export interface OwnerOnboardingProgress {
	isDemoData: boolean;
	completedSteps: number;
	totalSteps: number;
}

export interface ReportMetric {
	isDemoData: boolean;
	label: string;
	valueMinor: number;
	trendPercent: number;
}

// --- Coach ---

export interface CoachTeamSummary {
	teamId: string;
	name: string;
	sport: string;
	participants: number;
}

export interface RosterSummary {
	athletes: number;
	isAttendanceDemoData: boolean;
	attendanceRatePercent: number;
	availabilityResponsePercent: number;
}

export interface TeamPageStatusItem {
	teamId: string;
	teamName: string;
	status: string;
	slug: string | null;
}

export interface FundraisingProgress {
	campaignId: string;
	name: string;
	status: string;
	goalAmountMinor: number;
	currency: string;
	isRaisedDemoData: boolean;
	raisedMinor: number;
}

export interface AnnouncementItem {
	id: string;
	title: string;
	body: string;
	postedLabel: string;
	isNew: boolean;
}

// --- Parent ---

export interface HouseholdOverview {
	householdName: string;
}

export interface AthleteSummary {
	participantId: string;
	name: string;
	teamNames: string[];
}

export interface FeeLineItem {
	description: string;
	amountMinor: number;
	status: string;
	dueDate: string | null;
}

export interface OutstandingBalance {
	totalOutstandingMinor: number;
	currency: string;
	isApproximate: boolean;
	lineItems: FeeLineItem[];
}

export interface FamilyCredits {
	isDemoData: boolean;
	currency: string;
	pendingMinor: number;
	availableMinor: number;
	appliedThisSeasonMinor: number;
}

export interface FundraiserSummary {
	campaignId: string;
	name: string;
	isRaisedDemoData: boolean;
	raisedMinor: number;
	goalMinor: number;
	currency: string;
}

export interface UpdateItem {
	id: string;
	title: string;
	body: string;
	postedLabel: string;
}

// --- Athlete ---

export interface NextEventSummary {
	title: string;
	subtitle: string;
	dateLabel: string;
	location: string;
}

export interface AthleteOverview {
	displayName: string;
	isDemoData: boolean;
	nextEvent: NextEventSummary | null;
}

export interface AthleteTeamSummary {
	name: string;
	detail: string;
	coachName: string;
}

export interface HistoryItem {
	id: string;
	opponent: string;
	dateLabel: string;
	location: string;
	result: string;
	won: boolean | null;
}

export interface GuardianSummary {
	name: string;
	role: string;
	email: string;
	phone: string;
}
