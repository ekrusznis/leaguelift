import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCampaigns } from "../fundraising/api";
import { useSponsorshipPackages } from "../sponsorship/api";
import { useProducts, useStores, useVariants } from "../store/api";
import type { Product } from "../store/types";
import { appPaths } from "../../routes/appPaths";
import {
	useCreateOfflineContribution,
	useCreateOfflineOrder,
	useCreateOfflineSponsorship,
	useOfflineFinancialRecords,
	useVerifyOfflineFinancialRecord,
} from "./api";
import type { OfflineFinancialRecordType, OfflinePaymentMethod, OfflineVerificationStatus } from "./types";

type EntryMode = "CONTRIBUTION" | "SPONSORSHIP" | "ORDER";
type OrderLineDraft = { key: string; productId: string; productVariantId: string; quantity: number };

const PAYMENT_METHODS: Array<{ value: OfflinePaymentMethod; label: string }> = [
	{ value: "CASH", label: "Cash" },
	{ value: "CHECK", label: "Check" },
	{ value: "ACH", label: "ACH / bank transfer" },
	{ value: "EXTERNAL_CARD", label: "External card terminal" },
	{ value: "VENMO", label: "Venmo" },
	{ value: "ZELLE", label: "Zelle" },
	{ value: "OTHER", label: "Other" },
];

function localDateTimeNow() {
	const date = new Date();
	date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
	return date.toISOString().slice(0, 16);
}

function newOrderLine(): OrderLineDraft {
	return { key: crypto.randomUUID(), productId: "", productVariantId: "", quantity: 1 };
}

