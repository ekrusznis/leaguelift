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
			{page.cover ? (
				<div className="h-52 w-full overflow-hidden bg-navy sm:h-72">
					<img src={page.cover.url} alt={page.cover.altText ?? `${page.title} cover`} className="size-full object-cover" />
				</div>
			) : (
				<div className="h-28 bg-gradient-to-r from-navy to-navy-800 sm:h-40" aria-hidden="true" />
			)}
			<div className="mx-auto -mt-10 flex max-w-2xl flex-col gap-6 px-4 pb-12 sm:-mt-12">
				<div className="rounded-xl bg-pure-white p-6 shadow-sm">
					<div className="flex flex-wrap items-center gap-4">
						{page.logo ? (
							<img src={page.logo.url} alt={page.logo.altText ?? `${page.title} logo`} className="size-20 rounded-xl bg-navy-900 object-contain p-2" />
						) : (
							<span className="flex size-20 items-center justify-center rounded-xl bg-navy-900 font-heading text-xl font-bold text-white" aria-hidden="true">
								{page.title.slice(0, 2).toUpperCase()}
							</span>
						)}
						<div className="min-w-0 flex-1">
							<p className="text-sm font-medium uppercase tracking-wide text-slate-gray">
								{TYPE_LABELS[page.pageType]} Page
							</p>
							<h1 className="mt-1 break-words font-heading text-3xl font-bold text-navy">{page.title}</h1>
						</div>
					</div>
					{page.summary && <p className="mt-6 text-lg leading-relaxed text-slate-gray">{page.summary}</p>}
					<hr className="my-6 border-slate-gray/20" />
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
		</div>
	);
}
