export type Sport =
	| "SOCCER"
	| "BASKETBALL"
	| "BASEBALL"
	| "SOFTBALL"
	| "FOOTBALL"
	| "ICE_HOCKEY"
	| "FIELD_HOCKEY"
	| "VOLLEYBALL"
	| "LACROSSE"
	| "SWIMMING"
	| "TRACK_AND_FIELD"
	| "CROSS_COUNTRY"
	| "TENNIS"
	| "WRESTLING"
	| "CHEERLEADING"
	| "GYMNASTICS"
	| "GOLF"
	| "RUGBY"
	| "OTHER";

export const SPORT_OPTIONS: { value: Sport; label: string }[] = [
	{ value: "SOCCER", label: "Soccer" },
	{ value: "BASKETBALL", label: "Basketball" },
	{ value: "BASEBALL", label: "Baseball" },
	{ value: "SOFTBALL", label: "Softball" },
	{ value: "FOOTBALL", label: "Football" },
	{ value: "ICE_HOCKEY", label: "Ice Hockey" },
	{ value: "FIELD_HOCKEY", label: "Field Hockey" },
	{ value: "VOLLEYBALL", label: "Volleyball" },
	{ value: "LACROSSE", label: "Lacrosse" },
	{ value: "SWIMMING", label: "Swimming" },
	{ value: "TRACK_AND_FIELD", label: "Track & Field" },
	{ value: "CROSS_COUNTRY", label: "Cross Country" },
	{ value: "TENNIS", label: "Tennis" },
	{ value: "WRESTLING", label: "Wrestling" },
	{ value: "CHEERLEADING", label: "Cheerleading" },
	{ value: "GYMNASTICS", label: "Gymnastics" },
	{ value: "GOLF", label: "Golf" },
	{ value: "RUGBY", label: "Rugby" },
	{ value: "OTHER", label: "Other" },
];

export const SPORT_VALUES = SPORT_OPTIONS.map((option) => option.value) as [Sport, ...Sport[]];

const SPORT_LABELS: Record<Sport, string> = Object.fromEntries(SPORT_OPTIONS.map((option) => [option.value, option.label])) as Record<Sport, string>;

/** Renders the "Other" custom name when set, otherwise the canonical label for [sport]. */
export function sportLabel(team: { sport: Sport; sportOtherLabel: string | null }): string {
	if (team.sport === "OTHER" && team.sportOtherLabel) return team.sportOtherLabel;
	return SPORT_LABELS[team.sport] ?? team.sport;
}

/**
 * The sport-terminology matrix. Applied only when a single, real team's [Sport] is in
 * view (a team schedule, roster, etc.) — a coach or athlete can span multiple sports
 * outside of one team's context, so any cross-team surface (My Teams, org dashboards,
 * org-wide event templates) must stay on [GENERIC_TERMINOLOGY] rather than pick one
 * sport's words for everyone.
 */
export interface SportTerminology {
	event: string;
	eventPlural: string;
	athlete: string;
	athletePlural: string;
	venue: string;
}

export const GENERIC_TERMINOLOGY: SportTerminology = {
	event: "Game / match",
	eventPlural: "Games / matches",
	athlete: "Athlete",
	athletePlural: "Athletes",
	venue: "Venue",
};

const SPORT_TERMINOLOGY: Record<Sport, SportTerminology> = {
	SOCCER: { event: "Match", eventPlural: "Matches", athlete: "Player", athletePlural: "Players", venue: "Field" },
	BASKETBALL: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Court" },
	BASEBALL: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Field" },
	SOFTBALL: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Field" },
	FOOTBALL: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Field" },
	ICE_HOCKEY: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Rink" },
	FIELD_HOCKEY: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Field" },
	VOLLEYBALL: { event: "Match", eventPlural: "Matches", athlete: "Player", athletePlural: "Players", venue: "Court" },
	LACROSSE: { event: "Game", eventPlural: "Games", athlete: "Player", athletePlural: "Players", venue: "Field" },
	SWIMMING: { event: "Meet", eventPlural: "Meets", athlete: "Swimmer", athletePlural: "Swimmers", venue: "Pool" },
	TRACK_AND_FIELD: { event: "Meet", eventPlural: "Meets", athlete: "Athlete", athletePlural: "Athletes", venue: "Track" },
	CROSS_COUNTRY: { event: "Meet", eventPlural: "Meets", athlete: "Runner", athletePlural: "Runners", venue: "Course" },
	TENNIS: { event: "Match", eventPlural: "Matches", athlete: "Player", athletePlural: "Players", venue: "Court" },
	WRESTLING: { event: "Meet", eventPlural: "Meets", athlete: "Wrestler", athletePlural: "Wrestlers", venue: "Mat" },
	CHEERLEADING: { event: "Competition", eventPlural: "Competitions", athlete: "Athlete", athletePlural: "Athletes", venue: "Venue" },
	GYMNASTICS: { event: "Meet", eventPlural: "Meets", athlete: "Gymnast", athletePlural: "Gymnasts", venue: "Gym" },
	GOLF: { event: "Match", eventPlural: "Matches", athlete: "Golfer", athletePlural: "Golfers", venue: "Course" },
	RUGBY: { event: "Match", eventPlural: "Matches", athlete: "Player", athletePlural: "Players", venue: "Field" },
	OTHER: GENERIC_TERMINOLOGY,
};

/** Pass `null`/`undefined` for any cross-team context — this deliberately falls back to [GENERIC_TERMINOLOGY]. */
export function terminologyForSport(sport: Sport | null | undefined): SportTerminology {
	if (!sport) return GENERIC_TERMINOLOGY;
	return SPORT_TERMINOLOGY[sport] ?? GENERIC_TERMINOLOGY;
}
