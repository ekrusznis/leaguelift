import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import { initNewRelicBrowserAgent } from "./monitoring/newRelicBrowser";
import "./styles/globals.css";

initNewRelicBrowserAgent();

createRoot(document.getElementById("root")!).render(
	<StrictMode>
		<App />
	</StrictMode>,
);
