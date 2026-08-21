import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Logo } from "../marketing/components/Logo";
import { ActionCenterLink } from "../features/actionCenter/ActionCenterLink";
import { AnnouncementInboxLink } from "../features/communications/AnnouncementInboxLink";
import { ActivityFeedPanel } from "./ActivityFeedPanel";
import { GlobalSearchBox } from "./GlobalSearchBox";
import { Avatar } from "./components/Avatar";
import { CardBackground } from "./components/CardBackground";
import { BellIcon, ChevronDownIcon, HelpIcon } from "./icons";
import { useMyActivity } from "../features/activity/api";
import { AppFooter } from "../marketing/components/AppFooter";
import type { SearchScope } from "../features/search/api";

export type DashNavItem = {
	icon: ReactNode;
	label: string;
	to: string;
};

export type DashboardShellProps = {
	contextIcon: ReactNode;
	contextIconTone?: string;
	contextName: string;
	contextRole: string;
	navItems: DashNavItem[];
	showSearch?: boolean;
	/** When set, the search bar is a real GlobalSearchBox for this scope. */
	searchScope?: SearchScope;
	showHelp?: boolean;
	notificationCount?: number;
	userName: string;
	userRole?: string;
	userAvatarSrc?: string;
	promo: { heading: string; copy: string; linkLabel?: string; to?: string; backgroundSrc?: string };
	children: ReactNode;
};

function destinationIsActive(pathname: string, hash: string, to: string) {
	const [targetPath, targetHash] = to.split("#");
	if (targetHash) {
		return pathname === targetPath && hash === `#${targetHash}`;
	}
	if (targetPath === "/app") {
		return pathname === "/app" && !hash;
	}
	return pathname === targetPath || pathname.startsWith(`${targetPath}/`);
}

