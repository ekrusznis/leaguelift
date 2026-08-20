import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

/**
 * `/founding-organizations/join?code=XXXX` is the unlisted link the founder sends
 * directly to a hand-picked org (never linked from the public `/founding-organizations`
 * marketing page's own CTA, which stays pointed at Talk to Sales for general interest).
 * `RegisterPage` already handles the `?code=` param directly — this route exists only
 * so the founder can hand out a self-explanatory URL instead of `/auth/register?code=`.
 */
export function FoundingOrganizationJoinRedirect() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();

	useEffect(() => {
		const code = searchParams.get("code");
		navigate(code ? `/auth/register?code=${encodeURIComponent(code)}` : "/auth/register", { replace: true });
	}, [navigate, searchParams]);

	return null;
}
