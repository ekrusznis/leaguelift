import { describe, expect, it } from "vitest";
import { dateSeparatorLabel, formatRelativeListTime, isDifferentDay, shouldGroupWithPrevious } from "../dateFormat";

const NOW = new Date("2026-08-20T16:30:00Z");

describe("formatRelativeListTime", () => {
	it("shows a time-of-day for a message sent today", () => {
		const result = formatRelativeListTime("2026-08-20T16:26:00Z", NOW);
		expect(result).toMatch(/\d{1,2}:\d{2}\s?(AM|PM)/);
	});

	it('shows "Yesterday" for a message sent the day before', () => {
		expect(formatRelativeListTime("2026-08-19T10:00:00Z", NOW)).toBe("Yesterday");
	});

	it("shows a short weekday within the last week", () => {
		const result = formatRelativeListTime("2026-08-17T10:00:00Z", NOW);
		expect(result).toMatch(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$/);
	});

	it("shows a short date for anything older than a week", () => {
		expect(formatRelativeListTime("2026-07-01T10:00:00Z", NOW)).toBe("Jul 1");
	});
});

describe("dateSeparatorLabel", () => {
	it("returns TODAY / YESTERDAY / a short date", () => {
		expect(dateSeparatorLabel("2026-08-20T09:00:00Z", NOW)).toBe("TODAY");
		expect(dateSeparatorLabel("2026-08-19T09:00:00Z", NOW)).toBe("YESTERDAY");
		expect(dateSeparatorLabel("2026-08-01T09:00:00Z", NOW)).toBe("AUG 1");
	});
});

describe("isDifferentDay", () => {
	it("distinguishes calendar days regardless of time", () => {
		// A 2-day gap can never disagree about "different calendar day" across any real
		// timezone offset, unlike a same-UTC-day-but-near-midnight pair (timezone-fragile).
		expect(isDifferentDay("2026-08-20T12:00:00Z", "2026-08-20T12:05:00Z")).toBe(false);
		expect(isDifferentDay("2026-08-20T12:00:00Z", "2026-08-22T12:00:00Z")).toBe(true);
	});
});

describe("shouldGroupWithPrevious", () => {
	it("merges consecutive same-sender messages within the window", () => {
		const previous = { senderUserId: "coach-1", sentAt: "2026-08-20T16:00:00Z" };
		const current = { senderUserId: "coach-1", sentAt: "2026-08-20T16:03:00Z" };
		expect(shouldGroupWithPrevious(current, previous)).toBe(true);
	});

	it("does not merge across different senders", () => {
		const previous = { senderUserId: "coach-1", sentAt: "2026-08-20T16:00:00Z" };
		const current = { senderUserId: "parent-1", sentAt: "2026-08-20T16:01:00Z" };
		expect(shouldGroupWithPrevious(current, previous)).toBe(false);
	});

	it("does not merge once the window has elapsed", () => {
		const previous = { senderUserId: "coach-1", sentAt: "2026-08-20T16:00:00Z" };
		const current = { senderUserId: "coach-1", sentAt: "2026-08-20T16:06:00Z" };
		expect(shouldGroupWithPrevious(current, previous)).toBe(false);
	});

	it("returns false with no previous message", () => {
		const current = { senderUserId: "coach-1", sentAt: "2026-08-20T16:00:00Z" };
		expect(shouldGroupWithPrevious(current, undefined)).toBe(false);
	});
});
