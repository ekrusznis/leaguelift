import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { ApiError } from "../../lib/apiError";
import { useCreateOrganization } from "./api";
import { createOrganizationSchema, type CreateOrganizationFormValues } from "./schema";
import { ORGANIZATION_TYPES } from "./types";

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

export function CreateOrganizationForm({ onCreated }: { onCreated?: () => void }) {
	const {
		register,
		handleSubmit,
		setError,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateOrganizationFormValues>({
		resolver: zodResolver(createOrganizationSchema),
		defaultValues: { name: "", slug: "", organizationType: "RECREATIONAL_LEAGUE" },
	});
	const createOrganization = useCreateOrganization();

	const onSubmit = handleSubmit(async (values) => {
		try {
			await createOrganization.mutateAsync(values);
			reset();
			onCreated?.();
		} catch (error) {
			if (error instanceof ApiError && error.code === "ORGANIZATION_SLUG_TAKEN") {
				setError("slug", { message: "This slug is already in use." });
				return;
			}
			setError("root", { message: "Could not create the organization. Please try again." });
		}
	});

	return (
		<form onSubmit={onSubmit} className="flex max-w-md flex-col gap-4" noValidate>
			<div className="flex flex-col gap-1">
				<label htmlFor="org-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Organization name
				</label>
				<input
					id="org-name"
					type="text"
					{...register("name")}
					aria-invalid={!!errors.name}
					aria-describedby={errors.name ? "org-name-error" : undefined}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
				{errors.name && (
					<p id="org-name-error" role="alert" className="text-sm text-error-red">
						{errors.name.message}
					</p>
				)}
			</div>

			<div className="flex flex-col gap-1">
				<label htmlFor="org-slug" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Public URL slug
				</label>
				<input
					id="org-slug"
					type="text"
					{...register("slug")}
					aria-invalid={!!errors.slug}
					aria-describedby={errors.slug ? "org-slug-error" : undefined}
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
				/>
				{errors.slug && (
					<p id="org-slug-error" role="alert" className="text-sm text-error-red">
						{errors.slug.message}
					</p>
				)}
			</div>

			<div className="flex flex-col gap-1">
				<label htmlFor="org-type" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Organization type
				</label>
				<select
					id="org-type"
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

			{errors.root && (
				<p role="alert" className="text-sm text-error-red">
					{errors.root.message}
				</p>
			)}

			<Button type="submit" disabled={isSubmitting}>
				{isSubmitting ? "Creating…" : "Create organization"}
			</Button>
		</form>
	);
}
