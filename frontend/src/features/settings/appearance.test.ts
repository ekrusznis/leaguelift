import { describe, expect, it } from "vitest";
import { resolveAppearance } from "./appearance";

describe("resolveAppearance", () => {
	it("uses explicit light and dark choices", () => {
		expect(resolveAppearance("LIGHT", true)).toBe("LIGHT");
		expect(resolveAppearance("DARK", false)).toBe("DARK");
	});

	it("follows the device only when System is selected", () => {
		expect(resolveAppearance("SYSTEM", true)).toBe("DARK");
		expect(resolveAppearance("SYSTEM", false)).toBe("LIGHT");
	});
});
