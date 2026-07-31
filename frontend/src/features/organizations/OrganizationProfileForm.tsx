import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { useUpdateOrganizationProfile } from "./api";
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
		},
	});
	const updateProfile = useUpdateOrganizationProfile(organization.id);
	const selectedSports = watch("sports");

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
				<label htmlFor="profile-name" className="text-sm font-medium text-navy">
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
				<label htmlFor="profile-type" className="text-sm font-medium text-navy">
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
				<legend className="text-sm font-medium text-navy">Sports offered</legend>
				<div className="flex flex-wrap gap-2">
					{COMMON_SPORTS.map((sport) => {
						const checked = selectedSports.includes(sport);
						return (
							<label
								key={sport}
								className={`min-h-11 cursor-pointer rounded-full border px-3 py-2 text-sm ${
									checked
										? "border-victory-green bg-victory-green/10 text-navy"
										: "border-slate-gray/30 text-slate-gray"
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
				<label htmlFor="profile-contact-email" className="text-sm font-medium text-navy">
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
				<label htmlFor="profile-contact-phone" className="text-sm font-medium text-navy">
					Contact phone (optional)
				</label>
				<input
					id="profile-contact-phone"
					type="tel"
					{...register("contactPhone")}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
			</div>

			{updateProfile.isError && (
				<p role="alert" className="text-sm text-error-red">
					Could not save changes. Please try again.
				</p>
			)}
			{updateProfile.isSuccess && !isDirty && (
				<p role="status" className="text-sm text-victory-green">
					Saved.
				</p>
			)}

			<Button type="submit" disabled={isSubmitting} className="self-start">
				{isSubmitting ? "Saving…" : "Save profile"}
			</Button>
		</form>
	);
}
