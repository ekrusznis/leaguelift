export interface RoleAssignment {
	id: string;
	userId: string;
	userEmail: string | null;
	userDisplayName: string | null;
	contextType: string;
	resourceId: string | null;
	role: string;
}
