import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { PRIMARY_NAV, SIMPLE_NAV_LINKS } from "../content/nav";
import { useScrollToHash } from "../useScrollToHash";
import { PrimaryButton, TextButton } from "./buttons";
import { track } from "../analytics";

type MobileNavigationProps = {
	open: boolean;
	onClose: () => void;
};

/** Full-height navy drawer (section 8.2). Locks body scroll, traps initial focus on close, closes on Escape. */
export function MobileNavigation({ open, onClose }: MobileNavigationProps) {
	const closeButtonRef = useRef<HTMLButtonElement>(null);
	const scrollToHash = useScrollToHash();

	useEffect(() => {
		if (!open) return;

		document.body.style.overflow = "hidden";
		closeButtonRef.current?.focus();

		function handleKeyDown(event: KeyboardEvent) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", handleKeyDown);

		return () => {
			document.body.style.overflow = "";
			document.removeEventListener("keydown", handleKeyDown);
		};
	}, [open, onClose]);

	if (!open) return null;

	return (
		<div className="fixed inset-0 z-50 flex flex-col overflow-y-auto bg-navy-950 text-white lg:hidden" role="dialog" aria-modal="true" aria-label="Site navigation">
			<div className="flex items-center justify-between px-5 py-4">
				<span className="font-heading text-lg font-extrabold">Rally26</span>
				<button
					ref={closeButtonRef}
					type="button"
					onClick={onClose}
					aria-label="Close menu"
					className="rounded-full p-2 hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-orange-400"
				>
					<svg className="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
						<path d="m6 6 12 12M18 6 6 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
					</svg>
				</button>
			</div>

			<nav className="flex flex-1 flex-col gap-6 px-5 py-4" aria-label="Primary">
				{PRIMARY_NAV.map((group) => (
					<div key={group.label}>
						<p className="font-heading text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-[#cbd5e1]">{group.label}</p>
						<ul className="mt-2 flex flex-col">
							{group.items.map((item) => (
								<li key={item.label}>
									{"hash" in item ? (
										<button
											type="button"
											onClick={() => {
												track("nav_item_clicked", { label: item.label });
												onClose();
												scrollToHash(item.hash);
											}}
											className="block w-full py-2.5 text-left text-base font-medium text-slate-200 hover:text-white"
										>
											{item.label}
										</button>
									) : (
										<Link
											to={item.to}
											onClick={() => {
												track("nav_item_clicked", { label: item.label });
												onClose();
											}}
											className="block py-2.5 text-base font-medium text-slate-200 hover:text-white"
										>
											{item.label}
										</Link>
									)}
								</li>
							))}
						</ul>
					</div>
				))}

				<div className="border-t border-white/10 pt-4">
					{SIMPLE_NAV_LINKS.map((link) =>
						"hash" in link ? (
							<button
								key={link.label}
								type="button"
								onClick={() => {
									onClose();
									scrollToHash(link.hash);
								}}
								className="block w-full py-2.5 text-left text-base font-medium text-slate-200 hover:text-white"
							>
								{link.label}
							</button>
						) : (
							<Link
								key={link.to}
								to={link.to}
								onClick={onClose}
								className="block py-2.5 text-base font-medium text-slate-200 hover:text-white"
							>
								{link.label}
							</Link>
						),
					)}
				</div>
			</nav>

			<div className="flex flex-col gap-3 px-5 pb-8">
				<TextButton to="/auth/sign-in" onClick={onClose} className="justify-center text-white">
					Log In
				</TextButton>
				<PrimaryButton to="/auth/register" onClick={onClose} className="w-full justify-center">
					Get Started
				</PrimaryButton>
			</div>
		</div>
	);
}