export function OfflineFinancialRecordsPanel({ organizationId }: { organizationId: string }) {
	const [mode, setMode] = useState<EntryMode>("CONTRIBUTION");
	const [paymentMethod, setPaymentMethod] = useState<OfflinePaymentMethod>("CHECK");
	const [paymentReference, setPaymentReference] = useState("");
	const [receivedAt, setReceivedAt] = useState(localDateTimeNow());
	const [internalNotes, setInternalNotes] = useState("");
	const [markVerified, setMarkVerified] = useState(false);
	const [sendAcknowledgement, setSendAcknowledgement] = useState(false);
	const [successMessage, setSuccessMessage] = useState("");
	const [formError, setFormError] = useState("");

	const [campaignId, setCampaignId] = useState("");
	const [contributionAmount, setContributionAmount] = useState("");
	const [contributorName, setContributorName] = useState("");
	const [contributorEmail, setContributorEmail] = useState("");
	const [anonymous, setAnonymous] = useState(false);

	const [packageId, setPackageId] = useState("");
	const [sponsorName, setSponsorName] = useState("");
	const [sponsorEmail, setSponsorEmail] = useState("");
	const [sponsorPhone, setSponsorPhone] = useState("");
	const [sponsorCompany, setSponsorCompany] = useState("");

	const [storeId, setStoreId] = useState("");
	const [orderLines, setOrderLines] = useState<OrderLineDraft[]>([newOrderLine()]);
	const [orderName, setOrderName] = useState("");
	const [orderEmail, setOrderEmail] = useState("");
	const [shippingLine1, setShippingLine1] = useState("");
	const [shippingLine2, setShippingLine2] = useState("");
	const [shippingCity, setShippingCity] = useState("");
	const [shippingState, setShippingState] = useState("");
	const [shippingPostalCode, setShippingPostalCode] = useState("");
	const [shippingCountry, setShippingCountry] = useState("US");

	const [statusFilter, setStatusFilter] = useState<OfflineVerificationStatus | "">("");
	const [typeFilter, setTypeFilter] = useState<OfflineFinancialRecordType | "">("");

	const campaigns = useCampaigns(organizationId);
	const packages = useSponsorshipPackages(organizationId);
	const stores = useStores(organizationId);
	const products = useProducts(organizationId, storeId);
	const records = useOfflineFinancialRecords(organizationId, statusFilter, typeFilter);
	const createContribution = useCreateOfflineContribution(organizationId);
	const createSponsorship = useCreateOfflineSponsorship(organizationId);
	const createOrder = useCreateOfflineOrder(organizationId);
	const verifyRecord = useVerifyOfflineFinancialRecord(organizationId);

	const manualProducts = useMemo(
		() => (products.data?.items ?? []).filter((product) => product.catalogSource === "MANUAL" && product.status === "ACTIVE"),
		[products.data],
	);
	const isSubmitting = createContribution.isPending || createSponsorship.isPending || createOrder.isPending;
	const acknowledgementEmailAvailable = (
		mode === "CONTRIBUTION" ? contributorEmail : mode === "SPONSORSHIP" ? sponsorEmail : orderEmail
	).trim().length > 0;

	function resetCommon() {
		setPaymentReference("");
		setReceivedAt(localDateTimeNow());
		setInternalNotes("");
		setMarkVerified(false);
		setSendAcknowledgement(false);
	}

	function commonInput() {
		return {
			paymentMethod,
			paymentReference: paymentReference || null,
			receivedAt: new Date(receivedAt).toISOString(),
			internalNotes: internalNotes || null,
			idempotencyKey: crypto.randomUUID(),
			markVerified,
			sendAcknowledgement: sendAcknowledgement && acknowledgementEmailAvailable,
		};
	}

	async function submit(event: FormEvent) {
		event.preventDefault();
		setFormError("");
		setSuccessMessage("");
		if (!receivedAt || Number.isNaN(new Date(receivedAt).getTime())) {
			setFormError("Enter a valid received date and time.");
			return;
		}
		try {
			if (mode === "CONTRIBUTION") {
				const amountMinor = Math.round(Number(contributionAmount) * 100);
				if (!campaignId || !Number.isFinite(amountMinor) || amountMinor <= 0) throw new Error("Select a campaign and enter a positive contribution amount.");
				await createContribution.mutateAsync({
					...commonInput(), campaignId, amountMinor, supporterName: anonymous ? null : contributorName || null,
					isAnonymous: anonymous, supporterEmail: contributorEmail || null,
				});
				setContributionAmount(""); setContributorName(""); setContributorEmail(""); setAnonymous(false);
			}
			if (mode === "SPONSORSHIP") {
				if (!packageId || !sponsorName.trim()) throw new Error("Select a package and enter the sponsor name.");
				await createSponsorship.mutateAsync({
					...commonInput(), packageId, sponsorName, sponsorContactEmail: sponsorEmail || null,
					sponsorPhone: sponsorPhone || null, sponsorCompanyName: sponsorCompany || null,
				});
				setSponsorName(""); setSponsorEmail(""); setSponsorPhone(""); setSponsorCompany("");
			}
			if (mode === "ORDER") {
				const validLines = orderLines.filter((line) => line.productVariantId && line.quantity > 0);
				if (!storeId || validLines.length !== orderLines.length || validLines.length === 0) throw new Error("Select a Swag Shop, product variant, and quantity for every order line.");
				await createOrder.mutateAsync({
					...commonInput(), storeId,
					items: validLines.map((line) => ({ productVariantId: line.productVariantId, quantity: line.quantity })),
					supporterName: orderName || null, supporterEmail: orderEmail || null,
					shippingAddress: shippingLine1 || shippingCity || shippingPostalCode ? {
						name: orderName || null, line1: shippingLine1 || null, line2: shippingLine2 || null,
						city: shippingCity || null, state: shippingState || null, postalCode: shippingPostalCode || null,
						country: shippingCountry || null,
					} : null,
				});
				setOrderLines([newOrderLine()]); setOrderName(""); setOrderEmail(""); setShippingLine1("");
				setShippingLine2(""); setShippingCity(""); setShippingState(""); setShippingPostalCode(""); setShippingCountry("US");
			}
			resetCommon();
			setSuccessMessage(markVerified ? "The offline transaction was recorded and verified." : "The offline transaction was recorded for verification.");
		} catch (error) {
			setFormError(error instanceof Error ? error.message : "The offline transaction could not be recorded.");
		}
	}

	return (
		<div className="flex flex-col gap-6">
			<section className="rounded-xl border border-amber-300 bg-amber-50 dark:bg-amber-950 p-4" aria-label="Offline payment boundary">
				<h3 className="font-heading font-semibold text-navy dark:text-[#f8fafc]">Recorded in Rally26—not processed by Rally26</h3>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Use this workspace only after money was received through cash, check, bank transfer, an external terminal, or another outside method. Verification records balanced ledger activity but never creates Stripe IDs or payout-eligible Rally26 earnings.</p>
				<Link className="mt-2 inline-flex text-sm font-medium text-info-blue underline-offset-2 hover:underline" to={appPaths.helpArticle("recording-and-verifying-offline-payments")}>Read the offline-payment how-to</Link>
			</section>

			<section className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5" aria-labelledby="offline-entry-heading">
				<div>
					<h3 id="offline-entry-heading" className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Record an offline transaction</h3>
					<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Create a pending record for a second-person review, or verify immediately when your organization’s controls allow it.</p>
				</div>
				<div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Offline transaction type">
					{(["CONTRIBUTION", "SPONSORSHIP", "ORDER"] as const).map((item) => (
						<Button key={item} type="button" variant={mode === item ? "primary" : "secondary"} onClick={() => { setMode(item); setFormError(""); setSuccessMessage(""); }}>
							{item === "CONTRIBUTION" ? "Contribution" : item === "SPONSORSHIP" ? "Sponsorship" : "Swag Shop order"}
						</Button>
					))}
				</div>

				<form onSubmit={submit} className="mt-5 grid gap-4" noValidate>
					{mode === "CONTRIBUTION" && (
						<div className="grid gap-3 sm:grid-cols-2">
							<Field label="Campaign" id="offline-campaign" required><select id="offline-campaign" value={campaignId} onChange={(event) => setCampaignId(event.target.value)} className={inputClass}><option value="">Select campaign</option>{campaigns.data?.items.filter((item) => item.status !== "ARCHIVED").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
							<Field label="Amount" id="offline-contribution-amount" required><input id="offline-contribution-amount" type="number" min="1" step="0.01" value={contributionAmount} onChange={(event) => setContributionAmount(event.target.value)} className={inputClass} placeholder="100.00" /></Field>
							<Field label="Supporter name" id="offline-contributor-name"><input id="offline-contributor-name" value={contributorName} onChange={(event) => setContributorName(event.target.value)} disabled={anonymous} className={inputClass} /></Field>
							<Field label="Supporter email" id="offline-contributor-email"><input id="offline-contributor-email" type="email" value={contributorEmail} onChange={(event) => setContributorEmail(event.target.value)} className={inputClass} /></Field>
							<label className="flex items-center gap-2 text-sm text-navy dark:text-[#f8fafc] sm:col-span-2"><input type="checkbox" checked={anonymous} onChange={(event) => setAnonymous(event.target.checked)} /> Record publicly as anonymous</label>
						</div>
					)}

					{mode === "SPONSORSHIP" && (
						<div className="grid gap-3 sm:grid-cols-2">
							<Field label="Sponsorship package" id="offline-package" required><select id="offline-package" value={packageId} onChange={(event) => setPackageId(event.target.value)} className={inputClass}><option value="">Select package</option>{packages.data?.items.filter((item) => item.status !== "ARCHIVED" && !item.soldOut).map((item) => <option key={item.id} value={item.id}>{item.name} — {formatMoney(item.priceMinor, item.currency)}</option>)}</select></Field>
							<Field label="Sponsor name" id="offline-sponsor-name" required><input id="offline-sponsor-name" value={sponsorName} onChange={(event) => setSponsorName(event.target.value)} className={inputClass} /></Field>
							<Field label="Contact email" id="offline-sponsor-email"><input id="offline-sponsor-email" type="email" value={sponsorEmail} onChange={(event) => setSponsorEmail(event.target.value)} className={inputClass} /></Field>
							<Field label="Phone" id="offline-sponsor-phone"><input id="offline-sponsor-phone" value={sponsorPhone} onChange={(event) => setSponsorPhone(event.target.value)} className={inputClass} /></Field>
							<Field label="Company" id="offline-sponsor-company"><input id="offline-sponsor-company" value={sponsorCompany} onChange={(event) => setSponsorCompany(event.target.value)} className={inputClass} /></Field>
						</div>
					)}

					{mode === "ORDER" && (
						<div className="grid gap-4">
							<Field label="Swag Shop" id="offline-store" required><select id="offline-store" value={storeId} onChange={(event) => { setStoreId(event.target.value); setOrderLines([newOrderLine()]); }} className={inputClass}><option value="">Select active Swag Shop</option>{stores.data?.items.filter((item) => item.status === "ACTIVE").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
							<div className="grid gap-3">
								{orderLines.map((line, index) => <OrderLineEditor key={line.key} organizationId={organizationId} line={line} index={index} products={manualProducts} onChange={(next) => setOrderLines((current) => current.map((item) => item.key === line.key ? next : item))} onRemove={orderLines.length > 1 ? () => setOrderLines((current) => current.filter((item) => item.key !== line.key)) : undefined} />)}
								<Button type="button" variant="secondary" className="justify-self-start" onClick={() => setOrderLines((current) => [...current, newOrderLine()])} disabled={!storeId}>Add line item</Button>
							</div>
							<div className="grid gap-3 sm:grid-cols-2">
								<Field label="Customer name" id="offline-order-name"><input id="offline-order-name" value={orderName} onChange={(event) => setOrderName(event.target.value)} className={inputClass} /></Field>
								<Field label="Customer email" id="offline-order-email"><input id="offline-order-email" type="email" value={orderEmail} onChange={(event) => setOrderEmail(event.target.value)} className={inputClass} /></Field>
								<Field label="Shipping address" id="offline-shipping-line1"><input id="offline-shipping-line1" value={shippingLine1} onChange={(event) => setShippingLine1(event.target.value)} className={inputClass} /></Field>
								<Field label="Address line 2" id="offline-shipping-line2"><input id="offline-shipping-line2" value={shippingLine2} onChange={(event) => setShippingLine2(event.target.value)} className={inputClass} /></Field>
								<Field label="City" id="offline-shipping-city"><input id="offline-shipping-city" value={shippingCity} onChange={(event) => setShippingCity(event.target.value)} className={inputClass} /></Field>
								<Field label="State" id="offline-shipping-state"><input id="offline-shipping-state" value={shippingState} onChange={(event) => setShippingState(event.target.value)} className={inputClass} /></Field>
								<Field label="Postal code" id="offline-shipping-postal"><input id="offline-shipping-postal" value={shippingPostalCode} onChange={(event) => setShippingPostalCode(event.target.value)} className={inputClass} /></Field>
								<Field label="Country code" id="offline-shipping-country"><input id="offline-shipping-country" maxLength={2} value={shippingCountry} onChange={(event) => setShippingCountry(event.target.value.toUpperCase())} className={inputClass} /></Field>
							</div>
						</div>
					)}

					<div className="grid gap-3 border-t border-slate-gray/20 pt-4 sm:grid-cols-2">
						<Field label="Payment method" id="offline-payment-method" required><select id="offline-payment-method" value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as OfflinePaymentMethod)} className={inputClass}>{PAYMENT_METHODS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></Field>
						<Field label="Reference or check number" id="offline-payment-reference"><input id="offline-payment-reference" value={paymentReference} onChange={(event) => setPaymentReference(event.target.value)} className={inputClass} /></Field>
						<Field label="Received at" id="offline-received-at" required><input id="offline-received-at" type="datetime-local" value={receivedAt} onChange={(event) => setReceivedAt(event.target.value)} className={inputClass} /></Field>
						<Field label="Internal notes" id="offline-notes"><textarea id="offline-notes" rows={3} value={internalNotes} onChange={(event) => setInternalNotes(event.target.value)} className={inputClass} /></Field>
					</div>
					<div className="grid gap-2 rounded-lg bg-ice-white dark:bg-[#0f172a] p-4 text-sm text-navy dark:text-[#f8fafc]">
						<label className="flex items-start gap-2"><input type="checkbox" checked={markVerified} onChange={(event) => setMarkVerified(event.target.checked)} className="mt-1" /><span><strong>Verify now.</strong> Use only when the receipt, deposit, or external payment source has already been checked.</span></label>
						<label className={`flex items-start gap-2 ${acknowledgementEmailAvailable ? "" : "text-slate-gray dark:text-[#cbd5e1]"}`}><input type="checkbox" checked={sendAcknowledgement && acknowledgementEmailAvailable} disabled={!acknowledgementEmailAvailable} onChange={(event) => setSendAcknowledgement(event.target.checked)} className="mt-1" /><span>Send an acknowledgement after verification. Enter a payer email to enable this option.</span></label>
					</div>
					{formError && <p role="alert" className="rounded-lg bg-red-50 dark:bg-red-950 p-3 text-sm text-error-red">{formError}</p>}
					{successMessage && <p role="status" className="rounded-lg bg-green-50 dark:bg-green-950 p-3 text-sm text-green-800">{successMessage}</p>}
					<Button type="submit" className="justify-self-start" disabled={isSubmitting}>{isSubmitting ? "Recording…" : markVerified ? "Record and verify" : "Record for verification"}</Button>
				</form>
			</section>

			<section className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5" aria-labelledby="offline-records-heading">
				<div className="flex flex-wrap items-start justify-between gap-3">
					<div><h3 id="offline-records-heading" className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Offline financial records</h3><p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Pending records do not affect confirmed totals until an authorized manager verifies them.</p></div>
					<div className="flex flex-wrap gap-2">
						<label className="sr-only" htmlFor="offline-status-filter">Verification status</label><select id="offline-status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as OfflineVerificationStatus | "")} className={inputClass}><option value="">All statuses</option><option value="PENDING_VERIFICATION">Pending verification</option><option value="VERIFIED">Verified</option><option value="REVERSED">Reversed</option></select>
						<label className="sr-only" htmlFor="offline-type-filter">Record type</label><select id="offline-type-filter" value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as OfflineFinancialRecordType | "")} className={inputClass}><option value="">All types</option><option value="CONTRIBUTION">Contributions</option><option value="SPONSORSHIP">Sponsorships</option><option value="ORDER">Orders</option></select>
					</div>
				</div>
				{records.isLoading && <LoadingState label="Loading offline financial records…" />}
				{records.isError && <ErrorState message="Could not load offline financial records." onRetry={() => records.refetch()} />}
				{records.data?.items.length === 0 && <EmptyState title="No offline financial records" description="Transactions recorded outside Rally26 will appear here." />}
				{records.data && records.data.items.length > 0 && (
					<div className="mt-4 overflow-x-auto">
						<table className="min-w-full border-separate border-spacing-0 text-left text-sm">
							<thead><tr className="text-slate-gray dark:text-[#cbd5e1]"><th className="border-b border-slate-gray/20 px-3 py-2">Received</th><th className="border-b border-slate-gray/20 px-3 py-2">Type</th><th className="border-b border-slate-gray/20 px-3 py-2">Details</th><th className="border-b border-slate-gray/20 px-3 py-2">Method</th><th className="border-b border-slate-gray/20 px-3 py-2">Amount</th><th className="border-b border-slate-gray/20 px-3 py-2">Status</th><th className="border-b border-slate-gray/20 px-3 py-2"><span className="sr-only">Actions</span></th></tr></thead>
							<tbody>{records.data.items.map((record) => <tr key={record.id}><td className="border-b border-slate-gray/10 px-3 py-3 whitespace-nowrap">{new Date(record.receivedAt).toLocaleDateString()}</td><td className="border-b border-slate-gray/10 px-3 py-3">{humanize(record.recordType)}</td><td className="border-b border-slate-gray/10 px-3 py-3"><p className="font-medium text-navy dark:text-[#f8fafc]">{record.displayLabel}</p><p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{record.payerName ?? "No payer name"}{record.paymentReference ? ` · ${record.paymentReference}` : ""}</p></td><td className="border-b border-slate-gray/10 px-3 py-3">{humanize(record.paymentMethod)}</td><td className="border-b border-slate-gray/10 px-3 py-3 whitespace-nowrap">{formatMoney(record.amountMinor, record.currency)}</td><td className="border-b border-slate-gray/10 px-3 py-3"><span className={`rounded-full px-2 py-1 text-xs font-medium ${record.verificationStatus === "VERIFIED" ? "bg-green-100 text-green-800" : "bg-amber-100 text-amber-800"}`}>{humanize(record.verificationStatus)}</span></td><td className="border-b border-slate-gray/10 px-3 py-3">{record.verificationStatus === "PENDING_VERIFICATION" && <Button type="button" variant="secondary" disabled={verifyRecord.isPending} onClick={() => verifyRecord.mutate(record.id)}>Verify</Button>}</td></tr>)}</tbody>
						</table>
					</div>
				)}
			</section>
		</div>
	);
}

