import type { CSSProperties } from "react";
import { useParams } from "react-router-dom";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { usePublicPage } from "../features/publicpage/api";
import type { PageType } from "../features/publicpage/types";
import { SiteFooter } from "../marketing/components/SiteFooter";

const TYPE_LABELS: Record<PageType, string> = {
	ORGANIZATION: "Organization",
	TEAM: "Team",
	TOURNAMENT: "Tournament",
};

export function PublicPageView() {
	const { slug } = useParams<{ slug: string }>();
	const { data: page, isLoading, isError } = usePublicPage(slug ?? "");

	if (isLoading) return <div className="flex min-h-screen flex-col"><main className="flex flex-1 items-center justify-center"><LoadingState label="Loading page…" /></main><SiteFooter /></div>;
	if (isError || !page) {
		return (
			<div className="flex min-h-screen flex-col bg-ice-white dark:bg-[#0f172a]">
				<main className="flex flex-1 items-center justify-center"><ErrorState message="This page could not be found or is not published." /></main>
				<SiteFooter />
			</div>
		);
	}

	const teamColorStyle = {
		"--team-color-1": page.primaryColor,
		"--team-color-2": page.secondaryColor,
	} as CSSProperties;

	return (
		<div className="flex min-h-screen flex-col bg-ice-white dark:bg-[#0f172a]" style={teamColorStyle}>
			<main className="flex-1">
			{page.cover ? (
				<div className="h-52 w-full overflow-hidden sm:h-72" style={{ backgroundColor: "var(--team-color-1)" }}>
					<img src={page.cover.url} alt={page.cover.altText ?? `${page.title} cover`} className="size-full object-cover" />
				</div>
			) : (
				<div
					className="h-28 sm:h-40"
					style={{ background: "linear-gradient(to right, var(--team-color-1), var(--team-color-2))" }}
					aria-hidden="true"
				/>
			)}
			<div className="mx-auto -mt-10 flex max-w-2xl flex-col gap-6 px-4 pb-12 sm:-mt-12">
				<div className="rounded-xl bg-pure-white dark:bg-[#111827] p-6 shadow-sm">
					<div className="flex flex-wrap items-center gap-4">
						{page.logo ? (
							<img src={page.logo.url} alt={page.logo.altText ?? `${page.title} logo`} className="size-20 rounded-xl object-contain p-2" style={{ backgroundColor: "var(--team-color-1)" }} />
						) : (
							<span
								className="flex size-20 items-center justify-center rounded-xl font-heading text-xl font-bold text-white"
								style={{ backgroundColor: "var(--team-color-1)" }}
								aria-hidden="true"
							>
								{page.title.slice(0, 2).toUpperCase()}
							</span>
						)}
						<div className="min-w-0 flex-1">
							<p className="text-sm font-medium uppercase tracking-wide text-slate-gray dark:text-[#cbd5e1]">
								{TYPE_LABELS[page.pageType]} Page
							</p>
							<h1 className="mt-1 break-words font-heading text-3xl font-bold text-navy dark:text-[#f8fafc]">{page.title}</h1>
						</div>
					</div>
					{page.summary && <p className="mt-6 text-lg leading-relaxed text-slate-gray dark:text-[#cbd5e1]">{page.summary}</p>}
					<hr className="my-6 border-slate-gray/20" />
					<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">
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
			</main>
			<SiteFooter />
		</div>
	);
}
