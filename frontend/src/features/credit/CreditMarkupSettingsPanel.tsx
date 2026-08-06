import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePrintifyBlueprints } from "../store/api";
import {
	useApproveCreditTransfer,
	useDeleteMarkupRule,
	useMarkupRules,
	useOrganizationCreditSettings,
	usePendingCreditTransfers,
	useRejectCreditTransfer,
	useUpdateOrganizationCreditSettings,
	useUpsertMarkupRule,
} from "./api";
import type { OrganizationCreditSettings } from "./types";
import { formatMoneyMinorUnits } from "../../lib/money";

/**
 * Phase 23 (DESIGN-DOC.md section 13/14.1): org-owner controls for the family
 * credit program (default grant %, expiration/rollover, P2P transfer toggle)
 * and the Swag Shop markup rule engine (org-wide default + per-blueprint
 * overrides). Both settle to the same `membershipService.requireManagerRole`
 * gate on the backend, so this panel is shown wherever `canManageOrganization`
 * already gates the rest of Settings.
 */
export function CreditMarkupSettingsPanel({ organizationId }: { organizationId: string }) {
	return (
		<div className="flex flex-col gap-6">
			<CreditSettingsForm organizationId={organizationId} />
			<PendingTransfersPanel organizationId={organizationId} />
			<MarkupRulesPanel organizationId={organizationId} />
		</div>
	);
}

/**
 * A guardian-initiated P2P transfer only moves value once approved here — the
 * sender's balance is held (not yet delivered to the recipient) from the
 * moment they request it. Always rendered, not just when P2P is enabled,
 * since a request could still be pending from before an owner disabled it.
 */
function PendingTransfersPanel({ organizationId }: { organizationId: string }) {
	const pending = usePendingCreditTransfers(organizationId);
	const approve = useApproveCreditTransfer(organizationId);
	const reject = useRejectCreditTransfer(organizationId);
	const [rejectingId, setRejectingId] = useState<string | null>(null);
	const [rejectNote, setRejectNote] = useState("");
	const [error, setError] = useState<string | null>(null);

	if (pending.isLoading) return <LoadingState label="Loading pending credit transfers…" />;
	if (pending.isError || !pending.data) return <ErrorState message="Could not load pending credit transfers." onRetry={() => pending.refetch()} />;

	async function submitReject(transferId: string) {
		setError(null);
		try {
			await reject.mutateAsync({ transferId, reviewNote: rejectNote });
			setRejectingId(null);
			setRejectNote("");
		} catch {
			setError("Could not reject this transfer. Please try again.");
		}
	}

	return (
		<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<h3 className="font-heading text-base font-semibold text-navy">Pending Credit Transfers</h3>
			<p className="mt-1 text-sm text-slate-gray">Family-to-family credit transfers are held until approved here — nothing moves without your review.</p>
			{pending.data.length === 0 ? (
				<p className="mt-4 text-sm text-slate-gray">No pending transfer requests.</p>
			) : (
				<ul className="mt-4 flex flex-col gap-3">
					{pending.data.map((item) => (
						<li key={item.id} className="rounded-md bg-ice-white p-3 text-sm">
							<div className="flex flex-wrap items-center justify-between gap-2">
								<span>
									<span className="font-medium text-navy">{formatMoneyMinorUnits(item.amountMinor, "USD")}</span>{" "}
									from household <code className="text-xs">{item.fromHouseholdId.slice(0, 8)}</code> to <code className="text-xs">{item.toHouseholdId.slice(0, 8)}</code>
								</span>
								<span className="flex items-center gap-2">
									<Button type="button" disabled={approve.isPending} onClick={() => approve.mutate(item.id)}>Approve</Button>
									<Button type="button" variant="secondary" disabled={reject.isPending} onClick={() => setRejectingId(rejectingId === item.id ? null : item.id)}>Reject</Button>
								</span>
							</div>
							{rejectingId === item.id && (
								<div className="mt-3 flex flex-col gap-2 border-t border-slate-200 pt-3">
									<input
										placeholder="Optional reason for the family"
										value={rejectNote}
										onChange={(event) => setRejectNote(event.target.value)}
										className="min-h-11 rounded-md border border-slate-300 px-3 py-2 text-sm"
									/>
									<Button type="button" variant="secondary" disabled={reject.isPending} onClick={() => submitReject(item.id)}>
										{reject.isPending ? "Rejecting…" : "Confirm reject"}
									</Button>
								</div>
							)}
						</li>
					))}
				</ul>
			)}
			{error && <p role="alert" className="mt-2 text-sm text-error-red">{error}</p>}
		</section>
	);
}