function OrderLineEditor({ organizationId, line, index, products, onChange, onRemove }: { organizationId: string; line: OrderLineDraft; index: number; products: Product[]; onChange: (line: OrderLineDraft) => void; onRemove?: () => void }) {
	const variants = useVariants(organizationId, line.productId);
	const manualVariants = variants.data?.filter((variant) => variant.catalogSource === "MANUAL" && variant.isActive) ?? [];
	return <div className="grid gap-3 rounded-lg border border-slate-gray/20 p-3 sm:grid-cols-[1fr_1fr_8rem_auto]">
		<Field label={`Product ${index + 1}`} id={`offline-line-product-${line.key}`} required><select id={`offline-line-product-${line.key}`} value={line.productId} onChange={(event) => onChange({ ...line, productId: event.target.value, productVariantId: "" })} className={inputClass}><option value="">Select product</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}</select></Field>
		<Field label="Variant" id={`offline-line-variant-${line.key}`} required><select id={`offline-line-variant-${line.key}`} value={line.productVariantId} onChange={(event) => onChange({ ...line, productVariantId: event.target.value })} disabled={!line.productId || variants.isLoading} className={inputClass}><option value="">Select variant</option>{manualVariants.map((variant) => <option key={variant.id} value={variant.id}>{variant.label} — {formatMoney(variant.priceMinor, variant.currency)}</option>)}</select></Field>
		<Field label="Quantity" id={`offline-line-quantity-${line.key}`} required><input id={`offline-line-quantity-${line.key}`} type="number" min={1} max={100} value={line.quantity} onChange={(event) => onChange({ ...line, quantity: Number(event.target.value) })} className={inputClass} /></Field>
		{onRemove && <Button type="button" variant="danger" className="self-end" onClick={onRemove}>Remove</Button>}
	</div>;
}

function Field({ label, id, required = false, children }: { label: string; id: string; required?: boolean; children: ReactNode }) {
	return <div className="flex flex-col gap-1"><label htmlFor={id} className="text-sm font-medium text-navy dark:text-[#f8fafc]">{label}{required && <span aria-hidden> *</span>}</label>{children}</div>;
}

const inputClass = "min-h-11 rounded-md border border-slate-gray/30 bg-pure-white dark:bg-[#111827] px-3 py-2 text-navy dark:text-[#f8fafc]";
function formatMoney(amountMinor: number, currency: string) { return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100); }
function humanize(value: string) { return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase()); }
