import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useContexts } from "../authorization/api";

/**
 * Compact shell for routed feature pages. Navigation is derived from the caller's
 * real contexts so a guardian, athlete, coach, or tournament-only user is not shown
 * an organization-directory link they cannot use. Role-specific module navigation
 * remains in the dashboard registry and in each routed entity page.
 */
export function AppShell() {
	const { user, logout } = useAuth();
	const contexts = useContexts();
	const canBrowseOrganizations = contexts.data?.some((context) => context.contextType === "ORGANIZATION") ?? false;
	const navItems = [
		{ to: "/app", label: "Dashboard" },
		...(canBrowseOrganizations ? [{ to: "/app/organizations", label: "Organizations" }] : []),
		{ to: "/help", label: "Help" },
	];

	return (
		<div className="min-h-screen bg-ice-white">
			<a
				href="#main-content"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-pure-white focus:p-2"
			>
				Skip to main content
			</a>
			<header className="border-b border-slate-gray/15 bg-navy text-pure-white">
				<div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
					<NavLink to="/app" className="font-heading text-lg font-bold">LeagueLift</NavLink>
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
					<div className="flex items-center gap-3 text-sm">
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
			<main id="main-content" className="mx-auto max-w-6xl px-4 py-8">
				<Outlet />
			</main>
		</div>
	);
}
