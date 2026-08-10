import type { ChangeEvent } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { OrganizationSettingsDirectory } from "./OrganizationSettingsDirectory";
import {
	useNotificationPreferences,
	useUpdateNotificationTopic,
	useUpdateSmsConsent,
	useUpdateUserPreferences,
	useUserPreferences,
} from "./api";
import type {
	AppearancePreference,
	NotificationPreferenceState,
	NotificationTopic,
	NotificationTopicPreference,
} from "./types";

const APPEARANCE_OPTIONS: Array<{ value: AppearancePreference; title: string; description: string }> = [
	{ value: "SYSTEM", title: "System", description: "Follow this device's light or dark appearance." },
	{ value: "LIGHT", title: "Light", description: "Always use Rally26's light appearance." },
	{ value: "DARK", title: "Dark", description: "Always use Rally26's dark appearance." },
];

const TOPIC_LABELS: Record<NotificationTopic, { title: string; description: string }> = {
	EVENTS_SCHEDULE: { title: "Events & schedule", description: "Schedule changes and event reminders." },
	RSVP: { title: "RSVP", description: "Participation and response reminders." },
	FEES_PAYMENTS: { title: "Fees & payments", description: "Optional fee and balance reminders; receipts remain required." },
	DOCUMENTS_ELIGIBILITY: { title: "Documents & eligibility", description: "Optional document and future eligibility reminders." },
	ANNOUNCEMENTS: { title: "Announcements", description: "General organization and team announcements." },
	FUNDRAISING: { title: "Fundraising", description: "Campaign launches and fundraising updates." },
	SWAG_SHOP_ORDERS: { title: "Swag Shop & orders", description: "Optional store updates; order/refund receipts remain required." },
	MESSAGES: { title: "Messages", description: "Optional message notifications. Safety-required guardian visibility cannot be disabled." },
	SUPPORT: { title: "Support", description: "Optional support-case updates and follow-ups." },
};

const STATE_OPTIONS: Array<{ value: NotificationPreferenceState; label: string }> = [
	{ value: "DEFAULT", label: "Default" },
	{ value: "ENABLED", label: "On" },
	{ value: "DISABLED", label: "Off" },
];

