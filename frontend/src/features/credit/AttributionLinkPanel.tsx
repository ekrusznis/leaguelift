import { useState } from "react";
import { Button } from "../../components/Button";
import { useAttributionLink, useSetAttributionPublicName } from "./api";

/**
 * Phase 23 (DESIGN-DOC.md section 13/14.1): a guardian's private sharing link
 * for one campaign — always privately code-tracked, with an optional
 * supporter-facing public display name. Generating the link (via GET, which
 * creates it on first request) is what makes future contributions through it
 * grant this household family credit.
 */
export function AttributionLinkPanel({ organizationId, householdId, campaignId, campaignSlug }: { organizationId: string; householdId: string; campaignId: string; campaignSlug: string }) {
	const link = useAttributionLink(organizationId, householdId, campaignId);
	const setPublicName = useSetAttributionPublicName(organizationId, householdId, campaignId);
	const [publicName, setPublicNameInput] = useState("");
	const [copied, setCopied] = useState(false);

	if (link.isLoading) return <p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Loading your sharing link…</p>;
	if (link.isError || !link.data) return <p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Could not load your sharing link.</p>;

	const shareUrl = `${window.location.origin}/campaigns/${campaignSlug}?ref=${link.data.code}`;

	async function copyLink() {
		await navigator.clipboard.writeText(shareUrl);
		setCopied(true);
		setTimeout(() => setCopied(false), 2000);
	}

	return (
		<div className="mt-2 flex flex-col gap-2 rounded-md bg-ice-white dark:bg-[#0f172a] p-3 text-sm">
			<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Contributions made through your link are tracked privately to your family — supporters never see this link's ownership.</p>
			<div className="flex items-center gap-2">
				<input readOnly value={shareUrl} className="min-h-11 flex-1 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 text-xs" onFocus={(event) => event.target.select()} />
				<Button type="button" variant="secondary" onClick={copyLink}>{copied ? "Copied!" : "Copy"}</Button>
			</div>
			<div className="flex items-center gap-2">
				<input
					placeholder="Optional public name (e.g. The Johnson Family)"
					value={publicName || link.data.publicDisplayName || ""}
					onChange={(event) => setPublicNameInput(event.target.value)}
					className="min-h-11 flex-1 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 text-xs"
				/>
				<Button
					type="button"
					variant="secondary"
					disabled={setPublicName.isPending}
					onClick={() => setPublicName.mutate(publicName || null)}
				>
					Save
				</Button>
			</div>
		</div>
	);
}
