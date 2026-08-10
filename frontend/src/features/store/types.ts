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

export type ManualVendorStatus = "ACTIVE" | "ARCHIVED";
export interface ManualVendor {
	id: string;
	organizationId: string;
	name: string;
	contactName: string | null;
	contactEmail: string | null;
	phone: string | null;
	websiteUrl: string | null;
	notes: string | null;
	status: ManualVendorStatus;
	createdAt: string;
	updatedAt: string;
}

export type CatalogSource = "PRINTIFY" | "MANUAL";
export type ProductStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export interface Product {
	id: string;
	organizationId: string;
	storeId: string;
	name: string;
	description: string | null;
	catalogSource: CatalogSource;
	manualVendorId: string | null;
	manualVendorName: string | null;
	printifyBlueprintId: number | null;
	printifyPrintPosition: string;
	hasDesign: boolean;
	/** Swag Shop: whether a team logo has been frozen onto this product for order-time compositing. */
	hasSwagLogo: boolean;
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
	catalogSource: CatalogSource;
	label: string;
	sku: string | null;
	size: string | null;
	color: string | null;
	printifyPrintProviderId: number | null;
	printifyVariantId: number | null;
	currency: string;
	costMinor: number;
	priceMinor: number;
	isActive: boolean;
	/** Swag Shop: real Printify print-area pixel dimensions, captured at creation time — null for manual variants. */
	printAreaWidthPx: number | null;
	printAreaHeightPx: number | null;
	/** Real Printify "back" placeholder dimensions, so BACK-placed personalization can print on the garment's actual back. Null if the blueprint has no back placeholder or the variant predates this capability. */
	backPrintAreaWidthPx: number | null;
	backPrintAreaHeightPx: number | null;
	/** Real photorealistic Printify mockup images (this variant's color, design already composited) — null for manual variants or variants created before this capability. */
	mockupFrontUrl: string | null;
	mockupBackUrl: string | null;
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

export interface EligiblePrintProvider {
	id: number;
	title: string;
	decorationMethods: string[] | null;
	location: PrintifyLocation;
}

export interface PrintifyPlaceholder {
	position: string;
	widthPx: number;
	heightPx: number;
}

export interface PrintifyCatalogVariant {
	id: number;
	title: string;
	options: Record<string, string> | null;
	placeholders: PrintifyPlaceholder[] | null;
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
	/** Phase 35 (ADR-099): resolved (always non-null) team colors — Rally26 defaults when the store has no team or the team has no override. */
	primaryColor: string;
	secondaryColor: string;
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
	paymentSource: "STRIPE" | "OFFLINE";
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

export type FulfillmentSource = "PRINTIFY" | "MANUAL";
export type FulfillmentStatus =
	| "NOT_SUBMITTED"
	| "DRAFT_CREATED"
	| "FAILED"
	| "READY"
	| "IN_PRODUCTION"
	| "SHIPPED"
	| "DELIVERED"
	| "NEEDS_ATTENTION"
	| "CANCELED";

export interface Fulfillment {
	id: string;
	orderId: string;
	source: FulfillmentSource;
	status: FulfillmentStatus;
	printifyOrderId: string | null;
	manualVendorId: string | null;
	manualVendorName: string | null;
	vendorOrderReference: string | null;
	carrier: string | null;
	trackingNumber: string | null;
	trackingUrl: string | null;
	internalNotes: string | null;
	attentionReason: string | null;
	lastError: string | null;
	statusChangedAt: string;
	shippedAt: string | null;
	deliveredAt: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface FulfillmentHistory {
	id: string;
	previousStatus: FulfillmentStatus | null;
	newStatus: FulfillmentStatus;
	note: string;
	actorUserId: string | null;
	createdAt: string;
}

export type FulfillmentReprintStatus = "REQUESTED" | "IN_PRODUCTION" | "SHIPPED" | "DELIVERED" | "CANCELED";
export interface FulfillmentReprint {
	id: string;
	fulfillmentId: string;
	orderId: string;
	status: FulfillmentReprintStatus;
	reason: string;
	vendorOrderReference: string | null;
	carrier: string | null;
	trackingNumber: string | null;
	trackingUrl: string | null;
	internalNotes: string | null;
	requestedByUserId: string;
	createdAt: string;
	updatedAt: string;
	shippedAt: string | null;
	deliveredAt: string | null;
}

export interface CartLine {
	productVariantId: string;
	quantity: number;
}

export type PersonalizationPlacement = "LEFT_CHEST" | "RIGHT_CHEST" | "CENTER_FRONT" | "BACK";

/** Path 2 (24.2) curated logo-size preset — defaults to "STANDARD" when personalized but unset. */
export type SwagLogoSize = "SMALL" | "STANDARD" | "LARGE";

export interface SwagShopApparelType {
	storeId: string;
	storeName: string;
	productId: string;
	productName: string;
	description: string | null;
	hasSwagLogo: boolean;
	/** Short-lived signed preview of the snapshotted logo, for the buyer's live-preview overlay. Null when no logo is assigned. */
	logoPreviewUrl: string | null;
	variants: ProductVariant[];
}

export interface CreateSwagShopOrderRequest {
	productVariantId: string;
	participantId: string;
	personalizationName?: string | null;
	personalizationNumber?: string | null;
	personalizationPlacement?: PersonalizationPlacement | null;
	personalizationLogoSize?: SwagLogoSize | null;
}
