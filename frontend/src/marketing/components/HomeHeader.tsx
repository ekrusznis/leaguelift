import { useEffect, useState } from "react";
import { HOME_NAV_LINKS, HOMEPAGE_SECTION_IDS } from "../content/nav";
import { Logo } from "./Logo";
import { PageContainer } from "./PageContainer";
import { PrimaryButton, TextButton } from "./buttons";
import { track } from "../analytics";

type HomeSectionId = (typeof HOMEPAGE_SECTION_IDS)[keyof typeof HOMEPAGE_SECTION_IDS];

function scrollToSection(hash: string) {
	const behavior: ScrollBehavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
	document.getElementById(hash)?.scrollIntoView({ behavior, block: "start" });
}

/**
 * Self-contained header for the single-page homepage (see HomePage.tsx). Every
 * destination is a same-page anchor rather than a route, so this uses a flat nav
 * with IntersectionObserver-based active-section highlighting instead of
 * SiteHeader's Product/Solutions/Resources dropdown IA used on the surviving
 * inner pages (solution detail pages, help/legal/contact, etc.).
 */
export function HomeHeader() {
	const [scrolled, setScrolled] = useState(false);
	const [mobileOpen, setMobileOpen] = useState(false);
	const [activeHash, setActiveHash] = useState<HomeSectionId>(HOME_NAV_LINKS[0].hash);

	useEffect(() => {
		function handleScroll() {
			setScrolled(window.scrollY > 12);
		}
		handleScroll();
		window.addEventListener("scroll", handleScroll, { passive: true });
		return () => window.removeEventListener("scroll", handleScroll);
	}, []);

	useEffect(() => {
		const sections = HOME_NAV_LINKS.map((link) => document.getElementById(link.hash)).filter(
			(el): el is HTMLElement => el !== null,
		);
		if (sections.length === 0) return;

		const observer = new IntersectionObserver(
			(entries) => {
				const visible = entries.filter((entry) => entry.isIntersecting);
				if (visible.length === 0) return;
				const topMost = visible.reduce((a, b) => (a.boundingClientRect.top < b.boundingClientRect.top ? a : b));
				setActiveHash(topMost.target.id as HomeSectionId);
			},
			{ rootMargin: "-30% 0px -60% 0px", threshold: [0, 1] },
		);

		sections.forEach((section) => observer.observe(section));
		return () => observer.disconnect();
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
						{HOME_NAV_LINKS.map((link) => (
							<button
								key={link.hash}
								type="button"
								onClick={() => {
									track("nav_item_clicked", { label: link.label });
									scrollToSection(link.hash);
								}}
								aria-current={activeHash === link.hash ? "true" : undefined}
								className={`rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-orange-400 ${
									activeHash === link.hash ? "text-white" : "text-white/70 hover:text-white"
								}`}
							>
								{link.label}
							</button>
						))}
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
						onClick={() => setMobileOpen((value) => !value)}
						aria-label={mobileOpen ? "Close menu" : "Open menu"}
						aria-expanded={mobileOpen}
						className="rounded-md p-2 text-white hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-orange-400 lg:hidden"
					>
						<svg className="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
							{mobileOpen ? (
								<path d="m6 6 12 12M18 6 6 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
							) : (
								<path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
							)}
						</svg>
					</button>
				</PageContainer>
			</header>

			{mobileOpen && (
				<div className="sticky top-[76px] z-30 border-b border-white/10 bg-navy-950 sm:top-20 lg:hidden">
					<PageContainer className="flex flex-col gap-1 py-4">
						{HOME_NAV_LINKS.map((link) => (
							<button
								key={link.hash}
								type="button"
								onClick={() => {
									track("nav_item_clicked", { label: link.label });
									setMobileOpen(false);
									scrollToSection(link.hash);
								}}
								className={`rounded-md px-3 py-3 text-left text-base font-medium ${
									activeHash === link.hash ? "text-white" : "text-white/70 hover:text-white"
								}`}
							>
								{link.label}
							</button>
						))}
						<div className="mt-3 flex flex-col gap-3 border-t border-white/10 pt-4">
							<TextButton to="/auth/sign-in" onClick={() => setMobileOpen(false)} className="justify-center text-white">
								Log In
							</TextButton>
							<PrimaryButton to="/auth/register" onClick={() => setMobileOpen(false)} className="w-full justify-center">
								Get Started
							</PrimaryButton>
						</div>
					</PageContainer>
				</div>
			)}
		</>
	);
}
