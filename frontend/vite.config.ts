/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [react(), tailwindcss()],
	test: {
		environment: "jsdom",
		exclude: ["e2e/**", "node_modules/**"],
		globals: true,
		setupFiles: ["./src/test/setup.ts"],
		css: true,
	},
});
