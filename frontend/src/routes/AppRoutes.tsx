import { Suspense, lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../app/AppShell";
import { AuthLayout } from "../app/AuthLayout";
import { MarketingLayout } from "../app/MarketingLayout";
import { AuthErrorPage } from "../pages/auth/AuthErrorPage";
import { ForgotPasswordPage } from "../pages/auth/ForgotPasswordPage";
import { InvitationPage } from "../pages/auth/InvitationPage";
import { RegisterPage } from "../pages/auth/RegisterPage";
import { ResetPasswordPage } from "../pages/auth/ResetPasswordPage";
import { SignInPage } from "../pages/auth/SignInPage";
import { AboutPage } from "../pages/marketing/AboutPage";
import { BookDemoPage } from "../pages/marketing/BookDemoPage";
import { ContactPage } from "../pages/marketing/ContactPage";
import { HelpPage } from "../pages/marketing/HelpPage";
import { HomePage } from "../pages/marketing/HomePage";
import { HowItWorksPage } from "../pages/marketing/HowItWorksPage";
import { LandingPreviewPage } from "../pages/marketing/LandingPreviewPage";
import { AccessibilityPage } from "../pages/marketing/legal/AccessibilityPage";
import { PrivacyPage } from "../pages/marketing/legal/PrivacyPage";
import { TermsPage } from "../pages/marketing/legal/TermsPage";
import { PricingPage } from "../pages/marketing/PricingPage";
import { PublicCampaignView } from "../pages/marketing/PublicCampaignView";
import { PublicSponsorshipView } from "../pages/marketing/PublicSponsorshipView";
import { PublicStoreView } from "../pages/marketing/PublicStoreView";
import { SecurityPage } from "../pages/marketing/SecurityPage";
import { SolutionDetailPage } from "../pages/marketing/SolutionDetailPage";
import { SolutionsOverviewPage } from "../pages/marketing/SolutionsOverviewPage";
import { TalkToSalesPage } from "../pages/marketing/TalkToSalesPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { PublicPageView } from "../pages/PublicPageView";
import { ProtectedRoute } from "./ProtectedRoute";
import { RequireCapability } from "../authorization/RequireCapability";
import { Capabilities } from "../authorization/capabilityConstants";

const DashboardPage = lazy(() => import("../pages/DashboardPage").then((m) => ({ default: m.DashboardPage })));
const CollectionsPage = lazy(() => import("../features/collections/CollectionsPage").then((m) => ({ default: m.CollectionsPage })));
const EventDetailPage = lazy(() => import("../features/events/EventDetailPage").then((m) => ({ default: m.EventDetailPage })));
const EventsPage = lazy(() => import("../features/events/EventsPage").then((m) => ({ default: m.EventsPage })));
const PlatformAdminConsoleLayout = lazy(() => import("../features/platformAdmin/PlatformAdminConsoleLayout").then((m) => ({ default: m.PlatformAdminConsoleLayout })));
const PlatformAuditPage = lazy(() => import("../features/platformAdmin/PlatformAuditPage").then((m) => ({ default: m.PlatformAuditPage })));
const PlatformOperationsPage = lazy(() => import("../features/platformAdmin/PlatformOperationsPage").then((m) => ({ default: m.PlatformOperationsPage })));
const PlatformOrganizationConsolePage = lazy(() => import("../features/platformAdmin/PlatformOrganizationConsolePage").then((m) => ({ default: m.PlatformOrganizationConsolePage })));
const PlatformOrganizationsPage = lazy(() => import("../features/platformAdmin/PlatformOrganizationsPage").then((m) => ({ default: m.PlatformOrganizationsPage })));
const PlatformUsersPage = lazy(() => import("../features/platformAdmin/PlatformUsersPage").then((m) => ({ default: m.PlatformUsersPage })));
const PlatformSupportSessionsPage = lazy(() => import("../features/platformAdmin/PlatformSupportSessionsPage").then((m) => ({ default: m.PlatformSupportSessionsPage })));
const PlatformReportsPage = lazy(() => import("../features/reporting/PlatformReportsPage").then((m) => ({ default: m.PlatformReportsPage })));
const HouseholdDetailPage = lazy(() => import("../pages/HouseholdDetailPage").then((m) => ({ default: m.HouseholdDetailPage })));
const OrganizationDetailPage = lazy(() => import("../pages/OrganizationDetailPage").then((m) => ({ default: m.OrganizationDetailPage })));
const OrganizationsPage = lazy(() => import("../pages/OrganizationsPage").then((m) => ({ default: m.OrganizationsPage })));

export function AppRoutes() {
	return (
		<Suspense fallback={null}>
			<Routes>
			<Route element={<MarketingLayout />}>
				<Route index element={<HomePage />} />
				<Route path="how-it-works" element={<HowItWorksPage />} />
				<Route path="solutions" element={<SolutionsOverviewPage />} />
				<Route path="solutions/:slug" element={<SolutionDetailPage />} />
				<Route path="pricing" element={<PricingPage />} />
				<Route path="talk-to-sales" element={<TalkToSalesPage />} />
				<Route path="founding-pilot" element={<Navigate to="/talk-to-sales" replace />} />
				<Route path="book-demo" element={<BookDemoPage />} />
				<Route path="about" element={<AboutPage />} />
				<Route path="contact" element={<ContactPage />} />
				<Route path="security" element={<SecurityPage />} />
				<Route path="help" element={<HelpPage />} />
				<Route path="privacy" element={<PrivacyPage />} />
				<Route path="terms" element={<TermsPage />} />
				<Route path="accessibility" element={<AccessibilityPage />} />
				<Route path="campaigns/:slug" element={<PublicCampaignView />} />
				<Route path="stores/:slug" element={<PublicStoreView />} />
				<Route path="sponsors/:slug" element={<PublicSponsorshipView />} />
				<Route path="404" element={<NotFoundPage />} />
			</Route>

			<Route element={<AuthLayout />}>
				<Route path="auth/sign-in" element={<SignInPage />} />
				<Route path="auth/register" element={<RegisterPage />} />
				<Route path="auth/forgot-password" element={<ForgotPasswordPage />} />
				<Route path="auth/reset-password" element={<ResetPasswordPage />} />
				<Route path="auth/invitation" element={<InvitationPage />} />
				<Route path="auth/error" element={<AuthErrorPage />} />
			</Route>

			{/* Single-page redesign preview — not nested under MarketingLayout since it
			    brings its own header/footer to demo an anchor-nav IA (see sales-site
			    redesign review). Remove once the comparison is settled. */}
			<Route path="landing-preview" element={<LandingPreviewPage />} />

			<Route path="p/:slug" element={<PublicPageView />} />

			<Route path="app" element={<ProtectedRoute />}>
				{/* The dashboard-role preview renders its own full shell (docs/LEAGUELIFT_DASHBOARD_DESIGN.md
				    section 4.2), so it deliberately sits outside AppShell's simpler nav below. */}
				<Route index element={<DashboardPage />} />
				<Route
					path="platform"
					element={
						<RequireCapability capability={Capabilities.PLATFORM_ORG_VIEW} contextType="PLATFORM_ADMIN" resourceId={null}>
							<PlatformAdminConsoleLayout />
						</RequireCapability>
					}
				>
					<Route index element={<Navigate to="organizations" replace />} />
					<Route path="organizations" element={<PlatformOrganizationsPage />} />
					<Route path="organizations/:organizationId" element={<PlatformOrganizationConsolePage />} />
					<Route path="users" element={<PlatformUsersPage />} />
					<Route path="operations" element={<PlatformOperationsPage />} />
					<Route path="reports" element={<PlatformReportsPage />} />
					<Route path="audit" element={<PlatformAuditPage />} />
					<Route path="support-sessions" element={<PlatformSupportSessionsPage />} />
				</Route>
				<Route element={<AppShell />}>
					<Route path="organizations" element={<OrganizationsPage />} />
					<Route path="organizations/:organizationId" element={<OrganizationDetailPage />} />
					<Route path="organizations/:organizationId/collections" element={<CollectionsPage />} />
					<Route path="organizations/:organizationId/events/:eventId" element={<EventDetailPage />} />
					<Route path="organizations/:organizationId/teams/:teamId/events" element={<EventsPage scopeType="team" />} />
					<Route path="organizations/:organizationId/tournaments/:tournamentId/events" element={<EventsPage scopeType="tournament" />} />
					<Route path="organizations/:organizationId/participants/:participantId/events" element={<EventsPage scopeType="participant" />} />
					<Route path="organizations/:organizationId/households/:householdId" element={<HouseholdDetailPage />} />
					<Route path="organizations/:organizationId/households/:householdId/:section" element={<HouseholdDetailPage />} />
					<Route path="organizations/:organizationId/:section" element={<OrganizationDetailPage />} />
				</Route>
			</Route>

			<Route path="*" element={<NotFoundPage />} />
			</Routes>
		</Suspense>
	);
}
