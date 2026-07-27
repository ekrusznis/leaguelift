import { Route, Routes } from "react-router-dom";
import { AppShell } from "../app/AppShell";
import { DashboardPage } from "../pages/DashboardPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { OrganizationDetailPage } from "../pages/OrganizationDetailPage";
import { OrganizationsPage } from "../pages/OrganizationsPage";
import { ProtectedRoute } from "./ProtectedRoute";

export function AppRoutes() {
	return (
		<Routes>
			<Route
				element={
					<ProtectedRoute>
						<AppShell />
					</ProtectedRoute>
				}
			>
				<Route index element={<DashboardPage />} />
				<Route path="organizations" element={<OrganizationsPage />} />
				<Route path="organizations/:organizationId" element={<OrganizationDetailPage />} />
			</Route>
			<Route path="*" element={<NotFoundPage />} />
		</Routes>
	);
}
