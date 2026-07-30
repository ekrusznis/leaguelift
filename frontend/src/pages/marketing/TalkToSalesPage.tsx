import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { CheckboxField } from "../../components/forms/CheckboxField";
import { FormField } from "../../components/forms/FormField";
import { SelectField } from "../../components/forms/SelectField";
import { InlineAlert } from "../../marketing/components/InlineAlert";
import { PageContainer } from "../../marketing/components/PageContainer";
import { GuidedOnboardingCard } from "../../marketing/components/GuidedOnboardingCard";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { captureAttribution, track } from "../../marketing/analytics";
import { heroImages } from "../../marketing/heroImages";
import {
	APPLICANT_ROLE_OPTIONS,
	ORGANIZATION_TYPE_OPTIONS,
	PRODUCT_INTEREST_OPTIONS,
	talkToSalesSchema,
	type TalkToSalesFormValues,
} from "./talkToSalesSchema";

function generateReferenceNumber(): string {
	return `LL-${Date.now().toString(36).toUpperCase()}`;
}

const DEFAULT_VALUES: TalkToSalesFormValues = {
	firstName: "",
	lastName: "",
	workEmail: "",
	phone: "",
	applicantRole: "",
	organizationName: "",
	organizationWebsite: "",
	city: "",
	state: "",
	organizationType: "",
	sports: "",
	numberOfTeams: "",
	approxAthletes: "",
	currentSoftware: "",
	currentMerchProvider: "",
	estMerchRevenue: "",
	estFundraisingRevenue: "",
	estSponsorshipRevenue: "",
	currentDuesMethod: "",
	productInterest: [],
	biggestChallenge: "",
	desiredLaunchMonth: "",
	additionalComments: "",
	howHeard: "",
	consentContacted: undefined,
	confirmAdult: undefined,
	agreePrivacy: undefined,
	company: "",
} as unknown as TalkToSalesFormValues;

/**
 * There is no `/sales-requests` (or similar) endpoint in docs/openapi.yaml yet —
 * this validates and shows the section 17.7-style success state, but does not
 * persist anywhere. Wire `onSubmit` to a real endpoint once one is designed and
 * documented; do not invent a contract here in the meantime (DESIGN-DOC.md
 * section 27.4).
 */