function CreditSettingsForm({ organizationId }: { organizationId: string }) {
	const settings = useOrganizationCreditSettings(organizationId);
	const updateSettings = useUpdateOrganizationCreditSettings(organizationId);
	const [form, setForm] = useState<OrganizationCreditSettings | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);

	useEffect(() => {
		if (settings.data && !form) setForm(settings.data);
	}, [settings.data]);

	if (settings.isLoading) return <LoadingState label="Loading credit settings…" />;
	if (settings.isError || !settings.data) return <ErrorState message="Could not load credit settings." onRetry={() => settings.refetch()} />;
	const current = form ?? settings.data;

	async function onSubmit(event: React.FormEvent) {
		event.preventDefault();
		setError(null);
		setSaved(false);
		if (current.expirationPolicy === "EXPIRES" && !current.expirationMonths) {
			setError("Enter how many months until credit expires.");
			return;
		}
		try {
			await updateSettings.mutateAsync(current);
			setSaved(true);
		} catch {
			setError("Could not save credit settings. Please try again.");
		}
	}

	return (
		<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<h3 className="font-heading text-base font-semibold text-navy">Family Credit</h3>
			<p className="mt-1 text-sm text-slate-gray">Control what percentage of a household-attributed contribution becomes family credit, whether it expires, and whether families can send credit to each other.</p>
			<form onSubmit={onSubmit} className="mt-4 flex flex-col gap-4" noValidate>
				<label className="flex max-w-xs flex-col gap-1 text-sm font-medium text-navy">
					Default credit percent (%)
					<input
						type="number"
						min="0"
						max="100"
						step="0.01"
						value={current.defaultCreditPercent / 100}
						onChange={(event) => setForm({ ...current, defaultCreditPercent: Math.round(Number(event.target.value) * 100) })}
						className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal"
					/>
				</label>
				<label className="flex max-w-xs flex-col gap-1 text-sm font-medium text-navy">
					Expiration policy
					<select
						value={current.expirationPolicy}
						onChange={(event) => setForm({ ...current, expirationPolicy: event.target.value as OrganizationCreditSettings["expirationPolicy"] })}
						className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal"
					>
						<option value="ROLLOVER">Never expires (rolls over)</option>
						<option value="EXPIRES">Expires after a fixed window</option>
					</select>
				</label>
				{current.expirationPolicy === "EXPIRES" && (
					<label className="flex max-w-xs flex-col gap-1 text-sm font-medium text-navy">
						Expires after (months)
						<input
							type="number"
							min="1"
							value={current.expirationMonths ?? ""}
							onChange={(event) => setForm({ ...current, expirationMonths: event.target.value ? Number(event.target.value) : null })}
							className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal"
						/>
					</label>
				)}
				<label className="flex items-center gap-2 text-sm font-medium text-navy">
					<input
						type="checkbox"
						checked={current.p2pTransferEnabled}
						onChange={(event) => setForm({ ...current, p2pTransferEnabled: event.target.checked })}
						className="h-4 w-4"
					/>
					Allow families to transfer credit to each other
				</label>
				{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
				{saved && !error && <p className="text-sm text-green-600">Saved.</p>}
				<div>
					<Button type="submit" disabled={updateSettings.isPending}>{updateSettings.isPending ? "Saving…" : "Save credit settings"}</Button>
				</div>
			</form>
		</section>
	);
}

