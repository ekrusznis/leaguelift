import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../app/AppShell";
import { AuthLayout } from "../app/AuthLayout";
import { MarketingLayout } from "../app/MarketingLayout";
import { Capabilities } from "../authorization/capabilityConstants";
import { RequireCapability } from "../authorization/RequireCapability";
import { ActionCenterPage } from "../features/actionCenter/ActionCenterPage";
import { AnnouncementsPage } from "../features/communications/AnnouncementsPage";
import { CollectionsPage } from "../features/collections/CollectionsPage";
import { DisputesPage } from "../features/disputes/DisputesPage";
import { EventDetailPage } from "../features/events/EventDetailPage";
import { EventsPage } from "../features/events/EventsPage";
import { PersonalIntegrationsPage } from "../features/integrations/PersonalIntegrationsPage";
import { MessagesPage } from "../features/messaging/MessagesPage";
import { AuditHistoryPage } from "../features/auditHistory/AuditHistoryPage";
import { OrganizationBillingPage } from "../features/subscriptionBilling/OrganizationBillingPage";
import { PlatformSubscriptionsPage } from "../features/subscriptionBilling/PlatformSubscriptionsPage";
import { PlatformAdminConsoleLayout } from "../features/platformAdmin/PlatformAdminConsoleLayout";
import { PlatformAuditPage } from "../features/platformAdmin/PlatformAuditPage";
import { PlatformOperationsPage } from "../features/platformAdmin/PlatformOperationsPage";
import { PlatformOrganizationConsolePage } from "../features/platformAdmin/PlatformOrganizationConsolePage";
import { PlatformOrganizationsPage } from "../features/platformAdmin/PlatformOrganizationsPage";
import { PlatformSupportSessionsPage } from "../features/platformAdmin/PlatformSupportSessionsPage";
import { PlatformSwagShopPage } from "../features/platformAdmin/PlatformSwagShopPage";
import { PlatformPaymentsPage } from "../features/platformAdmin/PlatformPaymentsPage";
import { PlatformDuplicateIdentitiesPage } from "../features/platformAdmin/PlatformDuplicateIdentitiesPage";
import { PlatformUsersPage } from "../features/platformAdmin/PlatformUsersPage";
import { PlatformReportsPage } from "../features/reporting/PlatformReportsPage";
import { SettingsPage } from "../features/settings/SettingsPage";
import { SwagShopOrderFlow } from "../features/swagshop/SwagShopOrderFlow";
import { TeamRosterPage } from "../features/teams/TeamRosterPage";
import { HelpArticlePage } from "../features/support/HelpArticlePage";
import { HelpCenterPage } from "../features/support/HelpCenterPage";
import { PlatformHelpArticlesPage } from "../features/support/PlatformHelpArticlesPage";
import { PlatformSupportCasesPage } from "../features/support/PlatformSupportCasesPage";
import { SupportRequestPage } from "../features/support/SupportRequestPage";
import { DashboardPage } from "../pages/DashboardPage";
import { HouseholdDetailPage } from "../pages/HouseholdDetailPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { OrganizationDetailPage } from "../pages/OrganizationDetailPage";
import { OrganizationsPage } from "../pages/OrganizationsPage";
import { OwnerOnboardingPage } from "../pages/OwnerOnboardingPage";
import { PublicPageView } from "../pages/PublicPageView";
import { AuthErrorPage } from "../pages/auth/AuthErrorPage";
import { ForgotPasswordPage } from "../pages/auth/ForgotPasswordPage";
import { InvitationPage } from "../pages/auth/InvitationPage";
import { RegisterPage } from "../pages/auth/RegisterPage";
import { ResendVerificationPage } from "../pages/auth/ResendVerificationPage";
import { ResetPasswordPage } from "../pages/auth/ResetPasswordPage";
import { SignInPage } from "../pages/auth/SignInPage";
import { VerifyEmailPage } from "../pages/auth/VerifyEmailPage";
import { ContactRedirect } from "../pages/marketing/ContactRedirect";
import { HomePage } from "../pages/marketing/HomePage";
import { PublicAthleteStorefrontView } from "../pages/marketing/PublicAthleteStorefrontView";
import { PublicCampaignView } from "../pages/marketing/PublicCampaignView";
import { PublicSponsorshipView } from "../pages/marketing/PublicSponsorshipView";
import { PublicStoreView } from "../pages/marketing/PublicStoreView";
import { SecurityPage } from "../pages/marketing/SecurityPage";
import { SolutionDetailPage } from "../pages/marketing/SolutionDetailPage";
import { TalkToSalesPage } from "../pages/marketing/TalkToSalesPage";
import { AccessibilityPage } from "../pages/marketing/legal/AccessibilityPage";
import { PrivacyPage } from "../pages/marketing/legal/PrivacyPage";
import { TermsPage } from "../pages/marketing/legal/TermsPage";
import { ProtectedRoute } from "./ProtectedRoute";

