import type { ReactNode } from "react";

const VIDEO_EXTENSIONS = [".mp4", ".mov", ".webm"];

function isSafeUrl(url: string): boolean {
	return url.startsWith("/") || url.startsWith("https://");
}

function cleanPath(url: string): string {
	return url.split(/[?#]/)[0]?.toLowerCase() ?? "";
}

function isVideoUrl(url: string): boolean {
	const path = cleanPath(url);
	return VIDEO_EXTENSIONS.some((extension) => path.endsWith(extension));
}

function isPdfUrl(url: string): boolean {
	return cleanPath(url).endsWith(".pdf");
}

function inline(text: string): ReactNode[] {
	const parts = text.split(/(!\[[^\]]*\]\([^)]+\)|\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*)/g).filter(Boolean);
	return parts.map((part, index) => {
		const embed = /^!\[([^\]]*)\]\(([^)]+)\)$/.exec(part);
		if (embed) {
			const [, alt, url] = embed;
			if (!isSafeUrl(url)) return null;
			if (isVideoUrl(url)) {
				return (
					<video key={index} src={url} controls playsInline aria-label={alt || "Video"} className="max-w-full rounded-lg">
						Your browser does not support embedded video. <a href={url}>Open the video</a> instead.
					</video>
				);
			}
			if (isPdfUrl(url)) {
				return (
					<a key={index} href={url} target="_blank" rel="noreferrer" className="inline-flex rounded-lg border border-slate-200 px-4 py-3 font-medium text-info-blue underline hover:no-underline dark:border-slate-700">
						{alt || "Open attached PDF"}
					</a>
				);
			}
			return <img key={index} src={url} alt={alt} loading="lazy" className="max-w-full rounded-lg" />;
		}
		const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
		if (link) {
			const safe = isSafeUrl(link[2]) ? link[2] : "#";
			const external = safe.startsWith("https://");
			return <a key={index} href={safe} target={external ? "_blank" : undefined} rel={external ? "noreferrer" : undefined} className="font-medium text-info-blue underline hover:no-underline">{link[1]}</a>;
		}
		if (part.startsWith("**") && part.endsWith("**")) return <strong key={index}>{part.slice(2, -2)}</strong>;
		return part;
	});
}

/** Small safe Markdown subset: headings, paragraphs, ordered/unordered lists, bold, links, and image/GIF/video/PDF embeds (`![alt](url)`). Raw HTML is never interpreted. */
export function SupportMarkdown({ body }: { body: string }) {
	const blocks = body.replace(/\r\n/g, "\n").split(/\n\s*\n/).map((block) => block.trim()).filter(Boolean);
	return (
		<div className="flex flex-col gap-5 text-slate-700 dark:text-[#cbd5e1]">
			{blocks.map((block, index) => {
				if (block.startsWith("### ")) return <h3 key={index} className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{inline(block.slice(4))}</h3>;
				if (block.startsWith("## ")) return <h2 key={index} className="font-heading text-xl font-bold text-navy-900 dark:text-[#f8fafc]">{inline(block.slice(3))}</h2>;
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
