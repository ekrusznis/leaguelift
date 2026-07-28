import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const NAV_ITEMS = [
	{ to: "/app", label: "Overview" },
	{ to: "/app/organizations", label: "Organizations" },
];

/**
 * Only milestone-enabled modules appear here (DESIGN-DOC.md section 17.3) — Phase 0
 * only has Overview and Organizations. Add nav items as their feature module ships,
 * not before.
 */
export function AppShell() {
	const { user, logout } = useAuth();

	return (
		<div className="min-h-screen bg-ice-white">
			<a
				href="#main-content"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-pure-white focus:p-2"
			>
				Skip to main content
			</a>
			<header className="border-b border-slate-gray/15 bg-navy text-pure-white">
				<div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
					<span className="font-heading text-lg font-bold">LeagueLift</span>
					<nav aria-label="Primary" className="flex gap-4">
						{NAV_ITEMS.map((item) => (
							<NavLink
								key={item.to}
								to={item.to}
								end={item.to === "/app"}
								className={({ isActive }) =>
									`rounded-md px-2 py-1 text-sm font-medium ${
										isActive ? "bg-pure-white/10" : "hover:bg-pure-white/5"
									}`
								}
							>
								{item.label}
							</NavLink>
						))}
					</nav>
					<div className="flex items-center gap-3 text-sm">
						{user && <span>{user.displayName}</span>}
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
