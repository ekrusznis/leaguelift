import { describe, expect, it } from "vitest";
import {
	classifyQuickBooksAccount,
	compatibilityLabel,
	type OwnerQuickBooksAccount,
	type QuickBooksMappingDefinition,
} from "./quickBooksMappingTools";

const definition: QuickBooksMappingDefinition = {
	mappingType: "FEES_RECEIVABLE",
	label: "Program fees receivable",
	description: "Receivable mapping",
	recommendedAccountTypes: ["Accounts Receivable"],
	warningAccountTypes: ["Other Current Asset"],
};

function account(id: string, accountType: string, active = true): OwnerQuickBooksAccount {
	return {
		id,
		name: id,
		fullyQualifiedName: id,
		accountType,
		accountSubType: null,
		classification: null,
		active,
	};
}

describe("QuickBooks owner mapping guidance", () => {
	it("classifies server-defined recommended warning and blocked account types", () => {
		expect(classifyQuickBooksAccount(account("ar", "Accounts Receivable"), definition)).toBe("RECOMMENDED");
		expect(classifyQuickBooksAccount(account("asset", "Other Current Asset"), definition)).toBe("ALLOWED_WITH_WARNING");
		expect(classifyQuickBooksAccount(account("bank", "Bank"), definition)).toBe("BLOCKED");
	});

	it("never permits an inactive account even when its type would otherwise be recommended", () => {
		expect(classifyQuickBooksAccount(account("old-ar", "Accounts Receivable", false), definition)).toBe("BLOCKED");
		expect(compatibilityLabel("BLOCKED")).toBe("Unavailable");
	});
});
