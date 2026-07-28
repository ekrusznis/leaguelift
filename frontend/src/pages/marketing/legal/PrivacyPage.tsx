import { Link } from "react-router-dom";
import { LegalPageLayout } from "./LegalPageLayout";

export function PrivacyPage() {
	return (
		<LegalPageLayout title="Privacy Policy" description="How LeagueLift collects, uses, and protects information.">
			<p>
				This policy describes how LeagueLift handles information for youth sports organizations, the adults
				who administer and support them, and the households connected to them. It applies to the LeagueLift
				public site and the authenticated LeagueLift application.
			</p>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Who this policy covers</h2>
				<p className="mt-2">
					LeagueLift accounts are created and controlled by adults. This includes organization owners and
					administrators, coaches and team staff, and parents or guardians managing a household. LeagueLift
					does not create login accounts for children.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Information we collect</h2>
				<p className="mt-2">We collect the following categories of information:</p>
				<ul className="mt-3 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Adult account information</strong> — name, email address, and, if you sign in through a
						managed identity provider, the identifier that provider assigns to your account.
					</li>
					<li>
						<strong>Organization information</strong> — organization name, public URL, sport(s), contact
						details, and staff roles.
					</li>
					<li>
						<strong>Household information</strong> — a household display name and contact details, entered
						by an adult.
					</li>
					<li>
						<strong>Participant (athlete) information</strong> — first name, last name, and date of birth,
						entered and controlled entirely by an adult on the household account. See{" "}
						<em>Children&rsquo;s information</em> below.
					</li>
					<li>
						<strong>Fee and financial records</strong> — fee descriptions, amounts, and due dates assigned
						to a household. LeagueLift does not store payment card numbers; once live payment processing
						is enabled, card data will be handled directly by a third-party payment processor.
					</li>
					<li>
						<strong>Usage information</strong> — privacy-safe product analytics (e.g. which page or button
						was used) that never include names, emails, participant information, or payment details. See{" "}
						<Link to="/security" className="text-green-600 hover:underline">
							Security
						</Link>{" "}
						for how this is handled.
					</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Children&rsquo;s information</h2>
				<p className="mt-2">
					LeagueLift does not knowingly collect information directly from children, and children do not have
					their own LeagueLift login. Any participant (athlete) record is entered and controlled by the
					adult managing the household, and is visible only to that household and to organization staff who
					need it to run a team, tournament, or fee assignment. We do not collect medical, educational,
					behavioral, background-check, or precise-location information about participants.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">How we use information</h2>
				<p className="mt-2">We use the information above to:</p>
				<ul className="mt-3 flex list-disc flex-col gap-2 pl-5">
					<li>Operate organization, team, tournament, and household accounts;</li>
					<li>Publish and display public organization, team, tournament, and campaign pages you choose to publish;</li>
					<li>Assign, track, and report on fees, and — once available — process fundraising contributions and family credits;</li>
					<li>Provide account support and respond to inquiries; and</li>
					<li>Maintain the security, integrity, and audit history of the platform.</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">How we share information</h2>
				<p className="mt-2">
					We do not sell personal information. We share information only:
				</p>
				<ul className="mt-3 flex list-disc flex-col gap-2 pl-5">
					<li>Within an organization, scoped to the staff roles that need it to do their job;</li>
					<li>With service providers who host, secure, or operate the platform on our behalf (e.g. cloud hosting, and — once configured — an identity provider and a payment processor), under obligations to protect it;</li>
					<li>When required by law, or to protect the rights, safety, or property of LeagueLift, our users, or others.</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Data retention</h2>
				<p className="mt-2">
					We retain account, organization, household, and financial records for as long as the account is
					active, plus any additional period needed for legal, accounting, or audit purposes. Audit records
					of significant account and financial actions are retained and are not user-deletable, consistent
					with maintaining a trustworthy financial history.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Your choices and rights</h2>
				<p className="mt-2">
					You may ask us to access, correct, export, or delete information associated with your account or
					household by contacting us. We will honor these requests to the extent we are able, subject to
					legal, accounting, and audit-retention requirements described above. Depending on where you live,
					you may also have specific statutory privacy rights; this section will be updated once those
					have been confirmed by legal review.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Security</h2>
				<p className="mt-2">
					See our{" "}
					<Link to="/security" className="text-green-600 hover:underline">
						Security page
					</Link>{" "}
					for how we approach authentication, authorization, and data protection.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Changes to this policy</h2>
				<p className="mt-2">
					We may update this policy as the product changes. We will update the date at the top of this page
					when we do.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Questions</h2>
				<p className="mt-2">
					Contact us through the{" "}
					<Link to="/contact" className="text-green-600 hover:underline">
						Contact page
					</Link>{" "}
					with any privacy questions.
				</p>
			</section>
		</LegalPageLayout>
	);
}
