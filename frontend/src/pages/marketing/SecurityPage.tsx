import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { SecondaryLightButton } from "../../marketing/components/buttons";

const SECTIONS = [
	{
		heading: "Adult-controlled, scoped access for everyone",
		copy: "Adults create and manage households, registration, and payments — that never changes. Coaches, parents, and athletes each get their own sign-in with access limited to what their role needs; an athlete account can see schedules, messages, and team updates, but never billing, household management, or another family's contact information.",
	},
	{
		heading: "Role-based authorization",
		copy: "Every organization-scoped action checks the caller's membership and role before touching organization data — enforced by the backend, never by the frontend alone.",
	},
	{
		heading: "Authentication",
		copy: "Passwords are never stored in plain text — only a salted hash. Rally26 also supports signing in with a Google or Apple account so you're not required to create a new password at all.",
	},
	{
		heading: "Encryption in transit",
		copy: "All production traffic is served over HTTPS.",
	},
	{
		heading: "Protected secrets",
		copy: "Credentials and provider keys are kept in environment configuration or a secret manager, never committed to source control or bundled into the public frontend.",
	},
	{
		heading: "Payment handling",
		copy: "Card payments run through Stripe, a PCI-compliant payment processor. Rally26 never sees or stores raw card numbers — every checkout is a direct, hosted Stripe flow.",
	},
	{
		heading: "Audit events",
		copy: "Membership changes, public-page publishing, fee assignments, and campaign actions all produce audit records.",
	},
	{
		heading: "Backups",
		copy: "Database backups run on a regular schedule, and the restore process is rehearsed against real backup files — not just assumed to work.",
	},
	{
		heading: "Responsible disclosure",
		copy: "If you believe you've found a security issue, contact us — we'll respond and work with you in good faith.",
	},
];

export function SecurityPage() {
	return (
		<>
			<Seo title="Security" description="How Rally26 approaches authentication, authorization, and data protection." />

			<section className="bg-navy-950 py-16 sm:py-20">
				<PageContainer className="max-w-2xl">
					<SectionHeading tone="dark" align="left" heading="Security" />
				</PageContainer>
			</section>

			<section className="bg-white dark:bg-[#111827] py-16 sm:py-20">
				<PageContainer className="mx-auto flex max-w-3xl flex-col gap-8">
					{SECTIONS.map((section) => (
						<div key={section.heading}>
							<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{section.heading}</h2>
							<p className="mt-2 leading-relaxed text-slate-700 dark:text-[#cbd5e1]">{section.copy}</p>
						</div>
					))}

					<SecondaryLightButton to="/contact" className="self-start">
						Contact Us About Security
					</SecondaryLightButton>
				</PageContainer>
			</section>
		</>
	);
}
