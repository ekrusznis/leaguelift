import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "danger";

const VARIANT_CLASSES: Record<Variant, string> = {
	primary: "bg-victory-green text-pure-white hover:opacity-90 focus-visible:outline-victory-green",
	secondary: "bg-pure-white text-navy border border-slate-gray/30 hover:bg-ice-white focus-visible:outline-info-blue",
	danger: "bg-error-red text-pure-white hover:opacity-90 focus-visible:outline-error-red",
};

export function Button({
	variant = "primary",
	className = "",
	...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
	return (
		<button
			{...props}
			className={`min-h-11 rounded-md px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT_CLASSES[variant]} ${className}`}
		/>
	);
}
