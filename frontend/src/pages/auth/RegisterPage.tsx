import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { AuthTabs } from "../../components/forms/AuthTabs";
import { CheckboxField } from "../../components/forms/CheckboxField";
import { FormField } from "../../components/forms/FormField";
import { PasswordField } from "../../components/forms/PasswordField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { track } from "../../marketing/analytics";
import { useAuth } from "../../auth/AuthContext";
import { registerAccountSchema, type RegisterAccountFormValues } from "./schema";

/**
 * Owner registration only — non-owner personas are invitation-only in Phase 15.
 * Organization setup still happens after sign-in inside the app (`/app/organizations`),
 * so this page collects only identity credentials and now requires email verification
 * before first login.
 */
export function RegisterPage() {
	const navigate = useNavigate();
	const { register: registerAccount } = useAuth();
	const [step, setStep] = useState<"form" | "confirmation">("form");
	const [submitError, setSubmitError] = useState<string | null>(null);
	const [createdName, setCreatedName] = useState("");

	const accountForm = useForm<RegisterAccountFormValues>({
		resolver: zodResolver(registerAccountSchema),
		defaultValues: {
			firstName: "",
			lastName: "",
			email: "",
			password: "",
			confirmPassword: "",
			agreeToTerms: undefined,
			confirmAdult: undefined,
		} as unknown as RegisterAccountFormValues,
	});

	const onAccountSubmit = accountForm.handleSubmit(async (values) => {
		track("registration_started");
		setSubmitError(null);
		const result = await registerAccount({
			firstName: values.firstName,
			lastName: values.lastName,
			email: values.email,
			password: values.password,
		});
		if (result.success) {
			track("registration_succeeded");
			setCreatedName(values.firstName);
			setStep("confirmation");
		} else {
			setSubmitError(result.error ?? "We could not create your account. Please try again.");
		}
	});

	return (
		<div className="flex flex-col gap-6">
			<Seo title="Create Owner Account" description="Create your Rally26 organization owner account." noIndex />
			<AuthTabs active="register" />

			<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				{step === "form" && (
					<>
						<h1 className="font-heading text-2xl font-extrabold text-white">Create your owner account</h1>
						<p className="mt-1 text-sm text-slate-300">Verify your email to continue with organization setup.</p>
						<form onSubmit={onAccountSubmit} noValidate className="mt-6 flex flex-col gap-5">
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField
									tone="dark"
									label="First name"
									required
									error={accountForm.formState.errors.firstName?.message}
									{...accountForm.register("firstName")}
								/>
								<FormField
									tone="dark"
									label="Last name"
									required
									error={accountForm.formState.errors.lastName?.message}
									{...accountForm.register("lastName")}
								/>
							</div>
							<FormField
								tone="dark"
								label="Email"
								type="email"
								required
								error={accountForm.formState.errors.email?.message}
								{...accountForm.register("email")}
							/>
							<div className="grid gap-5 sm:grid-cols-2">
								<PasswordField
									tone="dark"
									label="Password"
									required
									error={accountForm.formState.errors.password?.message}
									{...accountForm.register("password")}
								/>
								<PasswordField
									tone="dark"
									label="Confirm password"
									required
									error={accountForm.formState.errors.confirmPassword?.message}
									{...accountForm.register("confirmPassword")}
								/>
							</div>
							<CheckboxField
								tone="dark"
								label={
									<>
										I agree to the{" "}
										<Link to="/terms" className="text-green-400 hover:underline">
											Terms of Service
										</Link>{" "}
										and{" "}
										<Link to="/privacy" className="text-green-400 hover:underline">
											Privacy Policy
										</Link>
										.
									</>
								}
								error={accountForm.formState.errors.agreeToTerms?.message}
								{...accountForm.register("agreeToTerms")}
							/>
							<CheckboxField
								tone="dark"
								label="I am at least 18 years old."
								error={accountForm.formState.errors.confirmAdult?.message}
								{...accountForm.register("confirmAdult")}
							/>

							{submitError && <InlineAlert tone="error" title={submitError} />}

							<PrimaryButton type="submit" loading={accountForm.formState.isSubmitting} className="w-full justify-center">
								Create Owner Account
							</PrimaryButton>
						</form>
					</>
				)}

				{step === "confirmation" && (
					<div className="flex flex-col items-center gap-4 py-4 text-center">
						<span className="flex size-14 items-center justify-center rounded-full bg-green-500/15 text-green-400">
							<svg className="size-7" viewBox="0 0 24 24" fill="none" aria-hidden="true">
								<path d="m5 12.5 4.5 4.5L19 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
							</svg>
						</span>
						<h1 className="font-heading text-2xl font-extrabold text-white">Check your email, {createdName}</h1>
						<p className="max-w-sm text-sm text-slate-300">
							Use the verification link we sent to activate your account, then sign in to create your organization.
						</p>
						<Link to="/auth/resend-verification" className="text-sm text-green-400 hover:underline">
							Didn&rsquo;t get the email? Resend verification
						</Link>
						<PrimaryButton onClick={() => navigate("/auth/sign-in")} icon="arrow" className="mt-2">
							Go to Sign In
						</PrimaryButton>
					</div>
				)}
			</div>
		</div>
	);
}
