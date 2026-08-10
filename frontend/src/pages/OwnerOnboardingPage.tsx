import { useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
	createBillingPortal,
	getOwnerOnboarding,
	listOnboardingPlans,
	saveOnboardingOrganization,
	selectOnboardingPlan,
	startSubscriptionCheckout,
	suggestOnboardingTimezone,
	type OwnerOnboarding,
	type SaveOrganizationInput,
	type SubscriptionPlan,
} from "../features/onboarding/ownerOnboardingApi";
import { ApiError } from "../lib/apiError";

const ORGANIZATION_TYPES = [
	["TRAVEL_CLUB", "Travel club"],
	["INDIVIDUAL_TEAM", "Individual team"],
	["RECREATIONAL_LEAGUE", "Recreational league"],
	["TOURNAMENT_OPERATOR", "Tournament operator"],
	["BOOSTER_ORGANIZATION", "Booster organization"],
	["MULTISPORT_FACILITY", "Multi-sport facility"],
	["COMMUNITY_PROGRAM", "Community program"],
	["OTHER", "Other"],
] as const;

const STEP_ORDER = ["Account", "Organization", "Plan", "Review & Checkout"];

// Curated, not free text: shared naming keeps team/tournament sport filters and
// reporting consistent instead of accumulating typo variants ("Soccer" vs "soccer").
const SPORT_OPTIONS = [
	"Baseball",
	"Basketball",
	"Cheerleading",
	"Cross Country",
	"Field Hockey",
	"Football",
	"Golf",
	"Gymnastics",
	"Ice Hockey",
	"Lacrosse",
	"Soccer",
	"Softball",
	"Swimming",
	"Tennis",
	"Track and Field",
	"Volleyball",
	"Wrestling",
	"Other",
] as const;

function slugify(value: string) {
	return value
		.toLowerCase()
		.trim()
		.replace(/[^a-z0-9]+/g, "-")
		.replace(/^-+|-+$/g, "")
		.slice(0, 63);
}

function money(plan: SubscriptionPlan) {
	if (plan.amountMinor == null || !plan.currency) return "Contact us";
	return new Intl.NumberFormat("en-US", { style: "currency", currency: plan.currency }).format(plan.amountMinor / 100);
}

type OnboardingRouteStep = "organization" | "plan" | "review";

function defaultRouteStep(onboarding: OwnerOnboarding): OnboardingRouteStep {
	if (onboarding.currentStep === "ORGANIZATION") return "organization";
	if (onboarding.currentStep === "PLAN") return "plan";
	return "review";
}

function routeStepIndex(step: OnboardingRouteStep) {
	if (step === "organization") return 1;
	if (step === "plan") return 2;
	return 3;
}

function isRouteStep(value: string | undefined): value is OnboardingRouteStep {
	return value === "organization" || value === "plan" || value === "review";
}

