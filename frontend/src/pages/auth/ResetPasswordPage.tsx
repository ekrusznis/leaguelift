import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useSearchParams } from "react-router-dom";
import { completePasswordReset, messageForPasswordResetError } from "../../auth/authApi";
import { PasswordField } from "../../components/forms/PasswordField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { resetPasswordSchema, type ResetPasswordFormValues } from "./schema";

export function ResetPasswordPage() {
	const [searchParams] = useSearchParams();
	const token = searchParams.get("token")?.trim() ?? "";
	const [completed, setCompleted] = useState(false);
	const [submitError, setSubmitError] = useState<string | null>(null);
	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<ResetPasswordFormValues>({
		resolver: zodResolver(resetPasswordSchema),
		defaultValues: { password: "", confirmPassword: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		if (!token) return;
		setSubmitError(null);
		try {
			await completePasswordReset(token, values.password);
			setCompleted(true);
		} catch (error) {
			setSubmitError(messageForPasswordResetError(error));
		}
	});

	if (!token) {
		return (
			<div className="flex flex-col gap-6">
				<Seo title="Reset Password" description="Reset your Rally26 password." noIndex />
				<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 text-center shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
					<h1 className="font-heading text-2xl font-extrabold text-white">Invalid reset link</h1>
					<p className="mt-2 text-sm text-slate-300">Open the full reset link from your email, or request a new one.</p>
					<PrimaryButton to="/auth/forgot-password" className="mt-6">
						Request a reset link
					</PrimaryButton>
				</div>
			</div>
		);
	}

	return (
		<div className="flex flex-col gap-6">
			<Seo title="Reset Password" description="Reset your Rally26 password." noIndex />

			<div className="rounded-[24px] border border-white/[0.16] bg-navy-800 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.32)] sm:p-9">
				<h1 className="font-heading text-2xl font-extrabold text-white">Reset your password</h1>
				<p className="mt-2 text-sm text-slate-300">Choose a new password for your Rally26 account.</p>

				{completed ? (
					<div className="mt-6">
						<InlineAlert tone="success" title="Password updated">
							Your password has been reset. You can now sign in with your new password.
						</InlineAlert>
						<PrimaryButton to="/auth/sign-in" className="mt-6">
							Go to sign in
						</PrimaryButton>
					</div>
				) : (
					<form onSubmit={onSubmit} noValidate className="mt-6 flex flex-col gap-5">
						<PasswordField
							label="New password"
							autoComplete="new-password"
							required
							error={errors.password?.message}
							{...register("password")}
						/>
						<PasswordField
							label="Confirm new password"
							autoComplete="new-password"
							required
							error={errors.confirmPassword?.message}
							{...register("confirmPassword")}
						/>
						{submitError && <InlineAlert tone="error" title={submitError} />}
						<PrimaryButton type="submit" loading={isSubmitting} className="w-full justify-center">
							Reset password
						</PrimaryButton>
					</form>
				)}
				<p className="mt-6 text-center text-sm text-slate-400">
					Need help?{" "}
					<Link to="/contact" className="text-green-400 hover:underline">
						Contact support
					</Link>
				</p>
			</div>
		</div>
	);
}
