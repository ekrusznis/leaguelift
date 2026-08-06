export interface PageResponse<T> {
	items: T[];
	page: number;
	size: number;
	totalElements: number;
}

export interface PlatformOrganizationListItem {
	organizationId: string;
	name: string;
	slug: string;
	organizationType: string;
	status: string;
	contactEmail: string | null;
	primaryOwnerName: string | null;
	primaryOwnerEmail: string | null;
	createdAt: string;
	activeMembers: number;
	teams: number;
	households: number;
	participants: number;
	grossVolumeMinor: number;
}

export interface PlatformSwagShopProductListItem {
	productId: string;
	productName: string;
	status: string;
	catalogSource: string;
	storeId: string;
	storeName: string;
	teamId: string | null;
	teamName: string | null;
	organizationId: string;
	organizationName: string;
	variantCount: number;
	hasSwagLogo: boolean;
	createdAt: string;
	updatedAt: string;
}

export interface PlatformOrganizationDetail extends PlatformOrganizationListItem {
	contactPhone: string | null;
	updatedAt: string;
	ownerNames: string[];
	ownerEmails: string[];
	invitedMembers: number;
	tournaments: number;
	guardians: number;
	events: number;
	stores: number;
	orders: number;
	campaigns: number;
	contributions: number;
	sponsorships: number;
	documents: number;
	activeEventConnections: number;
	grossVolumeMinor: number;
	refundedMinor: number;
	organizationEarningsMinor: number;
}

export interface PlatformUserOrganizationMembership {
	organizationId: string;
	organizationName: string;
	role: string;
}

export interface PlatformUserListItem {
	userId: string;
	email: string;
	displayName: string;
	status: string;
	createdAt: string;
	platformAdmin: boolean;
	activeMemberships: number;
	organizationMemberships: PlatformUserOrganizationMembership[];
}

export interface PlatformSupportAccess {
	id: string;
	organizationId: string;
	organizationName: string;
	reason: string;
	status: "ACTIVE" | "ENDED" | "EXPIRED";
	expiresAt: string;
	endedAt: string | null;
	createdAt: string;
}

export interface PlatformSupportAccessListItem {
	id: string;
	platformAdminUserId: string;
	platformAdminName: string;
	platformAdminEmail: string;
	organizationId: string;
	organizationName: string;
	reason: string;
	status: "ACTIVE" | "ENDED" | "EXPIRED";
	expiresAt: string;
	endedAt: string | null;
	createdAt: string;
}

export interface OutboxEvent {
	id: string;
	aggregateType: string;
	aggregateId: string;
	organizationId: string | null;
	eventType: string;
	status: string;
	attemptCount: number;
	availableAt: string;
	processedAt: string | null;
	lastError: string | null;
	createdAt: string;
}
