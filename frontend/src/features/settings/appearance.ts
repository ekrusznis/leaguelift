import type { AppearancePreference } from "./types";

export type ResolvedAppearance = "LIGHT" | "DARK";

export function resolveAppearance(
	preference: AppearancePreference,
	systemPrefersDark: boolean,
): ResolvedAppearance {
	if (preference === "SYSTEM") return systemPrefersDark ? "DARK" : "LIGHT";
	return preference;
}

export function applyAppearance(
	preference: AppearancePreference,
	systemPrefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches,
) {
	const resolved = resolveAppearance(preference, systemPrefersDark);
	const root = document.documentElement;
	root.dataset.rally26Theme = resolved.toLowerCase();
	root.classList.toggle("dark", resolved === "DARK");
	root.style.colorScheme = resolved.toLowerCase();
}

export function clearAppearance() {
	const root = document.documentElement;
	delete root.dataset.rally26Theme;
	root.classList.remove("dark");
	root.style.removeProperty("color-scheme");
}

export function subscribeToSystemAppearance(
	preference: AppearancePreference,
	onChange: () => void,
) {
	if (preference !== "SYSTEM") return () => undefined;
	const query = window.matchMedia("(prefers-color-scheme: dark)");
	query.addEventListener("change", onChange);
	return () => query.removeEventListener("change", onChange);
}
