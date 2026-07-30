import { useState, type ReactNode } from "react";
import { Logo } from "../marketing/components/Logo";
import { Avatar } from "./components/Avatar";
import { CardBackground } from "./components/CardBackground";
import { BellIcon, HelpIcon, SearchIcon, ChevronDownIcon } from "./icons";
import { ActivityFeedPanel } from "./ActivityFeedPanel";
import { useMyActivity } from "../features/activity/api";
import { GlobalSearchBox } from "./GlobalSearchBox";
import type { SearchScope } from "../features/search/api";

export type DashNavItem = {
	icon: ReactNode;
	label: string;
	active?: boolean;
};

export type DashboardShellProps = {
	contextIcon: ReactNode;
	contextIconTone?: string;
	contextName: string;
	contextRole: string;
	navItems: DashNavItem[];
	showSearch?: boolean;
	/** When set, the search bar is a real GlobalSearchBox for this scope; when showSearch is true but this is omitted, the search bar stays the decorative placeholder it always was. */
	searchScope?: SearchScope;
	showHelp?: boolean;
	notificationCount?: number;
	userName: string;
	userRole?: string;
	userAvatarSrc?: string;
	promo: { heading: string; copy: string; linkLabel: string; backgroundSrc?: string };
	children: ReactNode;
};

/**
 * Shared application chrome for every dashboard role (DESIGN-DOC.md section
 * 10.1-10.3). The context switcher, search, and user menu remain static, inert
 * elements — a fixed role-to-dashboard routing is the intended product behavior
 * (only an org owner reassigning a member's role changes what they land on, not a
 * session-level switcher), not an unbuilt gap. The bell icon is real: it opens the
 * cross-org activity feed (DESIGN-DOC.md section 13, Phase 7 completion).
 */
export function DashboardShell({
	contextIcon,
	contextIconTone = "bg-green-500/20 text-green-400",
	contextName,
	contextRole,
	navItems,
	showSearch = false,
	searchScope,
	showHelp = false,
	notificationCount,
	userName,
	userRole,
	userAvatarSrc,
	promo,
	children,
}: DashboardShellProps) {
	const activity = useMyActivity();
	const [activityOpen, setActivityOpen] = useState(false);
	const badgeCount = notificationCount ?? activity.data?.items.length ?? 0;

	return (
		<div className="flex h-full flex-col bg-ice-50">
			<header className="flex h-16 shrink-0 items-center gap-4 border-b border-white/10 bg-navy-950 px-4 sm:px-6">
				<Logo tone="dark" to="/app" compact />

				<div className="flex items-center gap-2 rounded-xl border border-white/15 bg-white/5 px-3 py-1.5">
					<span className={`flex size-7 shrink-0 items-center justify-center rounded-lg ${contextIconTone}`}>{contextIcon}</span>
					<span className="hidden flex-col leading-tight sm:flex">
						<span className="text-sm font-semibold text-white">{contextName}</span>
						<span className="text-xs text-slate-400">{contextRole}</span>
					</span>
					<ChevronDownIcon className="size-4 text-slate-400" />
				</div>

				{showSearch && searchScope && (
					<GlobalSearchBox scope={searchScope} organizationId={searchScope.kind === "organization" ? searchScope.organizationId : undefined} />
				)}
				{showSearch && !searchScope && (
					<div className="hidden flex-1 items-center gap-2 rounded-xl border border-white/15 bg-white/5 px-3 py-2 text-slate-400 md:flex">
						<SearchIcon className="size-4 shrink-0" />
						<span className="flex-1 text-sm">Search athletes, teams, orders&hellip;</span>
						<span className="rounded-md border border-white/15 px-1.5 py-0.5 text-xs">&#8984;K</span>
					</div>
				)}

				<div className="ml-auto flex items-center gap-3 sm:gap-4">
					<div className="relative">
						<button
							type="button"
							aria-label="Recent activity"
							aria-expanded={activityOpen}
							onClick={() => setActivityOpen((open) => !open)}
							className="relative flex size-9 items-center justify-center rounded-full text-slate-300 hover:bg-white/5 hover:text-white"
						>
							<BellIcon className="size-5" />
							{badgeCount > 0 && (
								<span className="absolute -right-0.5 -top-0.5 flex size-4 items-center justify-center rounded-full bg-green-500 text-[10px] font-bold text-white">
									{badgeCount > 9 ? "9+" : badgeCount}
								</span>
							)}
						</button>
						{activityOpen && (
							<div className="absolute right-0 top-full z-20 mt-2">
								<ActivityFeedPanel />
							</div>
						)}
					</div>
					{showHelp && (
						<span className="hidden size-9 items-center justify-center rounded-full text-slate-300 hover:bg-white/5 hover:text-white sm:flex">
							<HelpIcon className="size-5" />
						</span>
					)}
					<div className="flex items-center gap-2">
						<Avatar name={userName} size="sm" src={userAvatarSrc} />
						<span className="hidden flex-col leading-tight sm:flex">
							<span className="text-sm font-semibold text-white">{userName}</span>
							{userRole && <span className="text-xs text-slate-400">{userRole}</span>}
						</span>
						<ChevronDownIcon className="hidden size-4 text-slate-400 sm:block" />
					</div>
				</div>
			</header>

			<div className="flex min-h-0 flex-1">
				<aside className="hidden w-64 shrink-0 flex-col justify-between bg-navy-900 lg:flex">
					<nav aria-label="Dashboard" className="flex flex-col gap-1 p-4">
						{navItems.map((item) => (
							<span
								key={item.label}
								className={`flex items-center gap-3 rounded-lg border-l-4 px-3 py-2.5 text-sm font-medium ${
									item.active
										? "border-green-500 bg-white/5 text-white"
										: "border-transparent text-slate-400"
								}`}
							>
								<span className={item.active ? "text-green-400" : "text-slate-500"}>{item.icon}</span>
								{item.label}
							</span>
						))}
					</nav>

					{promo.backgroundSrc ? (
						<CardBackground src={promo.backgroundSrc} className="m-4 p-5">
							<p className="font-heading text-sm font-bold text-white">{promo.heading}</p>
							<p className="mt-2 text-sm leading-relaxed text-slate-300">{promo.copy}</p>
							<p className="mt-3 text-sm font-semibold text-green-400">{promo.linkLabel} &rarr;</p>
						</CardBackground>
					) : (
						<div className="m-4 rounded-2xl bg-navy-800 p-5">
							<p className="font-heading text-sm font-bold text-white">{promo.heading}</p>
							<p className="mt-2 text-sm leading-relaxed text-slate-400">{promo.copy}</p>
							<p className="mt-3 text-sm font-semibold text-green-400">{promo.linkLabel} &rarr;</p>
						</div>
					)}
				</aside>

				<main className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">{children}</main>
			</div>
		</div>
	);
}
