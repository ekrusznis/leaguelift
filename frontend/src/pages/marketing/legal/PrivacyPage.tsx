import { Link } from "react-router-dom";
import { LegalPageLayout } from "./LegalPageLayout";

export function PrivacyPage() {
	return (
		<LegalPageLayout title="Privacy Policy" description="How Rally26 collects, uses, and protects information.">
			<p>
				This policy describes how Rally26 handles information for youth sports organizations, the adults
				who administer and support them, and the households connected to them. It applies to the Rally26
				public site and the authenticated Rally26 application.
			</p>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Who this policy covers</h2>
				<p className="mt-2">
					Rally26 accounts are created and controlled by adults. This includes organization owners and
					administrators, coaches and team staff, and parents or guardians managing a household. Rally26
					does not create login accounts for children.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Information we collect</h2>
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
						to a household. Rally26 does not store payment card numbers; once live payment processing
						is enabled, card data will be handled directly by a third-party payment processor.
					</li>
					<li>
						<strong>Usage information</strong> — privacy-safe product analytics (e.g. which page or button
						was used) that never include names, emails, participant information, or payment details. See{" "}
						<Link to="/security" className="text-green-600 underline">
							Security
						</Link>{" "}
						for how this is handled.
					</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Children&rsquo;s information</h2>
				<p className="mt-2">
					Rally26 does not knowingly collect information directly from children, and children do not have
					their own Rally26 login. Any participant (athlete) record is entered and controlled by the
					adult managing the household, and is visible only to that household and to organization staff who
					need it to run a team, tournament, or fee assignment. We do not collect medical, educational,
					behavioral, background-check, or precise-location information about participants.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">How we use information</h2>
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
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">How we share information</h2>
				<p className="mt-2">
					We do not sell personal information. We share information only:
				</p>
				<ul className="mt-3 flex list-disc flex-col gap-2 pl-5">
					<li>Within an organization, scoped to the staff roles that need it to do their job;</li>
					<li>With service providers who host, secure, or operate the platform on our behalf (e.g. cloud hosting, payment processing, and email/SMS delivery), under obligations to protect it;</li>
					<li>With a league-management provider (e.g. SportsEngine or TeamSnap) only if an organization has connected that integration, to import the roster, schedule, or eligibility information the organization has authorized;</li>
					<li>When required by law, or to protect the rights, safety, or property of Rally26, our users, or others.</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Data retention</h2>
				<p className="mt-2">
					We retain an organization&rsquo;s operational records for as long as the organization is active
					on Rally26. When an organization&rsquo;s account closes, we intend to retain financial and ledger
					records for a standard accounting-recordkeeping window &mdash; commonly around seven years in the
					United States &mdash; since those records also serve as the organization&rsquo;s own tax and
					accounting evidence, and to delete or anonymize non-financial personal data (household and
					participant contact details) after a shorter period of organization inactivity. These specific
					windows are a proposed starting point, not final policy, pending legal review. Audit records of
					significant account and financial actions are retained and are not user-deletable, consistent
					with maintaining a trustworthy financial history.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Your choices and rights</h2>
				<p className="mt-2">
					You may contact us to ask about accessing, correcting, or exporting information associated with
					your account or household, and we will respond to the extent we are able. Automated self-service
					deletion is not yet available in the product; a deletion request is instead handled manually and
					may be limited by the legal, accounting, and audit-retention requirements described above, or by
					current technical limitations. Depending on where you live, you may also have specific statutory
					privacy rights; this section will be updated once those have been confirmed by legal review.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Text message (SMS) communications</h2>
				<p className="mt-2">
					If a household opts in, Rally26 may send fee-payment reminders by text message through a
					third-party SMS provider. SMS is opt-in only &mdash; it is never turned on by default &mdash; and
					is a separate, household-level choice from required account, security, and billing
					communications, which are not sent by SMS. Message and data rates may apply. Reply STOP to a
					Rally26 text message at any time to opt out, or manage this choice from your account settings.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Security</h2>
				<p className="mt-2">
					See our{" "}
					<Link to="/security" className="text-green-600 underline">
						Security page
					</Link>{" "}
					for how we approach authentication, authorization, and data protection.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Changes to this policy</h2>
				<p className="mt-2">
					We may update this policy as the product changes. We will update the date at the top of this page
					when we do.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Questions</h2>
				<p className="mt-2">
					Contact us through the{" "}
					<Link to="/contact" className="text-green-600 underline">
						Contact page
					</Link>{" "}
					with any privacy questions.
				</p>
			</section>
		</LegalPageLayout>
	);
}
