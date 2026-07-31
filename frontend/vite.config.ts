/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [react(), tailwindcss()],
	build: {
		rollupOptions: {
			output: {
				manualChunks(id) {
					if (!id.includes("node_modules")) {
						return undefined;
					}

					if (id.includes("react-router-dom") || id.includes("react-dom") || id.includes("node_modules/react/")) {
						return "react";
					}

					if (id.includes("@tanstack/react-query")) {
						return "query";
					}

					if (id.includes("react-hook-form") || id.includes("@hookform/resolvers") || id.includes("/zod/")) {
						return "forms";
					}

					return undefined;
				},
			},
		},
	},
	test: {
		environment: "jsdom",
		exclude: ["e2e/**", "node_modules/**"],
		globals: true,
		setupFiles: ["./src/test/setup.ts"],
		css: true,
	},
});
