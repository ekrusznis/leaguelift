import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { AuthTabs } from "../../components/forms/AuthTabs";
import { FormField } from "../../components/forms/FormField";
import { PasswordField } from "../../components/forms/PasswordField";
import { SocialAuthButton } from "../../components/forms/SocialAuthButton";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, TextButton } from "../../marketing/components/buttons";
import { featureFlags } from "../../marketing/featureFlags";
import { track } from "../../marketing/analytics";
import { useAuth } from "../../auth/AuthContext";
import { signInSchema, type SignInFormValues } from "./schema";

export function SignInPage() {
	const navigate = useNavigate();
	const { login } = useAuth();
	const [submitError, setSubmitError] = useState<string | null>(null);
	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<SignInFormValues>({ resolver: zodResolver(signInSchema), defaultValues: { email: "", password: "" } });

	const onSubmit = handleSubmit(async (values) => {
		track("sign_in_started");
		setSubmitError(null);
		const result = await login(values.email, values.password);
		if (result.success) {
			track("sign_in_succeeded");
			navigate("/app");
		} else {
			track("sign_in_failed");
			setSubmitError(result.error ?? "We could not sign you in. Check your information or try again.");
		}
	});

	return (
		<div className="flex flex-col gap-6">
			<Seo title="Sign In" description="Sign in to your LeagueLift account." noIndex />
			<AuthTabs active="sign-in" />

			<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<h1 className="font-heading text-2xl font-extrabold text-white">Welcome back</h1>
				<p className="mt-1 text-sm text-slate-300">Sign in to access your LeagueLift account.</p>

				<form onSubmit={onSubmit} noValidate className="mt-6 flex flex-col gap-5">
					<FormField
						label="Email address"
						type="email"
						autoComplete="email"
						required
						error={errors.email?.message}
						{...register("email")}
					/>
					<div className="flex flex-col gap-2">
						<PasswordField
							label="Password"
							autoComplete="current-password"
							required
							error={errors.password?.message}
							{...register("password")}
						/>
						<TextButton to="/auth/forgot-password" className="self-end text-slate-300 hover:text-white">
							Forgot password?
						</TextButton>
					</div>

					{submitError && <InlineAlert tone="error" title={submitError} />}

					<PrimaryButton type="submit" loading={isSubmitting} className="w-full justify-center">
						Sign In
					</PrimaryButton>
				</form>

				{featureFlags.socialAuthProviders && (
					<div className="mt-6 flex flex-col gap-3">
						<p className="text-center text-xs uppercase tracking-wide text-slate-500">or continue with</p>
						<div className="flex gap-3">
							<SocialAuthButton provider="Google" />
							<SocialAuthButton provider="Microsoft" />
							<SocialAuthButton provider="Apple" />
						</div>
					</div>
				)}

				<p className="mt-6 text-center text-sm text-slate-400">
					By signing in, you agree to our{" "}
					<Link to="/terms" className="text-green-400 hover:underline">
						Terms of Service
					</Link>{" "}
					and{" "}
					<Link to="/privacy" className="text-green-400 hover:underline">
						Privacy Policy
					</Link>
					.
				</p>
			</div>

			<p className="text-center text-sm text-slate-400">
				Need help?{" "}
				<Link to="/contact" className="text-green-400 hover:underline">
					Contact support
				</Link>
			</p>
		</div>
	);
}
