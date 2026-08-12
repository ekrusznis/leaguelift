/** Mirrors frontend/src/features/support/types.ts — the personal-use subset mobile needs (no platform-admin article/case management, not a mobile persona). */

export type SupportArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface SupportArticle {
  id: string;
  slug: string;
  title: string;
  summary: string;
  bodyMarkdown: string;
  category: string;
  status: SupportArticleStatus;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export const HELP_CATEGORIES = [
  'Getting Started',
  'Organization Setup',
  'Teams and Families',
  'Fees and Payments',
  'Fundraising',
  'Stores and Orders',
  'Sponsorships',
  'Events and RSVP',
  'Accounts and Invitations',
  'Troubleshooting',
] as const;

export type SupportCaseCategory =
  | 'ACCOUNT_ACCESS'
  | 'ORGANIZATION_SETUP'
  | 'TEAM_OR_ROSTER'
  | 'FEES_OR_PAYMENTS'
  | 'FUNDRAISING'
  | 'STORE_OR_ORDER'
  | 'SPONSORSHIP'
  | 'EVENT_OR_RSVP'
  | 'TECHNICAL_PROBLEM'
  | 'PRIVACY_OR_SAFETY'
  | 'OTHER';

export type SupportCaseStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_ON_CUSTOMER' | 'RESOLVED' | 'CLOSED';

export interface SupportCase {
  id: string;
  organizationId: string | null;
  organizationName: string | null;
  category: SupportCaseCategory;
  subject: string;
  description: string;
  status: SupportCaseStatus;
  resolution: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export const SUPPORT_CASE_CATEGORIES: { value: SupportCaseCategory; label: string }[] = [
  { value: 'ACCOUNT_ACCESS', label: 'Account access' },
  { value: 'ORGANIZATION_SETUP', label: 'Organization setup' },
  { value: 'TEAM_OR_ROSTER', label: 'Team or roster' },
  { value: 'FEES_OR_PAYMENTS', label: 'Fees or payments' },
  { value: 'FUNDRAISING', label: 'Fundraising' },
  { value: 'STORE_OR_ORDER', label: 'Store or order' },
  { value: 'SPONSORSHIP', label: 'Sponsorship' },
  { value: 'EVENT_OR_RSVP', label: 'Event or RSVP' },
  { value: 'TECHNICAL_PROBLEM', label: 'Technical problem' },
  { value: 'PRIVACY_OR_SAFETY', label: 'Privacy or safety' },
  { value: 'OTHER', label: 'Other' },
];
