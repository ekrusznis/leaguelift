import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { acceptOwnershipTransferInvitation, messageForOwnershipTransferInvitationError } from "../../auth/authApi";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryDarkButton } from "../../marketing/components/buttons";

/**
 * Accepts an organization-ownership-transfer invitation
 * (`POST /ownership-transfer-invitations/{token}/accept`). Mirrors
 * `HouseholdInvitationPage.tsx` exactly — same no-public-preview limitation, same
 * authenticated-or-register-first flow, same `next` redirect-back pattern.
 */
export function OwnershipTransferInvitationPage() {
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
			await acceptOwnershipTransferInvitation(token);
			navigate("/app", { replace: true });
		} catch (error) {
			setSubmitError(messageForOwnershipTransferInvitationError(error));
		} finally {
			setSubmitting(false);
		}
	};

	if (!token) {
		return (
			<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<Seo title="Invitation Expired" description="This Rally26 invitation has expired." noIndex />
				<h1 className="font-heading text-2xl font-extrabold text-white">This invitation has expired</h1>
				<p className="max-w-sm text-sm text-slate-300">Ask whoever sent this invitation to send a new one.</p>
				<SecondaryDarkButton to="/contact">Contact support</SecondaryDarkButton>
			</div>
		);
	}

	const returnTo = `/auth/ownership-transfer-invitation?token=${token}`;

	return (
		<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
			<Seo title="Accept Ownership Transfer" description="Accept organization ownership on Rally26." noIndex />
			<h1 className="font-heading text-2xl font-extrabold text-white">You&rsquo;ve been invited to become an organization owner</h1>

			{status === "authenticated" ? (
				<>
					<p className="max-w-sm text-sm text-slate-300">
						Accepting gives the account you&rsquo;re signed in as full ownership of this organization, including billing,
						teams, and members. The current owner will become an Administrator.
					</p>
					{submitError && <InlineAlert tone="error" title={submitError} />}
					<div className="mt-2 flex flex-wrap justify-center gap-3">
						<PrimaryButton onClick={onAccept} loading={submitting}>Accept Ownership</PrimaryButton>
						<SecondaryDarkButton to="/app">Go to app</SecondaryDarkButton>
					</div>
				</>
			) : (
				<>
					<p className="max-w-sm text-sm text-slate-300">
						Sign in if you already have a Rally26 account, or create one — either way, use the email address this
						invitation was sent to so it can be linked automatically.
					</p>
					{submitError && <InlineAlert tone="error" title={submitError} />}
					<div className="mt-2 flex flex-wrap justify-center gap-3">
						<PrimaryButton to={`/auth/sign-in?next=${encodeURIComponent(returnTo)}`}>Sign In</PrimaryButton>
						<SecondaryDarkButton to={`/auth/register?next=${encodeURIComponent(returnTo)}`}>Create Account</SecondaryDarkButton>
					</div>
				</>
			)}
		</div>
	);
}
