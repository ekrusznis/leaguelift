import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";

/**
 * There is no password-reset backend at all yet (`ForgotPasswordPage` is a
 * client-side-only mock — no email is actually sent; see
 * docs/GA-TRANSITION-CHECKLIST.md). This page only exists so a stray
 * `/auth/reset-password` link fails gracefully instead of 404ing, rather than
 * pretending a real emailed-token flow exists behind it.
 */
export function ResetPasswordPage() {
	return (
		<div className="flex flex-col gap-6">
			<Seo title="Reset Password" description="Reset your LeagueLift password." noIndex />

			<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<h1 className="font-heading text-2xl font-extrabold text-white">Reset your password by email</h1>
				<p className="mt-2 text-sm text-slate-300">
					Password resets are completed through the link we email you, not on this page. Request a new link if
					you don&rsquo;t have one.
				</p>
				<PrimaryButton to="/auth/forgot-password" className="mt-6">
					Request a reset link
				</PrimaryButton>
			</div>
		</div>
	);
}
