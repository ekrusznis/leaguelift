import { Link } from "react-router-dom";

/** Sign In / Create Account tabs at the top of the auth card (section 24.1). */
export function AuthTabs({ active }: { active: "sign-in" | "register" }) {
	const tabs = [
		{ key: "sign-in", label: "Sign In", to: "/auth/sign-in" },
		{ key: "register", label: "Create Account", to: "/auth/register" },
	] as const;

	return (
		<div role="tablist" aria-label="Authentication" className="flex gap-6 border-b border-white/10">
			{tabs.map((tab) => {
				const isActive = tab.key === active;
				return (
					<Link
						key={tab.key}
						to={tab.to}
						role="tab"
						aria-selected={isActive}
						className={`relative pb-3 text-sm font-semibold ${isActive ? "text-white" : "text-slate-400 hover:text-slate-200"}`}
					>
						{tab.label}
						{isActive && <span className="absolute inset-x-0 -bottom-px h-0.5 rounded-full bg-green-500" aria-hidden="true" />}
					</Link>
				);
			})}
		</div>
	);
}
