/**
 * Typed paths into /public/demo-assets (see docs/RALLY26_MEDIA_ASSET_DESIGN.md
 * sections 18-21 for the source sheets, crop plan, and per-dashboard mapping).
 * These are demo-only, fictional assets — never represented as real customers.
 * Served as static public files for now; a real MediaReferenceResponse from the
 * backend (section 6) is future work once the media_asset/variant/assignment
 * tables exist. Components should accept a plain URL string either way so
 * swapping this module out later doesn't touch call sites.
 */

const BASE = "/demo-assets";

export const athleteAvatars = {
	avatar01: `${BASE}/avatars/athletes/athlete-avatar-01.webp`,
	avatar02: `${BASE}/avatars/athletes/athlete-avatar-02.webp`,
	avatar03: `${BASE}/avatars/athletes/athlete-avatar-03.webp`,
	avatar04: `${BASE}/avatars/athletes/athlete-avatar-04.webp`,
	avatar05: `${BASE}/avatars/athletes/athlete-avatar-05.webp`,
	avatar06: `${BASE}/avatars/athletes/athlete-avatar-06.webp`,
	avatar07: `${BASE}/avatars/athletes/athlete-avatar-07.webp`,
	avatar08: `${BASE}/avatars/athletes/athlete-avatar-08.webp`,
} as const;

export const adultAvatars = {
	guardianSarah: `${BASE}/avatars/adults/adult-avatar-01.webp`,
	coachJordan: `${BASE}/avatars/adults/adult-avatar-02.webp`,
	director: `${BASE}/avatars/adults/adult-avatar-03.webp`,
	owner: `${BASE}/avatars/adults/adult-avatar-04.webp`,
	guardianSecondary: `${BASE}/avatars/adults/adult-avatar-05.webp`,
	teamManager: `${BASE}/avatars/adults/adult-avatar-06.webp`,
	clubAdministrator: `${BASE}/avatars/adults/adult-avatar-07.webp`,
	treasurer: `${BASE}/avatars/adults/adult-avatar-08.webp`,
} as const;

export const teamLogos = {
	wolfpack: { src: `${BASE}/logos/teams/team-wolfpack.png`, alt: "Wolfpack team logo" },
	falcons: { src: `${BASE}/logos/teams/team-falcons.png`, alt: "Falcons team logo" },
	storm: { src: `${BASE}/logos/teams/team-storm.png`, alt: "Storm team logo" },
	strikers: { src: `${BASE}/logos/teams/team-strikers.png`, alt: "Strikers team logo" },
	hawks: { src: `${BASE}/logos/teams/team-hawks.png`, alt: "Hawks team logo" },
	blaze: { src: `${BASE}/logos/teams/team-blaze.png`, alt: "Blaze team logo" },
} as const;

export const organizationLogos = {
	eliteFc: { src: `${BASE}/logos/organizations/org-elite-fc.png`, alt: "Elite FC logo" },
	championsCup: { src: `${BASE}/logos/organizations/org-champions-cup.png`, alt: "Champions Cup logo" },
	sharksBaseball: { src: `${BASE}/logos/organizations/org-sharks-baseball.png`, alt: "Sharks Baseball logo" },
	falconsAthletics: { src: `${BASE}/logos/organizations/org-falcons-athletics.png`, alt: "Falcons Athletics logo" },
	allStarShowdown: { src: `${BASE}/logos/organizations/org-all-star-showdown.png`, alt: "All-Star Showdown logo" },
} as const;

export const productImages = {
	greenHoodie: { src: `${BASE}/products/product-green-hoodie.webp`, alt: "Green team hoodie" },
	navyPerformanceTee: { src: `${BASE}/products/product-navy-performance-tee.webp`, alt: "Navy performance tee" },
	whiteBaseballJersey: { src: `${BASE}/products/product-white-baseball-jersey.webp`, alt: "White baseball jersey" },
	navyTeamCap: { src: `${BASE}/products/product-navy-team-cap.webp`, alt: "Navy team cap" },
	tournamentSweatshirt: { src: `${BASE}/products/product-tournament-sweatshirt.webp`, alt: "Tournament sweatshirt" },
	blackQuarterZip: { src: `${BASE}/products/product-black-quarter-zip.webp`, alt: "Black quarter-zip pullover" },
} as const;

export const panelBackgrounds = {
	soccer: `${BASE}/backgrounds/soccer/soccer-bg-1280.webp`,
	hockey: `${BASE}/backgrounds/hockey/hockey-bg-1280.webp`,
	basketball: `${BASE}/backgrounds/basketball/basketball-bg-1280.webp`,
};

export const sidebarPromoBackground = `${BASE}/promotions/sidebar-promo-480.webp`;
