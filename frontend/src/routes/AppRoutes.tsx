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
import { DashboardPage } from "../pages/DashboardPage";
import { CollectionsPage } from "../features/collections/CollectionsPage";
import { HouseholdDetailPage } from "../pages/HouseholdDetailPage";
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
import { OrganizationDetailPage } from "../pages/OrganizationDetailPage";
import { OrganizationsPage } from "../pages/OrganizationsPage";
import { PublicPageView } from "../pages/PublicPageView";
import { ProtectedRoute } from "./ProtectedRoute";

export function AppRoutes() {
	return (
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
				<Route element={<AppShell />}>
					<Route path="organizations" element={<OrganizationsPage />} />
					<Route path="organizations/:organizationId" element={<OrganizationDetailPage />} />
					<Route path="organizations/:organizationId/collections" element={<CollectionsPage />} />
					<Route path="organizations/:organizationId/households/:householdId" element={<HouseholdDetailPage />} />
				</Route>
			</Route>

			<Route path="*" element={<NotFoundPage />} />
		</Routes>
	);
}
