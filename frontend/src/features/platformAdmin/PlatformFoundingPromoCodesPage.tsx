import { useState } from "react";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useGenerateFoundingPromoCode, usePlatformFoundingPromoCodes } from "../foundingOrg/api";

const STATUS_LABELS: Record<string, string> = {
	UNREDEEMED: "Unredeemed",
	RESERVED: "Registering",
	ACTIVE: "Active pilot",
	CONVERTED: "Converted to paid",
	EXPIRED: "Expired",
};

function joinLink(code: string) {
	return `${window.location.origin}/founding-organizations/join?code=${encodeURIComponent(code)}`;
}

export function PlatformFoundingPromoCodesPage() {
	const codes = usePlatformFoundingPromoCodes();
	const generate = useGenerateFoundingPromoCode();
	const [copiedCode, setCopiedCode] = useState<string | null>(null);

	async function copyLink(code: string) {
		await navigator.clipboard.writeText(joinLink(code));
		setCopiedCode(code);
		setTimeout(() => setCopiedCode(null), 2000);
	}

	return (
		<section className="rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5 shadow-sm sm:p-7">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Founding Organization codes</h1>
					<p className="mt-1 text-sm text-slate-600 dark:text-[#cbd5e1]">
						Single-use codes granting free Club-tier access for a 90-day pilot. Generate a code, then copy its link and send it
						directly to the org — this page is the only place these links are surfaced.
					</p>
				</div>
				<button
					type="button"
					disabled={generate.isPending}
					onClick={() => generate.mutate()}
					className="min-h-11 rounded-lg bg-green-600 px-4 text-sm font-semibold text-white disabled:opacity-50"
				>
					{generate.isPending ? "Generating…" : "Generate code"}
				</button>
			</div>

			{generate.isError && <div className="mt-4"><ErrorState message="Could not generate a code. Try again." /></div>}
			{codes.isLoading && <LoadingState label="Loading codes…" />}
			{codes.isError && <ErrorState message="Codes could not be loaded." onRetry={() => codes.refetch()} />}
			{codes.data && codes.data.length === 0 && (
				<div className="mt-5">
					<EmptyState compact title="No codes yet" description="Generate the first code to get a shareable pilot link." />
				</div>
			)}

			{codes.data && codes.data.length > 0 && (
				<div className="mt-5 overflow-x-auto">
					<table className="w-full text-left text-sm">
						<thead className="bg-slate-50 dark:bg-[#1e293b] text-xs uppercase tracking-wide text-slate-500 dark:text-[#cbd5e1]">
							<tr>
								<th className="px-3 py-2">Code</th>
								<th className="px-3 py-2">Status</th>
								<th className="px-3 py-2">Pilot ends</th>
								<th className="px-3 py-2">Link</th>
							</tr>
						</thead>
						<tbody>
							{codes.data.map((code) => (
								<tr key={code.id} className="border-t border-slate-200 dark:border-[#334155]">
									<td className="px-3 py-2 font-mono">{code.code}</td>
									<td className="px-3 py-2">{STATUS_LABELS[code.pilotStatus] ?? code.pilotStatus}</td>
									<td className="px-3 py-2">{code.pilotEndsAt ? new Date(code.pilotEndsAt).toLocaleDateString() : "—"}</td>
									<td className="px-3 py-2">
										{code.pilotStatus === "UNREDEEMED" ? (
											<button type="button" onClick={() => copyLink(code.code)} className="font-semibold text-green-700 hover:underline">
												{copiedCode === code.code ? "Copied!" : "Copy link"}
											</button>
										) : (
											<span className="text-slate-400 dark:text-[#64748b]">—</span>
										)}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</section>
	);
}
