export type FundraisingGameType = 'BIG_GAME_SQUARES' | 'BRACKET_CHALLENGE' | 'PREDICTION_CHALLENGE' | 'FREE_PRIZE_DRAWING' | 'TRIVIA_CHALLENGE';

export interface FundraisingGamePermissions {
	canEdit: boolean;
	canOpen: boolean;
	canClose: boolean;
	canDrawWinner: boolean;
}

export interface FundraisingGame {
	id: string;
	campaignId: string;
	type: FundraisingGameType;
	status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
	title?: string | null;
	description?: string | null;
	instructions?: string | null;
	gameType?: FundraisingGameType;
	prizeDescription?: string | null;
	maxEntries?: number | null;
	entriesPerPerson?: number;
	rows?: number;
	cols?: number;
	entryCount?: number;
	permissions?: FundraisingGamePermissions;
	createdAt: string;
	updatedAt: string;
}

export interface FundraisingGamePage {
	items: FundraisingGame[];
	page: number;
	size: number;
	totalElements: number;
}

