import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { useOrganizationParticipants } from "../onboarding/api";
import { useTeams } from "../teams/api";
import { useProducts, useStores, useTeamRoster } from "./api";
import {
	useArchiveAthleteStorefront,
	useAthleteStorefronts,
	useBuildAthleteStorefrontShareLink,
	useCreateAthleteStorefront,
	usePublishAthleteStorefront,
	useUnpublishAthleteStorefront,
	type AthleteStorefront,
} from "./athleteStorefrontApi";
import { createAthleteStorefrontSchema, type CreateAthleteStorefrontFormValues } from "./schema";

type StorefrontTransition = "publish" | "unpublish" | "archive";

export function AthleteStorefrontPanel({ organizationId }: { organizationId: string }) {
	const storefronts = useAthleteStorefronts(organizationId);
	const stores = useStores(organizationId);
	const teams = useTeams(organizationId);
	const participants = useOrganizationParticipants(organizationId);
	const createStorefront = useCreateAthleteStorefront(organizationId);
	const publish = usePublishAthleteStorefront(organizationId);
	const unpublish = useUnpublishAthleteStorefront(organizationId);
	const archive = useArchiveAthleteStorefront(organizationId);
	const [showForm, setShowForm] = useState(false);
	const [mutationError, setMutationError] = useState<string | null>(null);
	const [shareSlug, setShareSlug] = useState<string | null>(null);

	const {
		register,
		handleSubmit,
		reset,
		watch,
		formState: { errors, isSubmitting },
	} = useForm<CreateAthleteStorefrontFormValues>({
		resolver: zodResolver(createAthleteStorefrontSchema),
		defaultValues: { teamId: "", participantId: "", storeId: "", productIds: [], slug: "" },
	});
	const watchedTeamId = watch("teamId");
	const watchedStoreId = watch("storeId");
	const roster = useTeamRoster(organizationId, watchedTeamId || null);
	const storeProducts = useProducts(organizationId, watchedStoreId || "");
	const activeProducts = (storeProducts.data?.items ?? []).filter((product) => product.status === "ACTIVE");
	const activeStores = (stores.data?.items ?? []).filter((store) => store.status === "ACTIVE");

	const onSubmit = handleSubmit(async (values) => {
		setMutationError(null);
		try {
			await createStorefront.mutateAsync(values);
			reset({ teamId: "", participantId: "", storeId: "", productIds: [], slug: "" });
			setShowForm(false);
		} catch (error) {
			setMutationError(error instanceof ApiError ? error.message : "Could not create the athlete storefront.");
		}
	});

	function athleteLabel(participantId: string) {
		const participant = participants.data?.items.find((item) => item.id === participantId);
		return participant ? `${participant.firstName} ${participant.lastName}` : "Athlete";
	}
	function teamLabel(teamId: string | null) {
		if (!teamId) return null;
		return teams.data?.items.find((team) => team.id === teamId)?.name ?? null;
	}
	function storeLabel(storeId: string) {
		return stores.data?.items.find((store) => store.id === storeId)?.name ?? "Swag Shop";
	}

	async function transition(action: StorefrontTransition, storefrontId: string) {
		setMutationError(null);
		try {
			if (action === "publish") await publish.mutateAsync(storefrontId);
			else if (action === "unpublish") await unpublish.mutateAsync(storefrontId);
			else await archive.mutateAsync(storefrontId);
		} catch (error) {
			setMutationError(error instanceof ApiError ? error.message : "Could not update the storefront.");
		}
	}

	return (
		<section className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" aria-labelledby="athlete-storefronts-heading">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h3 id="athlete-storefronts-heading" className="font-heading text-base font-semibold text-navy">Athlete Storefronts</h3>
					<p className="text-sm text-slate-gray">A public, no-login storefront for one athlete with a stable link and QR code.</p>
				</div>
				<Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>{showForm ? "Cancel" : "Add storefront"}</Button>
			</div>
			{mutationError && <p role="alert" className="text-sm text-error-red">{mutationError}</p>}
			{showForm && (
				<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-4" noValidate aria-label="Create an athlete storefront">
					<div className="grid gap-3 md:grid-cols-2">
						<label className="flex flex-col gap-1 text-sm font-medium text-navy">
							Team
							<select {...register("teamId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
								<option value="">Select a team…</option>
								{teams.data?.items.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
							</select>
							{errors.teamId && <span role="alert" className="text-sm text-error-red">{errors.teamId.message}</span>}
						</label>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy">
							Athlete
							<select {...register("participantId")} disabled={!watchedTeamId} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
								<option value="">{watchedTeamId ? "Select an athlete…" : "Choose a team first"}</option>
								{roster.data?.map((participant) => <option key={participant.id} value={participant.id}>{participant.firstName} {participant.lastName}</option>)}
							</select>
							{errors.participantId && <span role="alert" className="text-sm text-error-red">{errors.participantId.message}</span>}
						</label>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy">
							Swag Shop
							<select {...register("storeId")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
								<option value="">Select a Swag Shop…</option>
								{activeStores.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}
							</select>
							{errors.storeId && <span role="alert" className="text-sm text-error-red">{errors.storeId.message}</span>}
						</label>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy">
							Public URL slug
							<input {...register("slug")} placeholder="maya-johnson" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
							{errors.slug && <span role="alert" className="text-sm text-error-red">{errors.slug.message}</span>}
						</label>
					</div>
					<fieldset className="flex flex-col gap-1">
						<legend className="text-sm font-medium text-navy">Approved products</legend>
						{!watchedStoreId && <p className="text-sm text-slate-gray">Choose a Swag Shop to see its active products.</p>}
						{watchedStoreId && activeProducts.length === 0 && <p className="text-sm text-slate-gray">This Swag Shop has no active products yet.</p>}
						{activeProducts.length > 0 && (
							<div className="grid gap-1 sm:grid-cols-2">
								{activeProducts.map((product) => (
									<label key={product.id} className="flex items-center gap-2 text-sm text-navy">
										<input type="checkbox" value={product.id} {...register("productIds")} /> {product.name}
									</label>
								))}
							</div>
						)}
						{errors.productIds && <span role="alert" className="text-sm text-error-red">{errors.productIds.message}</span>}
					</fieldset>
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { reset(); setShowForm(false); }}>Cancel</Button>
						<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create storefront"}</Button>
					</div>
				</form>
			)}
			{storefronts.isLoading && <LoadingState label="Loading athlete storefronts…" />}
			{storefronts.isError && <ErrorState message="Could not load athlete storefronts." onRetry={() => storefronts.refetch()} />}
			{storefronts.data && storefronts.data.items.length === 0 && !showForm && (
				<EmptyState title="No athlete storefronts yet" description="Create a storefront to let families buy Swag Shop products for one athlete, no Rally26 login required." />
			)}
			{storefronts.data && storefronts.data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Athlete storefronts">
					{storefronts.data.items.map((storefront) => (
						<AthleteStorefrontRow
							key={storefront.id}
							organizationId={organizationId}
							storefront={storefront}
							athleteName={athleteLabel(storefront.participantId)}
							teamName={teamLabel(storefront.teamId)}
							storeName={storeLabel(storefront.storeId)}
							onTransition={(action) => transition(action, storefront.id)}
							pending={publish.isPending || unpublish.isPending || archive.isPending}
							showShare={shareSlug === storefront.slug}
							onToggleShare={() => setShareSlug((current) => (current === storefront.slug ? null : storefront.slug))}
						/>
					))}
				</ul>
			)}
		</section>
	);
}

function AthleteStorefrontRow({
	organizationId,
	storefront,
	athleteName,
	teamName,
	storeName,
	onTransition,
	pending,
	showShare,
	onToggleShare,
}: {
	organizationId: string;
	storefront: AthleteStorefront;
	athleteName: string;
	teamName: string | null;
	storeName: string;
	onTransition: (action: StorefrontTransition) => void;
	pending: boolean;
	showShare: boolean;
	onToggleShare: () => void;
}) {
	const publicUrl = `${window.location.origin}/swag-shop/athlete/${storefront.slug}`;
	const buildShareLink = useBuildAthleteStorefrontShareLink(organizationId);
	const [copied, setCopied] = useState(false);

	async function copyLink() {
		await navigator.clipboard.writeText(publicUrl);
		setCopied(true);
		setTimeout(() => setCopied(false), 2000);
	}

	async function toggleShare() {
		const opening = !showShare;
		onToggleShare();
		if (opening && !buildShareLink.data) {
			await buildShareLink.mutateAsync(publicUrl);
		}
	}

	return (
		<li className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div className="min-w-0 flex-1">
					<p className="break-words font-medium text-navy">
						{athleteName}
						<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">{storefront.status}</span>
					</p>
					<p className="text-sm text-slate-gray">/swag-shop/athlete/{storefront.slug} · {storeName}{teamName ? ` · ${teamName}` : ""}</p>
				</div>
				<div className="flex shrink-0 flex-wrap gap-2">
					{storefront.status === "DRAFT" && <Button type="button" variant="secondary" onClick={() => onTransition("publish")} disabled={pending}>Publish</Button>}
					{storefront.status === "PUBLISHED" && <Button type="button" variant="secondary" onClick={() => onTransition("unpublish")} disabled={pending}>Unpublish</Button>}
					{storefront.status !== "ARCHIVED" && <Button type="button" variant="secondary" onClick={() => onTransition("archive")} disabled={pending}>Archive</Button>}
					{storefront.status === "PUBLISHED" && (
						<>
							<Button type="button" variant="secondary" onClick={copyLink}>{copied ? "Copied!" : "Copy link"}</Button>
							<Button type="button" variant="secondary" onClick={toggleShare}>{showShare ? "Hide QR" : "Show QR"}</Button>
						</>
					)}
				</div>
			</div>
			{showShare && (
				<div className="mt-3 flex flex-col items-start gap-2 border-t border-slate-gray/20 pt-3">
					{buildShareLink.isPending && <p className="text-sm text-slate-gray">Generating QR code…</p>}
					{buildShareLink.isError && <p role="alert" className="text-sm text-error-red">Could not generate a QR code.</p>}
					{buildShareLink.data && <img src={buildShareLink.data.qrDataUri} alt={`QR code for ${publicUrl}`} className="h-32 w-32 rounded-md border border-slate-gray/20" />}
					<p className="break-all text-xs text-slate-gray">{publicUrl}</p>
				</div>
			)}
		</li>
	);
}