function MarkupRulesPanel({ organizationId }: { organizationId: string }) {
	const rules = useMarkupRules(organizationId);
	const blueprints = usePrintifyBlueprints(organizationId);
	const upsertRule = useUpsertMarkupRule(organizationId);
	const deleteRule = useDeleteMarkupRule(organizationId);
	const [printifyBlueprintId, setPrintifyBlueprintId] = useState<string>("");
	const [markupType, setMarkupType] = useState<"PERCENTAGE" | "FLAT">("PERCENTAGE");
	const [markupValue, setMarkupValue] = useState("");
	const [error, setError] = useState<string | null>(null);

	if (rules.isLoading) return <LoadingState label="Loading markup rules…" />;
	if (rules.isError || !rules.data) return <ErrorState message="Could not load markup rules." onRetry={() => rules.refetch()} />;

	const defaultRule = rules.data.find((rule) => rule.printifyBlueprintId === null);
	const overrideRules = rules.data.filter((rule) => rule.printifyBlueprintId !== null);
	const blueprintTitle = (id: number) => blueprints.data?.find((blueprint) => blueprint.id === id)?.title ?? `Blueprint #${id}`;

	async function onSubmit(event: React.FormEvent) {
		event.preventDefault();
		setError(null);
		const value = markupType === "PERCENTAGE" ? Math.round(Number(markupValue) * 100) : Math.round(Number(markupValue) * 100);
		if (!markupValue || value <= 0) {
			setError("Enter a valid markup value.");
			return;
		}
		try {
			await upsertRule.mutateAsync({
				printifyBlueprintId: printifyBlueprintId ? Number(printifyBlueprintId) : null,
				markupType,
				markupValue: value,
			});
			setPrintifyBlueprintId("");
			setMarkupValue("");
		} catch {
			setError("Could not save markup rule. Please try again.");
		}
	}

	return (
		<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<h3 className="font-heading text-base font-semibold text-navy">Swag Shop Markup</h3>
			<p className="mt-1 text-sm text-slate-gray">Set the org-wide default markup applied over Printify's cost, plus optional overrides for specific apparel types. Prices left blank when adding a variant are computed from these rules.</p>

			<div className="mt-4 flex flex-col gap-2 text-sm">
				<div className="flex items-center justify-between rounded-md bg-ice-white p-3">
					<span className="font-medium text-navy">Org-wide default</span>
					<span className="flex items-center gap-3">
						<span>{defaultRule ? formatMarkup(defaultRule.markupType, defaultRule.markupValue) : "40% (system default)"}</span>
						{defaultRule && (
							<Button type="button" variant="secondary" disabled={deleteRule.isPending} onClick={() => deleteRule.mutate(defaultRule.id)}>Remove</Button>
						)}
					</span>
				</div>
				{overrideRules.map((rule) => (
					<div key={rule.id} className="flex items-center justify-between rounded-md bg-ice-white p-3">
						<span className="font-medium text-navy">{blueprintTitle(rule.printifyBlueprintId as number)}</span>
						<span className="flex items-center gap-3">
							<span>{formatMarkup(rule.markupType, rule.markupValue)}</span>
							<Button type="button" variant="secondary" disabled={deleteRule.isPending} onClick={() => deleteRule.mutate(rule.id)}>Remove</Button>
						</span>
					</div>
				))}
				{overrideRules.length === 0 && <p className="text-slate-gray">No per-apparel-type overrides yet.</p>}
			</div>

			<form onSubmit={onSubmit} className="mt-4 flex flex-wrap items-end gap-3 border-t border-slate-gray/20 pt-4">
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Apparel type
					<select value={printifyBlueprintId} onChange={(event) => setPrintifyBlueprintId(event.target.value)} className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal">
						<option value="">Org-wide default</option>
						{(blueprints.data ?? []).map((blueprint) => (
							<option key={blueprint.id} value={blueprint.id}>{blueprint.title}</option>
						))}
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Type
					<select value={markupType} onChange={(event) => setMarkupType(event.target.value as "PERCENTAGE" | "FLAT")} className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal">
						<option value="PERCENTAGE">Percentage</option>
						<option value="FLAT">Flat amount</option>
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					{markupType === "PERCENTAGE" ? "Markup %" : "Markup $"}
					<input type="number" min="0" step="0.01" value={markupValue} onChange={(event) => setMarkupValue(event.target.value)} className="min-h-11 w-32 rounded-md border border-slate-300 px-3 py-2 font-normal" />
				</label>
				<Button type="submit" disabled={upsertRule.isPending}>{upsertRule.isPending ? "Saving…" : "Save rule"}</Button>
			</form>
			{error && <p role="alert" className="mt-2 text-sm text-error-red">{error}</p>}
		</section>
	);
}

function formatMarkup(markupType: "PERCENTAGE" | "FLAT", markupValue: number): string {
	return markupType === "PERCENTAGE" ? `${(markupValue / 100).toFixed(2)}%` : `+$${(markupValue / 100).toFixed(2)}`;
}
