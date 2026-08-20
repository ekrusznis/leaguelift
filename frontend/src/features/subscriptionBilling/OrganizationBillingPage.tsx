import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { ApiError } from "../../lib/apiError";
import { useApplyPlanChange, useCreateBillingPortal, useOrganizationSubscription, usePreviewPlanChange, useSubscriptionPlans } from "./api";
import type { BillingRecoveryState, OrganizationSubscriptionStatus, PlanChangePreview, PlanChangeResult } from "./types";

export function OrganizationBillingPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const subscription = useOrganizationSubscription(organizationId);
	const portal = useCreateBillingPortal();
	const plans = useSubscriptionPlans(organizationId);
	const preview = usePreviewPlanChange(organizationId);
	const apply = useApplyPlanChange(organizationId);
	const [targetPlanCode, setTargetPlanCode] = useState<string | null>(null);
	const [planPreview, setPlanPreview] = useState<PlanChangePreview | null>(null);
	const [applyResult, setApplyResult] = useState<PlanChangeResult | null>(null);

	if (!organizationId) return <ErrorState message="Organization is required." />;
	if (subscription.isLoading) return <LoadingState label="Loading billing…" />;
	if (subscription.isError) return <ErrorState message="Could not load organization billing." onRetry={() => subscription.refetch()} />;

	const value = subscription.data;
	const openPortal = async () => {
		const response = await portal.mutateAsync(organizationId);
		window.location.assign(response.url);
	};
	const startPlanChange = async (planCode: string) => {
		setApplyResult(null);
		setTargetPlanCode(planCode);
		const result = await preview.mutateAsync(planCode);
		setPlanPreview(result);
	};
	const cancelPlanChange = () => {
		setTargetPlanCode(null);
		setPlanPreview(null);
	};
	const confirmPlanChange = async () => {
		if (!targetPlanCode) return;
		const result = await apply.mutateAsync(targetPlanCode);
		setApplyResult(result);
		if (result.outcome === "CHECKOUT_REQUIRED" && result.checkoutUrl) {
			window.location.assign(result.checkoutUrl);
			return;
		}
		if (result.outcome !== "BLOCKED") {
			setTargetPlanCode(null);
			setPlanPreview(null);
		}
	};

	return (
		<div className="mx-auto flex w-full max-w-4xl flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Billing</h1>
				<p className="mt-1 text-slate-500 dark:text-[#cbd5e1]">Review your Rally26 organization subscription and manage billing through Stripe.</p>
			</div>
			{!value ? (
				<div className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-6">
					<h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">No subscription record yet</h2>
					<p className="mt-2 text-sm text-slate-600 dark:text-[#cbd5e1]">Complete owner onboarding to choose a plan and start subscription checkout.</p>
				</div>
			) : (
				<>
					<RecoveryBanner state={value.recoveryState} cancelAtPeriodEnd={value.cancelAtPeriodEnd} downgradeToPlanCode={value.downgradeToPlanCode} currentPeriodEnd={value.currentPeriodEnd} plans={plans.data ?? []} />
					<div className="grid gap-4 md:grid-cols-2">
						<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
							<p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-[#cbd5e1]">Plan</p>
							<h2 className="mt-1 font-heading text-xl font-semibold text-navy-900 dark:text-[#f8fafc]">{value.planName ?? value.planCode}</h2>
							<p className="mt-2 text-sm text-slate-600 dark:text-[#cbd5e1]">{formatPlanPrice(value.amountMinor, value.currency, value.billingInterval)}</p>
						</section>
						<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
							<p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-[#cbd5e1]">Subscription status</p>
							<div className="mt-2"><StatusPill status={value.status} /></div>
							<p className="mt-3 text-sm text-slate-600 dark:text-[#cbd5e1]">Stripe subscription webhooks—not browser redirects—control Rally26 activation and suspension.</p>
						</section>
					</div>
					<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
						<h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Payment history</h2>
						<dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
							<div><dt className="text-slate-500 dark:text-[#cbd5e1]">Last payment failure</dt><dd className="mt-1 font-medium text-navy-900 dark:text-[#f8fafc]">{formatDateTime(value.lastPaymentFailureAt)}</dd></div>
							<div><dt className="text-slate-500 dark:text-[#cbd5e1]">Last successful invoice</dt><dd className="mt-1 font-medium text-navy-900 dark:text-[#f8fafc]">{formatDateTime(value.lastPaymentSuccessAt)}</dd></div>
						</dl>
					</section>
					<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
						<h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Change plan</h2>
						<p className="mt-2 text-sm text-slate-600 dark:text-[#cbd5e1]">Switch plans at any time. Upgrades and paid-to-paid changes apply immediately with prorated billing; a downgrade to Free takes effect at the end of your current billing period.</p>
						{plans.isLoading && <p className="mt-4 text-sm text-slate-500 dark:text-[#cbd5e1]">Loading plans…</p>}
						{plans.data && (
							<div className="mt-4 grid gap-3 sm:grid-cols-3">
								{plans.data.map((plan) => {
									const isCurrent = plan.code === value.planCode;
									return (
										<div key={plan.code} className={`rounded-lg border p-4 ${isCurrent ? "border-green-500 ring-2 ring-green-100" : "border-slate-200 dark:border-[#334155]"}`}>
											<p className="font-semibold text-navy-900 dark:text-[#f8fafc]">{plan.name}</p>
											<p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">{formatPlanPrice(plan.amountMinor, plan.currency, plan.billingInterval)}</p>
											{isCurrent ? (
												<span className="mt-3 inline-block rounded-full bg-green-100 px-2 py-1 text-xs font-bold text-green-800">Current plan</span>
											) : (
												<button
													type="button"
													disabled={preview.isPending && targetPlanCode === plan.code}
													onClick={() => void startPlanChange(plan.code)}
													className="mt-3 min-h-11 rounded-md border border-navy-900 px-3 py-2 text-sm font-semibold text-navy-900 dark:text-[#f8fafc] disabled:opacity-50"
												>
													Switch to {plan.name}
												</button>
											)}
										</div>
									);
								})}
							</div>
						)}
						{targetPlanCode && planPreview && (
							<div className="mt-5 rounded-xl border border-slate-200 dark:border-[#334155] bg-slate-50 dark:bg-[#1e293b] p-4">
								{planPreview.violations.length > 0 ? (
									<>
										<p className="font-semibold text-navy-900 dark:text-[#f8fafc]">This plan can&rsquo;t be applied yet</p>
										<ul className="mt-3 flex flex-col gap-2">
											{planPreview.violations.map((violation) => (
												<li key={violation.code} className="text-sm text-slate-700 dark:text-[#cbd5e1]">
													{violation.message}
													{violation.actionLink && <> <Link to={violation.actionLink} className="font-semibold text-green-700 hover:underline">Go fix this</Link></>}
												</li>
											))}
										</ul>
										<p className="mt-3 text-sm text-slate-600 dark:text-[#cbd5e1]">
											Still stuck? <Link to={appPaths.helpSupport()} className="font-semibold underline">Contact support</Link>.
										</p>
										<button type="button" onClick={cancelPlanChange} className="mt-4 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 text-sm font-semibold text-slate-700 dark:text-[#cbd5e1]">Close</button>
									</>
								) : (
									<>
										<p className="font-semibold text-navy-900 dark:text-[#f8fafc]">Confirm switch to {planPreview.targetPlanCode}?</p>
										<p className="mt-1 text-sm text-slate-600 dark:text-[#cbd5e1]">
											{planPreview.direction === "DOWNGRADE" && planPreview.targetPlanCode === "FREE"
												? "You'll keep your current plan's access until the billing period ends, then switch to Free."
												: "This takes effect immediately, with prorated billing where applicable."}
										</p>
										<div className="mt-4 flex gap-2">
											<button type="button" disabled={apply.isPending} onClick={() => void confirmPlanChange()} className="min-h-11 rounded-md bg-navy-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
												{apply.isPending ? "Applying…" : "Confirm"}
											</button>
											<button type="button" onClick={cancelPlanChange} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 text-sm font-semibold text-slate-700 dark:text-[#cbd5e1]">Cancel</button>
										</div>
									</>
								)}
							</div>
						)}
						{applyResult?.outcome === "APPLIED" && <p role="status" className="mt-4 text-sm font-medium text-green-700">Plan changed successfully.</p>}
						{applyResult?.outcome === "SCHEDULED_DOWNGRADE" && <p role="status" className="mt-4 text-sm font-medium text-green-700">Your plan will switch to Free on {formatDateTime(applyResult.effectiveAt)}.</p>}
						{(preview.isError || apply.isError) && (
							<p role="alert" className="mt-4 text-sm text-red-700">
								{describePlanChangeError(preview.error ?? apply.error)}
							</p>
						)}
					</section>
					<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
						<h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Manage billing</h2>
						<p className="mt-2 text-sm text-slate-600 dark:text-[#cbd5e1]">Stripe Billing Portal is the billing-management surface for payment methods and invoices. Rally26 applies resulting subscription changes only after signed Stripe webhook events arrive.</p>
						<button
							type="button"
							disabled={!value.billingPortalAvailable || portal.isPending}
							onClick={() => void openPortal()}
							className="mt-4 min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800 disabled:cursor-not-allowed disabled:opacity-50"
						>
							{portal.isPending ? "Opening Stripe…" : value.recoveryState === "PAYMENT_ACTION_REQUIRED" ? "Fix billing in Stripe" : "Manage billing in Stripe"}
						</button>
						{!value.billingPortalAvailable && <p className="mt-3 text-sm text-slate-500 dark:text-[#cbd5e1]">The Free plan has no Stripe billing to manage — upgrade to a paid plan above to unlock this.</p>}
						{portal.isError && <p role="alert" className="mt-3 text-sm text-red-700">Could not open Stripe Billing Portal. Please try again.</p>}
					</section>
				</>
			)}
		</div>
	);
}

function describePlanChangeError(error: unknown): string {
	return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}

function RecoveryBanner({
	state,
	cancelAtPeriodEnd,
	downgradeToPlanCode,
	currentPeriodEnd,
	plans,
}: {
	state: BillingRecoveryState;
	cancelAtPeriodEnd: boolean;
	downgradeToPlanCode: string | null;
	currentPeriodEnd: string | null;
	plans: { code: string; name: string }[];
}) {
	if (cancelAtPeriodEnd && downgradeToPlanCode && state === "CURRENT") {
		const targetName = plans.find((plan) => plan.code === downgradeToPlanCode)?.name ?? downgradeToPlanCode;
		return (
			<div role="status" className="rounded-xl border border-blue-300 bg-blue-50 dark:bg-blue-950 p-4 text-sm font-medium text-blue-950">
				This subscription will switch to {targetName} on {formatDateTime(currentPeriodEnd)}. You can change your mind any time before then from this page.
			</div>
		);
	}
	if (cancelAtPeriodEnd && state === "CURRENT") {
		return <div role="status" className="rounded-xl border border-amber-300 bg-amber-50 dark:bg-amber-950 p-4 text-sm font-medium text-amber-950">This subscription is scheduled to cancel at the end of its Stripe billing period. Open Stripe Billing Portal to review or change that cancellation.</div>;
	}
	if (state === "CURRENT") return null;
	const copy = state === "PAYMENT_ACTION_REQUIRED"
		? "A payment needs attention. Your organization stays accessible for billing recovery while the subscription is past due."
		: state === "ENDED"
			? "This subscription has ended."
			: "Subscription checkout or recovery is still required.";
	return <div role="status" className="rounded-xl border border-amber-300 bg-amber-50 dark:bg-amber-950 p-4 text-sm font-medium text-amber-950">{copy}</div>;
}

function StatusPill({ status }: { status: OrganizationSubscriptionStatus }) {
	const classes = status === "ACTIVE" || status === "TRIALING"
		? "bg-green-100 text-green-800"
		: status === "PAST_DUE"
			? "bg-amber-100 text-amber-900"
			: "bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-[#cbd5e1]";
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
