import type { PlatformSupportAccess } from "./types";

const STORAGE_KEY = "rally26.platformSupportAccess";

export function readStoredSupportAccess(): PlatformSupportAccess | null {
	if (typeof window === "undefined") return null;
	try {
		const raw = window.localStorage.getItem(STORAGE_KEY);
		if (!raw) return null;
		const value = JSON.parse(raw) as PlatformSupportAccess;
		if (value.status !== "ACTIVE" || new Date(value.expiresAt).getTime() <= Date.now()) {
			window.localStorage.removeItem(STORAGE_KEY);
			return null;
		}
		return value;
	} catch {
		window.localStorage.removeItem(STORAGE_KEY);
		return null;
	}
}

export function storeSupportAccess(access: PlatformSupportAccess) {
	if (typeof window === "undefined") return;
	window.localStorage.setItem(STORAGE_KEY, JSON.stringify(access));
	window.dispatchEvent(new CustomEvent("rally26:support-access-changed"));
}

export function clearStoredSupportAccess() {
	if (typeof window === "undefined") return;
	window.localStorage.removeItem(STORAGE_KEY);
	window.dispatchEvent(new CustomEvent("rally26:support-access-changed"));
}
