import { FaqAccordion } from "../../marketing/components/FaqAccordion";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";

const HELP_CATEGORIES: { category: string; items: { question: string; answer: string }[] }[] = [
	{
		category: "Getting Started",
		items: [
			{
				question: "How do I get started with Rally26?",
				answer: "Create an account, and we'll guide you through organization setup — or talk to our team for a hands-on, guided setup.",
			},
		],
	},
	{
		category: "Accounts",
		items: [
			{
				question: "Who can create a Rally26 account?",
				answer: "Adult administrators, team managers, and parents or guardians. Rally26 does not create child login accounts.",
			},
		],
	},
	{
		category: "Organizations",
		items: [
			{
				question: "Can I add multiple teams under one organization?",
				answer: "Yes. Organizations can create and manage multiple teams and tournaments.",
			},
		],
	},
	{
		category: "Public Pages",
		items: [
			{
				question: "Can I preview a page before publishing it?",
				answer: "Yes. Team, tournament, and organization pages support a draft, preview, and publish workflow.",
			},
		],
	},
	{
		category: "Fundraising",
		items: [
			{
				question: "How do supporters contribute to a campaign?",
				answer: "Share the campaign link or QR code from a team or tournament page — supporters can contribute without creating an account.",
			},
		],
	},
	{
		category: "Apparel",
		items: [
			{
				question: "Do we need to hold inventory to sell apparel?",
				answer: "No — apparel stores are built around per-order fulfillment rather than bulk inventory purchases.",
			},
		],
	},
	{
		category: "Dues & Fees",
		items: [
			{
				question: "Can families see what they owe?",
				answer: "Yes. Households can see assigned fees, due dates, and outstanding balances.",
			},
		],
	},
	{
		category: "Credits",
		items: [
			{
				question: "Are family credits cash?",
				answer: "No. Family credits are organization-approved fee credits only — never withdrawable or transferable.",
			},
		],
	},
	{
		category: "Billing",
		items: [
			{
				question: "How is Rally26 priced?",
				answer: "Organization subscriptions plus clearly disclosed transaction fees. See the Pricing page for details.",
			},
		],
	},
	{
		category: "Privacy",
		items: [
			{
				question: "What data does Rally26 collect about participants?",
				answer: "Lightweight participant records managed by adults — no child accounts or logins. See the Privacy Policy for details.",
			},
		],
	},
];

export function HelpPage() {
	return (
		<>
			<Seo title="Help Center" description="Frequently asked questions about using Rally26." />

			<section className="bg-navy-950 py-16 sm:py-20">
				<PageContainer className="max-w-2xl">
					<SectionHeading tone="dark" align="left" heading="Help Center" />
				</PageContainer>
			</section>

			<section className="bg-white dark:bg-[#111827] py-16 sm:py-20">
				<PageContainer className="mx-auto flex max-w-3xl flex-col gap-10">
					{HELP_CATEGORIES.map((category) => (
						<div key={category.category}>
							<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{category.category}</h2>
							<div className="mt-4">
								<FaqAccordion items={category.items} />
							</div>
						</div>
					))}
				</PageContainer>
			</section>
		</>
	);
}
