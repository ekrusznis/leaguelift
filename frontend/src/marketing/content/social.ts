import { env } from "../../lib/env";

export type SocialLink = { label: string; href: string };

/**
 * Social profiles are intentionally configuration-driven. Blank values remain
 * hidden so Rally26 never publishes guessed or placeholder accounts.
 */
export const SOCIAL_LINKS: SocialLink[] = [
	{ label: "LinkedIn", href: env.socialLinkedInUrl },
	{ label: "Facebook", href: env.socialFacebookUrl },
	{ label: "Instagram", href: env.socialInstagramUrl },
	{ label: "X", href: env.socialXUrl },
].filter((link) => link.href.startsWith("https://"));
