import { PageContainer } from "../marketing/components/PageContainer";
import { Seo } from "../marketing/components/Seo";
import { PrimaryButton, SecondaryLightButton, TextButton } from "../marketing/components/buttons";

/** Route fallback (section 31) — restrained sports language, no repeated puns. */
export function NotFoundPage() {
	return (
		<div className="flex min-h-screen items-center justify-center bg-ice-50">
			<Seo title="Page Not Found" description="The page you're looking for is not available." noIndex />
			<PageContainer className="flex flex-col items-center gap-5 py-20 text-center">
				<h1 className="font-heading text-3xl font-extrabold text-navy-900 sm:text-4xl">That page is out of bounds.</h1>
				<p className="max-w-md text-slate-700">The page may have moved, been unpublished, or no longer be available.</p>
				<div className="mt-2 flex flex-wrap items-center justify-center gap-4">
					<PrimaryButton to="/">Return Home</PrimaryButton>
					<SecondaryLightButton to="/solutions">Explore Solutions</SecondaryLightButton>
				</div>
				<TextButton to="/contact" className="text-slate-600">
					Contact Support
				</TextButton>
			</PageContainer>
		</div>
	);
}
