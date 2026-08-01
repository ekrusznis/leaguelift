import { Link, useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { useHelpArticle } from "./api";
import { SupportMarkdown } from "./SupportMarkdown";

export function HelpArticlePage({ authenticated = false }: { authenticated?: boolean }) {
	const { slug } = useParams<{ slug: string }>();
	const article = useHelpArticle(authenticated, slug);
	const base = authenticated ? "/app/help" : "/help";
	if (article.isLoading) return <LoadingState label="Loading help article…" />;
	if (article.isError || !article.data) return <ErrorState message="This help article could not be found." />;
	return (
		<PageContainer className="max-w-3xl py-10 sm:py-14">
			<Seo title={article.data.title} description={article.data.summary} />
			<Link to={base} className="text-sm font-semibold text-info-blue hover:underline">← Back to Help Center</Link>
			<article className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-9">
				<p className="text-xs font-semibold uppercase tracking-[0.16em] text-green-700">{article.data.category}</p>
				<h1 className="mt-3 font-heading text-3xl font-extrabold text-navy-900">{article.data.title}</h1>
				<p className="mt-4 text-lg leading-7 text-slate-600">{article.data.summary}</p>
				<div className="my-7 border-t border-slate-200" />
				<SupportMarkdown body={article.data.bodyMarkdown} />
			</article>
			<div className="mt-6 rounded-xl bg-navy-950 p-5 text-white sm:flex sm:items-center sm:justify-between">
				<div><h2 className="font-heading font-bold">Still need help?</h2><p className="mt-1 text-sm text-slate-300">Open a support case and receive confirmation by email.</p></div>
				<Link to={`${base}/support`} className="mt-4 inline-flex rounded-lg bg-green-600 px-4 py-2 text-sm font-semibold hover:bg-green-700 sm:mt-0">Contact support</Link>
			</div>
		</PageContainer>
	);
}
