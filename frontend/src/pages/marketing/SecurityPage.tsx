import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { SecondaryLightButton } from "../../marketing/components/buttons";

const SECTIONS = [
	{
		heading: "Adult-controlled accounts",
		copy: "Rally26 does not create child login accounts. Adults manage households and participant records on behalf of their families.",
	},
	{
		heading: "Role-based authorization",
		copy: "Every organization-scoped action checks the caller's membership and role before touching organization data — enforced by the backend, never by the frontend alone.",
	},
	{
		heading: "Managed authentication",
		copy: "Sign-in is designed to run through a managed identity provider rather than Rally26 storing passwords directly.",
	},
	{
		heading: "Encryption in transit",
		copy: "Rally26 is designed to serve all production traffic over HTTPS.",
	},
	{
		heading: "Protected secrets",
		copy: "Credentials and provider keys are kept in environment configuration or a secret manager, never committed to source control or bundled into the public frontend.",
	},
	{
		heading: "Provider-based payment handling",
		copy: "When live payments launch, card data will be handled by a PCI-compliant payment processor — Rally26 is not designed to store raw card numbers.",
	},
	{
		heading: "Audit events",
		copy: "Membership changes, public-page publishing, fee assignments, and campaign actions all produce audit records.",
	},
	{
		heading: "Backups",
		copy: "Database backups are part of the launch plan before any organization goes live with real data.",
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

			<section className="bg-white py-16 sm:py-20">
				<PageContainer className="mx-auto flex max-w-3xl flex-col gap-8">
					{SECTIONS.map((section) => (
						<div key={section.heading}>
							<h2 className="font-heading text-lg font-bold text-navy-900">{section.heading}</h2>
							<p className="mt-2 leading-relaxed text-slate-700">{section.copy}</p>
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
