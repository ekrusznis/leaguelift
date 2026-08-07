import { describe, expect, it } from "vitest";
import { formatEventAllDayDate, formatEventDateTime } from "./formatEventDateTime";

describe("formatEventDateTime", () => {
	it("formats an instant as fixed dd/mm/yyyy HH:mm plus the event's own zone abbreviation", () => {
		// 2026-08-15T18:00:00Z is 14:00 EDT in America/New_York.
		expect(formatEventDateTime("2026-08-15T18:00:00Z", "America/New_York")).toBe("15/08/2026 14:00 EDT");
	});

	it("always uses the EVENT's timezone, not any implicit viewer zone", () => {
		expect(formatEventDateTime("2026-08-15T18:00:00Z", "America/Los_Angeles")).toBe("15/08/2026 11:00 PDT");
		expect(formatEventDateTime("2026-08-15T18:00:00Z", "Pacific/Kiritimati")).toBe("16/08/2026 08:00 GMT+14");
	});

	it("returns null for a null instant", () => {
		expect(formatEventDateTime(null, "America/New_York")).toBeNull();
	});

	it("pads single-digit day, month, hour, and minute with leading zeros", () => {
		expect(formatEventDateTime("2026-01-05T09:05:00Z", "UTC")).toBe("05/01/2026 09:05 UTC");
	});

	it("renders midnight as 00:00, not 24:00", () => {
		expect(formatEventDateTime("2026-01-05T00:00:00Z", "UTC")).toBe("05/01/2026 00:00 UTC");
	});
});

describe("formatEventAllDayDate", () => {
	it("formats a plain YYYY-MM-DD string as dd/mm/yyyy with no zone conversion", () => {
		expect(formatEventAllDayDate("2026-07-04")).toBe("04/07/2026");
	});

	it("never shifts regardless of what the string would do if round-tripped through Date", () => {
		// A naive `new Date("2026-12-31").toISOString()` round-trip can shift a day in
		// negative-UTC-offset browsers — this function must never do that round-trip at all.
		expect(formatEventAllDayDate("2026-12-31")).toBe("31/12/2026");
	});
});
