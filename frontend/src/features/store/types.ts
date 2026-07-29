export type StoreStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export interface Store {
	id: string;
	organizationId: string;
	teamId: string | null;
	name: string;
	slug: string;
	status: StoreStatus;
	createdAt: string;
	updatedAt: string;
}

export interface StorePage {
	items: Store[];
	page: number;
	size: number;
	totalElements: number;
}

export type ProductStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export interface Product {
	id: string;
	organizationId: string;
	storeId: string;
	name: string;
	description: string | null;
	printifyBlueprintId: number;
	printifyPrintPosition: string;
	/** True once a design has been pushed to Printify's image library (first variant creation) — not just "an image was assigned" in our own system. */
	hasDesign: boolean;
	status: ProductStatus;
	createdAt: string;
	updatedAt: string;
}

export interface ProductPage {
	items: Product[];
	page: number;
	size: number;
	totalElements: number;
}

export interface ProductVariant {
	id: string;
	productId: string;
	label: string;
	printifyPrintProviderId: number;
	printifyVariantId: number;
	currency: string;
	/** Printify's real cost for this variant/provider, in minor units — never guessed. */
	costMinor: number;
	priceMinor: number;
	isActive: boolean;
}

export interface PrintifyBlueprint {
	id: number;
	title: string;
	brand: string | null;
	model: string | null;
}

export interface PrintifyLocation {
	country: string | null;
	region: string | null;
	city: string | null;
}

/** A location (and, where available, decoration-method) filtered list — not a price comparison. Printify's catalog has no cost signal to rank by until a product is actually created. */
export interface EligiblePrintProvider {
	id: number;
	title: string;
	decorationMethods: string[] | null;
	location: PrintifyLocation;
}

export interface PrintifyCatalogVariant {
	id: number;
	title: string;
	options: Record<string, string> | null;
}

export interface MediaAssignmentDescriptor {
	usageSlot: string;
	assetId: string;
	url: string;
	altText: string | null;
	contentType: string | null;
	widthPx: number | null;
	heightPx: number | null;
	byteSizeBytes: number | null;
	visibility: string;
	publicationStatus: string;
	updatedAt: string;
}

export interface PublicProductVariant {
	id: string;
	label: string;
	priceMinor: number;
	currency: string;
}

export interface PublicProduct {
	id: string;
	name: string;
	description: string | null;
	designUrl: string | null;
	variants: PublicProductVariant[];
}

export interface PublicStore {
	id: string;
	name: string;
	slug: string;
	products: PublicProduct[];
}

export type OrderStatus = "PENDING" | "CONFIRMED" | "CANCELED" | "REFUNDED";

export interface OrderCheckout {
	orderId: string;
	checkoutUrl: string;
}

export interface OrderStatusResult {
	id: string;
	status: OrderStatus;
	currency: string;
	confirmedAt: string | null;
}

export interface ShippingAddress {
	name: string | null;
	line1: string | null;
	line2: string | null;
	city: string | null;
	state: string | null;
	postalCode: string | null;
	country: string | null;
}

export interface Order {
	id: string;
	storeId: string;
	status: OrderStatus;
	currency: string;
	supporterName: string | null;
	supporterEmail: string | null;
	shippingAddress: ShippingAddress | null;
	confirmedAt: string | null;
	refundedAt: string | null;
	createdAt: string;
}

export interface OrderPage {
	items: Order[];
	page: number;
	size: number;
	totalElements: number;
}

export type FulfillmentStatus = "NOT_SUBMITTED" | "DRAFT_CREATED" | "FAILED";

export interface Fulfillment {
	status: FulfillmentStatus;
	printifyOrderId: string | null;
	lastError: string | null;
}

export interface CartLine {
	productVariantId: string;
	quantity: number;
}
