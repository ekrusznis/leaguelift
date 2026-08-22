import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { verifyEmail } from "../../auth/authApi";
import { ApiError } from "../../lib/apiError";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryDarkButton } from "../../marketing/components/buttons";

export function VerifyEmailPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const token = searchParams.get("token")?.trim() ?? "";
	// Set when this link was reached via registering from an invitation-accept page
	// (EmailVerificationService/OwnerEmailVerificationHandler embed it in the emailed
	// link) — carried through to Sign In so the invitee lands back on accepting the
	// invitation instead of a generic destination.
	const next = searchParams.get("next");
	const signInHref = next && next.startsWith("/") ? `/auth/sign-in?next=${encodeURIComponent(next)}` : "/auth/sign-in";
	const [status, setStatus] = useState<"idle" | "submitting" | "verified" | "error">("idle");
	const [error, setError] = useState<string | null>(null);
	// Guards against a double-click firing two requests before the button's `disabled`
	// state re-renders. Without this, a fast double-click can send the same token twice:
	// the first request succeeds, the second correctly gets rejected as already-used, and
	// (before the EMAIL_VERIFICATION_ALREADY_USED special-case below existed) that second
	// response would overwrite the successful "verified" state with a scary generic error.
	const inFlightRef = useRef(false);

	const onVerify = async () => {
		if (!token || inFlightRef.current) return;
		inFlightRef.current = true;
		setError(null);
		setStatus("submitting");
		try {
			await verifyEmail(token);
			setStatus("verified");
		} catch (err) {
			// A link that's "already been used" almost always means this exact account
			// already got verified — by an earlier click, a slow request that actually
			// succeeded, or (rarely) a genuine race — not that the link was ever invalid.
			// Show success rather than an alarming error the user has no useful action for.
			if (err instanceof ApiError && err.code === "EMAIL_VERIFICATION_ALREADY_USED") {
				setStatus("verified");
			} else {
				setError("This verification link is invalid or expired. Request a new account verification email.");
				setStatus("error");
			}
		} finally {
			inFlightRef.current = false;
		}
	};

	// BUG-005: hand off to Sign In on its own once verified — the user shouldn't have
	// to click through a stale confirmation screen to get there.
	useEffect(() => {
		if (status !== "verified") return;
		const timer = window.setTimeout(() => navigate(signInHref, { replace: true }), 1500);
		return () => window.clearTimeout(timer);
	}, [status, signInHref, navigate]);

	if (!token) {
		return (
			<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<Seo title="Invalid Verification Link" description="This Rally26 verification link is invalid." noIndex />
				<h1 className="font-heading text-2xl font-extrabold text-white">Invalid verification link</h1>
				<p className="max-w-sm text-sm text-slate-300">Open the link from your verification email or register again.</p>
				<div className="mt-2 flex flex-wrap justify-center gap-3">
					<SecondaryDarkButton to="/auth/register">Create owner account</SecondaryDarkButton>
					<SecondaryDarkButton to="/auth/resend-verification">Resend verification</SecondaryDarkButton>
				</div>
			</div>
		);
	}

	return (
		<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
			<Seo title="Verify Email" description="Verify your Rally26 account email address." noIndex />
			<h1 className="font-heading text-2xl font-extrabold text-white">Verify your email</h1>
			<p className="max-w-sm text-sm text-slate-300">
				{next
					? "Verify your email address to continue accepting your invitation."
					: "Finish owner account setup by verifying your email address."}
			</p>
			{error && <InlineAlert tone="error" title={error} />}
			{status === "verified" && (
				<InlineAlert tone="success" title="Email verified">
					{next ? "Redirecting you to accept your invitation…" : "Redirecting you to sign in…"}
				</InlineAlert>
			)}
			{status !== "verified" && (
				<div className="mt-2 flex flex-wrap justify-center gap-3">
					<PrimaryButton onClick={onVerify} loading={status === "submitting"} disabled={status === "submitting"}>
						Verify Email
					</PrimaryButton>
					<SecondaryDarkButton to="/auth/resend-verification">Resend verification</SecondaryDarkButton>
					<SecondaryDarkButton to={signInHref}>Sign In</SecondaryDarkButton>
				</div>
			)}
		</div>
	);
}

