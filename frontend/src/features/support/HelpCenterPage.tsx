import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { useHelpArticles } from "./api";
import { HELP_CATEGORIES } from "./types";

export function HelpCenterPage({ authenticated = false }: { authenticated?: boolean }) {
	const [query, setQuery] = useState("");
	const [category, setCategory] = useState("");
	const articles = useHelpArticles(authenticated, query, category);
	const base = authenticated ? "/app/help" : "/help";
	const resultCategories = useMemo(() => new Set(articles.data?.map((article) => article.category) ?? []), [articles.data]);
	const categories = HELP_CATEGORIES.filter((item) => !category || item === category || resultCategories.has(item));

	return (
		<>
			<Seo title="Help Center" description="Search LeagueLift help articles and contact ticket-based support." />
			<section className={authenticated ? "rounded-2xl bg-navy-950 px-5 py-10 sm:px-8" : "bg-navy-950 py-16 sm:py-20"}>
				<PageContainer className="max-w-4xl">
					<p className="text-sm font-semibold uppercase tracking-[0.18em] text-green-400">LeagueLift Support</p>
					<h1 className="mt-3 font-heading text-3xl font-extrabold text-white sm:text-4xl">How can we help?</h1>
					<p className="mt-3 max-w-2xl text-slate-300">Search walkthroughs and FAQs, or open a support case. Support is ticket and email based—not live chat.</p>
					<div className="mt-7 flex flex-col gap-3 sm:flex-row">
						<label className="sr-only" htmlFor="help-search">Search help articles</label>
						<input id="help-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search help articles" className="min-h-11 flex-1 rounded-lg border border-white/20 bg-white px-4 text-navy-900 outline-none focus:ring-2 focus:ring-green-400" />
						<label className="sr-only" htmlFor="help-category">Filter by category</label>
						<select id="help-category" value={category} onChange={(event) => setCategory(event.target.value)} className="min-h-11 rounded-lg border border-white/20 bg-white px-3 text-navy-900">
							<option value="">All categories</option>
							{HELP_CATEGORIES.map((item) => <option key={item}>{item}</option>)}
						</select>
					</div>
				</PageContainer>
			</section>

			<section className={authenticated ? "py-8" : "bg-white py-14 sm:py-16"}>
				<PageContainer className="max-w-5xl">
					<div className="mb-8 flex flex-wrap items-center justify-between gap-3">
						<div>
							<h2 className="font-heading text-2xl font-bold text-navy-900">Help articles</h2>
							<p className="mt-1 text-sm text-slate-600">Content is filtered to what your current access is allowed to see.</p>
						</div>
						<Link to={`${base}/support`} className="rounded-lg bg-green-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-green-700">Contact support</Link>
					</div>
					{articles.isLoading && <LoadingState label="Loading help articles…" />}
					{articles.isError && <ErrorState message="Help articles could not be loaded." />}
					{articles.data && articles.data.length === 0 && (
						<div className="rounded-xl border border-slate-200 bg-ice-50 p-8 text-center text-slate-600">No published articles match this search.</div>
					)}
					<div className="grid gap-4 md:grid-cols-2">
						{articles.data?.map((article) => (
							<Link key={article.id} to={`${base}/${article.slug}`} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-green-300 hover:shadow-md">
								<div className="flex flex-wrap items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
									<span>{article.category}</span><span aria-hidden="true">•</span><span>{article.audience.replace("_", " ")}</span>
								</div>
								<h3 className="mt-3 font-heading text-lg font-bold text-navy-900">{article.title}</h3>
								<p className="mt-2 text-sm leading-6 text-slate-600">{article.summary}</p>
							</Link>
						))}
					</div>
					{!query && !category && (
						<div className="mt-12 border-t border-slate-200 pt-8">
							<h2 className="font-heading text-lg font-bold text-navy-900">Browse categories</h2>
							<div className="mt-4 flex flex-wrap gap-2">{categories.map((item) => <button key={item} type="button" onClick={() => setCategory(item)} className="rounded-full border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:border-green-500 hover:text-navy-900">{item}</button>)}</div>
						</div>
					)}
				</PageContainer>
			</section>
		</>
	);
}
