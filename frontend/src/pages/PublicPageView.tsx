import { useParams } from "react-router-dom";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { usePublicPage } from "../features/publicpage/api";
import type { PageType } from "../features/publicpage/types";

const TYPE_LABELS: Record<PageType, string> = {
	ORGANIZATION: "Organization",
	TEAM: "Team",
	TOURNAMENT: "Tournament",
};

export function PublicPageView() {
	const { slug } = useParams<{ slug: string }>();
	const { data: page, isLoading, isError } = usePublicPage(slug ?? "");

	if (isLoading) return <LoadingState label="Loading page…" />;
	if (isError || !page) {
		return (
			<div className="flex min-h-screen items-center justify-center bg-ice-white">
				<ErrorState message="This page could not be found or is not published." />
			</div>
		);
	}

	return (
		<div className="min-h-screen bg-ice-white">
			<div className="mx-auto max-w-2xl px-4 py-12 flex flex-col gap-6">
				<div>
					<p className="text-sm font-medium text-slate-gray uppercase tracking-wide">
						{TYPE_LABELS[page.pageType]} Page
					</p>
					<h1 className="font-heading text-3xl font-bold text-navy mt-1">{page.title}</h1>
				</div>
				{page.summary && (
					<p className="text-slate-gray text-lg leading-relaxed">{page.summary}</p>
				)}
				<hr className="border-slate-gray/20" />
				<p className="text-xs text-slate-gray">
					Published at{" "}
					{page.publishedAt
						? new Date(page.publishedAt).toLocaleDateString("en-US", {
								year: "numeric",
								month: "long",
								day: "numeric",
							})
						: "—"}
				</p>
			</div>
		</div>
	);
}
