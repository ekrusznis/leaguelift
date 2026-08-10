import { useEffect, useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	usePreviewQuickBooksExport,
	useQuickBooksOverview,
	useRefreshQuickBooksCompany,
} from "./api";
import {
	useOwnerQuickBooksAccounts,
	useQuickBooksMappingRules,
	useQuickBooksMappingValidation,
	useSaveOwnerQuickBooksMapping,
} from "./quickBooksMappingApi";
import {
	classifyQuickBooksAccount,
	compatibilityLabel,
	validationLabel,
	type OwnerQuickBooksAccountMapping,
	type OwnerQuickBooksExportPreview,
	type OwnerQuickBooksMappingType,
} from "./quickBooksMappingTools";

function dateInputValue(date: Date) {
	return date.toISOString().slice(0, 10);
}

function rankCompatibility(value: string): number {
	if (value === "RECOMMENDED") return 0;
	if (value === "ALLOWED_WITH_WARNING") return 1;
	return 2;
}

export function QuickBooksScaffoldPanel({ organizationId }: { organizationId: string }) {
	const query = useQuickBooksOverview(organizationId);
	const connectionId = query.data?.catalog.connection?.id ?? null;
	const providerReadAvailable = ["CONNECTED", "DEGRADED"].includes(query.data?.catalog.connection?.status ?? "");
	const accountsQuery = useOwnerQuickBooksAccounts(organizationId, connectionId, providerReadAvailable);
	const rulesQuery = useQuickBooksMappingRules(organizationId);
	const validationQuery = useQuickBooksMappingValidation(organizationId, connectionId, false);
	const refreshCompany = useRefreshQuickBooksCompany(organizationId, connectionId);
	const saveMapping = useSaveOwnerQuickBooksMapping(organizationId, connectionId);
	const previewExport = usePreviewQuickBooksExport(organizationId, connectionId);
	const [selectedAccounts, setSelectedAccounts] = useState<Partial<Record<OwnerQuickBooksMappingType, string>>>({});
	const [warningAcknowledgements, setWarningAcknowledgements] = useState<
		Partial<Record<OwnerQuickBooksMappingType, boolean>>
	>({});
	const [periodStart, setPeriodStart] = useState(() =>
		dateInputValue(new Date(new Date().getFullYear(), new Date().getMonth(), 1)),
	);
	const [periodEnd, setPeriodEnd] = useState(() => dateInputValue(new Date()));
	const [preview, setPreview] = useState<OwnerQuickBooksExportPreview | null>(null);

	useEffect(() => {
		if (!query.data) return;
		const mappings = query.data.mappings as unknown as OwnerQuickBooksAccountMapping[];
		setSelectedAccounts(
			Object.fromEntries(mappings.map((mapping) => [mapping.mappingType, mapping.externalAccountId])),
		);
		setWarningAcknowledgements(
			Object.fromEntries(mappings.map((mapping) => [mapping.mappingType, mapping.warningAcknowledged])),
		);
	}, [query.data]);

	const ownerMappings = query.data?.mappings as unknown as OwnerQuickBooksAccountMapping[] | undefined;

	const accountsById = useMemo(
		() => new Map((accountsQuery.data ?? []).map((account) => [account.id, account])),
		[accountsQuery.data],
	);

	if (query.isLoading) return <LoadingState label="Loading QuickBooks readiness…" />;
	if (query.isError || !query.data) {
		return <ErrorState message="Could not load QuickBooks readiness." onRetry={() => query.refetch()} />;
	}

	const { catalog, setting, recentBatches, activationReadiness } = query.data;
	const status = activationReadiness.stage;

	async function save(type: OwnerQuickBooksMappingType) {
		const accountId = selectedAccounts[type];
		if (!accountId) return;
		const definition = rulesQuery.data?.find((item) => item.mappingType === type);
		const account = accountsById.get(accountId);
		const compatibility = account && definition ? classifyQuickBooksAccount(account, definition) : "BLOCKED";
		await saveMapping.mutateAsync({
			mappingType: type,
			accountId,
			acknowledgeWarning: compatibility === "ALLOWED_WITH_WARNING" && warningAcknowledgements[type] === true,
		});
	}

	async function runPreview() {
		const result = await previewExport.mutateAsync({
			periodStart,
			periodEnd,
			idempotencyKey: `qb-preview-${crypto.randomUUID()}`,
		});
		setPreview(result as OwnerQuickBooksExportPreview);
	}

	return (
		<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="quickbooks-heading">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h3 id="quickbooks-heading" className="font-heading text-lg font-semibold text-navy-900">
						QuickBooks Online readiness
					</h3>
					<p className="mt-1 text-sm text-slate-500">
						Owner/admin accounting setup. Readiness is gate-based: saved connection metadata never proves live Intuit verification or write activation.
					</p>
				</div>
				<span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
					{status.replaceAll("_", " ")}
				</span>
			</div>

			<div className="mt-4 grid gap-4 md:grid-cols-2">
				<div className="rounded-lg border border-slate-200 p-4">
					<p className="text-xs font-semibold uppercase tracking-wide text-slate-500">QuickBooks company context</p>
					<p className="mt-2 font-medium text-navy-900">{setting?.companyName ?? "No company authorized"}</p>
					<p className="mt-1 text-sm text-slate-500">
						{setting?.realmId ? `Realm ${setting.realmId}` : catalog.activationRequirement}
					</p>
					<p className="mt-2 text-xs text-slate-500">
						{activationReadiness.credentialedProviderVerified
							? "Credentialed Intuit verification is recorded."
							: "No credentialed Intuit verification is claimed yet; realm/company metadata alone is only setup context."}
					</p>
					{providerReadAvailable && (
						<div className="mt-3 flex flex-wrap gap-2">
							<Button
								type="button"
								variant="secondary"
								onClick={() => refreshCompany.mutate()}
								disabled={refreshCompany.isPending}
							>
								{refreshCompany.isPending ? "Refreshing…" : "Refresh company"}
							</Button>
							<Button
								type="button"
								variant="secondary"
								onClick={() => void accountsQuery.refetch()}
								disabled={accountsQuery.isFetching}
							>
								{accountsQuery.isFetching ? "Refreshing accounts…" : "Refresh accounts"}
							</Button>
						</div>
					)}
				</div>
				<div className="rounded-lg border border-slate-200 p-4">
					<p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Export policy</p>
					<p className="mt-2 font-medium text-navy-900">Provider writes disabled</p>
					<p className="mt-1 text-sm text-slate-500">
						Sandbox verification, credentialed activation, and accounting approval are required before Rally26 can send accounting data.
					</p>
				</div>
			</div>

			<div className="mt-5 rounded-lg border border-slate-200 p-4">
				<div className="flex flex-wrap items-start justify-between gap-3">
					<div>
						<h4 className="font-medium text-navy-900">Activation readiness gates</h4>
						<p className="mt-1 text-sm text-slate-500">
							These gates distinguish local setup from verified provider access. Phase 29 cannot satisfy or bypass the future credential, sandbox, accounting-approval, or write-policy gates.
						</p>
					</div>
					<span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
						{activationReadiness.activationAllowed ? "Activation allowed" : "Activation blocked"}
					</span>
				</div>
				<ul className="mt-3 grid gap-2 md:grid-cols-2">
					{activationReadiness.gates.map((gate) => (
						<li key={gate.code} className="rounded-md bg-slate-50 p-3 text-xs text-slate-600">
							<div className="flex items-start justify-between gap-2">
								<span className="font-semibold text-navy-900">{gate.label}</span>
								<span className="font-semibold">
									{gate.status === "SATISFIED"
										? "Satisfied"
										: gate.status === "BLOCKED_BY_PHASE_POLICY"
											? "Phase 29 blocked"
											: "Pending"}
								</span>
							</div>
							<p className="mt-1">{gate.detail}</p>
						</li>
					))}
				</ul>
			</div>

			<div className="mt-5">
				<div className="flex flex-wrap items-start justify-between gap-3">
					<div>
						<h4 className="font-medium text-navy-900">Chart-of-accounts mapping</h4>
						<p className="mt-1 text-sm text-slate-500">
							Choose the accounts your organization already uses. Rally26 recommends compatible account types, but the owner/accountant controls the final mapping.
						</p>
					</div>
					{providerReadAvailable && (
						<Button
							type="button"
							variant="secondary"
							onClick={() => void validationQuery.refetch()}
							disabled={validationQuery.isFetching}
						>
							{validationQuery.isFetching ? "Checking…" : "Revalidate mappings"}
						</Button>
					)}
				</div>
				{accountsQuery.isError && (
					<p role="alert" className="mt-2 text-sm text-error-red">
						Could not read the authorized company&apos;s chart of accounts.
					</p>
				)}
				{rulesQuery.isError && (
					<p role="alert" className="mt-2 text-sm text-error-red">Could not load QuickBooks mapping rules.</p>
				)}
				{validationQuery.isError && (
					<p role="alert" className="mt-2 text-sm text-error-red">Could not revalidate the saved mappings.</p>
				)}

				{validationQuery.data && (
					<div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
						<p className="text-sm font-medium text-navy-900">Latest mapping check</p>
						<ul className="mt-2 grid gap-2 md:grid-cols-2">
							{validationQuery.data.map((item) => (
								<li key={item.mappingType} className="text-xs text-slate-600">
									<span className="font-semibold text-navy-900">{validationLabel(item.status)}:</span> {item.message}
								</li>
							))}
						</ul>
					</div>
				)}

				<div className="mt-3 grid gap-3 md:grid-cols-2">
					{(rulesQuery.data ?? []).map((definition) => {
						const type = definition.mappingType;
						const existing = ownerMappings?.find((mapping) => mapping.mappingType === type);
						const selected = selectedAccounts[type] ?? "";
						const selectedAccount = accountsById.get(selected);
						const compatibility = selectedAccount
							? classifyQuickBooksAccount(selectedAccount, definition)
							: null;
						const warningRequired = compatibility === "ALLOWED_WITH_WARNING";
						const sortedAccounts = [...(accountsQuery.data ?? [])].sort((left, right) => {
							const leftRank = rankCompatibility(classifyQuickBooksAccount(left, definition));
							const rightRank = rankCompatibility(classifyQuickBooksAccount(right, definition));
							return leftRank - rightRank || left.name.localeCompare(right.name);
						});

						return (
							<div key={type} className="rounded-lg border border-slate-200 p-3">
								<label htmlFor={`quickbooks-${type}`} className="text-sm font-medium text-navy-900">
									{definition.label}
								</label>
								<p className="mt-1 text-xs text-slate-500">{definition.description}</p>
								<select
									id={`quickbooks-${type}`}
									value={selected}
									onChange={(event) => {
										setSelectedAccounts((current) => ({ ...current, [type]: event.target.value }));
										setWarningAcknowledgements((current) => ({ ...current, [type]: false }));
									}}
									disabled={!providerReadAvailable || accountsQuery.isLoading}
									className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-navy-900 disabled:bg-slate-50"
								>
									<option value="">{existing ? existing.externalAccountName : "Choose an account"}</option>
									{sortedAccounts.map((account) => {
										const accountCompatibility = classifyQuickBooksAccount(account, definition);
										return (
											<option
												key={account.id}
												value={account.id}
												disabled={accountCompatibility === "BLOCKED"}
											>
												{account.fullyQualifiedName ?? account.name} · {account.accountType} · {compatibilityLabel(accountCompatibility)}
											</option>
										);
									})}
								</select>

								{selectedAccount && compatibility && (
									<p className="mt-2 text-xs text-slate-600">
										<strong>{compatibilityLabel(compatibility)}.</strong>{" "}
										{compatibility === "RECOMMENDED"
											? `This ${selectedAccount.accountType} account matches Rally26's recommended accounting role.`
											: compatibility === "ALLOWED_WITH_WARNING"
												? `This ${selectedAccount.accountType} account is permitted, but an owner/accountant should confirm the treatment.`
												: "This account cannot be used for this mapping role."}
									</p>
								)}

								{warningRequired && (
									<label className="mt-2 flex items-start gap-2 text-xs text-slate-700">
										<input
											type="checkbox"
											checked={warningAcknowledgements[type] === true}
											onChange={(event) =>
												setWarningAcknowledgements((current) => ({ ...current, [type]: event.target.checked }))
											}
											className="mt-0.5 h-4 w-4"
										/>
										<span>I understand this is a nonstandard mapping and want to use it for this organization.</span>
									</label>
								)}

								<div className="mt-2 flex items-center justify-between gap-2">
									<span className="text-xs text-slate-500">
										{existing
											? `Mapped to ${existing.externalAccountFullyQualifiedName ?? existing.externalAccountName}`
											: "Owner/accountant selection required before later export activation"}
									</span>
									<Button
										type="button"
										variant="secondary"
										onClick={() => void save(type)}
										disabled={
											!providerReadAvailable ||
											!selected ||
											saveMapping.isPending ||
											compatibility === "BLOCKED" ||
											(warningRequired && warningAcknowledgements[type] !== true)
										}
									>
										Save
									</Button>
								</div>
							</div>
						);
					})}
				</div>
			</div>

			<div className="mt-5 rounded-lg border border-slate-200 p-4">
				<h4 className="font-medium text-navy-900">Export-readiness preview</h4>
				<p className="mt-1 text-sm text-slate-500">
					Counts Rally26 source records and revalidates saved mappings. It never sends data to QuickBooks in Phase 29.
				</p>
				<div className="mt-3 flex flex-wrap items-end gap-3">
					<div>
						<label htmlFor="quickbooks-period-start" className="block text-sm font-medium text-navy-900">Period start</label>
						<input
							id="quickbooks-period-start"
							type="date"
							value={periodStart}
							onChange={(event) => setPeriodStart(event.target.value)}
							className="mt-1 min-h-11 rounded-md border border-slate-300 px-3 py-2"
						/>
					</div>
					<div>
						<label htmlFor="quickbooks-period-end" className="block text-sm font-medium text-navy-900">Period end</label>
						<input
							id="quickbooks-period-end"
							type="date"
							value={periodEnd}
							onChange={(event) => setPeriodEnd(event.target.value)}
							className="mt-1 min-h-11 rounded-md border border-slate-300 px-3 py-2"
						/>
					</div>
					<Button
						type="button"
						onClick={() => void runPreview()}
						disabled={!providerReadAvailable || previewExport.isPending || !periodStart || !periodEnd}
					>
						{previewExport.isPending ? "Previewing…" : "Preview export"}
					</Button>
				</div>
				{previewExport.isError && (
					<p role="alert" className="mt-3 text-sm text-error-red">Could not create the export preview.</p>
				)}
				{preview && (
					<div role="status" className="mt-3 rounded-md bg-ice-50 p-3 text-sm text-slate-700">
						<p className="font-medium text-navy-900">{preview.counts.total} candidate records</p>
						<p className="mt-1">
							{preview.counts.contributions} contributions · {preview.counts.sponsorships} sponsorships · {preview.counts.orders} orders · {preview.counts.feePayments} fee payments · {preview.counts.corrections} corrections
						</p>
						<p className="mt-1">{preview.reason}</p>
						{preview.missingMappings.length > 0 && (
							<p className="mt-1">Missing: {preview.missingMappings.map((item) => item.replaceAll("_", " ")).join(", ")}</p>
						)}
						{preview.mappingDiagnostics.some((item) => !["VALID", "VALID_WITH_WARNING"].includes(item.status)) && (
							<p className="mt-1">One or more saved mappings need owner/accountant attention before any later activation.</p>
						)}
					</div>
				)}
			</div>

			{recentBatches.length > 0 && (
				<div className="mt-4">
					<h4 className="font-medium text-navy-900">Recent export previews</h4>
					<ul className="mt-2 space-y-2">
						{recentBatches.slice(0, 5).map((batch) => (
							<li key={batch.id} className="flex flex-wrap justify-between gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm">
								<span>{batch.periodStart} – {batch.periodEnd}</span>
								<span className="text-slate-500">{batch.candidateCount} candidates · {batch.status}</span>
							</li>
						))}
					</ul>
				</div>
			)}
		</section>
	);
}
