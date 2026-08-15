export type BoxPoolBoxStatus = "OPEN" | "RESERVED" | "CLAIMED";

export interface BoxPoolBox {
	id: string;
	rowIndex: number;
	colIndex: number;
	status: BoxPoolBoxStatus;
	claimantName: string | null;
}

export interface BoxPool {
	id: string;
	campaignId: string;
	sport: string;
	rows: number;
	cols: number;
	pricePerBoxMinor: number;
	rowAxisLabel: string | null;
	colAxisLabel: string | null;
	prizeDescription: string | null;
	boxes: BoxPoolBox[];
	createdAt: string;
}

export interface ReserveBoxResult {
	contributionId: string;
	checkoutUrl: string;
}
