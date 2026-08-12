import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useCreateSupportCase } from "../../features/support/api";
import { FormField } from "../../components/forms/FormField";
import { HOMEPAGE_SECTION_IDS } from "../content/nav";
import { captureAttribution } from "../analytics";
import { InlineAlert } from "./InlineAlert";
import { PageContainer } from "./PageContainer";
import { SectionHeading } from "./SectionHeading";
import { PrimaryButton } from "./buttons";

function newIdempotencyKey() {
	return typeof crypto !== "undefined" && "randomUUID" in crypto
		? crypto.randomUUID()
		: `contact-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

const contactSchema = z.object({
	name: z.string().trim().min(2, "Name must be at least 2 characters.").max(120),
	email: z.string().trim().min(1, "Email is required.").email("Enter a valid email address."),
	organization: z.string().trim().max(200).optional().or(z.literal("")),
	message: z.string().trim().min(20, "Tell us a bit more — at least 20 characters.").max(5000),
	// Honeypot — real visitors never see or fill this field (visually hidden, not just
	// off-screen, and never focusable). Matches the anti-spam pattern the old /contact
	// mock page used, now backed by a real submission worth protecting.
	company: z.string().max(0).optional().or(z.literal("")),
});
type ContactFormValues = z.infer<typeof contactSchema>;

/**
 * Real, backend-wired Contact Us section (ADR-059) replacing both the removed
 * /book-demo page and the non-functional /contact mock. Submits through the same
 * public, unauthenticated support-case endpoint the Help Center's own request form
 * uses (`useCreateSupportCase(false)` -> `POST /api/v1/public/support-cases`) rather
 * than a bespoke contact-form backend — this durably persists every inbound message
 * (visible to Platform Admins) before email is even attempted, and reuses the
 * existing idempotency/dedup behavior "for free." Category is hardcoded to `OTHER`:
 * these are pre-sale/general inquiries from people who, per the founder, "probably
 * aren't a customer yet" — not a real support ticket category.
 */
export function ContactUsSection() {
	const createCase = useCreateSupportCase(false);
	const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<ContactFormValues>({
		resolver: zodResolver(contactSchema),
		defaultValues: { name: "", email: "", organization: "", message: "", company: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		if (values.company) return; // honeypot tripped — silently drop, no error shown to the bot
		captureAttribution();
		const organizationLine = values.organization ? `Organization: ${values.organization}\n\n` : "";
		await createCase.mutateAsync({
			idempotencyKey,
			requesterName: values.name,
			requesterEmail: values.email,
			category: "OTHER",
			subject: "Website contact form inquiry",
			description: `${organizationLine}${values.message}`,
		});
		reset();
		setIdempotencyKey(newIdempotencyKey());
	});

	return (
		<section id={HOMEPAGE_SECTION_IDS.contactUs} className="scroll-mt-28 bg-white dark:bg-[#111827] py-20 sm:py-28">
			<PageContainer className="mx-auto flex max-w-2xl flex-col items-center gap-8">
				<SectionHeading
					eyebrow="Get in touch"
					heading="Questions before you get started?"
					copy="Tell us about your organization and what you're looking for — a real person will follow up."
				/>

				<div className="w-full rounded-[24px] border border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] p-6 shadow-sm sm:p-9">
					{createCase.isSuccess ? (
						<InlineAlert tone="success" title="Thanks — your message has been received.">
							We'll follow up at the email address you provided.
						</InlineAlert>
					) : (
						<form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
							<input type="text" tabIndex={-1} autoComplete="off" aria-hidden="true" className="hidden" {...register("company")} />
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="Name" required error={errors.name?.message} {...register("name")} />
								<FormField label="Email" type="email" required error={errors.email?.message} {...register("email")} />
							</div>
							<FormField label="Organization" hint="Optional" error={errors.organization?.message} {...register("organization")} />
							<div className="flex flex-col gap-1.5">
								<label htmlFor="contact-us-message" className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">
									Message <span aria-hidden="true" className="text-error-600">*</span>
								</label>
								<textarea
									id="contact-us-message"
									rows={5}
									required
									aria-invalid={!!errors.message}
									aria-describedby={errors.message ? "contact-us-message-error" : undefined}
									className={`rounded-[10px] border px-3.5 py-2.5 text-navy-900 dark:text-[#f8fafc] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 ${
										errors.message ? "border-error-600" : "border-slate-200 dark:border-[#334155]"
									}`}
									{...register("message")}
								/>
								{errors.message && (
									<p id="contact-us-message-error" role="alert" className="text-sm text-error-600">
										{errors.message.message}
									</p>
								)}
							</div>
							{createCase.isError && (
								<InlineAlert tone="error" title="Your message could not be sent. Please try again.">
									Your details remain in the form so you can retry.
								</InlineAlert>
							)}
							<PrimaryButton type="submit" loading={isSubmitting || createCase.isPending} className="w-full justify-center sm:w-auto sm:self-start">
								Send Message
							</PrimaryButton>
						</form>
					)}
				</div>
			</PageContainer>
		</section>
	);
}
