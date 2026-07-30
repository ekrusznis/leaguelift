export interface Membership {
	id: string;
	organizationId: string;
	userId: string;
	userEmail: string | null;
	userDisplayName: string | null;
	role: string;
	status: string;
	createdAt: string;
}

export interface MembershipPage {
	items: Membership[];
	page: number;
	size: number;
	totalElements: number;
}
