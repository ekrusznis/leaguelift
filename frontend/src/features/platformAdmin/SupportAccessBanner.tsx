import { Link, useNavigate } from "react-router-dom";
import { useEndSupportAccess } from "./api";
import type { PlatformSupportAccess } from "./types";

export function SupportAccessBanner({ access }: { access: PlatformSupportAccess }) {
	const endAccess = useEndSupportAccess();
	const navigate = useNavigate();

	return (
		<section aria-label="Active platform support access" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950">
			<div>
				<p className="font-semibold">Rally26 support access: {access.organizationName}</p>
				<p className="mt-0.5 text-xs">Reason: {access.reason} · Expires {new Date(access.expiresAt).toLocaleString()}</p>
			</div>
			<div className="flex flex-wrap gap-2">
				<Link to={`/app/platform/organizations/${access.organizationId}`} className="rounded-md border border-amber-400 bg-white px-3 py-2 font-medium hover:bg-amber-100">
					Organization console
				</Link>
				<button
					type="button"
					disabled={endAccess.isPending}
					onClick={() => endAccess.mutate(access.id, { onSuccess: () => navigate("/app/platform/organizations") })}
					className="rounded-md bg-amber-900 px-3 py-2 font-medium text-white hover:bg-amber-800 disabled:opacity-60"
				>
					{endAccess.isPending ? "Ending…" : "End access"}
				</button>
			</div>
		</section>
	);
}
