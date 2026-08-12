import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { useTimezoneSuggestion, useUpdateOrganizationProfile } from "./api";
import { COMMON_SPORTS, updateOrganizationProfileSchema, type UpdateOrganizationProfileFormValues } from "./schema";
import { ORGANIZATION_TYPES, type Organization } from "./types";

const TYPE_LABELS: Record<(typeof ORGANIZATION_TYPES)[number], string> = {
	RECREATIONAL_LEAGUE: "Recreational league",
	TRAVEL_CLUB: "Travel club",
	INDIVIDUAL_TEAM: "Individual team",
	TOURNAMENT_OPERATOR: "Tournament operator",
	BOOSTER_ORGANIZATION: "Booster organization",
	MULTISPORT_FACILITY: "Multisport facility",
	COMMUNITY_PROGRAM: "Community program",
	OTHER: "Other",
};

export function OrganizationProfileForm({ organization }: { organization: Organization }) {
	const {
		register,
		handleSubmit,
		watch,
		setValue,
		formState: { errors, isSubmitting, isDirty },
	} = useForm<UpdateOrganizationProfileFormValues>({
		resolver: zodResolver(updateOrganizationProfileSchema),
		defaultValues: {
			name: organization.name,
			organizationType: organization.organizationType,
			sports: organization.sports,
			contactEmail: organization.contactEmail ?? "",
			contactPhone: organization.contactPhone ?? "",
			addressLine1: organization.addressLine1 ?? "",
			addressLine2: organization.addressLine2 ?? "",
			addressCity: organization.addressCity ?? "",
			addressState: organization.addressState ?? "",
			addressPostalCode: organization.addressPostalCode ?? "",
			addressCountry: organization.addressCountry ?? "",
			timezone: organization.timezone ?? "",
			zelleHandle: organization.zelleHandle ?? "",
		},
	});
	const updateProfile = useUpdateOrganizationProfile(organization.id);
	const selectedSports = watch("sports");
	const currentTimezone = watch("timezone");
	const suggestion = useTimezoneSuggestion(organization.id, organization.timezone == null);
	const suggestedTimezone = suggestion.data?.timezone ?? null;
	const showSuggestionBanner = !!suggestedTimezone && currentTimezone !== suggestedTimezone;

	const toggleSport = (sport: string) => {
		const next = selectedSports.includes(sport)
			? selectedSports.filter((s) => s !== sport)
			: [...selectedSports, sport];
		setValue("sports", next, { shouldDirty: true, shouldValidate: true });
	};

	const onSubmit = handleSubmit((values) => {
		updateProfile.mutate(values);
	});

	return (
		<form onSubmit={onSubmit} className="flex max-w-lg flex-col gap-4" noValidate>
			<div className="flex flex-col gap-1">
				<label htmlFor="profile-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Organization name
				</label>
				<input
					id="profile-name"
					type="text"
					{...register("name")}
					aria-invalid={!!errors.name}
					aria-describedby={errors.name ? "profile-name-error" : undefined}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
				{errors.name && (
					<p id="profile-name-error" role="alert" className="text-sm text-error-red">
						{errors.name.message}
					</p>
				)}
			</div>

			<div className="flex flex-col gap-1">
				<label htmlFor="profile-type" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Organization type
				</label>
				<select
					id="profile-type"
					{...register("organizationType")}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				>
					{ORGANIZATION_TYPES.map((type) => (
						<option key={type} value={type}>
							{TYPE_LABELS[type]}
						</option>
					))}
				</select>
			</div>

			<fieldset className="flex flex-col gap-2">
				<legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Sports offered</legend>
				<div className="flex flex-wrap gap-2">
					{COMMON_SPORTS.map((sport) => {
						const checked = selectedSports.includes(sport);
						return (
							<label
								key={sport}
								className={`min-h-11 cursor-pointer rounded-full border px-3 py-2 text-sm ${
									checked
										? "border-victory-green bg-victory-green/10 text-navy dark:text-[#f8fafc]"
										: "border-slate-gray/30 text-slate-gray dark:text-[#cbd5e1]"
								}`}
							>
								<input
									type="checkbox"
									className="sr-only"
									checked={checked}
									onChange={() => toggleSport(sport)}
								/>
								{sport}
							</label>
						);
					})}
				</div>
				{errors.sports && (
					<p role="alert" className="text-sm text-error-red">
						{errors.sports.message}
					</p>
				)}
			</fieldset>

			<div className="flex flex-col gap-1">
				<label htmlFor="profile-contact-email" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Contact email
				</label>
				<input
					id="profile-contact-email"
					type="email"
					{...register("contactEmail")}
					aria-invalid={!!errors.contactEmail}
					aria-describedby={errors.contactEmail ? "profile-contact-email-error" : undefined}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
				{errors.contactEmail && (
					<p id="profile-contact-email-error" role="alert" className="text-sm text-error-red">
						{errors.contactEmail.message}
					</p>
				)}
			</div>

			<div className="flex flex-col gap-1">
				<label htmlFor="profile-contact-phone" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Contact phone (optional)
				</label>
				<input
					id="profile-contact-phone"
					type="tel"
					{...register("contactPhone")}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
			</div>

			<fieldset className="flex flex-col gap-3">
				<legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Address (optional)</legend>
				<div className="flex flex-col gap-1">
					<label htmlFor="profile-address-line1" className="text-sm text-slate-gray dark:text-[#cbd5e1]">
						Street address
					</label>
					<input
						id="profile-address-line1"
						type="text"
						{...register("addressLine1")}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="profile-address-line2" className="text-sm text-slate-gray dark:text-[#cbd5e1]">
						Street address line 2
					</label>
					<input
						id="profile-address-line2"
						type="text"
						{...register("addressLine2")}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</div>
				<div className="flex flex-wrap gap-3">
					<div className="flex flex-col gap-1">
						<label htmlFor="profile-address-city" className="text-sm text-slate-gray dark:text-[#cbd5e1]">
							City
						</label>
						<input
							id="profile-address-city"
							type="text"
							{...register("addressCity")}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="profile-address-state" className="text-sm text-slate-gray dark:text-[#cbd5e1]">
							State / province
						</label>
						<input
							id="profile-address-state"
							type="text"
							placeholder="e.g. TX"
							{...register("addressState")}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="profile-address-postal-code" className="text-sm text-slate-gray dark:text-[#cbd5e1]">
							Postal code
						</label>
						<input
							id="profile-address-postal-code"
							type="text"
							{...register("addressPostalCode")}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="profile-address-country" className="text-sm text-slate-gray dark:text-[#cbd5e1]">
							Country
						</label>
						<input
							id="profile-address-country"
							type="text"
							placeholder="e.g. US"
							{...register("addressCountry")}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</div>
				</div>
			</fieldset>

			<div className="flex flex-col gap-1">
				<label htmlFor="profile-zelle-handle" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Zelle handle (optional)
				</label>
				<input
					id="profile-zelle-handle"
					type="text"
					placeholder="e.g. payments@yourclub.org or (555) 555-0100"
					{...register("zelleHandle")}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
				<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">
					Shown to guardians as payment instructions on the household fee-payment screen. Zelle payments still settle
					directly to your organization — staff record them manually once received.
				</p>
			</div>

			<div className="flex flex-col gap-1">
				<label htmlFor="profile-timezone" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Timezone
				</label>
				<input
					id="profile-timezone"
					type="text"
					placeholder="e.g. America/New_York"
					{...register("timezone")}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
				<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">
					IANA time zone id. Used as the default for new events, teams, and tournaments that don't set their own
					override.
				</p>
				{showSuggestionBanner && (
					<div
						role="status"
						className="mt-1 flex flex-wrap items-center gap-2 rounded-md border border-championship-gold/40 bg-championship-gold/10 px-3 py-2 text-sm text-navy dark:text-[#f8fafc]"
					>
						<span>
							Suggested timezone based on your address: <strong>{suggestedTimezone}</strong>
						</span>
						<Button
							type="button"
							variant="secondary"
							onClick={() =>
								setValue("timezone", suggestedTimezone ?? "", { shouldDirty: true, shouldValidate: true })
							}
						>
							Use this
						</Button>
					</div>
				)}
			</div>

			{updateProfile.isError && (
				<p role="alert" className="text-sm text-error-red">
					Could not save changes. Please try again.
				</p>
			)}
			{updateProfile.isSuccess && !isDirty && (
				<p role="status" className="text-sm text-success-700 dark:text-success-400">
					Saved.
				</p>
			)}

			<Button type="submit" disabled={isSubmitting} className="self-start">
				{isSubmitting ? "Saving…" : "Save profile"}
			</Button>
		</form>
	);
}
