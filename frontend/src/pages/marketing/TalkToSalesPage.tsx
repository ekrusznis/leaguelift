import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "../../components/forms/FormField";
import { useCreateSupportCase } from "../../features/support/api";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { PageContainer } from "../../marketing/components/PageContainer";
import { GuidedOnboardingCard } from "../../marketing/components/GuidedOnboardingCard";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { captureAttribution, track } from "../../marketing/analytics";
import { heroImages } from "../../marketing/heroImages";

function newIdempotencyKey() {
	return typeof crypto !== "undefined" && "randomUUID" in crypto
		? crypto.randomUUID()
		: `sales-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

const talkToSalesSchema = z.object({
	name: z.string().trim().min(2, "Name must be at least 2 characters.").max(120),
	workEmail: z.string().trim().min(1, "Work email is required.").email("Enter a valid email address."),
	organizationName: z.string().trim().min(1, "Organization name is required.").max(200),
	message: z.string().trim().min(10, "Tell us a bit about your organization and what you're looking for.").max(5000),
	// Honeypot — real visitors never see or fill this field.
	company: z.string().max(0).optional().or(z.literal("")),
});
type TalkToSalesFormValues = z.infer<typeof talkToSalesSchema>;

/**
 * A real, backend-wired lead form (LR-021) replacing the old 25-field version, which
 * validated, showed a fake "request received" confirmation with a generated reference
 * number, and never persisted or sent the submission anywhere — every prospective
 * customer who filled it out was silently dropped. Submits through the same public
 * support-case endpoint `ContactUsSection` uses (`POST /api/v1/public/support-cases`),
 * which durably persists the inquiry and is visible to Platform Admins, and email
 * notifications route to support@rally26.com per the existing outbox/Resend wiring —
 * no separate sales-lead pipeline needed for this scale.
 */
export function TalkToSalesPage() {
	const createCase = useCreateSupportCase(false);
	const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<TalkToSalesFormValues>({
		resolver: zodResolver(talkToSalesSchema),
		defaultValues: { name: "", workEmail: "", organizationName: "", message: "", company: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		if (values.company) return; // honeypot tripped — silently drop
		track("sales_form_submitted");
		captureAttribution();
		await createCase.mutateAsync({
			idempotencyKey,
			requesterName: values.name,
			requesterEmail: values.workEmail,
			category: "OTHER",
			subject: `Talk to sales: ${values.organizationName}`,
			description: values.message,
		});
		reset();
		setIdempotencyKey(newIdempotencyKey());
	});

	return (
		<>
			<Seo
				title="Talk to Our Team"
				description="Tell us about your organization and get a guided setup for teams, dues, fundraising, and more."
			/>

			<section className="bg-navy-950 py-20 sm:py-24">
				<PageContainer className="grid items-center gap-10 lg:grid-cols-[1.1fr_0.9fr]">
					<SectionHeading
						tone="dark"
						align="left"
						heading="Let's get your organization set up."
						copy="Tell us a bit about your organization and a member of our team will follow up directly."
					/>
					<div className="overflow-hidden rounded-[24px] border border-white/10">
						<img
							src={heroImages.communityHandoff}
							alt="A coach and families gathered around a team gear table at a youth sports event"
							className="aspect-[4/3] w-full object-cover"
						/>
					</div>
				</PageContainer>
			</section>

			<section className="bg-white dark:bg-[#111827] py-16">
				<PageContainer>
					<GuidedOnboardingCard />
				</PageContainer>
			</section>

			<section className="bg-ice-50 dark:bg-[#0f172a] py-16 sm:py-20">
				<PageContainer className="mx-auto max-w-xl">
					<h2 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Tell us about your organization</h2>
					<p className="mt-2 text-slate-700 dark:text-[#cbd5e1]">There is no obligation, and requests are reviewed on a rolling basis.</p>

					{createCase.isSuccess ? (
						<div className="mt-8">
							<InlineAlert tone="success" title="Thanks — your request has been received.">
								A member of our team will follow up at the email address you provided.
							</InlineAlert>
						</div>
					) : (
						<form onSubmit={onSubmit} noValidate className="mt-8 flex flex-col gap-5">
							<input type="text" tabIndex={-1} autoComplete="off" aria-hidden="true" className="hidden" {...register("company")} />
							<FormField label="Your name" required error={errors.name?.message} {...register("name")} />
							<FormField label="Work email" type="email" required error={errors.workEmail?.message} {...register("workEmail")} />
							<FormField label="Organization name" required error={errors.organizationName?.message} {...register("organizationName")} />
							<div className="flex flex-col gap-1.5">
								<label htmlFor="sales-message" className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">
									What are you looking for? <span aria-hidden="true" className="text-error-600">*</span>
								</label>
								<textarea
									id="sales-message"
									rows={5}
									required
									aria-invalid={!!errors.message}
									aria-describedby={errors.message ? "sales-message-error" : undefined}
									className={`rounded-[10px] border px-3.5 py-2.5 text-navy-900 dark:text-[#f8fafc] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 ${
										errors.message ? "border-error-600" : "border-slate-200 dark:border-[#334155]"
									}`}
									placeholder="Sport, approximate number of teams, current tools, and anything else that would help us prepare."
									{...register("message")}
								/>
								{errors.message && (
									<p id="sales-message-error" role="alert" className="text-sm text-error-600">
										{errors.message.message}
									</p>
								)}
							</div>
							{createCase.isError && (
								<InlineAlert tone="error" title="Your request could not be sent. Please try again.">
									Your details remain in the form so you can retry.
								</InlineAlert>
							)}
							<PrimaryButton type="submit" loading={isSubmitting || createCase.isPending} className="w-full justify-center sm:w-auto sm:self-start">
								Submit Request
							</PrimaryButton>
						</form>
					)}
				</PageContainer>
			</section>
		</>
	);
}
