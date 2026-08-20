import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AuthTabs } from "../../components/forms/AuthTabs";
import { CheckboxField } from "../../components/forms/CheckboxField";
import { FormField } from "../../components/forms/FormField";
import { PasswordField } from "../../components/forms/PasswordField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { track } from "../../marketing/analytics";
import { useAuth } from "../../auth/AuthContext";
import { useValidateFoundingPromoCode } from "../../features/foundingOrg/api";
import { registerAccountSchema, type RegisterAccountFormValues } from "./schema";

/** Reads the invitation token out of a `next=/auth/invitation?token=...` destination, if that's where `next` points. */
function invitationTokenFromNext(next: string | null): string | undefined {
	if (!next || !next.startsWith("/auth/invitation")) return undefined;
	try {
		return new URL(next, window.location.origin).searchParams.get("token") ?? undefined;
	} catch {
		return undefined;
	}
}

/**
 * The backend endpoint this calls (`/auth/register-owner`) only ever creates a bare
 * AppUser account pending email verification — it never creates an organization, so
 * this same form doubles as the "I don't have an account yet" path for someone
 * accepting an invitation (InvitationPage.tsx), not just for people setting up their
 * own org. Organization setup itself still happens after sign-in inside the app
 * (`/app/organizations`).
 *
 * `next` (if present) is carried through to the post-verification "Go to Sign In" link
 * so an invited person who registers lands back on the invitation page — where they can
 * accept with their invited role — instead of the generic sign-in destination. When
 * `next` points at an invitation, its token is also sent with registration itself so the
 * *verification email's own link* (opened from a different tab/device than this one)
 * carries the same redirect — otherwise the invitation would be silently orphaned for
 * anyone who doesn't keep this exact tab open through email verification.
 */
export function RegisterPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const next = searchParams.get("next");
	const invitationToken = invitationTokenFromNext(next);
	const foundingPromoCode = searchParams.get("code")?.trim().toUpperCase() || undefined;
	const foundingPromoValidation = useValidateFoundingPromoCode(foundingPromoCode ?? null);
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
			invitationToken,
			agreeToTerms: values.agreeToTerms,
			confirmAdult: values.confirmAdult,
			foundingPromoCode,
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
			<Seo
				title={invitationToken ? "Create Account" : "Create Owner Account"}
				description={
					invitationToken
						? "Create your Rally26 account to accept your invitation."
						: "Create your Rally26 organization owner account."
				}
				noIndex
			/>
			<AuthTabs active="register" />

			<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				{step === "form" && (
					<>
						<h1 className="font-heading text-2xl font-extrabold text-white">
							{foundingPromoCode
								? "Join as a Founding Organization"
								: invitationToken
									? "Create your account"
									: "Create your owner account"}
						</h1>
						<p className="mt-1 text-sm text-slate-300">
							{foundingPromoCode
								? "Verify your email to set up your organization with free Founding Organization access."
								: invitationToken
									? "Verify your email to continue accepting your invitation."
									: "Verify your email to continue with organization setup."}
						</p>
						{foundingPromoCode && foundingPromoValidation.data && !foundingPromoValidation.data.valid && (
							<div className="mt-4">
								<InlineAlert tone="error" title={foundingPromoValidation.data.reason ?? "This founding organization code cannot be used."} />
							</div>
						)}
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
										<Link to="/terms" className="text-green-400 underline">
											Terms of Service
										</Link>{" "}
										and{" "}
										<Link to="/privacy" className="text-green-400 underline">
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

							<PrimaryButton
								type="submit"
								loading={accountForm.formState.isSubmitting}
								disabled={!!foundingPromoCode && foundingPromoValidation.data?.valid === false}
								className="w-full justify-center"
							>
								{foundingPromoCode ? "Join Founding Organizations" : invitationToken ? "Create Account" : "Create Owner Account"}
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
							Use the verification link we sent to activate your account, then sign in to{" "}
							{invitationToken ? "accept your invitation" : "create your organization"}.
						</p>
						<Link to="/auth/resend-verification" className="text-sm text-green-400 hover:underline">
							Didn&rsquo;t get the email? Resend verification
						</Link>
						<PrimaryButton
							onClick={() =>
								navigate(next && next.startsWith("/") ? `/auth/sign-in?next=${encodeURIComponent(next)}` : "/auth/sign-in")
							}
							icon="arrow"
							className="mt-2"
						>
							Go to Sign In
						</PrimaryButton>
					</div>
				)}
			</div>
		</div>
	);
}
