import { Link } from "react-router-dom";
import { appPaths } from "../routes/appPaths";
import { ApiError } from "../lib/apiError";

/**
 * Shared error banner for any mutation blocked by `PlanEntitlementService` (backend code
 * `PLAN_UPGRADE_REQUIRED` — team/campaign capacity, fees, sponsorships, family credits,
 * SMS, integrations, advanced reporting). Renders the backend's real, specific message
 * plus a link to Billing, instead of a generic per-feature string. Falls back to
 * `fallbackMessage` for any other error so callers don't need a second error branch.
 */
export function PlanUpgradeAlert({
	error,
	organizationId,
	fallbackMessage = "Something went wrong. Please try again.",
}: {
	error: unknown;
	organizationId: string;
	fallbackMessage?: string;
}) {
	const isPlanUpgradeRequired = error instanceof ApiError && error.code === "PLAN_UPGRADE_REQUIRED";
	const message = error instanceof ApiError && isPlanUpgradeRequired ? error.message : fallbackMessage;

	return (
		<p role="alert" className="text-sm text-error-red">
			{message}
			{isPlanUpgradeRequired && (
				<>
					{" "}
					<Link to={appPaths.organizationBilling(organizationId)} className="font-semibold underline">
						View plans
					</Link>
				</>
			)}
		</p>
	);
}
