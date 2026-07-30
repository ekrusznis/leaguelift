import { useEffect, useState } from "react";
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
 */
export function GlobalSearchBox({ scope, organizationId }: { scope: SearchScope; organizationId?: string }) {
	const [query, setQuery] = useState("");
	const [debouncedQuery, setDebouncedQuery] = useState("");
	const [open, setOpen] = useState(false);
	const navigate = useNavigate();

	useEffect(() => {
		const timeout = setTimeout(() => setDebouncedQuery(query), 250);
		return () => clearTimeout(timeout);
	}, [query]);

	const { data, isFetching } = useSearch(scope, debouncedQuery);

	function handleSelect(hit: SearchHit) {
		setOpen(false);
		setQuery("");
		if (hit.type === "HOUSEHOLD" && organizationId) {
			navigate(`/app/organizations/${organizationId}/households/${hit.id}`);
		}
	}

	return (
		<div className="relative hidden flex-1 md:block">
			<div className="flex items-center gap-2 rounded-xl border border-white/15 bg-white/5 px-3 py-2 text-slate-400">
				<SearchIcon className="size-4 shrink-0" />
				<input
					type="text"
					value={query}
					onChange={(event) => {
						setQuery(event.target.value);
						setOpen(true);
					}}
					onFocus={() => setOpen(true)}
					onBlur={() => setTimeout(() => setOpen(false), 150)}
					placeholder={scope.kind === "platform" ? "Search organizations…" : "Search teams, athletes, households…"}
					className="flex-1 bg-transparent text-sm text-white placeholder:text-slate-400 focus:outline-none"
				/>
			</div>
			{open && debouncedQuery.trim().length >= 2 && (
				<div className="absolute left-0 right-0 top-full z-20 mt-2 max-h-80 overflow-y-auto rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
					{isFetching && <p className="px-2 py-2 text-sm text-slate-500">Searching…</p>}
					{!isFetching && data && data.items.length === 0 && <p className="px-2 py-2 text-sm text-slate-500">No results.</p>}
					{!isFetching && data && data.items.length > 0 && (
						<ul className="flex flex-col">
							{data.items.map((hit) => (
								<li key={`${hit.type}-${hit.id}`}>
									<button
										type="button"
										onMouseDown={(event) => event.preventDefault()}
										onClick={() => handleSelect(hit)}
										className="flex w-full items-center justify-between gap-2 rounded-lg px-2 py-2 text-left hover:bg-ice-50"
									>
										<span className="min-w-0">
											<span className="block truncate text-sm font-medium text-navy-900">{hit.label}</span>
											{hit.subtitle && <span className="block truncate text-xs text-slate-500">{hit.subtitle}</span>}
										</span>
										<span className="shrink-0 rounded-full bg-ice-50 px-2 py-0.5 text-xs text-slate-500">{TYPE_LABELS[hit.type]}</span>
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
