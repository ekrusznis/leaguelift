import { describe, expect, it } from "vitest";
import { formatMoneyMinorUnits } from "../money";

describe("formatMoneyMinorUnits", () => {
	it("formats integer minor units as currency, never using float math on the input", () => {
		expect(formatMoneyMinorUnits(12345, "USD")).toBe("$123.45");
		expect(formatMoneyMinorUnits(0, "USD")).toBe("$0.00");
		expect(formatMoneyMinorUnits(100, "USD")).toBe("$1.00");
	});
});
