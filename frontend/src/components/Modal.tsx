import { useEffect, useRef } from "react";
import type { ReactNode } from "react";

/**
 * First real dialog/modal primitive in this codebase — every prior "confirm before
 * a consequential action" flow used a bare `window.confirm`. Shared by the reorder
 * unavailable-vendor notice, the sync-update-from-source diff confirmation, and the
 * household media "release publicly" explanation dialog.
 */
export function Modal({
	open,
	onClose,
	title,
	children,
	actions,
}: {
	open: boolean;
	onClose: () => void;
	title: string;
	children: ReactNode;
	actions?: ReactNode;
}) {
	const dialogRef = useRef<HTMLDivElement>(null);

	useEffect(() => {
		if (!open) return;
		const previouslyFocused = document.activeElement as HTMLElement | null;
		dialogRef.current?.focus();

		function onKeyDown(event: KeyboardEvent) {
			if (event.key === "Escape") {
				onClose();
				return;
			}
			if (event.key !== "Tab" || !dialogRef.current) return;
			const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
				'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
			);
			if (focusable.length === 0) return;
			const first = focusable[0];
			const last = focusable[focusable.length - 1];
			if (event.shiftKey && document.activeElement === first) {
				event.preventDefault();
				last.focus();
			} else if (!event.shiftKey && document.activeElement === last) {
				event.preventDefault();
				first.focus();
			}
		}

		document.addEventListener("keydown", onKeyDown);
		return () => {
			document.removeEventListener("keydown", onKeyDown);
			previouslyFocused?.focus();
		};
	}, [open, onClose]);

	if (!open) return null;

	return (
		<div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="presentation">
			<div className="absolute inset-0 bg-navy/50" onClick={onClose} aria-hidden="true" />
			<div
				ref={dialogRef}
				role="dialog"
				aria-modal="true"
				aria-labelledby="modal-title"
				tabIndex={-1}
				className="relative flex max-h-[90vh] w-full max-w-lg flex-col gap-4 overflow-y-auto rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-6 shadow-xl focus:outline-none"
			>
				<h2 id="modal-title" className="font-heading text-lg font-bold text-navy dark:text-[#f8fafc]">
					{title}
				</h2>
				<div className="text-sm text-slate-gray dark:text-[#cbd5e1]">{children}</div>
				{actions && <div className="mt-2 flex flex-wrap justify-end gap-2">{actions}</div>}
			</div>
		</div>
	);
}
