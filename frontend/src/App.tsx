import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { queryClient } from "./lib/queryClient";
import { AppRoutes } from "./routes/AppRoutes";

function App() {
	return (
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				<BrowserRouter>
					<AppRoutes />
				</BrowserRouter>
			</AuthProvider>
		</QueryClientProvider>
	);
}

export default App;
