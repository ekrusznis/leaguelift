import { useNavigate, useSearchParams } from "react-router-dom";
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
	const [searchParams] = useSearchParams();
	const token = searchParams.get("token");
	const state = searchParams.get("state");

	if (!token || state === "expired") {
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

	if (state === "wrong-email") {
		return (
			<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<Seo title="Invitation Email Mismatch" description="This LeagueLift invitation was sent to a different email address." noIndex />
				<h1 className="font-heading text-2xl font-extrabold text-white">This invitation was sent to a different email</h1>
				<p className="max-w-sm text-sm text-slate-300">
					Sign in with the email address the invitation was sent to, or ask the organization for a new
					invitation.
				</p>
				<PrimaryButton to="/auth/sign-in">Sign in with a different account</PrimaryButton>
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
			<div className="mt-2 flex flex-wrap justify-center gap-3">
				<PrimaryButton onClick={() => navigate("/app")}>Accept Invitation</PrimaryButton>
				<SecondaryDarkButton to="/auth/sign-in">Sign In First</SecondaryDarkButton>
			</div>
		</div>
	);
}
