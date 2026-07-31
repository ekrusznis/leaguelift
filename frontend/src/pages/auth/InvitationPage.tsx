import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { acceptInvitation, messageForInvitationError } from "../../auth/authApi";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryDarkButton } from "../../marketing/components/buttons";

/**
 * There is no public "preview an invitation by token" endpoint — only
 * `POST /invitations/{token}/accept`, which requires an authenticated caller whose
 * email matches the invite (docs/openapi.yaml). Real authentication now exists
 * (traditional email/password — ADR-014), but wiring this page to actually call
 * that endpoint — checking sign-in state, calling the API, handling the 403
 * email-mismatch case for real instead of via a `state` query param — is a separate
 * follow-up. This still shows the section 26 states illustratively and
 * "Accept Invitation" still hands off straight to the dashboard rather than calling
 * the endpoint.
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
				<Seo title="Invitation Expired" description="This LeagueLift invitation has expired." noIndex />
				<h1 className="font-heading text-2xl font-extrabold text-white">This invitation has expired</h1>
				<p className="max-w-sm text-sm text-slate-300">
					Ask the organization administrator who invited you to send a new invitation.
				</p>
				<SecondaryDarkButton to="/contact">Contact organization administrator</SecondaryDarkButton>
			</div>
		);
	}

	return (
		<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
			<Seo title="Accept Invitation" description="Accept your invitation to join a LeagueLift organization." noIndex />
			<h1 className="font-heading text-2xl font-extrabold text-white">You&rsquo;ve been invited to LeagueLift</h1>
			<p className="max-w-sm text-sm text-slate-300">
				Sign in or create an account with the email address this invitation was sent to, then accept to join
				the organization.
			</p>
			{submitError && <InlineAlert tone="error" title={submitError} />}
			<div className="mt-2 flex flex-wrap justify-center gap-3">
				<PrimaryButton onClick={onAccept} loading={submitting}>
					Accept Invitation
				</PrimaryButton>
				{status === "authenticated" ? (
					<SecondaryDarkButton to="/app">Go to app</SecondaryDarkButton>
				) : (
					<SecondaryDarkButton to={`/auth/sign-in?next=${encodeURIComponent(`/auth/invitation?token=${token}`)}`}>
						Sign In First
					</SecondaryDarkButton>
				)}
			</div>
		</div>
	);
}
