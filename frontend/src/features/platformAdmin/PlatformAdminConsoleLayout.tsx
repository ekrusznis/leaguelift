import { Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { DashboardShell, type DashNavItem } from "../../dashboard/DashboardShell";
import { sidebarPromoBackground } from "../../dashboard/demoAssets";
import { ShieldIcon } from "../../dashboard/icons";
import { navItemsFor } from "../../dashboard/registry/navRegistry";
import { useCurrentSupportAccess } from "./api";
import { SupportAccessBanner } from "./SupportAccessBanner";

export function PlatformAdminConsoleLayout() {
	const { user } = useAuth();
	const contexts = useContexts();
	const supportAccess = useCurrentSupportAccess();
	const capabilities = capabilitiesFor(contexts.data, "PLATFORM_ADMIN", null);
	const navItems: DashNavItem[] = navItemsFor("PLATFORM_ADMIN", capabilities, {}).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));

	return (
		<DashboardShell
			contextIcon={<ShieldIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName="LeagueLift Platform"
			contextRole="Platform Admin"
			navItems={navItems}
			showSearch
			searchScope={{ kind: "platform" }}
			userName={user?.displayName ?? "Account"}
			userRole="LeagueLift employee"
			promo={{
				heading: supportAccess.data ? "Support access active" : "Platform operations",
				copy: supportAccess.data
					? `${supportAccess.data.organizationName} until ${new Date(supportAccess.data.expiresAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}.`
					: "Select an organization, record a reason, and enter its support workspace.",
				linkLabel: supportAccess.data ? "View organization console" : "Browse organizations",
				to: supportAccess.data ? `/app/platform/organizations/${supportAccess.data.organizationId}` : "/app/platform/organizations",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<div className="flex flex-col gap-5">
				{supportAccess.data && <SupportAccessBanner access={supportAccess.data} />}
				<Outlet />
			</div>
		</DashboardShell>
	);
}
