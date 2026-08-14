import { describe, expect, it } from "vitest";
import { currencyFractionDigits, formatMoneyMinorUnits, majorAmountToMinorUnits, minorUnitsToMajorInput, parseMajorAmountToMinorUnits } from "../money";

describe("money helpers", () => {
	it("formats integer minor units consistently", () => {
		expect(formatMoneyMinorUnits(12345, "USD")).toBe("$123.45");
		expect(formatMoneyMinorUnits(0, "USD")).toBe("$0.00");
		expect(formatMoneyMinorUnits(-2500, "USD")).toBe("-$25.00");
	});

	it("converts minor units to form-safe major-unit text", () => {
		expect(minorUnitsToMajorInput(12345, "USD")).toBe("123.45");
		expect(minorUnitsToMajorInput(-5, "USD")).toBe("-0.05");
	});

	it("parses major-unit input without feature-level float math", () => {
		expect(parseMajorAmountToMinorUnits("123.45", "USD")).toBe(12345);
		expect(parseMajorAmountToMinorUnits("1,234.50", "USD")).toBe(123450);
		expect(parseMajorAmountToMinorUnits("-25", "USD")).toBe(-2500);
		expect(parseMajorAmountToMinorUnits("12.345", "USD")).toBeNull();
		expect(parseMajorAmountToMinorUnits("$25.00", "USD")).toBeNull();
	});


	it("uses the strict shared converter at API/domain boundaries", () => {
		expect(majorAmountToMinorUnits("123.45", "USD")).toBe(12345);
		expect(majorAmountToMinorUnits(25, "USD")).toBe(2500);
		expect(() => majorAmountToMinorUnits("12.345", "USD")).toThrow("Invalid money amount.");
	});

	it("respects currencies that do not use two fraction digits", () => {
		expect(currencyFractionDigits("JPY")).toBe(0);
		expect(minorUnitsToMajorInput(2500, "JPY")).toBe("2500");
		expect(parseMajorAmountToMinorUnits("2500", "JPY")).toBe(2500);
	});
});
