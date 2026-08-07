import { useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCreateBillingPortal, useOrganizationSubscription } from "./api";
import type { BillingRecoveryState, OrganizationSubscriptionStatus } from "./types";

export function OrganizationBillingPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const subscription = useOrganizationSubscription(organizationId);
	const portal = useCreateBillingPortal();
	if (!organizationId) return <ErrorState message="Organization is required." />;
	if (subscription.isLoading) return <LoadingState label="Loading billing…" />;
	if (subscription.isError) return <ErrorState message="Could not load organization billing." onRetry={() => subscription.refetch()} />;

	const value = subscription.data;
	const openPortal = async () => {
		const response = await portal.mutateAsync(organizationId);
		window.location.assign(response.url);
	};

	return (
		<div className="mx-auto flex w-full max-w-4xl flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900">Billing</h1>
				<p className="mt-1 text-slate-500">Review your Rally26 organization subscription and manage billing through Stripe.</p>
			</div>
			{!value ? (
				<div className="rounded-xl border border-slate-200 bg-white p-6">
					<h2 className="font-heading text-lg font-semibold text-navy-900">No subscription record yet</h2>
					<p className="mt-2 text-sm text-slate-600">Complete owner onboarding to choose a plan and start subscription checkout.</p>
				</div>
			) : (
				<>
					<RecoveryBanner state={value.recoveryState} cancelAtPeriodEnd={value.cancelAtPeriodEnd} />
					<div className="grid gap-4 md:grid-cols-2">
						<section className="rounded-xl border border-slate-200 bg-white p-5">
							<p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Plan</p>
							<h2 className="mt-1 font-heading text-xl font-semibold text-navy-900">{value.planName ?? value.planCode}</h2>
							<p className="mt-2 text-sm text-slate-600">{formatPlanPrice(value.amountMinor, value.currency, value.billingInterval)}</p>
						</section>
						<section className="rounded-xl border border-slate-200 bg-white p-5">
							<p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Subscription status</p>
							<div className="mt-2"><StatusPill status={value.status} /></div>
							<p className="mt-3 text-sm text-slate-600">Stripe subscription webhooks—not browser redirects—control Rally26 activation and suspension.</p>
						</section>
					</div>
					<section className="rounded-xl border border-slate-200 bg-white p-5">
						<h2 className="font-heading text-lg font-semibold text-navy-900">Payment history</h2>
						<dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
							<div><dt className="text-slate-500">Last payment failure</dt><dd className="mt-1 font-medium text-navy-900">{formatDateTime(value.lastPaymentFailureAt)}</dd></div>
							<div><dt className="text-slate-500">Last successful invoice</dt><dd className="mt-1 font-medium text-navy-900">{formatDateTime(value.lastPaymentSuccessAt)}</dd></div>
						</dl>
					</section>
					<section className="rounded-xl border border-slate-200 bg-white p-5">
						<h2 className="font-heading text-lg font-semibold text-navy-900">Manage billing</h2>
						<p className="mt-2 text-sm text-slate-600">Stripe Billing Portal is the billing-management surface for payment methods, invoices, and cancellation. Rally26 applies resulting subscription changes only after signed Stripe webhook events arrive.</p>
						<button
							type="button"
							disabled={!value.billingPortalAvailable || portal.isPending}
							onClick={() => void openPortal()}
							className="mt-4 min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800 disabled:cursor-not-allowed disabled:opacity-50"
						>
							{portal.isPending ? "Opening Stripe…" : value.recoveryState === "PAYMENT_ACTION_REQUIRED" ? "Fix billing in Stripe" : "Manage billing in Stripe"}
						</button>
						{portal.isError && <p role="alert" className="mt-3 text-sm text-red-700">Could not open Stripe Billing Portal. Please try again.</p>}
					</section>
				</>
			)}
		</div>
	);
}

function RecoveryBanner({ state, cancelAtPeriodEnd }: { state: BillingRecoveryState; cancelAtPeriodEnd: boolean }) {
	if (cancelAtPeriodEnd && state === "CURRENT") {
		return <div role="status" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm font-medium text-amber-950">This subscription is scheduled to cancel at the end of its Stripe billing period. Open Stripe Billing Portal to review or change that cancellation.</div>;
	}
	if (state === "CURRENT") return null;
	const copy = state === "PAYMENT_ACTION_REQUIRED"
		? "A payment needs attention. Your organization stays accessible for billing recovery while the subscription is past due."
		: state === "ENDED"
			? "This subscription has ended."
			: "Subscription checkout or recovery is still required.";
	return <div role="status" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm font-medium text-amber-950">{copy}</div>;
}

function StatusPill({ status }: { status: OrganizationSubscriptionStatus }) {
	const classes = status === "ACTIVE" || status === "TRIALING"
		? "bg-green-100 text-green-800"
		: status === "PAST_DUE"
			? "bg-amber-100 text-amber-900"
			: "bg-slate-100 text-slate-700";
	return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{status.replaceAll("_", " ")}</span>;
}

function formatPlanPrice(amountMinor: number | null, currency: string | null, interval: string | null) {
	if (amountMinor == null || !currency) return "Contact Rally26";
	const value = new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amountMinor / 100);
	return `${value}${interval === "MONTHLY" ? " / month" : ""}`;
}

function formatDateTime(value: string | null) {
	return value ? new Date(value).toLocaleString() : "None recorded";
}
