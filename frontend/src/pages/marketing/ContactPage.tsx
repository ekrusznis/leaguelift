import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { CheckboxField } from "../../components/forms/CheckboxField";
import { FormField } from "../../components/forms/FormField";
import { SelectField } from "../../components/forms/SelectField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { captureAttribution } from "../../marketing/analytics";

const INQUIRY_TYPE_OPTIONS = [
	{ value: "SALES", label: "Sales" },
	{ value: "PILOT", label: "Pilot" },
	{ value: "PARTNERSHIP", label: "Partnership" },
	{ value: "SUPPORT", label: "Support" },
	{ value: "GENERAL", label: "General" },
] as const;

const contactSchema = z.object({
	name: z.string().trim().min(1, "Name is required."),
	email: z.string().trim().min(1, "Email is required.").email("Enter a valid email address."),
	organization: z.string().trim().max(200).optional().or(z.literal("")),
	inquiryType: z.string().min(1, "Select an inquiry type."),
	message: z.string().trim().min(1, "Message is required.").max(2000),
	consentContacted: z.literal(true, { error: "You must consent to be contacted." }),
	company: z.string().max(0).optional().or(z.literal("")),
});
type ContactFormValues = z.infer<typeof contactSchema>;

/** No `/contact` endpoint exists in docs/openapi.yaml yet — validates and shows the section 19.4 success state without persisting. */
export function ContactPage() {
	const [submitted, setSubmitted] = useState(false);
	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<ContactFormValues>({
		resolver: zodResolver(contactSchema),
		defaultValues: {
			name: "",
			email: "",
			organization: "",
			inquiryType: "",
			message: "",
			consentContacted: undefined,
			company: "",
		} as unknown as ContactFormValues,
	});

	const onSubmit = handleSubmit((values) => {
		if (values.company) return;
		captureAttribution();
		setSubmitted(true);
	});

	return (
		<>
			<Seo title="Contact" description="Get in touch with LeagueLift for sales, pilot, partnership, or support questions." />

			<section className="bg-navy-950 py-16 sm:py-20">
				<PageContainer className="max-w-2xl">
					<SectionHeading tone="dark" align="left" heading="Get in touch" />
				</PageContainer>
			</section>

			<section className="bg-white py-16 sm:py-20">
				<PageContainer className="mx-auto max-w-xl">
					{submitted ? (
						<InlineAlert tone="success" title="Thanks. Your message has been received." />
					) : (
						<form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
							<input type="text" tabIndex={-1} autoComplete="off" aria-hidden="true" className="hidden" {...register("company")} />
							<FormField label="Name" required error={errors.name?.message} {...register("name")} />
							<FormField label="Email" type="email" required error={errors.email?.message} {...register("email")} />
							<FormField label="Organization" hint="Optional" {...register("organization")} />
							<SelectField
								label="Inquiry type"
								required
								placeholder="Select an inquiry type"
								options={[...INQUIRY_TYPE_OPTIONS]}
								error={errors.inquiryType?.message}
								{...register("inquiryType")}
							/>
							<div className="flex flex-col gap-1.5">
								<label htmlFor="contact-message" className="text-sm font-semibold text-navy-900">
									Message <span aria-hidden="true" className="text-error-600">*</span>
								</label>
								<textarea
									id="contact-message"
									rows={5}
									required
									aria-invalid={!!errors.message}
									aria-describedby={errors.message ? "contact-message-error" : undefined}
									className={`rounded-[10px] border px-3.5 py-2.5 text-navy-900 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 ${
										errors.message ? "border-error-600" : "border-slate-200"
									}`}
									{...register("message")}
								/>
								{errors.message && (
									<p id="contact-message-error" role="alert" className="text-sm text-error-600">
										{errors.message.message}
									</p>
								)}
							</div>
							<CheckboxField
								label="I consent to be contacted about this message."
								error={errors.consentContacted?.message}
								{...register("consentContacted")}
							/>
							<PrimaryButton type="submit" loading={isSubmitting} className="w-full justify-center sm:w-auto sm:self-start">
								Send Message
							</PrimaryButton>
						</form>
					)}
				</PageContainer>
			</section>
		</>
	);
}
