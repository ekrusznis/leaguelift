import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { TournamentRoleAssignmentsSection } from "../authorization/TournamentRoleAssignmentsSection";
import { useArchiveTournament, useCreateTournament, useTournaments } from "./api";
import { createTournamentSchema, type CreateTournamentFormValues } from "./schema";

function formatDateRange(startDate: string | null, endDate: string | null): string {
	if (!startDate && !endDate) return "";
	if (startDate && !endDate) return startDate;
	if (!startDate && endDate) return `– ${endDate}`;
	return `${startDate} – ${endDate}`;
}

export function TournamentList({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useTournaments(organizationId);
	const createTournament = useCreateTournament(organizationId);
	const archiveTournament = useArchiveTournament(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [expandedTournamentId, setExpandedTournamentId] = useState<string | null>(null);

	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateTournamentFormValues>({
		resolver: zodResolver(createTournamentSchema),
		defaultValues: { name: "", sport: "", startDate: "", endDate: "", location: "", contactEmail: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await createTournament.mutateAsync(values);
		reset();
		setShowForm(false);
	});

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{data ? `${data.totalElements} tournament${data.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Add tournament"}
				</Button>
			</div>

			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Create a tournament">
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-name" className="text-sm font-medium text-navy">
								Name <span aria-hidden>*</span>
							</label>
							<input
								id="tournament-name"
								type="text"
								{...register("name")}
								aria-invalid={!!errors.name}
								aria-describedby={errors.name ? "tournament-name-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.name && (
								<p id="tournament-name-error" role="alert" className="text-sm text-error-red">
									{errors.name.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-sport" className="text-sm font-medium text-navy">
								Sport
							</label>
							<input
								id="tournament-sport"
								type="text"
								{...register("sport")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-start" className="text-sm font-medium text-navy">
								Start date
							</label>
							<input
								id="tournament-start"
								type="date"
								{...register("startDate")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-end" className="text-sm font-medium text-navy">
								End date
							</label>
							<input
								id="tournament-end"
								type="date"
								{...register("endDate")}
								aria-invalid={!!errors.endDate}
								aria-describedby={errors.endDate ? "tournament-end-error" : undefined}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.endDate && (
								<p id="tournament-end-error" role="alert" className="text-sm text-error-red">
									{errors.endDate.message}
								</p>
							)}
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="tournament-location" className="text-sm font-medium text-navy">
								Location
							</label>
							<input
								id="tournament-location"
								type="text"
								{...register("location")}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
					</div>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create tournament"}
						</Button>
					</div>
				</form>
			)}

			{isLoading && <LoadingState label="Loading tournaments…" />}
			{isError && <ErrorState message="Could not load tournaments." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No tournaments yet" description="Add your first tournament to get started." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Tournaments">
					{data.items.map((tournament) => (
						<li key={tournament.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy">{tournament.name}</p>
									<p className="text-sm text-slate-gray">
										{[tournament.sport, formatDateRange(tournament.startDate, tournament.endDate), tournament.location]
											.filter(Boolean)
											.join(" · ")}
									</p>
								</div>
								<div className="flex shrink-0 items-center gap-2">
									<Button
										type="button"
										variant="secondary"
										onClick={() => setExpandedTournamentId(expandedTournamentId === tournament.id ? null : tournament.id)}
									>
										{expandedTournamentId === tournament.id ? "Hide access" : "Manage access"}
									</Button>
									<Button
										type="button"
										variant="secondary"
										onClick={() => archiveTournament.mutate(tournament.id)}
										disabled={archiveTournament.isPending}
									>
										Archive
									</Button>
								</div>
							</div>
							{expandedTournamentId === tournament.id && (
								<div className="mt-3">
									<TournamentRoleAssignmentsSection organizationId={organizationId} tournamentId={tournament.id} />
								</div>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
