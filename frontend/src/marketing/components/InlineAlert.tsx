import type { ReactNode } from "react";

type InlineAlertProps = {
	tone: "success" | "error" | "info" | "warning";
	title: string;
	children?: ReactNode;
};

const TONE_STYLES: Record<InlineAlertProps["tone"], string> = {
	success: "border-green-500/30 bg-green-500/5 text-success-700",
	error: "border-error-600/30 bg-error-600/5 text-error-600",
	info: "border-info-600/30 bg-info-600/5 text-info-600",
	warning: "border-warning-600/30 bg-gold-500/10 text-warning-600",
};

export function InlineAlert({ tone, title, children }: InlineAlertProps) {
	return (
		<div role={tone === "error" ? "alert" : "status"} className={`rounded-xl border p-4 ${TONE_STYLES[tone]}`}>
			<p className="font-semibold">{title}</p>
			{children && <div className="mt-1 text-sm leading-relaxed">{children}</div>}
		</div>
	);
}
