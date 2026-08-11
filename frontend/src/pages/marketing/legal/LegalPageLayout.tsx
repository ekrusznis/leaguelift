import type { ReactNode } from "react";
import { PageContainer } from "../../../marketing/components/PageContainer";
import { Seo } from "../../../marketing/components/Seo";
import { InlineAlert } from "../../../marketing/components/InlineAlert";

const UPDATED = "Draft — not yet published";

/** Shared plain-text layout for /privacy, /terms, /accessibility (section 22). */
export function LegalPageLayout({ title, description, children }: { title: string; description: string; children: ReactNode }) {
	return (
		<>
			<Seo title={title} description={description} />
			<section className="bg-white dark:bg-[#111827] py-16 sm:py-20">
				<PageContainer className="mx-auto max-w-[760px]">
					<h1 className="font-heading text-3xl font-extrabold text-navy-900 dark:text-[#f8fafc]">{title}</h1>
					<p className="mt-2 text-sm text-slate-500 dark:text-[#cbd5e1]">Last updated: {UPDATED}</p>

					<div className="mt-6">
						<InlineAlert tone="warning" title="This is a working draft, not a published policy.">
							It has not been reviewed or approved by legal counsel. Do not rely on this page as
							Rally26&rsquo;s or your organization&rsquo;s actual {title.toLowerCase()} until it has
							been reviewed and formally approved.
						</InlineAlert>
					</div>

					<div className="mt-8 flex flex-col gap-6 leading-relaxed text-slate-700 dark:text-[#cbd5e1]">{children}</div>
				</PageContainer>
			</section>
		</>
	);
}
