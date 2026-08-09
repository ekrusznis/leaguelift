export type AppearancePreference = "SYSTEM" | "LIGHT" | "DARK";

export type NotificationTopic =
	| "EVENTS_SCHEDULE"
	| "RSVP"
	| "FEES_PAYMENTS"
	| "DOCUMENTS_ELIGIBILITY"
	| "ANNOUNCEMENTS"
	| "FUNDRAISING"
	| "SWAG_SHOP_ORDERS"
	| "MESSAGES"
	| "SUPPORT";

export type NotificationPreferenceState = "DEFAULT" | "ENABLED" | "DISABLED";

export interface UserPreferences {
	appearance: AppearancePreference;
}

export interface UpdateUserPreferencesRequest {
	appearance: AppearancePreference;
}

export interface NotificationTopicPreference {
	topic: NotificationTopic;
	inApp: NotificationPreferenceState;
	email: NotificationPreferenceState;
	sms: NotificationPreferenceState;
}

export interface RequiredCommunication {
	key: string;
	label: string;
}

export interface NotificationPreferences {
	smsConsent: boolean;
	topics: NotificationTopicPreference[];
	requiredCommunications: RequiredCommunication[];
}

export interface UpdateNotificationTopicRequest {
	inApp: NotificationPreferenceState;
	email: NotificationPreferenceState;
	sms: NotificationPreferenceState;
}

export interface UpdateSmsConsentRequest {
	consented: boolean;
}
