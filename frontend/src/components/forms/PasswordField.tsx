import { forwardRef, useId, useState, type InputHTMLAttributes } from "react";

type PasswordFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
	label: string;
	error?: string;
	hint?: string;
	tone?: "light" | "dark";
};

/** Show/hide toggle, allows paste and browser-generated passwords (section 11.4). */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(function PasswordField(
	{ label, error, hint, tone = "light", id, required, className = "", ...props },
	ref,
) {
	const [visible, setVisible] = useState(false);
	const generatedId = useId();
	const fieldId = id ?? generatedId;
	const hintId = hint ? `${fieldId}-hint` : undefined;
	const errorId = error ? `${fieldId}-error` : undefined;
	const labelClassName = tone === "dark" ? "text-white" : "text-navy-900";
	const hintClassName = tone === "dark" ? "text-slate-300" : "text-slate-500";
	const requiredClassName = tone === "dark" ? "text-gold-400" : "text-error-600";
	const errorClassName = tone === "dark" ? "text-gold-400" : "text-error-600";

	return (
		<div className="flex flex-col gap-1.5">
			<label htmlFor={fieldId} className={`text-sm font-semibold ${labelClassName}`}>
				{label}
				{required && (
					<span aria-hidden="true" className={requiredClassName}>
						{" "}
						*
					</span>
				)}
			</label>
			<div className="relative">
				<input
					id={fieldId}
					ref={ref}
					type={visible ? "text" : "password"}
					required={required}
					aria-invalid={!!error}
					aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
					className={`min-h-12 w-full rounded-[10px] border bg-white px-3.5 py-2.5 pr-12 text-navy-900 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 ${
						error ? "border-error-600" : "border-slate-200"
					} ${className}`}
					{...props}
				/>
				<button
					type="button"
					onClick={() => setVisible((value) => !value)}
					aria-label={visible ? "Hide password" : "Show password"}
					aria-pressed={visible}
					className="absolute right-1 top-1/2 -translate-y-1/2 rounded-md p-2 text-slate-500 hover:text-navy-900 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400"
				>
					{visible ? (
						<svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
							<path
								d="M3 3l18 18M10.6 10.6a2.5 2.5 0 0 0 3.5 3.5M6.5 6.7C4.3 8.1 2.7 10 2 12c1.6 4 5.6 7 10 7 1.7 0 3.3-.4 4.7-1.2M9.9 4.2A10.6 10.6 0 0 1 12 4c4.4 0 8.4 3 10 7-.5 1.2-1.2 2.4-2.1 3.4"
								stroke="currentColor"
								strokeWidth="1.6"
								strokeLinecap="round"
								strokeLinejoin="round"
							/>
						</svg>
					) : (
						<svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
							<path
								d="M2 12c1.6-4 5.6-7 10-7s8.4 3 10 7c-1.6 4-5.6 7-10 7s-8.4-3-10-7Z"
								stroke="currentColor"
								strokeWidth="1.6"
								strokeLinejoin="round"
							/>
							<circle cx="12" cy="12" r="2.75" stroke="currentColor" strokeWidth="1.6" />
						</svg>
					)}
				</button>
			</div>
			{hint && !error && (
				<p id={hintId} className={`text-sm ${hintClassName}`}>
					{hint}
				</p>
			)}
			{error && (
				<p id={errorId} role="alert" className={`text-sm ${errorClassName}`}>
					{error}
				</p>
			)}
		</div>
	);
});
