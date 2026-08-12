import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { NavGroup } from "../content/nav";
import { useScrollToHash } from "../useScrollToHash";
import { track } from "../analytics";

/**
 * One "Product / Solutions / Resources" dropdown in the desktop header (section
 * 8.1). Click-to-toggle only — an earlier hover-to-open version raced with click:
 * moving the pointer onto the button fired `mouseenter` (opening it), so the click
 * that followed immediately toggled it shut again before a real user could read it.
 */
export function NavDropdown({ group }: { group: NavGroup }) {
	const [open, setOpen] = useState(false);
	const containerRef = useRef<HTMLDivElement>(null);
	const scrollToHash = useScrollToHash();

	useEffect(() => {
		function handleClickOutside(event: MouseEvent) {
			if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
				setOpen(false);
			}
		}
		document.addEventListener("mousedown", handleClickOutside);
		return () => document.removeEventListener("mousedown", handleClickOutside);
	}, []);

	return (
		// oxlint-disable-next-line jsx-a11y/no-static-element-interactions -- this div isn't itself interactive, it just listens for Escape bubbling up from the real interactive children (button/links) below to close the dropdown
		<div
			ref={containerRef}
			className="relative"
			onKeyDown={(event) => {
				if (event.key === "Escape") setOpen(false);
			}}
		>
			<button
				type="button"
				aria-expanded={open}
				aria-haspopup="true"
				onClick={() => setOpen((value) => !value)}
				className="flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium text-white/90 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-orange-400"
			>
				{group.label}
				<svg className={`size-3.5 transition-transform duration-150 ${open ? "rotate-180" : ""}`} viewBox="0 0 16 16" fill="none" aria-hidden="true">
					<path d="m4 6 4 4 4-4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
				</svg>
			</button>
			{open && (
				<div
					role="menu"
					aria-label={group.label}
					className="absolute left-0 top-full z-40 mt-1 w-56 rounded-2xl border border-white/10 bg-navy-900/98 p-2 shadow-[0_22px_60px_rgba(0,0,0,0.4)] backdrop-blur"
				>
					{group.items.map((item) => {
						const label = item.label;
						const commonClasses =
							"block rounded-lg px-3 py-2 text-sm font-medium text-slate-200 hover:bg-white/5 hover:text-white";

						if ("hash" in item) {
							return (
								<button
									key={label}
									type="button"
									role="menuitem"
									onClick={() => {
										track("nav_item_clicked", { label });
										setOpen(false);
										scrollToHash(item.hash);
									}}
									className={`w-full text-left ${commonClasses}`}
								>
									{label}
								</button>
							);
						}

						return (
							// oxlint-disable-next-line jsx-a11y/interactive-supports-focus -- react-router's Link always renders a focusable <a href> here (item.to is always set on this branch), the linter just can't see through the component
							<Link
								key={label}
								to={item.to}
								role="menuitem"
								onClick={() => {
									track("nav_item_clicked", { label });
									setOpen(false);
								}}
								className={commonClasses}
							>
								{label}
							</Link>
						);
					})}
				</div>
			)}
		</div>
	);
}