export function TalkToSalesPage() {
	const [submitted, setSubmitted] = useState<{ organizationName: string; reference: string } | null>(null);
	const [submitError, setSubmitError] = useState<string | null>(null);
	const viewedTracked = useRef(false);

	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<TalkToSalesFormValues>({ resolver: zodResolver(talkToSalesSchema), defaultValues: DEFAULT_VALUES });

	useEffect(() => {
		if (!viewedTracked.current) {
			track("sales_form_viewed");
			viewedTracked.current = true;
		}
	}, []);

	const onSubmit = handleSubmit(
		async (values) => {
			if (values.company) return; // honeypot tripped — silently drop

			track("sales_form_submitted");
			captureAttribution();

			try {
				await new Promise((resolve) => setTimeout(resolve, 400));
				setSubmitError(null);
				setSubmitted({ organizationName: values.organizationName, reference: generateReferenceNumber() });
			} catch {
				track("sales_form_submission_failed");
				setSubmitError("We could not complete that request. Your information has not been lost. Please try again.");
			}
		},
		() => track("sales_form_validation_failed"),
	);

	if (submitted) {
		return (
			<section className="bg-ice-50 py-24">
				<PageContainer className="mx-auto flex max-w-xl flex-col items-center gap-4 text-center">
					<Seo title="Request Received" description="Your LeagueLift request has been received." noIndex />
					<span className="flex size-14 items-center justify-center rounded-full bg-green-500/15 text-green-600">
						<svg className="size-7" viewBox="0 0 24 24" fill="none" aria-hidden="true">
							<path d="m5 12.5 4.5 4.5L19 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
						</svg>
					</span>
					<h1 className="font-heading text-3xl font-extrabold text-navy-900">Thank you, {submitted.organizationName}</h1>
					<p className="text-slate-700">Your request has been received. A member of our team will follow up.</p>
					<p className="text-sm text-slate-500">Reference: {submitted.reference}</p>
					<PrimaryButton to="/" className="mt-4">
						Return Home
					</PrimaryButton>
				</PageContainer>
			</section>
		);
	}

	return (
		<>
			<Seo
				title="Talk to Our Team"
				description="Tell us about your organization and get a guided setup for team pages, dues, fundraising, and more."
			/>

			<section className="bg-navy-950 py-20 sm:py-24">
				<PageContainer className="grid items-center gap-10 lg:grid-cols-[1.1fr_0.9fr]">
					<SectionHeading
						tone="dark"
						align="left"
						heading="Let's get your organization set up."
						copy="Tell us about your organization and we'll help you get started with team pages, dues, and more — with guided onboarding and direct support."
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

			<section className="bg-white py-16">
				<PageContainer>
					<GuidedOnboardingCard />
				</PageContainer>
			</section>

			<section className="bg-ice-50 py-16 sm:py-20">
				<PageContainer className="mx-auto max-w-3xl">
					<h2 className="font-heading text-2xl font-bold text-navy-900">Tell us about your organization</h2>
					<p className="mt-2 text-slate-700">There is no obligation, and requests are reviewed on a rolling basis.</p>

					<form onSubmit={onSubmit} noValidate className="mt-8 flex flex-col gap-10">
						<input
							type="text"
							tabIndex={-1}
							autoComplete="off"
							aria-hidden="true"
							className="hidden"
							{...register("company")}
						/>

						<fieldset className="flex flex-col gap-5">
							<legend className="font-heading text-lg font-bold text-navy-900">Contact information</legend>
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="First name" required error={errors.firstName?.message} {...register("firstName")} />
								<FormField label="Last name" required error={errors.lastName?.message} {...register("lastName")} />
							</div>
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="Work email" type="email" required error={errors.workEmail?.message} {...register("workEmail")} />
								<FormField label="Phone number" type="tel" required error={errors.phone?.message} {...register("phone")} />
							</div>
							<SelectField
								label="Your role"
								required
								placeholder="Select your role"
								options={[...APPLICANT_ROLE_OPTIONS]}
								error={errors.applicantRole?.message}
								{...register("applicantRole")}
							/>
						</fieldset>

						<fieldset className="flex flex-col gap-5">
							<legend className="font-heading text-lg font-bold text-navy-900">Organization</legend>
							<FormField label="Organization name" required error={errors.organizationName?.message} {...register("organizationName")} />
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="Organization website" hint="Optional" error={errors.organizationWebsite?.message} {...register("organizationWebsite")} />
								<SelectField
									label="Organization type"
									required
									placeholder="Select organization type"
									options={[...ORGANIZATION_TYPE_OPTIONS]}
									error={errors.organizationType?.message}
									{...register("organizationType")}
								/>
							</div>
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="City" required error={errors.city?.message} {...register("city")} />
								<FormField label="State" required error={errors.state?.message} {...register("state")} />
							</div>
							<FormField
								label="Sport or sports"
								required
								hint="Separate multiple sports with commas."
								error={errors.sports?.message}
								{...register("sports")}
							/>
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="Number of teams" hint="Optional, approximate" error={errors.numberOfTeams?.message} {...register("numberOfTeams")} />
								<FormField label="Approximate number of athletes" hint="Optional" error={errors.approxAthletes?.message} {...register("approxAthletes")} />
							</div>
						</fieldset>

						<fieldset className="flex flex-col gap-5">
							<legend className="font-heading text-lg font-bold text-navy-900">Current workflow</legend>
							<div className="grid gap-5 sm:grid-cols-2">
								<FormField label="Current sports-management software" hint="Optional" {...register("currentSoftware")} />
								<FormField label="Current merchandise provider or method" hint="Optional" {...register("currentMerchProvider")} />
							</div>
							<div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
								<FormField label="Est. annual merchandise revenue" hint="Optional, whole dollars" error={errors.estMerchRevenue?.message} {...register("estMerchRevenue")} />
								<FormField label="Est. annual fundraising revenue" hint="Optional, whole dollars" error={errors.estFundraisingRevenue?.message} {...register("estFundraisingRevenue")} />
								<FormField label="Est. annual sponsorship revenue" hint="Optional, whole dollars" error={errors.estSponsorshipRevenue?.message} {...register("estSponsorshipRevenue")} />
							</div>
							<FormField label="Current method for collecting dues and fees" hint="Optional" {...register("currentDuesMethod")} />
						</fieldset>

						<fieldset className="flex flex-col gap-4">
							<legend className="font-heading text-lg font-bold text-navy-900">Product interest</legend>
							{errors.productInterest && (
								<p role="alert" className="text-sm text-error-600">
									{errors.productInterest.message}
								</p>
							)}
							<div className="grid gap-3 sm:grid-cols-2">
								{PRODUCT_INTEREST_OPTIONS.map((option) => (
									<label key={option.value} className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-3.5">
										<input
											type="checkbox"
											value={option.value}
											{...register("productInterest")}
											className="size-5 rounded border-slate-300 text-green-500 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400"
										/>
										<span className="text-sm font-medium text-navy-900">{option.label}</span>
									</label>
								))}
							</div>
						</fieldset>

						<fieldset className="flex flex-col gap-5">
							<legend className="font-heading text-lg font-bold text-navy-900">Needs</legend>
							<FormField
								label="Biggest current revenue or administration challenge"
								hint="Optional"
								error={errors.biggestChallenge?.message}
								{...register("biggestChallenge")}
							/>
							<FormField label="Desired launch month" type="month" hint="Optional" {...register("desiredLaunchMonth")} />
							<FormField
								label="Additional comments"
								hint="Optional"
								error={errors.additionalComments?.message}
								{...register("additionalComments")}
							/>
						</fieldset>

						<fieldset className="flex flex-col gap-5">
							<legend className="font-heading text-lg font-bold text-navy-900">How did you hear about LeagueLift?</legend>
							<FormField label="How did you hear about us?" hint="Optional" {...register("howHeard")} />
						</fieldset>

						<fieldset className="flex flex-col gap-4">
							<legend className="font-heading text-lg font-bold text-navy-900">Consent</legend>
							<CheckboxField label="I consent to be contacted about this request." error={errors.consentContacted?.message} {...register("consentContacted")} />
							<CheckboxField label="I confirm I am at least 18 years old." error={errors.confirmAdult?.message} {...register("confirmAdult")} />
							<CheckboxField label="I agree to the Privacy Policy." error={errors.agreePrivacy?.message} {...register("agreePrivacy")} />
						</fieldset>

						{submitError && <InlineAlert tone="error" title={submitError} />}

						<PrimaryButton type="submit" loading={isSubmitting} className="w-full justify-center sm:w-auto sm:self-start">
							Submit Request
						</PrimaryButton>
					</form>
				</PageContainer>
			</section>
		</>
	);
}
