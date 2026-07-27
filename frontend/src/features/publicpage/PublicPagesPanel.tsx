import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useTeams } from "../teams/api";
import { useTournaments } from "../tournaments/api";
import { useCreatePage, usePages, usePublishPage, useUnpublishPage } from "./api";
import { createPublicPageSchema, type CreatePublicPageFormValues } from "./schema";
import type { PageType, PublicPage } from "./types";

const STATUS_LABELS: Record<string, string> = {
	DRAFT: "Draft",
	PUBLISHED: "Published",
	ARCHIVED: "Archived",
};

const STATUS_COLORS: Record<string, string> = {
	DRAFT: "text-slate-gray bg-slate-gray/10",
	PUBLISHED: "text-victory-green bg-victory-green/10",
	ARCHIVED: "text-error-red bg-error-red/10",
};

const TYPE_LABELS: Record<PageType, string> = {
	ORGANIZATION: "Organization",
	TEAM: "Team",
	TOURNAMENT: "Tournament",
};

function PageRow({
	publicPage,
	entityName,
	organizationId,
}: {
	publicPage: PublicPage;
	entityName: string;
	organizationId: string;
}) {
	const publishPage = usePublishPage(organizationId);
	const unpublishPage = useUnpublishPage(organizationId);

	return (
		<li className="flex items-center justify-between rounded-lg border border-slate-gray/20 bg-pure-white p-3 gap-3 flex-wrap">
			<div className="flex flex-col gap-0.5 min-w-0">
				<p className="font-medium text-navy truncate">{publicPage.title}</p>
				<p className="text-sm text-slate-gray">
					{TYPE_LABELS[publicPage.pageType]} · {entityName} ·{" "}
					<span className="font-mono">/{publicPage.slug}</span>
				</p>
			</div>
			<div className="flex items-center gap-2 flex-shrink-0">
				<span
					className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[publicPage.status] ?? ""}`}
				>
					{STATUS_LABELS[publicPage.status] ?? publicPage.status}
				</span>
				{publicPage.status === "DRAFT" && (
					<Button
						type="button"
						onClick={() => publishPage.mutate(publicPage.id)}
						disabled={publishPage.isPending}
					>
						Publish
					</Button>
				)}
				{publicPage.status === "PUBLISHED" && (
					<Button
						type="button"
						variant="secondary"
						onClick={() => unpublishPage.mutate(publicPage.id)}
						disabled={unpublishPage.isPending}
					>
						Unpublish
					</Button>
				)}
			</div>
		</li>
	);
}

export function PublicPagesPanel({
	organizationId,
	organizationName,
}: {
	organizationId: string;
	organizationName: string;
}) {
	const { data: pagesData, isLoading: pagesLoading, isError: pagesError, refetch } = usePages(organizationId);
	const { data: teamsData } = useTeams(organizationId);
	const { data: tournamentsData } = useTournaments(organizationId);
	const createPage = useCreatePage(organizationId);
	const [showForm, setShowForm] = useState(false);

	const {
		register,
		handleSubmit,
		watch,
		reset,
		setValue,
		formState: { errors, isSubmitting },
	} = useForm<CreatePublicPageFormValues>({
		resolver: zodResolver(createPublicPageSchema),
		defaultValues: {
			pageType: "ORGANIZATION",
			entityId: organizationId,
			slug: "",
			title: "",
			summary: "",
		},
	});

	const selectedType = watch("pageType");

	const onPageTypeChange = (type: PageType) => {
		setValue("pageType", type);
		if (type === "ORGANIZATION") {
			setValue("entityId", organizationId);
		} else {
			setValue("entityId", "");
		}
	};

	const onSubmit = handleSubmit(async (values) => {
		await createPage.mutateAsync(values);
		reset({ pageType: "ORGANIZATION", entityId: organizationId, slug: "", title: "", summary: "" });
		setShowForm(false);
	});

	const entityNameMap = new Map<string, string>([
		[organizationId, organizationName],
		...(teamsData?.items.map((t) => [t.id, t.name] as [string, string]) ?? []),
		...(tournamentsData?.items.map((t) => [t.id, t.name] as [string, string]) ?? []),
	]);

	const existingEntityIds = new Set(pagesData?.items.map((p) => p.entityId) ?? []);

	const availableTeams = teamsData?.items.filter((t) => t.status === "ACTIVE" && !existingEntityIds.has(t.id)) ?? [];
	const availableTournaments =
		tournamentsData?.items.filter((t) => t.status === "ACTIVE" && !existingEntityIds.has(t.id)) ?? [];
	const orgPageExists = existingEntityIds.has(organizationId);

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<span className="text-sm text-slate-gray">
					{pagesData ? `${pagesData.totalElements} page${pagesData.totalElements !== 1 ? "s" : ""}` : ""}
				</span>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Create page"}
				</Button>
			</div>

			{showForm && (
				<form
					onSubmit={onSubmit}
					className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4"
					noValidate
					aria-label="Create a public page"
				>
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label className="text-sm font-medium text-navy">Page type</label>
							<select
								value={selectedType}
								onChange={(e) => onPageTypeChange(e.target.value as PageType)}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							>
								{!orgPageExists && <option value="ORGANIZATION">Organization</option>}
								{availableTeams.length > 0 && <option value="TEAM">Team</option>}
								{availableTournaments.length > 0 && <option value="TOURNAMENT">Tournament</option>}
							</select>
						</div>

						{selectedType === "TEAM" && (
							<div className="flex flex-col gap-1">
								<label htmlFor="page-entity" className="text-sm font-medium text-navy">
									Team
								</label>
								<select
									id="page-entity"
									{...register("entityId")}
									className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
								>
									<option value="">Select a team…</option>
									{availableTeams.map((t) => (
										<option key={t.id} value={t.id}>
											{t.name}
										</option>
									))}
								</select>
								{errors.entityId && (
									<p role="alert" className="text-sm text-error-red">
										{errors.entityId.message}
									</p>
								)}
							</div>
						)}

						{selectedType === "TOURNAMENT" && (
							<div className="flex flex-col gap-1">
								<label htmlFor="page-entity" className="text-sm font-medium text-navy">
									Tournament
								</label>
								<select
									id="page-entity"
									{...register("entityId")}
									className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
								>
									<option value="">Select a tournament…</option>
									{availableTournaments.map((t) => (
										<option key={t.id} value={t.id}>
											{t.name}
										</option>
									))}
								</select>
								{errors.entityId && (
									<p role="alert" className="text-sm text-error-red">
										{errors.entityId.message}
									</p>
								)}
							</div>
						)}

						<div className="flex flex-col gap-1">
							<label htmlFor="page-title" className="text-sm font-medium text-navy">
								Title <span aria-hidden>*</span>
							</label>
							<input
								id="page-title"
								type="text"
								{...register("title")}
								aria-invalid={!!errors.title}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
							{errors.title && (
								<p role="alert" className="text-sm text-error-red">
									{errors.title.message}
								</p>
							)}
						</div>

						<div className="flex flex-col gap-1">
							<label htmlFor="page-slug" className="text-sm font-medium text-navy">
								Slug <span aria-hidden>*</span>
							</label>
							<div className="flex items-center gap-1">
								<span className="text-slate-gray">/</span>
								<input
									id="page-slug"
									type="text"
									placeholder="my-org"
									{...register("slug")}
									aria-invalid={!!errors.slug}
									className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2 font-mono"
								/>
							</div>
							{errors.slug && (
								<p role="alert" className="text-sm text-error-red">
									{errors.slug.message}
								</p>
							)}
						</div>

						<div className="flex flex-col gap-1 w-full">
							<label htmlFor="page-summary" className="text-sm font-medium text-navy">
								Summary
							</label>
							<textarea
								id="page-summary"
								rows={3}
								{...register("summary")}
								className="rounded-md border border-slate-gray/30 px-3 py-2 resize-none"
							/>
						</div>
					</div>

					<div className="flex justify-end gap-2">
						<Button
							type="button"
							variant="secondary"
							onClick={() => {
								reset({ pageType: "ORGANIZATION", entityId: organizationId, slug: "", title: "", summary: "" });
								setShowForm(false);
							}}
						>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? "Creating…" : "Create page"}
						</Button>
					</div>
				</form>
			)}

			{pagesLoading && <LoadingState label="Loading pages…" />}
			{pagesError && <ErrorState message="Could not load pages." onRetry={() => refetch()} />}
			{pagesData && pagesData.items.length === 0 && !showForm && (
				<EmptyState
					title="No public pages yet"
					description="Create a page for your organization, teams, or tournaments."
				/>
			)}
			{pagesData && pagesData.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Public pages">
					{pagesData.items.map((p) => (
						<PageRow
							key={p.id}
							publicPage={p}
							entityName={entityNameMap.get(p.entityId) ?? p.entityId}
							organizationId={organizationId}
						/>
					))}
				</ul>
			)}
		</div>
	);
}