function platformGuard(capability: string, child: ReactNode) {
	return <RequireCapability capability={capability} contextType="PLATFORM_ADMIN" resourceId={null}>{child}</RequireCapability>;
}

export function AppRoutes() {
	return (
		<Routes>
			<Route path="/" element={<HomePage />} />
			<Route element={<MarketingLayout />}>
				<Route path="solutions/:slug" element={<SolutionDetailPage />} />
				<Route path="talk-to-sales" element={<TalkToSalesPage />} />
				<Route path="founding-pilot" element={<Navigate to="/talk-to-sales" replace />} />
				<Route path="book-demo" element={<Navigate to="/talk-to-sales" replace />} />
				<Route path="contact" element={<ContactRedirect />} />
				<Route path="security" element={<SecurityPage />} />
				<Route path="help" element={<HelpCenterPage />} />
				<Route path="help/support" element={<SupportRequestPage />} />
				<Route path="help/:slug" element={<HelpArticlePage />} />
				<Route path="privacy" element={<PrivacyPage />} />
				<Route path="terms" element={<TermsPage />} />
				<Route path="accessibility" element={<AccessibilityPage />} />
				<Route path="campaigns/:slug" element={<PublicCampaignView />} />
				<Route path="swag-shop/:slug" element={<PublicStoreView />} />
				<Route path="swag-shop/athlete/:slug" element={<PublicAthleteStorefrontView />} />
				<Route path="sponsors/:slug" element={<PublicSponsorshipView />} />
				<Route path="404" element={<NotFoundPage />} />
			</Route>
			<Route element={<AuthLayout />}>
				<Route path="auth/sign-in" element={<SignInPage />} />
				<Route path="auth/register" element={<RegisterPage />} />
				<Route path="auth/resend-verification" element={<ResendVerificationPage />} />
				<Route path="auth/forgot-password" element={<ForgotPasswordPage />} />
				<Route path="auth/reset-password" element={<ResetPasswordPage />} />
				<Route path="auth/verify-email" element={<VerifyEmailPage />} />
				<Route path="auth/invitation" element={<InvitationPage />} />
				<Route path="auth/error" element={<AuthErrorPage />} />
			</Route>
			<Route path="p/:slug" element={<PublicPageView />} />
			<Route path="app" element={<ProtectedRoute />}>
				<Route index element={<DashboardPage />} />
				<Route path="onboarding/:step?" element={<OwnerOnboardingPage />} />
				<Route element={<AppShell />}>
					<Route path="action-center" element={<ActionCenterPage />} />
					<Route path="announcements" element={<AnnouncementsPage />} />
					<Route path="messages" element={<MessagesPage />} />
					<Route path="history" element={<AuditHistoryPage />} />
					<Route path="integrations" element={<PersonalIntegrationsPage />} />
					<Route path="settings" element={<SettingsPage />} />
					<Route path="help" element={<HelpCenterPage authenticated />} />
					<Route path="help/support" element={<SupportRequestPage authenticated />} />
					<Route path="help/:slug" element={<HelpArticlePage authenticated />} />
					<Route path="organizations" element={<OrganizationsPage />} />
					<Route path="organizations/:organizationId" element={<OrganizationDetailPage />} />
					<Route path="organizations/:organizationId/billing" element={<OrganizationBillingPage />} />
					<Route path="organizations/:organizationId/collections" element={<CollectionsPage />} />
					<Route path="organizations/:organizationId/disputes" element={<DisputesPage />} />
					<Route path="organizations/:organizationId/swag-shop/order" element={<SwagShopOrderFlow />} />
					<Route path="organizations/:organizationId/events/:eventId" element={<EventDetailPage />} />
					<Route path="organizations/:organizationId/teams/:teamId/events" element={<EventsPage scopeType="team" />} />
					<Route path="organizations/:organizationId/teams/:teamId/roster" element={<TeamRosterPage />} />
					<Route path="organizations/:organizationId/tournaments/:tournamentId/events" element={<EventsPage scopeType="tournament" />} />
					<Route path="organizations/:organizationId/participants/:participantId/events" element={<EventsPage scopeType="participant" />} />
					<Route path="organizations/:organizationId/households/:householdId" element={<HouseholdDetailPage />} />
					<Route path="organizations/:organizationId/households/:householdId/:section" element={<HouseholdDetailPage />} />
					<Route path="organizations/:organizationId/:section" element={<OrganizationDetailPage />} />
				</Route>
				<Route path="platform" element={platformGuard(Capabilities.PLATFORM_ORG_VIEW, <PlatformAdminConsoleLayout />)}>
					<Route index element={<Navigate to="organizations" replace />} />
					<Route path="organizations" element={platformGuard(Capabilities.PLATFORM_ORG_VIEW, <PlatformOrganizationsPage />)} />
					<Route path="organizations/:organizationId" element={platformGuard(Capabilities.PLATFORM_ORG_VIEW, <PlatformOrganizationConsolePage />)} />
					<Route path="users" element={platformGuard(Capabilities.PLATFORM_USER_VIEW, <PlatformUsersPage />)} />
					<Route path="operations" element={platformGuard(Capabilities.PLATFORM_INTEGRATION_VIEW, <PlatformOperationsPage />)} />
					<Route path="reports" element={platformGuard(Capabilities.PLATFORM_AUDIT_VIEW, <PlatformReportsPage />)} />
					<Route path="audit" element={platformGuard(Capabilities.PLATFORM_AUDIT_VIEW, <PlatformAuditPage />)} />
					<Route path="support-sessions" element={platformGuard(Capabilities.PLATFORM_SUPPORT_ACCESS, <PlatformSupportSessionsPage />)} />
					<Route path="help-articles" element={platformGuard(Capabilities.PLATFORM_HELP_MANAGE, <PlatformHelpArticlesPage />)} />
					<Route path="support-cases" element={platformGuard(Capabilities.PLATFORM_SUPPORT_CASE_MANAGE, <PlatformSupportCasesPage />)} />
					<Route path="swag-shop" element={platformGuard(Capabilities.PLATFORM_SWAG_SHOP_VIEW, <PlatformSwagShopPage />)} />
					<Route path="payments" element={platformGuard(Capabilities.PLATFORM_PAYMENTS_VIEW, <PlatformPaymentsPage />)} />
					<Route path="subscriptions" element={platformGuard(Capabilities.PLATFORM_ORG_VIEW, <PlatformSubscriptionsPage />)} />
					<Route path="data-integrity/duplicates" element={platformGuard(Capabilities.PLATFORM_USER_VIEW, <PlatformDuplicateIdentitiesPage />)} />
				</Route>
			</Route>
			<Route element={<MarketingLayout />}>
				<Route path="*" element={<NotFoundPage />} />
			</Route>
		</Routes>
	);
}
