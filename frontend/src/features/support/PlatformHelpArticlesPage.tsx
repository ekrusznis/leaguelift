import { useEffect, useState } from "react";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ArticleAttachmentPicker } from "./ArticleAttachmentPicker";
import { HELP_CATEGORIES, type SupportArticle, type SupportAudience } from "./types";
import { useArchiveSupportArticle, usePlatformHelpArticles, usePublishSupportArticle, useSaveSupportArticle, type SaveSupportArticleInput } from "./api";

const AUDIENCES: SupportAudience[] = ["PUBLIC", "OWNER_ADMIN", "COACH", "GUARDIAN", "ATHLETE", "PLATFORM"];
const empty: SaveSupportArticleInput = { slug: "", title: "", summary: "", bodyMarkdown: "", category: HELP_CATEGORIES[0], audience: "PUBLIC" as SupportAudience, sortOrder: 0 };

export function PlatformHelpArticlesPage() {
	const [query, setQuery] = useState("");
	const articles = usePlatformHelpArticles({ query, size: 100 });
	const [selected, setSelected] = useState<SupportArticle | null>(null);
	const [form, setForm] = useState(empty);
	const save = useSaveSupportArticle(selected?.id);
	const publish = usePublishSupportArticle();
	const archive = useArchiveSupportArticle();

	useEffect(() => { setForm(selected ? { slug: selected.slug, title: selected.title, summary: selected.summary, bodyMarkdown: selected.bodyMarkdown, category: selected.category, audience: selected.audience, sortOrder: selected.sortOrder } : empty); }, [selected]);

	async function submit(event: React.FormEvent) {
		event.preventDefault();
		const saved = await save.mutateAsync(form);
		setSelected(saved);
	}
	async function publishSelected() {
		if (!selected) return;
		setSelected(await publish.mutateAsync(selected.id));
	}
	async function archiveSelected() {
		if (!selected) return;
		setSelected(await archive.mutateAsync(selected.id));
	}

	const noArticles = articles.data && articles.data.items.length === 0;
	const filtered = query.trim().length > 0;

	return (
		<div className="grid gap-6 xl:grid-cols-[0.85fr_1.15fr]">
			<section className="rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5 shadow-sm">
				<div className="flex items-center justify-between gap-3"><div><h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Help articles</h1><p className="mt-1 text-sm text-slate-600 dark:text-[#cbd5e1]">Draft, publish, and archive the shared Help Center catalog.</p></div><button onClick={() => setSelected(null)} className="rounded-lg bg-green-600 px-3 py-2 text-sm font-semibold text-white">New article</button></div>
				<input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search articles" className="mt-5 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3" />
				{articles.isLoading && <LoadingState label="Loading articles…" />}{articles.isError && <ErrorState message="Articles could not be loaded." />}
				<div className="mt-4 flex max-h-[65vh] flex-col gap-2 overflow-y-auto">
					{noArticles && <EmptyState compact title={filtered ? "No results found" : "No help articles yet"} description={filtered ? "Try changing your search." : "Create the first Help Center article to get started."} />}
					{articles.data?.items.map((article) => <button key={article.id} onClick={() => setSelected(article)} className={`rounded-lg border p-3 text-left ${selected?.id === article.id ? "border-green-500 bg-green-50 dark:bg-green-950" : "border-slate-200 dark:border-[#334155] hover:bg-ice-50 hover:dark:bg-[#0f172a]"}`}><div className="flex items-center justify-between gap-2"><span className="font-semibold text-navy-900 dark:text-[#f8fafc]">{article.title}</span><span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-1 text-[11px] font-semibold">{article.status}</span></div><p className="mt-1 text-xs text-slate-500 dark:text-[#cbd5e1]">{article.category} · {article.audience}</p></button>)}
				</div>
			</section>
			<section className="rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5 shadow-sm sm:p-7">
				<h2 className="font-heading text-xl font-bold text-navy-900 dark:text-[#f8fafc]">{selected ? "Edit article" : "Create article"}</h2>
				<form onSubmit={submit} className="mt-5 grid gap-4 sm:grid-cols-2">
					<label className="text-sm font-semibold">Title<input required maxLength={180} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal" /></label>
					<label className="text-sm font-semibold">Slug<input required maxLength={120} value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, "-") })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal" /></label>
					<label className="text-sm font-semibold">Category<select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal">{HELP_CATEGORIES.map((item) => <option key={item}>{item}</option>)}</select></label>
					<label className="text-sm font-semibold">Audience<select value={form.audience} onChange={(e) => setForm({ ...form, audience: e.target.value as SupportAudience })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal">{AUDIENCES.map((item) => <option key={item}>{item}</option>)}</select></label>
					<label className="text-sm font-semibold sm:col-span-2">Summary<textarea required maxLength={400} rows={3} value={form.summary} onChange={(e) => setForm({ ...form, summary: e.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" /></label>
					<label className="text-sm font-semibold sm:col-span-2">Body (safe Markdown subset)<textarea required maxLength={20000} rows={14} value={form.bodyMarkdown} onChange={(e) => setForm({ ...form, bodyMarkdown: e.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 font-mono text-sm font-normal" /></label>
					{selected && (
						<div className="sm:col-span-2">
							<ArticleAttachmentPicker articleId={selected.id} onInsert={(markdown) => setForm((current) => ({ ...current, bodyMarkdown: current.bodyMarkdown ? `${current.bodyMarkdown}\n\n${markdown}` : markdown }))} />
						</div>
					)}
					{!selected && <p className="sm:col-span-2 text-xs text-slate-500 dark:text-[#cbd5e1]">Save this article as a draft before attaching images, GIFs, video, or files.</p>}
					<label className="text-sm font-semibold">Sort order<input type="number" min={0} max={10000} value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal" /></label>
					<div className="flex flex-wrap items-end gap-2"><button disabled={save.isPending} className="min-h-11 rounded-lg bg-green-600 px-4 font-semibold text-white">{save.isPending ? "Saving…" : "Save draft"}</button>{selected && selected.status !== "ARCHIVED" && <button type="button" onClick={publishSelected} className="min-h-11 rounded-lg border border-green-600 px-4 font-semibold text-green-700">Publish</button>}{selected && selected.status !== "ARCHIVED" && <button type="button" onClick={archiveSelected} className="min-h-11 rounded-lg border border-error-300 px-4 font-semibold text-error-700">Archive</button>}</div>
					{(save.isError || publish.isError || archive.isError) && <div className="sm:col-span-2"><ErrorState message="The article change could not be saved." /></div>}
				</form>
			</section>
		</div>
	);
}
