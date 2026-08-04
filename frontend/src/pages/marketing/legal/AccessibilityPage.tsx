import { Link } from "react-router-dom";
import { LegalPageLayout } from "./LegalPageLayout";

export function AccessibilityPage() {
	return (
		<LegalPageLayout title="Accessibility" description="Rally26's accessibility commitment and target standard.">
			<p>
				Rally26 is built for adults and organizations of all abilities, and for the families and
				supporters who use our public pages. We target the Web Content Accessibility Guidelines (WCAG) 2.2
				Level AA across the public site and the authenticated application.
			</p>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">What we&rsquo;ve built toward this</h2>
				<ul className="mt-3 flex list-disc flex-col gap-2 pl-5">
					<li>Keyboard navigation and visible focus states throughout the site and application;</li>
					<li>Semantic landmarks, heading structure, and labeled form fields;</li>
					<li>Accessible menus, tabs, and accordions with correct ARIA roles and states;</li>
					<li>Respect for your device&rsquo;s reduced-motion setting; and</li>
					<li>No information conveyed by color alone.</li>
				</ul>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Where we are today</h2>
				<p className="mt-2">
					These are engineering targets we build against continuously, not the result of a completed,
					independent accessibility audit. If you encounter a barrier, we want to know about it and fix it
					— see <em>Feedback</em> below.
				</p>
			</section>

			<section>
				<h2 className="font-heading text-lg font-bold text-navy-900">Feedback</h2>
				<p className="mt-2">
					If you encounter an accessibility barrier using Rally26, contact us through the{" "}
					<Link to="/contact" className="text-green-600 hover:underline">
						Contact page
					</Link>{" "}
					so we can address it.
				</p>
			</section>
		</LegalPageLayout>
	);
}