export function OwnerOnboardingPage() {
	const { user } = useAuth();
	const navigate = useNavigate();
	const { step: routeStepParam } = useParams();
	const [searchParams] = useSearchParams();
	const [onboarding, setOnboarding] = useState<OwnerOnboarding | null>(null);
	const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [organizationForm, setOrganizationForm] = useState<SaveOrganizationInput>(() => ({
		name: "",
		slug: "",
		organizationType: "TRAVEL_CLUB",
		sports: [],
		contactEmail: "",
		contactPhone: "",
		addressLine1: "",
		addressLine2: "",
		addressCity: "",
		addressState: "",
		addressPostalCode: "",
		addressCountry: "US",
		timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "America/New_York",
	}));
	const [slugTouched, setSlugTouched] = useState(false);
	const [timezoneSuggestion, setTimezoneSuggestion] = useState<string | null>(null);

	const reload = async () => {
		const [nextOnboarding, nextPlans] = await Promise.all([getOwnerOnboarding(), listOnboardingPlans()]);
		setOnboarding(nextOnboarding);
		setPlans(nextPlans);
		if (nextOnboarding.organization) {
			const org = nextOnboarding.organization;
			setOrganizationForm({
				name: org.name,
				slug: org.slug,
				organizationType: org.organizationType,
				sports: org.sports,
				contactEmail: org.contactEmail ?? "",
				contactPhone: org.contactPhone ?? "",
				addressLine1: org.addressLine1 ?? "",
				addressLine2: org.addressLine2 ?? "",
				addressCity: org.addressCity ?? "",
				addressState: org.addressState ?? "",
				addressPostalCode: org.addressPostalCode ?? "",
				addressCountry: org.addressCountry ?? "US",
				timezone: org.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? "America/New_York",
			});
			setSlugTouched(true);
		}
	};

	useEffect(() => {
		reload()
			.catch((err) => setError(err instanceof ApiError ? err.message : "Could not load onboarding."))
			.finally(() => setLoading(false));
	}, []);

	useEffect(() => {
		if (!onboarding || isRouteStep(routeStepParam)) return;
		navigate(`/app/onboarding/${defaultRouteStep(onboarding)}`, { replace: true });
	}, [navigate, onboarding, routeStepParam]);

	// Stripe's return URL is informational only. Reload authoritative server state; a
	// successful browser redirect never marks the subscription active by itself.
	useEffect(() => {
		if (searchParams.get("checkout") !== "success") return;
		const timer = window.setInterval(() => void reload(), 2000);
		const stop = window.setTimeout(() => window.clearInterval(timer), 12000);
		return () => {
			window.clearInterval(timer);
			window.clearTimeout(stop);
		};
	}, [searchParams]);

	const selectedPlan = useMemo(
		() => plans.find((plan) => plan.code === onboarding?.selectedPlanCode) ?? null,
		[onboarding?.selectedPlanCode, plans],
	);

	const updateField = <K extends keyof SaveOrganizationInput>(key: K, value: SaveOrganizationInput[K]) => {
		setOrganizationForm((current) => ({ ...current, [key]: value }));
	};

	const toggleSport = (sport: string) => {
		setOrganizationForm((current) => ({
			...current,
			sports: current.sports.includes(sport) ? current.sports.filter((item) => item !== sport) : [...current.sports, sport],
		}));
	};

	const suggestTimezone = async () => {
		setError(null);
		try {
			const result = await suggestOnboardingTimezone(organizationForm.addressCountry, organizationForm.addressState);
			setTimezoneSuggestion(result.timezone);
			if (!result.timezone) setError("Rally26 could not confidently suggest a timezone from that country/state. Enter and confirm the IANA timezone manually.");
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Could not suggest a timezone.");
		}
	};

	const saveOrganization = async () => {
		setSaving(true);
		setError(null);
		try {
			const next = await saveOnboardingOrganization(organizationForm);
			setOnboarding(next);
			navigate("/app/onboarding/plan");
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Could not save organization details.");
		} finally {
			setSaving(false);
		}
	};

	const choosePlan = async (plan: SubscriptionPlan) => {
		if (plan.contactOnly) return;
		setSaving(true);
		setError(null);
		try {
			setOnboarding(await selectOnboardingPlan(plan.code));
			navigate("/app/onboarding/review");
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Could not select this plan.");
		} finally {
			setSaving(false);
		}
	};

	const checkout = async () => {
		setSaving(true);
		setError(null);
		try {
			const session = await startSubscriptionCheckout();
			window.location.assign(session.checkoutUrl);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Could not start Stripe Checkout.");
			setSaving(false);
		}
	};

	const openBillingPortal = async () => {
		if (!onboarding?.organization) return;
		setSaving(true);
		setError(null);
		try {
			const result = await createBillingPortal(onboarding.organization.id);
			window.location.assign(result.url);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Could not open billing management.");
			setSaving(false);
		}
	};

	if (loading) return <main className="mx-auto max-w-5xl p-6 text-slate-700">Loading setup…</main>;

	const isActive = onboarding?.subscriptionStatus === "ACTIVE" || onboarding?.subscriptionStatus === "TRIALING" || onboarding?.subscriptionStatus === "PAST_DUE";
	const routeStep: OnboardingRouteStep = isRouteStep(routeStepParam) ? routeStepParam : onboarding ? defaultRouteStep(onboarding) : "organization";
	const step = routeStepIndex(routeStep);

	return (
		<main className="min-h-screen bg-slate-50 px-4 py-10 sm:px-6">
			<div className="mx-auto max-w-5xl">
				<div className="mb-8">
					<p className="text-sm font-semibold uppercase tracking-[0.18em] text-green-700">Rally26 setup</p>
					<h1 className="mt-2 font-heading text-3xl font-extrabold text-navy-900">Create your organization</h1>
					<p className="mt-2 max-w-2xl text-slate-600">Your progress is saved after each step. You can close this page and resume after signing in again.</p>
				</div>

				<ol className="mb-8 grid gap-3 sm:grid-cols-4" aria-label="Onboarding progress">
					{STEP_ORDER.map((label, index) => {
						const target = index === 1 ? "/app/onboarding/organization" : index === 2 ? "/app/onboarding/plan" : index === 3 ? "/app/onboarding/review" : null;
						const enabled = index === 0 || index === 1 || (index === 2 && Boolean(onboarding?.organization)) || (index === 3 && Boolean(onboarding?.selectedPlanCode));
						const className = `block rounded-xl border px-4 py-3 text-sm font-semibold ${index <= step ? "border-green-300 bg-green-50 text-green-900" : "border-slate-200 bg-white text-slate-500"}`;
						return <li key={label}>{target && enabled ? <Link to={target} className={className} aria-current={index === step ? "step" : undefined}><span className="mr-2">{index + 1}.</span>{label}</Link> : <div className={className}><span className="mr-2">{index + 1}.</span>{label}</div>}</li>;
					})}
				</ol>

				{error && <div role="alert" className="mb-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</div>}
				{searchParams.get("checkout") === "cancelled" && <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Checkout was canceled. Your setup is saved and you can continue when ready.</div>}
				{searchParams.get("checkout") === "success" && !isActive && <div role="status" className="mb-6 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">Stripe returned successfully. We are waiting for the signed subscription webhook before activating your organization.</div>}


				{routeStep === "organization" && <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
					<h2 className="text-lg font-bold text-navy-900">2. Organization</h2>
					<p className="mt-1 text-sm text-slate-600">Create the draft club profile and confirm its timezone.</p>
					<div className="mt-5 grid gap-4 sm:grid-cols-2">
						<label className="text-sm font-semibold text-slate-700">Organization name<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.name} onChange={(e) => { updateField("name", e.target.value); if (!slugTouched) updateField("slug", slugify(e.target.value)); }} /></label>
						<label className="text-sm font-semibold text-slate-700">Organization URL<input disabled={Boolean(onboarding?.organization)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 disabled:bg-slate-100" value={organizationForm.slug} onChange={(e) => { setSlugTouched(true); updateField("slug", slugify(e.target.value)); }} /></label>
						<label className="text-sm font-semibold text-slate-700">Organization type<select className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.organizationType} onChange={(e) => updateField("organizationType", e.target.value)}>{ORGANIZATION_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
						<div className="sm:col-span-2">
							<span className="text-sm font-semibold text-slate-700">Sports</span>
							<div className="mt-1 grid grid-cols-2 gap-2 sm:grid-cols-3">
								{SPORT_OPTIONS.map((sport) => (
									<label key={sport} className="flex items-center gap-2 text-sm text-slate-700">
										<input type="checkbox" checked={organizationForm.sports.includes(sport)} onChange={() => toggleSport(sport)} className="size-4 rounded border-slate-300" />
										{sport}
									</label>
								))}
							</div>
						</div>
						<label className="text-sm font-semibold text-slate-700">Contact email<input type="email" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.contactEmail} onChange={(e) => updateField("contactEmail", e.target.value)} /></label>
						<label className="text-sm font-semibold text-slate-700">Contact phone<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.contactPhone ?? ""} onChange={(e) => updateField("contactPhone", e.target.value)} /></label>
						<label className="text-sm font-semibold text-slate-700 sm:col-span-2">Address<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.addressLine1} onChange={(e) => updateField("addressLine1", e.target.value)} /></label>
						<label className="text-sm font-semibold text-slate-700">City<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.addressCity} onChange={(e) => updateField("addressCity", e.target.value)} /></label>
						<label className="text-sm font-semibold text-slate-700">State / province<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.addressState} onChange={(e) => updateField("addressState", e.target.value)} /></label>
						<label className="text-sm font-semibold text-slate-700">Postal code<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.addressPostalCode} onChange={(e) => updateField("addressPostalCode", e.target.value)} /></label>
						<label className="text-sm font-semibold text-slate-700">Country code<input maxLength={2} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 uppercase" value={organizationForm.addressCountry} onChange={(e) => updateField("addressCountry", e.target.value.toUpperCase())} /></label>
						<div className="sm:col-span-2">
							<div className="flex flex-wrap items-end gap-3">
								<label className="min-w-0 flex-1 text-sm font-semibold text-slate-700">Confirmed timezone<input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={organizationForm.timezone} onChange={(e) => updateField("timezone", e.target.value)} /><span className="mt-1 block text-xs font-normal text-slate-500">Use an IANA timezone such as America/New_York.</span></label>
								<button type="button" onClick={suggestTimezone} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-bold text-slate-800">Suggest from address</button>
							</div>
							{timezoneSuggestion && <div className="mt-3 rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">Suggested timezone: <strong>{timezoneSuggestion}</strong>. <button type="button" onClick={() => { updateField("timezone", timezoneSuggestion); setTimezoneSuggestion(null); }} className="font-bold underline">Use this timezone</button></div>}
						</div>
					</div>
					<button type="button" disabled={saving || onboarding?.organization?.status === "ACTIVE" || onboarding?.organization?.status === "SUSPENDED"} onClick={saveOrganization} className="mt-5 rounded-lg bg-navy-900 px-5 py-2.5 text-sm font-bold text-white disabled:opacity-50">Save & continue</button>
				</section>}

				{routeStep === "plan" && <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
					<h2 className="text-lg font-bold text-navy-900">3. Plan</h2>
					<p className="mt-1 text-sm text-slate-600">Pricing comes from Rally26&rsquo;s backend plan catalog. Monthly billing is the only self-service frequency currently offered.</p>
					<div className="mt-5 grid gap-4 md:grid-cols-2">
						{plans.map((plan) => <div key={plan.code} className={`rounded-xl border p-5 ${onboarding?.selectedPlanCode === plan.code ? "border-green-500 ring-2 ring-green-100" : "border-slate-200"}`}><div className="flex items-start justify-between gap-3"><div><h3 className="font-bold text-navy-900">{plan.name}</h3><p className="mt-1 text-sm text-slate-600">{plan.description}</p></div>{onboarding?.selectedPlanCode === plan.code && <span className="rounded-full bg-green-100 px-2 py-1 text-xs font-bold text-green-800">Selected</span>}</div><p className="mt-4 text-2xl font-extrabold text-navy-900">{money(plan)}{!plan.contactOnly && <span className="text-sm font-medium text-slate-500"> / month</span>}</p>{plan.contactOnly ? <Link to="/talk-to-sales" className="mt-4 inline-block text-sm font-bold text-green-700 hover:underline">Contact Rally26</Link> : <button type="button" disabled={saving || !onboarding?.organization || isActive} onClick={() => choosePlan(plan)} className="mt-4 rounded-lg border border-navy-900 px-4 py-2 text-sm font-bold text-navy-900 disabled:opacity-50">Choose monthly</button>}</div>)}
					</div>
				</section>}

				{routeStep === "review" && <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
					<h2 className="text-lg font-bold text-navy-900">4. Review & Checkout</h2>
					{onboarding?.organization && selectedPlan ? <div className="mt-4 grid gap-4 md:grid-cols-3"><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Owner account</p><p className="mt-1 font-bold text-navy-900">{user?.displayName ?? "Organization owner"}</p><p className="text-sm text-slate-600">{user?.email}</p></div><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Organization</p><p className="mt-1 font-bold text-navy-900">{onboarding.organization.name}</p><p className="text-sm text-slate-600">{onboarding.organization.addressCity}, {onboarding.organization.addressState} · {onboarding.organization.timezone}</p></div><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Subscription</p><p className="mt-1 font-bold text-navy-900">{selectedPlan.name}</p><p className="text-sm text-slate-600">{money(selectedPlan)} monthly · renews monthly until canceled</p></div></div> : <p className="mt-4 text-sm text-slate-500">Complete the organization and plan steps to review checkout.</p>}
					{isActive ? <div className={`mt-5 rounded-xl border p-4 ${onboarding?.subscriptionStatus === "PAST_DUE" ? "border-amber-200 bg-amber-50" : "border-green-200 bg-green-50"}`}><p className={`font-bold ${onboarding?.subscriptionStatus === "PAST_DUE" ? "text-amber-900" : "text-green-900"}`}>{onboarding?.subscriptionStatus === "PAST_DUE" ? "Billing action required" : "Organization active"}</p><p className={`mt-1 text-sm ${onboarding?.subscriptionStatus === "PAST_DUE" ? "text-amber-800" : "text-green-800"}`}>{onboarding?.subscriptionStatus === "PAST_DUE" ? "Stripe reported a failed renewal. Your organization remains accessible while you update billing." : "Stripe confirmed the subscription through a signed webhook."}</p><div className="mt-4 flex flex-wrap gap-3"><Link to="/app" className="rounded-lg bg-navy-900 px-4 py-2 text-sm font-bold text-white">Go to dashboard</Link><button type="button" disabled={saving} onClick={openBillingPortal} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-bold text-slate-800">Manage billing</button></div></div> : <><p className="mt-5 text-sm text-slate-600">Checkout is hosted by Stripe. Returning from Checkout shows a pending state until Rally26 receives the signed subscription webhook.</p><button type="button" disabled={saving || !onboarding?.organization || !selectedPlan} onClick={checkout} className="mt-4 rounded-lg bg-green-600 px-5 py-2.5 text-sm font-bold text-white disabled:opacity-50">Continue to Stripe Checkout</button></>}
				</section>}
			</div>
		</main>
	);
}
