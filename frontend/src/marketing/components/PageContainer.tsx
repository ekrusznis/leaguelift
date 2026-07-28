import type { HTMLAttributes } from "react";

/**
 * Content-width wrapper (LEAGUELIFT_SALES_SITE_DESIGN.md section 6.2):
 * `width: min(100% - 40px, 1360px)`, centered.
 */
export function PageContainer({ className = "", ...props }: HTMLAttributes<HTMLDivElement>) {
	return (
		<div
			{...props}
			className={`mx-auto w-[min(100%-40px,1360px)] px-0 sm:w-[min(100%-64px,1360px)] lg:w-[min(100%-96px,1360px)] ${className}`}
		/>
	);
}
