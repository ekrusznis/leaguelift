/**
 * Backend now sends the canonical `Sport` enum code (e.g. "SOCCER", "OTHER") rather than
 * free text (backend/src/main/kotlin/com/rally26/team/domain/Team.kt) — this renders it
 * back to a readable label. Pass `sportOtherLabel` whenever the API response includes it
 * so a team on `sport: "OTHER"` shows its real name instead of the literal word "Other".
 */
const SPORT_LABELS: Record<string, string> = {
  SOCCER: 'Soccer',
  BASKETBALL: 'Basketball',
  BASEBALL: 'Baseball',
  SOFTBALL: 'Softball',
  FOOTBALL: 'Football',
  ICE_HOCKEY: 'Ice Hockey',
  FIELD_HOCKEY: 'Field Hockey',
  VOLLEYBALL: 'Volleyball',
  LACROSSE: 'Lacrosse',
  SWIMMING: 'Swimming',
  TRACK_AND_FIELD: 'Track & Field',
  CROSS_COUNTRY: 'Cross Country',
  TENNIS: 'Tennis',
  WRESTLING: 'Wrestling',
  CHEERLEADING: 'Cheerleading',
  GYMNASTICS: 'Gymnastics',
  GOLF: 'Golf',
  RUGBY: 'Rugby',
  OTHER: 'Other',
};

export function sportLabel(sport: string, sportOtherLabel?: string | null): string {
  if (sport === 'OTHER' && sportOtherLabel) return sportOtherLabel;
  return SPORT_LABELS[sport] ?? sport;
}

/**
 * Ported from frontend/src/features/teams/sport.ts (same matrix, same fallback rules) —
 * applied only when a single, real team's sport is in view (a team schedule, roster,
 * etc.). A coach or athlete can span multiple sports outside of one team's context, so
 * any cross-team surface must stay on [GENERIC_TERMINOLOGY] rather than pick one
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
  event: 'Game / match',
  eventPlural: 'Games / matches',
  athlete: 'Athlete',
  athletePlural: 'Athletes',
  venue: 'Venue',
};

const SPORT_TERMINOLOGY: Record<string, SportTerminology> = {
  SOCCER: { event: 'Match', eventPlural: 'Matches', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  BASKETBALL: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Court' },
  BASEBALL: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  SOFTBALL: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  FOOTBALL: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  ICE_HOCKEY: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Rink' },
  FIELD_HOCKEY: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  VOLLEYBALL: { event: 'Match', eventPlural: 'Matches', athlete: 'Player', athletePlural: 'Players', venue: 'Court' },
  LACROSSE: { event: 'Game', eventPlural: 'Games', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  SWIMMING: { event: 'Meet', eventPlural: 'Meets', athlete: 'Swimmer', athletePlural: 'Swimmers', venue: 'Pool' },
  TRACK_AND_FIELD: { event: 'Meet', eventPlural: 'Meets', athlete: 'Athlete', athletePlural: 'Athletes', venue: 'Track' },
  CROSS_COUNTRY: { event: 'Meet', eventPlural: 'Meets', athlete: 'Runner', athletePlural: 'Runners', venue: 'Course' },
  TENNIS: { event: 'Match', eventPlural: 'Matches', athlete: 'Player', athletePlural: 'Players', venue: 'Court' },
  WRESTLING: { event: 'Meet', eventPlural: 'Meets', athlete: 'Wrestler', athletePlural: 'Wrestlers', venue: 'Mat' },
  CHEERLEADING: { event: 'Competition', eventPlural: 'Competitions', athlete: 'Athlete', athletePlural: 'Athletes', venue: 'Venue' },
  GYMNASTICS: { event: 'Meet', eventPlural: 'Meets', athlete: 'Gymnast', athletePlural: 'Gymnasts', venue: 'Gym' },
  GOLF: { event: 'Match', eventPlural: 'Matches', athlete: 'Golfer', athletePlural: 'Golfers', venue: 'Course' },
  RUGBY: { event: 'Match', eventPlural: 'Matches', athlete: 'Player', athletePlural: 'Players', venue: 'Field' },
  OTHER: GENERIC_TERMINOLOGY,
};

/** Pass `null`/`undefined` for any cross-team context — this deliberately falls back to [GENERIC_TERMINOLOGY]. */
export function terminologyForSport(sport: string | null | undefined): SportTerminology {
  if (!sport) return GENERIC_TERMINOLOGY;
  return SPORT_TERMINOLOGY[sport] ?? GENERIC_TERMINOLOGY;
}
