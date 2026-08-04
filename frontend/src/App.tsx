import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { queryClient } from "./lib/queryClient";
import { AppRoutes } from "./routes/AppRoutes";
import { ScrollToTop } from "./routes/ScrollToTop";

function App() {
	return (
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				<BrowserRouter>
					<ScrollToTop />
					<AppRoutes />
				</BrowserRouter>
			</AuthProvider>
		</QueryClientProvider>
	);
}

export default App;
