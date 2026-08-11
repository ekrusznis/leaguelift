import { Link } from "react-router-dom";

/** Sign In / Create Account tabs at the top of the auth card (section 24.1). */
export function AuthTabs({ active, tone = "light" }: { active: "sign-in" | "register"; tone?: "light" | "dark" }) {
	const tabs = [
		{ key: "sign-in", label: "Sign In", to: "/auth/sign-in" },
		{ key: "register", label: "Create Account", to: "/auth/register" },
	] as const;

	const borderClassName = tone === "dark" ? "border-white/10" : "border-slate-300 dark:border-[#334155]";

	return (
		<div role="tablist" aria-label="Authentication" className={`flex gap-6 border-b ${borderClassName}`}>
			{tabs.map((tab) => {
				const isActive = tab.key === active;
				return (
					<Link
						key={tab.key}
						to={tab.to}
						role="tab"
						aria-selected={isActive}
						className={`relative pb-3 text-sm font-semibold ${
						isActive
							? tone === "dark" ? "text-white" : "text-navy-900 dark:text-[#f8fafc]"
							: tone === "dark" ? "text-slate-400 hover:text-slate-200" : "text-slate-600 dark:text-[#cbd5e1] hover:text-navy-900 hover:dark:text-[#f8fafc]"
					}`}
					>
						{tab.label}
						{isActive && <span className="absolute inset-x-0 -bottom-px h-0.5 rounded-full bg-green-500" aria-hidden="true" />}
					</Link>
				);
			})}
		</div>
	);
}
