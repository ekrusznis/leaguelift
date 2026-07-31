import { useSearchParams } from "react-router-dom";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryDarkButton } from "../../marketing/components/buttons";

/** Never displays raw OIDC or stack-trace details (section 29). */
export function AuthErrorPage() {
	const [searchParams] = useSearchParams();
	const requestId = searchParams.get("requestId");

	return (
		<div className="flex flex-col items-center gap-4 rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
			<Seo title="Sign-In Error" description="We could not sign you in." noIndex />
			<span className="flex size-14 items-center justify-center rounded-full bg-error-600/15 text-error-600">
				<svg className="size-7" viewBox="0 0 24 24" fill="none" aria-hidden="true">
					<path d="M12 8v5M12 16.5h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
					<circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.6" />
				</svg>
			</span>
			<h1 className="font-heading text-2xl font-extrabold text-white">We couldn&rsquo;t sign you in</h1>
			<p className="max-w-sm text-sm text-slate-300">Check your information or try again.</p>
			{requestId && <p className="text-xs text-slate-400">Reference: {requestId}</p>}
			<div className="mt-2 flex flex-wrap justify-center gap-3">
				<PrimaryButton to="/auth/sign-in">Try Again</PrimaryButton>
				<SecondaryDarkButton to="/">Return Home</SecondaryDarkButton>
			</div>
			<SecondaryDarkButton to="/contact" className="mt-1">
				Contact Support
			</SecondaryDarkButton>
		</div>
	);
}
