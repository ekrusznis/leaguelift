import { z } from "zod";

export const signInSchema = z.object({
	email: z.string().trim().min(1, "Enter your email address.").email("Enter a valid email address."),
	password: z.string().min(1, "Enter your password."),
});
export type SignInFormValues = z.infer<typeof signInSchema>;

export const registerAccountSchema = z
	.object({
		firstName: z.string().trim().min(1, "First name is required."),
		lastName: z.string().trim().min(1, "Last name is required."),
		email: z.string().trim().min(1, "Work email is required.").email("Enter a valid email address."),
		password: z.string().min(8, "Password must be at least 8 characters."),
		confirmPassword: z.string().min(1, "Confirm your password."),
		agreeToTerms: z.literal(true, { error: "You must agree to the Terms and Privacy Policy." }),
		confirmAdult: z.literal(true, { error: "You must confirm you are at least 18 years old." }),
	})
	.refine((values) => values.password === values.confirmPassword, {
		message: "Passwords must match.",
		path: ["confirmPassword"],
	});
export type RegisterAccountFormValues = z.infer<typeof registerAccountSchema>;

export const forgotPasswordSchema = z.object({
	email: z.string().trim().min(1, "Enter your email address.").email("Enter a valid email address."),
});
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
	.object({
		password: z.string().min(8, "Password must be at least 8 characters."),
		confirmPassword: z.string().min(1, "Confirm your password."),
	})
	.refine((values) => values.password === values.confirmPassword, {
		message: "Passwords must match.",
		path: ["confirmPassword"],
	});
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

