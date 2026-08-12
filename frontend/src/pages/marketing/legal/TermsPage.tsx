import { Link } from "react-router-dom";
import { LegalPageLayout } from "./LegalPageLayout";

export function TermsPage() {
	return (
		<LegalPageLayout title="Terms of Service" description="The terms that govern use of Rally26.">
			<p>
				These terms govern access to and use of the Rally26 public site and application. By creating an
				account or using Rally26 on behalf of an organization or household, you agree to these terms.
			</p>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Eligibility and accounts</h2>
				<p className="mt-2">
					Rally26 accounts are created by adults (18 years or older) acting on behalf of an organization
					or household. Rally26 does not create accounts for children. You are responsible for the
					accuracy of information you provide and for maintaining the confidentiality of your account
					credentials.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Organization responsibilities</h2>
				<p className="mt-2">
					An organization is responsible for the accuracy of the public pages, fee assignments, and
					fundraising campaigns it publishes, and for managing who on its staff has access to which parts
					of its Rally26 account. Organization owners and administrators can assign and revoke staff
					roles at any time.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Acceptable use</h2>
				<p className="mt-2">Accounts may not be used to:</p>
				<ul className="mt-3 flex list-disc flex-col gap-2 pl-5">
					<li>Collect or store information about a child beyond what an adult explicitly enters on that child&rsquo;s behalf;</li>
					<li>Misrepresent fees, fundraising contributions, or family credits to families or supporters;</li>
					<li>Publish false, misleading, or fraudulent content on a public page or campaign;</li>
					<li>Attempt to access another organization&rsquo;s or household&rsquo;s data without authorization; or</li>
					<li>Interfere with or disrupt the operation of the platform.</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Fees, pricing, and payment</h2>
				<p className="mt-2">
					Applicable Rally26 subscription fees, transaction fees, and any other service fees are
					disclosed to an organization before they take effect. Payments are handled by a third-party
					payment processor under its own terms; Rally26 does not store payment card numbers. For payments
					processed through Rally26, Rally26 &mdash; not the receiving organization &mdash; is the merchant
					of record, and may hold received funds for a brief period (currently up to seven days) before
					transferring an organization&rsquo;s share, to account for processing, verification, and any
					refund window described below.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Refunds</h2>
				<p className="mt-2">
					Where a refund is available, it is initiated by an organization administrator within a limited
					window (currently 14 days) from when the original payment was confirmed. Rally26&rsquo;s platform
					fee is not refunded. If a payment is instead disputed directly with the cardholder&rsquo;s bank
					(a chargeback), Rally26 handles that process with the payment processor as merchant of record.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Electronic signatures and waivers</h2>
				<p className="mt-2">
					Where Rally26 offers an electronic signature or acknowledgment flow &mdash; for example, a
					guardian completing a program waiver or eligibility document electronically &mdash; using that
					flow means you consent to conduct that specific transaction electronically. You may request a
					paper copy of an electronically signed document, or withdraw your consent to sign electronically
					for future transactions, by contacting us; withdrawing consent may affect your ability to
					complete certain actions online. Completing an electronic signature requires a device capable of
					accessing the Rally26 application and a valid email address.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Family credits</h2>
				<p className="mt-2">
					Where offered, family credits are organization-approved credits that may be applied only against
					eligible fees within the issuing organization. Family credits are not cash, are not withdrawable,
					are not presented as a stored-value or bank balance, and are not transferable between unrelated
					families.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Content and public pages</h2>
				<p className="mt-2">
					An organization retains ownership of the content (text, logos, and images) it uploads, and grants
					Rally26 the right to host, display, and distribute that content as needed to operate the
					service — including on the public pages the organization chooses to publish.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Third-party services</h2>
				<p className="mt-2">
					Rally26 relies on third-party providers for functions such as identity/authentication, hosting,
					and — once live — payment processing. Those providers&rsquo; own terms and privacy practices apply
					to the parts of the service they provide.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Disclaimers and limitation of liability</h2>
				<p className="mt-2">
					Rally26 is provided on an "as is" and "as available" basis. To the fullest extent permitted by
					law, Rally26 disclaims warranties of any kind, and Rally26&rsquo;s liability for any claim
					arising from use of the service is limited as set out in a final, legally reviewed version of
					these terms.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Termination</h2>
				<p className="mt-2">
					Either party may stop using or offering the service as described in an organization&rsquo;s
					subscription agreement. We may suspend or terminate access to an account that violates these
					terms.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Changes to these terms</h2>
				<p className="mt-2">
					We may update these terms as the product changes. We will update the date at the top of this
					page when we do, and will provide organizations with reasonable notice of material changes.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Governing law</h2>
				<p className="mt-2">To be finalized during legal review.</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">Questions</h2>
				<p className="mt-2">
					Contact us through the{" "}
					<Link to="/contact" className="text-green-600 underline">
						Contact page
					</Link>{" "}
					with any questions about these terms.
				</p>
			</section>
		</LegalPageLayout>
	);
}