function scrollToCurrentHash(hash: string) {
	if (!hash) return;
	const id = decodeURIComponent(hash.slice(1));
	window.requestAnimationFrame(() => document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" }));
}

/**
 * Shared application chrome for every dashboard role. Sidebar and mobile navigation
 * are generated from the same capability-filtered registry and every visible item is
 * a real link. The current role/context is intentionally a label, not a fake switcher.
 */
export function DashboardShell({
	contextIcon,
	contextIconTone = "bg-green-500/20 text-green-400",
	contextName,
	contextRole,
	navItems,
	showSearch = false,
	searchScope,
	showHelp = true,
	notificationCount,
	userName,
	userRole,
	userAvatarSrc,
	promo,
	children,
}: DashboardShellProps) {
	const activity = useMyActivity();
	const { logout } = useAuth();
	const navigate = useNavigate();
	const location = useLocation();
	const [activityOpen, setActivityOpen] = useState(false);
	const [userMenuOpen, setUserMenuOpen] = useState(false);
	const userMenuRef = useRef<HTMLDivElement>(null);
	const badgeCount = notificationCount ?? activity.data?.items.length ?? 0;

	useEffect(() => {
		scrollToCurrentHash(location.hash);
	}, [location.hash]);

	// Every authenticated page renders through this shell, but none of them set their own
	// document title (`<Seo>` is only used pre-auth/marketing-side) — without this, the tab
	// title just freezes on whatever the browser last showed (typically "Sign In | Rally26"
	// from before login) for the entire rest of the session, on every authenticated page.
	// contextName is already the real per-role label (org name, household name, "My Teams",
	// etc.), so this gives every page at least a correct, dynamic base title.
	useEffect(() => {
		document.title = `${contextName} | Rally26`;
	}, [contextName]);

	useEffect(() => {
		function closeMenus(event: MouseEvent) {
			if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
				setUserMenuOpen(false);
			}
		}
		document.addEventListener("mousedown", closeMenus);
		return () => document.removeEventListener("mousedown", closeMenus);
	}, []);

	function signOut() {
		logout();
		navigate("/auth/sign-in", { replace: true });
	}

	function navLink(item: DashNavItem, compact = false) {
		const active = destinationIsActive(location.pathname, location.hash, item.to);
		return (
			<Link
				key={`${compact ? "mobile" : "desktop"}-${item.label}`}
				to={item.to}
				aria-current={active ? "page" : undefined}
				onClick={() => {
					const hash = item.to.includes("#") ? `#${item.to.split("#")[1]}` : "";
					if (hash === location.hash) scrollToCurrentHash(hash);
				}}
				className={
					compact
						? `flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium ${active ? "bg-green-500/15 text-green-300" : "text-slate-300 hover:bg-white/5 hover:text-white"}`
						: `flex items-center gap-3 rounded-lg border-l-4 px-3 py-2.5 text-sm font-medium transition ${active ? "border-green-500 bg-white/5 text-white" : "border-transparent text-slate-400 hover:bg-white/5 hover:text-white"}`
				}
			>
				<span className={active ? "text-green-400" : "text-slate-500 dark:text-[#cbd5e1]"}>{item.icon}</span>
				{item.label}
			</Link>
		);
	}

	const promoContent = (
		<>
			<p className="font-heading text-sm font-bold text-white">{promo.heading}</p>
			<p className="mt-2 text-sm leading-relaxed text-slate-300">{promo.copy}</p>
			{promo.linkLabel && promo.to && (
				<Link to={promo.to} className="mt-3 inline-flex text-sm font-semibold text-green-400 hover:text-green-300 hover:underline">
					{promo.linkLabel} &rarr;
				</Link>
			)}
		</>
	);

	return (
		<div className="flex min-h-screen flex-col bg-ice-50 dark:bg-[#0f172a]">
			<a
				href="#dashboard-content"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:dark:bg-[#111827] focus:p-2 focus:text-navy-900 focus:dark:text-[#f8fafc]"
			>
				Skip to dashboard content
			</a>
			<header className="flex h-16 shrink-0 items-center gap-4 border-b border-white/10 bg-navy-950 px-4 sm:px-6">
				<Logo tone="dark" to="/app" compact />

				<div aria-label={`${contextName}, ${contextRole}`} className="flex items-center gap-2 rounded-xl border border-white/15 bg-white/5 px-3 py-1.5">
					<span className={`flex size-7 shrink-0 items-center justify-center rounded-lg ${contextIconTone}`}>{contextIcon}</span>
					<span className="hidden flex-col leading-tight sm:flex">
						<span className="text-sm font-semibold text-white">{contextName}</span>
						<span className="text-xs text-slate-400">{contextRole}</span>
					</span>
				</div>

				{showSearch && searchScope && (
					<GlobalSearchBox scope={searchScope} organizationId={searchScope.kind === "organization" ? searchScope.organizationId : undefined} />
				)}

				<div className="ml-auto flex items-center gap-2 sm:gap-3">
					<ActionCenterLink compact />
					<AnnouncementInboxLink compact />
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
							<div className="absolute right-0 top-full z-30 mt-2">
								<ActivityFeedPanel />
							</div>
						)}
					</div>
					{showHelp && (
						<Link
							to="/app/help"
							aria-label="Help center"
							className="hidden size-9 items-center justify-center rounded-full text-slate-300 hover:bg-white/5 hover:text-white sm:flex"
						>
							<HelpIcon className="size-5" />
						</Link>
					)}
					<div ref={userMenuRef} className="relative">
						<button
							type="button"
							aria-label="Account menu"
							aria-expanded={userMenuOpen}
							onClick={() => setUserMenuOpen((open) => !open)}
							className="flex items-center gap-2 rounded-lg p-1 text-left hover:bg-white/5"
						>
							<Avatar name={userName} size="sm" src={userAvatarSrc} />
							<span className="hidden flex-col leading-tight sm:flex">
								<span className="text-sm font-semibold text-white">{userName}</span>
								{userRole && <span className="text-xs text-slate-400">{userRole}</span>}
							</span>
							<ChevronDownIcon className="hidden size-4 text-slate-400 sm:block" />
						</button>
						{userMenuOpen && (
							<div className="absolute right-0 top-full z-30 mt-2 w-44 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-2 shadow-xl">
								<Link to="/app" className="block rounded-lg px-3 py-2 text-sm font-medium text-navy-900 dark:text-[#f8fafc] hover:bg-ice-50 hover:dark:bg-[#0f172a]">
									Dashboard
								</Link>
								<Link to="/app/action-center" className="block rounded-lg px-3 py-2 text-sm font-medium text-navy-900 dark:text-[#f8fafc] hover:bg-ice-50 hover:dark:bg-[#0f172a]">
									Action center
								</Link>
								<Link to="/app/announcements" className="block rounded-lg px-3 py-2 text-sm font-medium text-navy-900 dark:text-[#f8fafc] hover:bg-ice-50 hover:dark:bg-[#0f172a]">
									Announcements
								</Link>
								<Link to="/app/settings" className="block rounded-lg px-3 py-2 text-sm font-medium text-navy-900 dark:text-[#f8fafc] hover:bg-ice-50 hover:dark:bg-[#0f172a]">
									Settings
								</Link>
								<Link to="/app/help" className="block rounded-lg px-3 py-2 text-sm font-medium text-navy-900 dark:text-[#f8fafc] hover:bg-ice-50 hover:dark:bg-[#0f172a]">
									Help center
								</Link>
								<button type="button" onClick={signOut} className="w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-error-600 hover:bg-error-50">
									Sign out
								</button>
							</div>
						)}
					</div>
				</div>
			</header>

			<nav aria-label="Dashboard mobile navigation" className="flex gap-1 overflow-x-auto border-b border-white/10 bg-navy-900 px-3 py-2 lg:hidden">
				{navItems.map((item) => navLink(item, true))}
			</nav>

			<div className="flex min-h-0 flex-1">
				<aside className="hidden w-64 shrink-0 flex-col justify-between bg-navy-900 lg:flex">
					<nav aria-label="Dashboard" className="flex flex-col gap-1 p-4">
						{navItems.map((item) => navLink(item))}
					</nav>

					{promo.backgroundSrc ? (
						<CardBackground src={promo.backgroundSrc} className="m-4 p-5">
							{promoContent}
						</CardBackground>
					) : (
						<div className="m-4 rounded-2xl bg-navy-800 p-5">{promoContent}</div>
					)}
				</aside>

				<main id="dashboard-content" className="min-h-0 flex-1 scroll-mt-4 overflow-y-auto p-4 sm:p-6 lg:p-8">
					<div className="min-h-[calc(100vh-10rem)]">{children}</div>
					<AppFooter className="-mx-4 -mb-4 mt-10 sm:-mx-6 sm:-mb-6 lg:-mx-8 lg:-mb-8" />
				</main>
			</div>
		</div>
	);
}
