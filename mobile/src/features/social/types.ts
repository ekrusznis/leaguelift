/** Matches backend/src/main/kotlin/com/rally26/integration/core/web/IntegrationDto.kt — shared with every other integration, not social-specific. */
export type SocialProvider = 'INSTAGRAM' | 'FACEBOOK' | 'X';

export type SocialReadiness = 'AVAILABLE' | 'NOT_CONFIGURED' | 'PARTNER_PENDING' | 'PLATFORM_MANAGED' | 'UNSUPPORTED';

export type SocialConnectionStatus =
  | 'NOT_CONFIGURED'
  | 'AVAILABLE'
  | 'AUTHORIZATION_PENDING'
  | 'CONNECTED'
  | 'DEGRADED'
  | 'REVOKED'
  | 'DISCONNECTED'
  | 'UNSUPPORTED';

export interface SocialConnection {
  id: string;
  provider: SocialProvider;
  status: SocialConnectionStatus;
  externalAccountId: string | null;
  externalAccountName: string | null;
  lastErrorMessage: string | null;
  connectedAt: string | null;
}

export interface SocialCatalogItem {
  provider: SocialProvider;
  displayName: string;
  category: string;
  readiness: SocialReadiness;
  connection: SocialConnection | null;
}

export interface AuthorizationStart {
  authorizationUrl: string;
  expiresAt: string;
}

/** Matches backend/src/main/kotlin/com/rally26/social/domain/SocialPostDraft.kt. Only FUNDRAISER generates real content today — the rest fail closed with a clear message. */
export type SocialDraftSourceType = 'EVENT' | 'FUNDRAISER' | 'SPONSORSHIP' | 'MEDIA' | 'SWAG_PRODUCT' | 'SWAG_SHOP';

export interface SocialPostDraft {
  id: string;
  sourceType: SocialDraftSourceType;
  sourceId: string;
  organizationId: string;
  teamId: string | null;
  title: string;
  caption: string;
  publicUrl: string;
  allowedProviders: SocialProvider[];
  createdAt: string;
}

export type SocialPublishingStatus = 'PUBLISHING' | 'PUBLISHED' | 'FAILED';

export interface SocialPublishingHistory {
  id: string;
  draftId: string;
  provider: SocialProvider;
  sourceType: SocialDraftSourceType;
  sourceId: string;
  publicUrl: string;
  providerPostUrl: string | null;
  status: SocialPublishingStatus;
  failureMessageSafe: string | null;
  publishedAt: string | null;
  createdAt: string;
}
