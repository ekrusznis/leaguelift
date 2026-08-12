import { useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useOwnerPayoutStatus, useStartPayoutOnboarding } from "./api";

/**
 * Stripe Connect Express onboarding only (ADR-005) — no live charge routing yet.
 * "Connect payouts" is owner-only on the backend (MembershipService.requireOwnerRole);
 * the frontend doesn't currently have a cheap way to know the caller's own role for
 * this org, so the button is always shown and a non-owner gets the backend's 403
 * surfaced inline — matches DESIGN-DOC.md section 4.4's "show, don't hide" guidance.
 */
export function PayoutConnectPanel({ organizationId }: { organizationId: string }) {
	const { data: status, isLoading, isError, refetch } = useOwnerPayoutStatus(organizationId);
	const startOnboarding = useStartPayoutOnboarding(organizationId);
	const [error, setError] = useState<string | null>(null);

	if (isLoading) {
		return <LoadingState label="Loading payout status…" />;
	}
	if (isError) {
		return <ErrorState message="Could not load payout status." onRetry={() => refetch()} />;
	}

	async function handleConnect() {
		setError(null);
		try {
			const base = `${window.location.origin}/app/organizations/${organizationId}`;
			const result = await startOnboarding.mutateAsync({
				refreshUrl: `${base}?stripeOnboarding=refresh`,
				returnUrl: `${base}?stripeOnboarding=return`,
			});
			window.location.href = result.onboardingUrl;
		} catch {
			setError("Could not start payout onboarding. Only the organization owner can do this, and a test-mode Stripe key must be configured.");
		}
	}

	return (
		<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-4">
			<div className="min-w-0 flex-1">
				{status?.isFullyConnected ? (
					<>
						<p className="font-medium text-navy dark:text-[#f8fafc]">Payouts connected</p>
						<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Stripe account {status.stripeAccountId}</p>
					</>
				) : status ? (
					<>
						<p className="font-medium text-navy dark:text-[#f8fafc]">Payout setup in progress</p>
						<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Finish the remaining steps in Stripe's onboarding flow.</p>
					</>
				) : (
					<>
						<p className="font-medium text-navy dark:text-[#f8fafc]">Payouts not connected</p>
						<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Connect a Stripe account to receive payouts (test mode only for now).</p>
					</>
				)}
				{error && (
					<p role="alert" className="mt-1 text-sm text-error-red">
						{error}
					</p>
				)}
			</div>
			<Button type="button" variant="secondary" className="shrink-0" onClick={handleConnect} disabled={startOnboarding.isPending}>
				{startOnboarding.isPending ? "Starting…" : status?.isFullyConnected ? "Manage" : "Connect payouts"}
			</Button>
		</div>
	);
}
