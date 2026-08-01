import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useContexts } from "../authorization/api";
import { Capabilities } from "../authorization/capabilityConstants";
import { hasCapability } from "../authorization/capabilities";
import { ActionCenterLink } from "../features/actionCenter/ActionCenterLink";
import { AnnouncementInboxLink } from "../features/communications/AnnouncementInboxLink";
import { SupportAccessBanner } from "../features/platformAdmin/SupportAccessBanner";
import { useCurrentSupportAccess } from "../features/platformAdmin/api";
import { AppFooter } from "../marketing/components/AppFooter";
import { Logo } from "../marketing/components/Logo";

/**
 * Compact shell for routed feature pages. Platform employees retain their own
 * platform navigation while an amber, reasoned support-access banner makes it
 * impossible to confuse a customer workspace with ordinary tenant membership.
 */
export function AppShell() {
	const { user, logout } = useAuth();
	const contexts = useContexts();
	const isPlatformAdmin = hasCapability(contexts.data, Capabilities.PLATFORM_SUPPORT_ACCESS, {
		contextType: "PLATFORM_ADMIN",
		resourceId: null,
	});
	const supportAccess = useCurrentSupportAccess(isPlatformAdmin);
	const canBrowseOrganizations = contexts.data?.some((context) => context.contextType === "ORGANIZATION") ?? false;
	const navItems = [
		{ to: "/app", label: "Dashboard" },
		...(isPlatformAdmin ? [{ to: "/app/platform/organizations", label: "Platform Console" }] : []),
		...(!isPlatformAdmin && canBrowseOrganizations ? [{ to: "/app/organizations", label: "Organizations" }] : []),
		...(isPlatformAdmin && supportAccess.data
			? [{ to: `/app/platform/organizations/${supportAccess.data.organizationId}`, label: "Organization Console" }]
			: []),
		{ to: "/app/help", label: "Help" },
	];

	return (
		<div className="flex min-h-screen flex-col bg-ice-white">
			<a
				href="#main-content"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-pure-white focus:p-2"
			>
				Skip to main content
			</a>
			<header className="border-b border-slate-gray/15 bg-navy text-pure-white">
				<div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
					<Logo tone="dark" to="/app" />
					<nav aria-label="Primary" className="order-3 flex w-full gap-2 overflow-x-auto sm:order-none sm:w-auto sm:gap-4">
						{navItems.map((item) => (
							<NavLink
								key={item.to}
								to={item.to}
								end={item.to === "/app"}
								className={({ isActive }) =>
									`shrink-0 rounded-md px-2 py-1 text-sm font-medium ${
										isActive ? "bg-pure-white/10" : "hover:bg-pure-white/5"
									}`
								}
							>
								{item.label}
							</NavLink>
						))}
					</nav>
					<div className="flex items-center gap-1 text-sm">
						<ActionCenterLink compact />
						<AnnouncementInboxLink compact />
						{user && <span className="hidden sm:inline">{user.displayName}</span>}
						<button
							type="button"
							onClick={logout}
							className="rounded-md border border-pure-white/30 px-2 py-1 hover:bg-pure-white/10"
						>
							Sign out
						</button>
					</div>
				</div>
			</header>
			<main id="main-content" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
				{isPlatformAdmin && supportAccess.data && (
					<div className="mb-6"><SupportAccessBanner access={supportAccess.data} /></div>
				)}
				<Outlet />
			</main>
			<AppFooter />
		</div>
	);
}
