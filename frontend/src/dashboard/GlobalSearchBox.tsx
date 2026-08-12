import { useEffect, useId, useState, type KeyboardEvent } from "react";
import { useNavigate } from "react-router-dom";
import { SearchIcon } from "./icons";
import { useSearch, type SearchScope } from "../features/search/api";
import type { SearchHit } from "../features/search/types";

const TYPE_LABELS: Record<SearchHit["type"], string> = {
	TEAM: "Team",
	PARTICIPANT: "Athlete",
	HOUSEHOLD: "Household",
	ORGANIZATION: "Organization",
};

/**
 * Real global search (DESIGN-DOC.md section 13, Phase 7 completion) — replaces
 * DashboardShell's previously-decorative search input for the scopes that have a
 * backend search endpoint (organization, platform). Household results navigate to
 * the real household detail page; team/participant/organization results are
 * informational only (no detail route exists for them yet).
 *
 * Implements the ARIA combobox pattern (role="combobox"/"listbox"/"option",
 * aria-activedescendant) so the results list is fully keyboard-operable —
 * ArrowUp/ArrowDown move the highlight, Enter selects, Escape closes — not just
 * mouse/click, which was the only way to activate a result before (Phase 13
 * slice 3 accessibility audit).
 */
export function GlobalSearchBox({ scope, organizationId }: { scope: SearchScope; organizationId?: string }) {
	const [query, setQuery] = useState("");
	const [debouncedQuery, setDebouncedQuery] = useState("");
	const [open, setOpen] = useState(false);
	const [highlightedIndex, setHighlightedIndex] = useState(-1);
	const navigate = useNavigate();
	const listboxId = useId();

	useEffect(() => {
		const timeout = setTimeout(() => setDebouncedQuery(query), 250);
		return () => clearTimeout(timeout);
	}, [query]);

	const { data, isFetching } = useSearch(scope, debouncedQuery);
	const items = data?.items ?? [];

	useEffect(() => {
		setHighlightedIndex(-1);
	}, [data]);

	function handleSelect(hit: SearchHit) {
		setOpen(false);
		setQuery("");
		if (hit.type === "HOUSEHOLD" && organizationId) {
			navigate(`/app/organizations/${organizationId}/households/${hit.id}`);
		}
	}

	function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
		if (!open || items.length === 0) return;
		if (event.key === "ArrowDown") {
			event.preventDefault();
			setHighlightedIndex((index) => (index + 1) % items.length);
		} else if (event.key === "ArrowUp") {
			event.preventDefault();
			setHighlightedIndex((index) => (index <= 0 ? items.length - 1 : index - 1));
		} else if (event.key === "Enter" && highlightedIndex >= 0) {
			event.preventDefault();
			handleSelect(items[highlightedIndex]);
		} else if (event.key === "Escape") {
			setOpen(false);
		}
	}

	return (
		<div className="relative hidden flex-1 md:block">
			<div className="flex items-center gap-2 rounded-xl border border-white/15 bg-white/5 px-3 py-2 text-slate-400">
				<SearchIcon className="size-4 shrink-0" />
				<input
					type="text"
					role="combobox"
					aria-expanded={open && items.length > 0}
					aria-controls={listboxId}
					aria-autocomplete="list"
					aria-activedescendant={highlightedIndex >= 0 ? `${listboxId}-option-${highlightedIndex}` : undefined}
					value={query}
					onChange={(event) => {
						setQuery(event.target.value);
						setOpen(true);
					}}
					onFocus={() => setOpen(true)}
					onBlur={() => setTimeout(() => setOpen(false), 150)}
					onKeyDown={handleKeyDown}
					placeholder={scope.kind === "platform" ? "Search organizations…" : "Search teams, athletes, households…"}
					className="flex-1 bg-transparent text-sm text-white placeholder:text-slate-400 focus:outline-none"
				/>
			</div>
			{open && debouncedQuery.trim().length >= 2 && (
				<div className="absolute left-0 right-0 top-full z-20 mt-2 max-h-80 overflow-y-auto rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-2 shadow-lg">
					{isFetching && <p className="px-2 py-2 text-sm text-slate-500 dark:text-[#cbd5e1]">Searching…</p>}
					{!isFetching && data && items.length === 0 && <p className="px-2 py-2 text-sm text-slate-500 dark:text-[#cbd5e1]">No results.</p>}
					{!isFetching && items.length > 0 && (
						// oxlint-disable-next-line jsx-a11y/no-noninteractive-element-to-interactive-role -- ul/li elements re-rolled as listbox/option is the standard WAI-ARIA APG combobox pattern, not a mistake
						<ul id={listboxId} role="listbox" className="flex flex-col">
							{items.map((hit, index) => (
								<li key={`${hit.type}-${hit.id}`} role="presentation">
									<button
										id={`${listboxId}-option-${index}`}
										role="option"
										aria-selected={index === highlightedIndex}
										type="button"
										onMouseDown={(event) => event.preventDefault()}
										onMouseEnter={() => setHighlightedIndex(index)}
										onClick={() => handleSelect(hit)}
										className={`flex w-full items-center justify-between gap-2 rounded-lg px-2 py-2 text-left ${
											index === highlightedIndex ? "bg-ice-50 dark:bg-[#0f172a]" : "hover:bg-ice-50 hover:dark:bg-[#0f172a]"
										}`}
									>
										<span className="min-w-0">
											<span className="block truncate text-sm font-medium text-navy-900 dark:text-[#f8fafc]">{hit.label}</span>
											{hit.subtitle && <span className="block truncate text-xs text-slate-500 dark:text-[#cbd5e1]">{hit.subtitle}</span>}
										</span>
										<span className="shrink-0 rounded-full bg-ice-50 dark:bg-[#0f172a] px-2 py-0.5 text-xs text-slate-500 dark:text-[#cbd5e1]">{TYPE_LABELS[hit.type]}</span>
									</button>
								</li>
							))}
						</ul>
					)}
				</div>
			)}
		</div>
	);
}
