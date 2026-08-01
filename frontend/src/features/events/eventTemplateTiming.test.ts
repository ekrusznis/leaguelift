import { describe, expect, it } from "vitest";
import { deriveTemplateTimes } from "./eventTemplateTiming";

describe("deriveTemplateTimes", () => {
	it("derives end, arrival, and meeting timestamps from one event start", () => {
		expect(deriveTemplateTimes("2026-08-15T18:00:00.000Z", {
			durationMinutes: 90,
			arrivalOffsetMinutes: 30,
			meetingOffsetMinutes: 15,
		})).toEqual({
			endAt: "2026-08-15T19:30:00.000Z",
			arrivalAt: "2026-08-15T17:30:00.000Z",
			meetingAt: "2026-08-15T17:45:00.000Z",
		});
	});

	it("keeps unspecified offsets null", () => {
		expect(deriveTemplateTimes("2026-08-15T18:00:00.000Z", {
			durationMinutes: 60,
			arrivalOffsetMinutes: null,
			meetingOffsetMinutes: null,
		})).toEqual({
			endAt: "2026-08-15T19:00:00.000Z",
			arrivalAt: null,
			meetingAt: null,
		});
	});

	it("returns empty defaults when no valid start is supplied", () => {
		expect(deriveTemplateTimes(null, {
			durationMinutes: 60,
			arrivalOffsetMinutes: 15,
			meetingOffsetMinutes: 15,
		})).toEqual({ endAt: null, arrivalAt: null, meetingAt: null });
	});
});
