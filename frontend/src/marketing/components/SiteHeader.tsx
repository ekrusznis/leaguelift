import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PRIMARY_NAV, SIMPLE_NAV_LINKS } from "../content/nav";
import { Logo } from "./Logo";
import { NavDropdown } from "./NavDropdown";
import { MobileNavigation } from "./MobileNavigation";
import { PageContainer } from "./PageContainer";
import { PrimaryButton, TextButton } from "./buttons";
import { useScrollToHash } from "../useScrollToHash";
import { track } from "../analytics";

/** Sticky header with scroll-state shrink (section 8.3): 80px at top, 68-72px + shadow after scrolling. */
export function SiteHeader() {
	const [scrolled, setScrolled] = useState(false);
	const [mobileOpen, setMobileOpen] = useState(false);
	const scrollToHash = useScrollToHash();

	useEffect(() => {
		function handleScroll() {
			setScrolled(window.scrollY > 12);
		}
		handleScroll();
		window.addEventListener("scroll", handleScroll, { passive: true });
		return () => window.removeEventListener("scroll", handleScroll);
	}, []);

	return (
		<>
			<header
				className={`sticky top-0 z-30 border-b border-white/10 bg-navy-950/94 backdrop-blur-md transition-[height,box-shadow] duration-200 ${
					scrolled ? "h-[68px] shadow-[0_8px_24px_rgba(0,0,0,0.3)] sm:h-[72px]" : "h-[76px] sm:h-20"
				}`}
			>
				<PageContainer className="flex h-full items-center justify-between gap-4">
					<Logo tone="dark" />

					<nav aria-label="Primary" className="hidden items-center gap-1 lg:flex">
						{PRIMARY_NAV.map((group) => (
							<NavDropdown key={group.label} group={group} />
						))}
						{SIMPLE_NAV_LINKS.map((link) => {
							const commonClasses =
								"rounded-md px-3 py-2 text-sm font-medium text-white/90 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-orange-400";
							if ("hash" in link) {
								return (
									<button
										key={link.label}
										type="button"
										onClick={() => {
											track("nav_item_clicked", { label: link.label });
											scrollToHash(link.hash);
										}}
										className={commonClasses}
									>
										{link.label}
									</button>
								);
							}
							return (
								<Link
									key={link.to}
									to={link.to}
									onClick={() => track("nav_item_clicked", { label: link.label })}
									className={commonClasses}
								>
									{link.label}
								</Link>
							);
						})}
					</nav>

					<div className="hidden items-center gap-3 lg:flex">
						<TextButton to="/auth/sign-in" onClick={() => track("login_clicked")} className="text-white/90 hover:text-white">
							Log In
						</TextButton>
						<PrimaryButton to="/auth/register" onClick={() => track("primary_cta_clicked", { location: "header" })}>
							Get Started
						</PrimaryButton>
					</div>

					<button
						type="button"
						onClick={() => setMobileOpen(true)}
						aria-label="Open menu"
						className="rounded-md p-2 text-white hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-orange-400 lg:hidden"
					>
						<svg className="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
							<path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
						</svg>
					</button>
				</PageContainer>
			</header>

			<MobileNavigation open={mobileOpen} onClose={() => setMobileOpen(false)} />
		</>
	);
}
