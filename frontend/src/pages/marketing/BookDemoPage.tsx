import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "../../components/forms/FormField";
import { SelectField } from "../../components/forms/SelectField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { ORGANIZATION_TYPE_OPTIONS } from "./talkToSalesSchema";

const CONTACT_METHOD_OPTIONS = [
	{ value: "EMAIL", label: "Email" },
	{ value: "PHONE", label: "Phone" },
] as const;

const bookDemoSchema = z.object({
	name: z.string().trim().min(1, "Name is required."),
	workEmail: z.string().trim().min(1, "Work email is required.").email("Enter a valid email address."),
	organization: z.string().trim().min(1, "Organization is required."),
	role: z.string().trim().min(1, "Role is required."),
	organizationType: z.string().min(1, "Select an organization type."),
	numberOfAthletes: z.string().trim().regex(/^\d*$/, "Enter a whole number.").optional().or(z.literal("")),
	primaryInterest: z.string().trim().min(1, "Tell us your primary interest."),
	preferredContactMethod: z.string().min(1, "Select a preferred contact method."),
	message: z.string().trim().max(1000).optional().or(z.literal("")),
});
type BookDemoFormValues = z.infer<typeof bookDemoSchema>;

/**
 * No scheduling provider is configured (featureFlags.demoBookingProvider is false),
 * so this always uses the internal form (section 30, Option A) rather than an
 * empty or broken calendar embed.
 */
export function BookDemoPage() {
	const [submitted, setSubmitted] = useState(false);
	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<BookDemoFormValues>({
		resolver: zodResolver(bookDemoSchema),
		defaultValues: {
			name: "",
			workEmail: "",
			organization: "",
			role: "",
			organizationType: "",
			numberOfAthletes: "",
			primaryInterest: "",
			preferredContactMethod: "",
			message: "",
		},
	});

	const onSubmit = handleSubmit(() => {
		setSubmitted(true);
	});

	return (
		<>
			<Seo title="Book a Demo" description="Request a LeagueLift demo for your organization." />

			<section className="bg-navy-950 py-16 sm:py-20">
				<PageContainer className="max-w-2xl">
					<SectionHeading tone="dark" align="left" heading="Book a demo" copy="Tell us about your organization and we'll follow up to schedule a time." />
				</PageContainer>
			</section>

			<section className="bg-white py-16 sm:py-20">
				<PageContainer className="mx-auto max-w-xl">
					{submitted ? (
						<InlineAlert tone="success" title="Thanks. Your demo request has been received." />
					) : (
						<form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
							<FormField label="Name" required error={errors.name?.message} {...register("name")} />
							<FormField label="Work email" type="email" required error={errors.workEmail?.message} {...register("workEmail")} />
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="Organization" required error={errors.organization?.message} {...register("organization")} />
								<FormField label="Role" required error={errors.role?.message} {...register("role")} />
							</div>
							<div className="grid gap-5 sm:grid-cols-2">
								<SelectField
									label="Organization type"
									required
									placeholder="Select organization type"
									options={[...ORGANIZATION_TYPE_OPTIONS]}
									error={errors.organizationType?.message}
									{...register("organizationType")}
								/>
								<FormField label="Number of athletes" hint="Optional" error={errors.numberOfAthletes?.message} {...register("numberOfAthletes")} />
							</div>
							<FormField label="Primary interest" required error={errors.primaryInterest?.message} {...register("primaryInterest")} />
							<SelectField
								label="Preferred contact method"
								required
								placeholder="Select a contact method"
								options={[...CONTACT_METHOD_OPTIONS]}
								error={errors.preferredContactMethod?.message}
								{...register("preferredContactMethod")}
							/>
							<FormField label="Message" hint="Optional" {...register("message")} />
							<PrimaryButton type="submit" loading={isSubmitting} className="w-full justify-center sm:w-auto sm:self-start">
								Request Demo
							</PrimaryButton>
						</form>
					)}
				</PageContainer>
			</section>
		</>
	);
}
