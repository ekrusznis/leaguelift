import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { acceptInvitation, messageForInvitationError } from "../../auth/authApi";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryDarkButton } from "../../marketing/components/buttons";

/**
 * Calls the real `POST /invitations/{token}/accept` endpoint (docs/openapi.yaml), which
 * requires an authenticated caller whose email matches the invite. There is still no
 * public "preview an invitation by token" endpoint, so an unauthenticated visitor can't
 * be shown *who* invited them or *which* organization/role before they sign in — only
 * that an invitation exists. For that case this page offers both auth paths (an invited
 * person may or may not already have a Rally26 account) and carries a `next` redirect
 * back to this same URL so accepting resumes automatically once they're authenticated.
 * The 403 email-mismatch case is surfaced via `messageForInvitationError`.
 */
export function InvitationPage() {
	const navigate = useNavigate();
	const { status } = useAuth();
	const [searchParams] = useSearchParams();
	const token = searchParams.get("token");
	const [submitting, setSubmitting] = useState(false);
	const [submitError, setSubmitError] = useState<string | null>(null);

	const onAccept = async () => {
		if (!token) return;
		setSubmitError(null);
		setSubmitting(true);
		try {
			await acceptInvitation(token);
			navigate("/app", { replace: true });
		} catch (error) {
			setSubmitError(messageForInvitationError(error));
		} finally {
			setSubmitting(false);
		}
	};

	if (!token) {
		return (
			<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<Seo title="Invitation Expired" description="This Rally26 invitation has expired." noIndex />
				<h1 className="font-heading text-2xl font-extrabold text-white">This invitation has expired</h1>
				<p className="max-w-sm text-sm text-slate-300">
					Ask the organization administrator who invited you to send a new invitation.
				</p>
				<SecondaryDarkButton to="/contact">Contact organization administrator</SecondaryDarkButton>
			</div>
		);
	}

	// So Sign In / Register can send the person straight back here afterward instead of
	// dropping them on the generic dashboard — accepting the invitation is the point.
	const returnTo = `/auth/invitation?token=${token}`;

	return (
		<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
			<Seo title="Accept Invitation" description="Accept your invitation to join a Rally26 organization." noIndex />
			<h1 className="font-heading text-2xl font-extrabold text-white">You&rsquo;ve been invited to Rally26</h1>

			{status === "authenticated" ? (
				<>
					<p className="max-w-sm text-sm text-slate-300">
						Accept to join the organization with the account you&rsquo;re signed in as.
					</p>
					{submitError && <InlineAlert tone="error" title={submitError} />}
					<div className="mt-2 flex flex-wrap justify-center gap-3">
						<PrimaryButton onClick={onAccept} loading={submitting}>
							Accept Invitation
						</PrimaryButton>
						<SecondaryDarkButton to="/app">Go to app</SecondaryDarkButton>
					</div>
				</>
			) : (
				<>
					<p className="max-w-sm text-sm text-slate-300">
						Sign in if you already have a Rally26 account, or create one — either way, use the email
						address this invitation was sent to so it can be linked automatically.
					</p>
					{submitError && <InlineAlert tone="error" title={submitError} />}
					<div className="mt-2 flex flex-wrap justify-center gap-3">
						<PrimaryButton to={`/auth/sign-in?next=${encodeURIComponent(returnTo)}`}>Sign In</PrimaryButton>
						<SecondaryDarkButton to={`/auth/register?next=${encodeURIComponent(returnTo)}`}>
							Create Account
						</SecondaryDarkButton>
					</div>
				</>
			)}
		</div>
	);
}
