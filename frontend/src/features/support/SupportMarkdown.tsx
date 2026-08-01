import type { ReactNode } from "react";

function inline(text: string): ReactNode[] {
	const parts = text.split(/(\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*)/g).filter(Boolean);
	return parts.map((part, index) => {
		const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
		if (link) {
			const safe = link[2].startsWith("/") || link[2].startsWith("https://") ? link[2] : "#";
			return <a key={index} href={safe} className="font-medium text-info-blue underline hover:no-underline">{link[1]}</a>;
		}
		if (part.startsWith("**") && part.endsWith("**")) return <strong key={index}>{part.slice(2, -2)}</strong>;
		return part;
	});
}

/** Small safe Markdown subset: headings, paragraphs, ordered/unordered lists, bold, and links. Raw HTML is never interpreted. */
export function SupportMarkdown({ body }: { body: string }) {
	const blocks = body.replace(/\r\n/g, "\n").split(/\n\s*\n/).map((block) => block.trim()).filter(Boolean);
	return (
		<div className="flex flex-col gap-5 text-slate-700">
			{blocks.map((block, index) => {
				if (block.startsWith("### ")) return <h3 key={index} className="font-heading text-lg font-bold text-navy-900">{inline(block.slice(4))}</h3>;
				if (block.startsWith("## ")) return <h2 key={index} className="font-heading text-xl font-bold text-navy-900">{inline(block.slice(3))}</h2>;
				const lines = block.split("\n");
				if (lines.every((line) => /^[-*] /.test(line))) {
					return <ul key={index} className="list-disc space-y-2 pl-6">{lines.map((line) => <li key={line}>{inline(line.slice(2))}</li>)}</ul>;
				}
				if (lines.every((line) => /^\d+\. /.test(line))) {
					return <ol key={index} className="list-decimal space-y-2 pl-6">{lines.map((line) => <li key={line}>{inline(line.replace(/^\d+\. /, ""))}</li>)}</ol>;
				}
				return <p key={index} className="whitespace-pre-line leading-7">{inline(block)}</p>;
			})}
		</div>
	);
}
