import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useUpdateUserPreferences, useUserPreferences } from "./api";
import type { AppearancePreference } from "./types";

const APPEARANCE_OPTIONS: Array<{ value: AppearancePreference; title: string; description: string }> = [
	{ value: "SYSTEM", title: "System", description: "Follow this device's light or dark appearance." },
	{ value: "LIGHT", title: "Light", description: "Always use Rally26's light appearance." },
	{ value: "DARK", title: "Dark", description: "Always use Rally26's dark appearance." },
];

export function SettingsPage() {
	const { user } = useAuth();
	const preferences = useUserPreferences();
	const update = useUpdateUserPreferences();

	function selectAppearance(appearance: AppearancePreference) {
		if (appearance === preferences.data?.appearance || update.isPending) return;
		update.mutate({ appearance });
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">Personal settings</p>
				<h1 className="mt-1 font-heading text-3xl font-bold text-navy-900">Settings</h1>
				<p className="mt-2 max-w-3xl text-sm text-slate-600">
					Your personal preferences follow your Rally26 account. Organization controls appear in later Phase 28 slices only when your role allows them.
				</p>
			</div>

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="account-settings-heading">
				<h2 id="account-settings-heading" className="font-heading text-xl font-semibold text-navy-900">Account</h2>
				<dl className="mt-4 grid gap-4 sm:grid-cols-2">
					<div><dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Name</dt><dd className="mt-1 text-sm text-navy-900">{user?.displayName ?? "Signed-in user"}</dd></div>
					<div><dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Email</dt><dd className="mt-1 text-sm text-navy-900">{user?.email ?? "—"}</dd></div>
				</dl>
				<p className="mt-4 text-sm text-slate-600">
					Profile-photo, password, and verified-email changes continue through their existing protected workflows; this page does not bypass profile-correction or identity-verification rules.
				</p>
			</section>

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="appearance-settings-heading">
				<div>
					<h2 id="appearance-settings-heading" className="font-heading text-xl font-semibold text-navy-900">Appearance</h2>
					<p className="mt-1 text-sm text-slate-600">Choose how Rally26 looks on authenticated pages.</p>
				</div>
				{preferences.isLoading && <div className="mt-4"><LoadingState label="Loading appearance preference…" /></div>}
				{preferences.isError && <div className="mt-4"><ErrorState message="Could not load your settings." onRetry={() => preferences.refetch()} /></div>}
				{preferences.data && (
					<fieldset className="mt-4 grid gap-3 sm:grid-cols-3" disabled={update.isPending}>
						<legend className="sr-only">Appearance</legend>
						{APPEARANCE_OPTIONS.map((option) => {
							const selected = preferences.data.appearance === option.value;
							return (
								<label key={option.value} className={`cursor-pointer rounded-xl border p-4 ${selected ? "border-victory-green bg-victory-green/5" : "border-slate-200 bg-ice-white"}`}>
									<input
										type="radio"
										name="appearance"
										value={option.value}
										checked={selected}
										onChange={() => selectAppearance(option.value)}
										className="mr-2"
									/>
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

			<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="settings-links-heading">
				<h2 id="settings-links-heading" className="font-heading text-xl font-semibold text-navy-900">Account tools</h2>
				<p className="mt-1 text-sm text-slate-600">Settings links to existing modules instead of duplicating their data or permissions.</p>
				<div className="mt-4 flex flex-wrap gap-3">
					<Link to="/app/history" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-navy-900 hover:bg-ice-white">History</Link>
					<Link to="/app/integrations" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-navy-900 hover:bg-ice-white">Integrations</Link>
					<Link to="/app/help" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-navy-900 hover:bg-ice-white">Help</Link>
				</div>
			</section>

			<section className="rounded-xl border border-dashed border-slate-300 bg-ice-white p-5">
				<h2 className="font-heading text-lg font-semibold text-navy-900">Coming next in Phase 28</h2>
				<p className="mt-1 text-sm text-slate-600">Notification preferences arrive in Slice 28.2. Organization configuration is consolidated in Slice 28.3.</p>
			</section>
		</div>
	);
}
