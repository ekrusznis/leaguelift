type Provider = "Google" | "Microsoft" | "Apple";

/**
 * Rendered only behind `featureFlags.socialAuthProviders` (section 24.5: "Do not
 * show provider buttons until configured") — no OIDC social connection exists yet.
 */
export function SocialAuthButton({ provider, onClick }: { provider: Provider; onClick?: () => void }) {
	return (
		<button
			type="button"
			onClick={onClick}
			className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-[10px] border border-white/20 bg-white/5 px-3 py-2.5 text-sm font-medium text-white hover:bg-white/10 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400"
		>
			Continue with {provider}
		</button>
	);
}
