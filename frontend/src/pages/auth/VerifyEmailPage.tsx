import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { verifyEmail } from "../../auth/authApi";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryDarkButton } from "../../marketing/components/buttons";

export function VerifyEmailPage() {
	const [searchParams] = useSearchParams();
	const token = searchParams.get("token")?.trim() ?? "";
	const [status, setStatus] = useState<"idle" | "submitting" | "verified" | "error">("idle");
	const [error, setError] = useState<string | null>(null);

	const onVerify = async () => {
		if (!token) return;
		setError(null);
		setStatus("submitting");
		try {
			await verifyEmail(token);
			setStatus("verified");
		} catch {
			setError("This verification link is invalid or expired. Request a new account verification email.");
			setStatus("error");
		}
	};

	if (!token) {
		return (
			<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<Seo title="Invalid Verification Link" description="This LeagueLift verification link is invalid." noIndex />
				<h1 className="font-heading text-2xl font-extrabold text-white">Invalid verification link</h1>
				<p className="max-w-sm text-sm text-slate-300">Open the link from your verification email or register again.</p>
				<SecondaryDarkButton to="/auth/register">Create owner account</SecondaryDarkButton>
			</div>
		);
	}

	return (
		<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
			<Seo title="Verify Email" description="Verify your LeagueLift account email address." noIndex />
			<h1 className="font-heading text-2xl font-extrabold text-white">Verify your email</h1>
			<p className="max-w-sm text-sm text-slate-300">Finish owner account setup by verifying your email address.</p>
			{error && <InlineAlert tone="error" title={error} />}
			{status === "verified" && <InlineAlert tone="success" title="Email verified">You can now sign in and create your organization.</InlineAlert>}
			<div className="mt-2 flex flex-wrap justify-center gap-3">
				<PrimaryButton onClick={onVerify} loading={status === "submitting"}>
					Verify Email
				</PrimaryButton>
				<SecondaryDarkButton to="/auth/sign-in">Sign In</SecondaryDarkButton>
			</div>
		</div>
	);
}

