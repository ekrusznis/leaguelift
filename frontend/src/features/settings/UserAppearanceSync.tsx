import { useEffect } from "react";
import { useUserPreferences } from "./api";
import { applyAppearance, clearAppearance, subscribeToSystemAppearance } from "./appearance";
import "./appearance.css";

export function UserAppearanceSync() {
	const preferences = useUserPreferences();
	const appearance = preferences.data?.appearance ?? "SYSTEM";

	useEffect(() => {
		const apply = () => applyAppearance(appearance);
		apply();
		return subscribeToSystemAppearance(appearance, apply);
	}, [appearance]);

	useEffect(() => () => clearAppearance(), []);

	return null;
}
