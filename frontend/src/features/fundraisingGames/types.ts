export type FundraisingGameType = "BIG_GAME_SQUARES" | "BRACKET_CHALLENGE" | "PREDICTION_CHALLENGE" | "FREE_PRIZE_DRAWING" | "TRIVIA_CHALLENGE";
export type FundraisingGameStatus = "DRAFT" | "OPEN" | "CLOSED";
export interface FundraisingGamePermissions { canConfigure: boolean; canOpen: boolean; canClose: boolean; canDrawWinner: boolean; }
export interface FundraisingGame {
	id: string; organizationId: string; campaignId: string; gameType: FundraisingGameType; title: string; instructions: string | null;
	prizeDescription: string | null; maxEntries: number | null; entriesPerPerson: number; rows: number | null; cols: number | null;
	status: FundraisingGameStatus; winnerEntryId: string | null; winnerSelectedAt: string | null; entryCount: number; permissions: FundraisingGamePermissions;
}
export interface FundraisingGameEntry { id: string; displayName: string; email: string; selectionKey: string | null; selectionText: string | null; isWinner: boolean; createdAt: string; }
export interface PublicFundraisingGameEntry { id: string; displayName: string; selectionKey: string | null; selectionText: string | null; isWinner: boolean; }
export interface PublicFundraisingGame {
	id: string; campaignSlug: string; gameType: FundraisingGameType; title: string; instructions: string | null; prizeDescription: string | null;
	maxEntries: number | null; entriesPerPerson: number; rows: number | null; cols: number | null; status: FundraisingGameStatus; entryCount: number;
	winnerDisplayName: string | null; entries: PublicFundraisingGameEntry[]; freeEntryDisclosure: string;
}
export interface FundraisingGameFormValues {
	gameType: FundraisingGameType; title: string; instructions: string; prizeDescription: string; maxEntries: string; entriesPerPerson: number; rows: number; cols: number;
}
