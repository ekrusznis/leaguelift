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
