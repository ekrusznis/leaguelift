export type FundraisingGameType =
  | 'BIG_GAME_SQUARES'
  | 'BRACKET_CHALLENGE'
  | 'PREDICTION_CHALLENGE'
  | 'FREE_PRIZE_DRAWING'
  | 'TRIVIA_CHALLENGE';

export type FundraisingGameStatus = 'DRAFT' | 'OPEN' | 'CLOSED';

export interface FundraisingGamePermissions {
  canConfigure: boolean;
  canOpen: boolean;
  canClose: boolean;
  canDrawWinner: boolean;
}

export interface FundraisingGame {
  id: string;
  organizationId: string;
  campaignId: string;
  gameType: FundraisingGameType;
  title: string;
  instructions: string | null;
  prizeDescription: string | null;
  maxEntries: number | null;
  entriesPerPerson: number;
  rows: number | null;
  cols: number | null;
  status: FundraisingGameStatus;
  winnerEntryId: string | null;
  winnerSelectedAt: string | null;
  entryCount: number;
  permissions: FundraisingGamePermissions;
}

export interface FundraisingGameEntry {
  id: string;
  displayName: string;
  email: string;
  selectionKey: string | null;
  selectionText: string | null;
  isWinner: boolean;
  createdAt: string;
}

/** Retained for compatibility with any older list callers; one campaign now has one attached game. */
export interface FundraisingGamePage {
  items: FundraisingGame[];
  page: number;
  size: number;
  totalElements: number;
}
