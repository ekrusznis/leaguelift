import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { resendVerificationEmail } from "../../auth/authApi";
import { FormField } from "../../components/forms/FormField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, TextButton } from "../../marketing/components/buttons";
import { forgotPasswordSchema, type ForgotPasswordFormValues } from "./schema";

export function ResendVerificationPage() {
	const [submitted, setSubmitted] = useState(false);
	const [submitError, setSubmitError] = useState<string | null>(null);
	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<ForgotPasswordFormValues>({
		resolver: zodResolver(forgotPasswordSchema),
		defaultValues: { email: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		setSubmitError(null);
		try {
			await resendVerificationEmail(values.email);
			setSubmitted(true);
		} catch {
			setSubmitError("Could not request a new verification email. Please try again.");
		}
	});

	return (
		<div className="flex flex-col gap-6">
			<Seo title="Resend Verification Email" description="Request a new Rally26 account verification email." noIndex />
			<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<h1 className="font-heading text-2xl font-extrabold text-white">Resend verification email</h1>
				<p className="mt-1 text-sm text-slate-300">Enter your owner account email and we&rsquo;ll send a fresh verification link.</p>

				{submitted ? (
					<div className="mt-6">
						<InlineAlert tone="success" title="Check your email">
							If that address belongs to a pending owner account, a new verification email has been sent.
						</InlineAlert>
					</div>
				) : (
					<form onSubmit={onSubmit} noValidate className="mt-6 flex flex-col gap-5">
						<FormField
							tone="dark"
							label="Email address"
							type="email"
							autoComplete="email"
							required
							error={errors.email?.message}
							{...register("email")}
						/>
						{submitError && <InlineAlert tone="error" title={submitError} />}
						<PrimaryButton type="submit" loading={isSubmitting} className="w-full justify-center">
							Send verification email
						</PrimaryButton>
					</form>
				)}

				<TextButton to="/auth/sign-in" className="mt-6 inline-block text-slate-300 hover:text-white">
					Back to sign in
				</TextButton>
			</div>
			<p className="text-center text-sm text-slate-700 dark:text-[#cbd5e1]">
				Need help?{" "}
				<Link to="/contact" className="text-orange-600 dark:text-orange-400 underline">
					Contact support
				</Link>
			</p>
		</div>
	);
}