export function SettingsPage() {
	const { user } = useAuth();
	const preferences = useUserPreferences();
	const update = useUpdateUserPreferences();
	const notifications = useNotificationPreferences();
	const updateNotification = useUpdateNotificationTopic();
	const updateSmsConsent = useUpdateSmsConsent();
	const appearancePreferences = preferences.data;
	const notificationPreferences = notifications.data;

	function selectAppearance(appearance: AppearancePreference) {
		if (appearance === appearancePreferences?.appearance || update.isPending) return;
		update.mutate({ appearance });
	}

	function updateTopic(
		preference: NotificationTopicPreference,
		channel: "inApp" | "email" | "sms",
		state: NotificationPreferenceState,
	) {
		if (updateNotification.isPending || preference[channel] === state) return;
		updateNotification.mutate({
			topic: preference.topic,
			request: {
				inApp: channel === "inApp" ? state : preference.inApp,
				email: channel === "email" ? state : preference.email,
				sms: channel === "sms" ? state : preference.sms,
			},
		});
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">Personal settings</p>
				<h1 className="mt-1 font-heading text-3xl font-bold text-navy-900">Settings</h1>
				<p className="mt-2 max-w-3xl text-sm text-slate-600">
					Your personal preferences follow your Rally26 account. Organization controls appear only when your role and the underlying domain allow them.
				</p>
			</div>

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="account-settings-heading">
				<h2 id="account-settings-heading" className="font-heading text-xl font-semibold text-navy-900">Account</h2>
				<dl className="mt-4 grid gap-4 sm:grid-cols-2">
					<div><dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Name</dt><dd className="mt-1 text-sm text-navy-900">{user?.displayName ?? "Signed-in user"}</dd></div>
					<div><dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Email</dt><dd className="mt-1 text-sm text-navy-900">{user?.email ?? "—"}</dd></div>
				</dl>
				<p className="mt-4 text-sm text-slate-600">
					Profile-photo, password, and verified-email changes continue through their existing protected workflows; Settings does not bypass profile-correction or identity-verification rules.
				</p>
			</section>

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="appearance-settings-heading">
				<div>
					<h2 id="appearance-settings-heading" className="font-heading text-xl font-semibold text-navy-900">Appearance</h2>
					<p className="mt-1 text-sm text-slate-600">Choose how Rally26 looks on authenticated pages.</p>
				</div>
				{preferences.isPending && <div className="mt-4"><LoadingState label="Loading appearance preference…" /></div>}
				{preferences.isError && <div className="mt-4"><ErrorState message="Could not load your settings." onRetry={() => preferences.refetch()} /></div>}
				{appearancePreferences && (
					<fieldset className="mt-4 grid gap-3 sm:grid-cols-3" disabled={update.isPending}>
						<legend className="sr-only">Appearance</legend>
						{APPEARANCE_OPTIONS.map((option) => {
							const selected = appearancePreferences.appearance === option.value;
							return (
								<label key={option.value} className={`cursor-pointer rounded-xl border p-4 ${selected ? "border-victory-green bg-victory-green/5" : "border-slate-200 bg-ice-white"}`}>
									<input type="radio" name="appearance" value={option.value} checked={selected} onChange={() => selectAppearance(option.value)} className="mr-2" />
									<span className="font-semibold text-navy-900">{option.title}</span>
									<span className="mt-2 block text-sm text-slate-600">{option.description}</span>
								</label>
							);
						})}
					</fieldset>
				)}
				{update.isPending && <p role="status" className="mt-3 text-sm text-slate-600">Saving appearance…</p>}
				{update.isSuccess && <p role="status" className="mt-3 text-sm font-medium text-victory-green">Appearance saved.</p>}
				{update.isError && <p role="alert" className="mt-3 text-sm font-medium text-rose-700">Could not save appearance. Your previous preference is unchanged.</p>}
			</section>

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="notification-settings-heading">
				<div>
					<h2 id="notification-settings-heading" className="font-heading text-xl font-semibold text-navy-900">Notifications</h2>
					<p className="mt-1 text-sm text-slate-600">
						Optional in-app and email notifications default on. SMS defaults off and requires your individual consent plus an explicit topic setting.
					</p>
				</div>
				{notifications.isPending && <div className="mt-4"><LoadingState label="Loading notification preferences…" /></div>}
				{notifications.isError && <div className="mt-4"><ErrorState message="Could not load notification preferences." onRetry={() => notifications.refetch()} /></div>}
				{notificationPreferences && (
					<div className="mt-5 space-y-5">
						<label className="flex items-start gap-3 rounded-xl border border-slate-200 bg-ice-white p-4">
							<input
								type="checkbox"
								checked={notificationPreferences.smsConsent}
								disabled={updateSmsConsent.isPending}
								onChange={(event: ChangeEvent<HTMLInputElement>) => updateSmsConsent.mutate({ consented: event.target.checked })}
								className="mt-1"
							/>
							<span>
								<span className="block font-semibold text-navy-900">Allow optional SMS notifications</span>
								<span className="mt-1 block text-sm text-slate-600">Consent alone does not enable every SMS topic. You must also set that topic's SMS choice to On.</span>
							</span>
						</label>

						<div className="overflow-x-auto rounded-xl border border-slate-200">
							<table className="min-w-full divide-y divide-slate-200 text-sm">
								<thead className="bg-ice-white text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
									<tr><th className="px-4 py-3">Topic</th><th className="px-4 py-3">In-app</th><th className="px-4 py-3">Email</th><th className="px-4 py-3">SMS</th></tr>
								</thead>
								<tbody className="divide-y divide-slate-200 bg-white">
									{notificationPreferences.topics.map((preference) => {
										const label = TOPIC_LABELS[preference.topic];
										return (
											<tr key={preference.topic}>
												<td className="px-4 py-4"><span className="font-semibold text-navy-900">{label.title}</span><span className="mt-1 block max-w-md text-xs text-slate-600">{label.description}</span></td>
												{(["inApp", "email", "sms"] as const).map((channel) => (
													<td key={channel} className="px-4 py-4 align-top">
														<select
															aria-label={`${label.title} ${channel}`}
															value={preference[channel]}
															disabled={updateNotification.isPending || (channel === "sms" && !notificationPreferences.smsConsent)}
															onChange={(event: ChangeEvent<HTMLSelectElement>) => updateTopic(preference, channel, event.target.value as NotificationPreferenceState)}
															className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-navy-900"
														>
															{STATE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
														</select>
													</td>
												))}
											</tr>
										);
									})}
								</tbody>
							</table>
						</div>

						<div className="rounded-xl border border-victory-green/30 bg-victory-green/5 p-4">
							<h3 className="font-semibold text-navy-900">Always on</h3>
							<p className="mt-1 text-sm text-slate-600">These required communications are not controlled by optional notification preferences.</p>
							<ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-700">
								{notificationPreferences.requiredCommunications.map((item) => <li key={item.key}>{item.label}</li>)}
							</ul>
						</div>
					</div>
				)}
				{updateNotification.isError && <p role="alert" className="mt-3 text-sm font-medium text-rose-700">Could not save that notification preference.</p>}
				{updateSmsConsent.isError && <p role="alert" className="mt-3 text-sm font-medium text-rose-700">Could not update SMS consent.</p>}
			</section>

			<OrganizationSettingsDirectory />

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="settings-links-heading">
				<h2 id="settings-links-heading" className="font-heading text-xl font-semibold text-navy-900">Account tools</h2>
				<p className="mt-1 text-sm text-slate-600">Settings links to existing modules instead of duplicating their data or permissions.</p>
				<div className="mt-4 flex flex-wrap gap-3">
					<Link to="/app/history" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-navy-900 hover:bg-ice-white">History</Link>
					<Link to="/app/integrations" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-navy-900 hover:bg-ice-white">Integrations</Link>
					<Link to="/app/help" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-navy-900 hover:bg-ice-white">Help</Link>
				</div>
			</section>

		</div>
	);
}
