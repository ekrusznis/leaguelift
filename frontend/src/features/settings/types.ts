export type AppearancePreference = "SYSTEM" | "LIGHT" | "DARK";

export interface UserPreferences {
	appearance: AppearancePreference;
}

export interface UpdateUserPreferencesRequest {
	appearance: AppearancePreference;
}
