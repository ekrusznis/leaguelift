export type OrganizationSettingsAccess = {
	canManageOrganization: boolean;
	canManagePayouts: boolean;
};

export type OrganizationSettingsLink = {
	label: string;
	to: string;
};

export type OrganizationSettingsGroup = {
	key:
		| "organization-branding"
		| "financial-credits"
		| "communications"
		| "events-participation"
		| "commerce"
		| "integrations"
		| "billing"
		| "administration";
	title: string;
	description: string;
	links: OrganizationSettingsLink[];
};

function organizationPath(organizationId: string, section: string) {
	return `/app/organizations/${organizationId}/${section}`;
}

/**
 * Phase 28.3 is a directory over existing domain-owned controls. It intentionally
 * returns links only; no organization value is copied into the Settings domain.
 */
export function organizationSettingsGroups(
	organizationId: string,
	access: OrganizationSettingsAccess,
): OrganizationSettingsGroup[] {
	const groups: OrganizationSettingsGroup[] = [];

	if (access.canManageOrganization) {
		groups.push({
			key: "organization-branding",
			title: "Organization & Branding",
			description: "Profile, contact/address/timezone, logo/cover assets, public pages, and existing organization-owned presentation controls.",
			links: [
				{ label: "Profile, branding & public pages", to: organizationPath(organizationId, "settings") },
			],
		});
	}

	if (access.canManageOrganization || access.canManagePayouts) {
		const links: OrganizationSettingsLink[] = [
			{ label: access.canManageOrganization ? "Credits, markup & payouts" : "Payouts", to: organizationPath(organizationId, "settings") },
		];
		if (access.canManageOrganization) {
			links.push(
				{ label: "Fees & payments", to: organizationPath(organizationId, "fees") },
				{ label: "Financial operations", to: organizationPath(organizationId, "financial-operations") },
			);
		}
		groups.push({
			key: "financial-credits",
			title: "Financial & Credits",
			description: "Use the existing family-credit, markup, payout, fee, offline-payment, correction, and reconciliation workflows. No new financial toggle is invented here.",
			links,
		});
	}

	if (access.canManageOrganization) {
		groups.push(
			{
				key: "communications",
				title: "Communications",
				description: "Manage real announcements, messages, members, and invitations. SafeSport and guardian-visibility requirements remain fixed safety boundaries.",
				links: [
					{ label: "Announcements", to: "/app/announcements" },
					{ label: "Messages", to: "/app/messages" },
					{ label: "Members & invitations", to: organizationPath(organizationId, "members") },
				],
			},
			{
				key: "events-participation",
				title: "Events & Participation",
				description: "Use the organization timezone, event schedule, document, and participation workflows already owned by those domains.",
				links: [
					{ label: "Events", to: organizationPath(organizationId, "events") },
					{ label: "Documents", to: organizationPath(organizationId, "documents") },
					{ label: "Households & athletes", to: organizationPath(organizationId, "households") },
				],
			},
			{
				key: "commerce",
				title: "Commerce / Swag Shop",
				description: "Reach existing Swag Shop, fundraising, and sponsorship controls without copying catalog, order, campaign, or sponsorship state into Settings.",
				links: [
					{ label: "Swag Shop", to: organizationPath(organizationId, "swag-shop") },
					{ label: "Fundraising", to: organizationPath(organizationId, "fundraising") },
					{ label: "Sponsorships", to: organizationPath(organizationId, "sponsorships") },
				],
			},
			{
				key: "integrations",
				title: "Integrations",
				description: "Open the organization-owned integration workspace. Provider readiness, credentials, mappings, and write policy remain authoritative there.",
				links: [{ label: "Organization integrations", to: organizationPath(organizationId, "integrations") }],
			},
			{
				key: "billing",
				title: "Billing & Subscription",
				description: "Manage the organization's existing Rally26 subscription and billing lifecycle through the billing domain.",
				links: [{ label: "Billing & subscription", to: `/app/organizations/${organizationId}/billing` }],
			},
			{
				key: "administration",
				title: "History / Administration",
				description: "Use existing audit history and controlled organization-administration workflows. Settings does not broaden any history or support permission.",
				links: [
					{ label: "History", to: "/app/history" },
					{ label: "Profile corrections", to: organizationPath(organizationId, "corrections") },
					{ label: "Onboarding tools", to: organizationPath(organizationId, "onboarding") },
				],
			},
		);
	}

	return groups;
}
