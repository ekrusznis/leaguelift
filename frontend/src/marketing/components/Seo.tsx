import { useEffect } from "react";

type SeoProps = {
	title: string;
	description: string;
	/** Private/auth/confirmation pages must not be indexed (section 34.2). */
	noIndex?: boolean;
};

function setMetaTag(attr: "name" | "property", key: string, content: string) {
	let tag = document.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`);
	if (!tag) {
		tag = document.createElement("meta");
		tag.setAttribute(attr, key);
		document.head.appendChild(tag);
	}
	tag.setAttribute("content", content);
}

/**
 * Minimal per-page SEO without an extra dependency — sets title, description,
 * canonical URL, Open Graph tags, and robots directives directly on `document`.
 */
export function Seo({ title, description, noIndex = false }: SeoProps) {
	useEffect(() => {
		const fullTitle = title.includes("Rally26") ? title : `${title} | Rally26`;
		document.title = fullTitle;

		setMetaTag("name", "description", description);
		setMetaTag("property", "og:title", fullTitle);
		setMetaTag("property", "og:description", description);
		setMetaTag("property", "og:type", "website");
		setMetaTag("name", "robots", noIndex ? "noindex, nofollow" : "index, follow");

		let canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
		if (!canonical) {
			canonical = document.createElement("link");
			canonical.setAttribute("rel", "canonical");
			document.head.appendChild(canonical);
		}
		canonical.setAttribute("href", window.location.origin + window.location.pathname);
	}, [title, description, noIndex]);

	return null;
}
