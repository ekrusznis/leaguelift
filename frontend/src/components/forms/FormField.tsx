import { forwardRef, useId, type InputHTMLAttributes } from "react";

type FormFieldProps = InputHTMLAttributes<HTMLInputElement> & {
	label: string;
	error?: string;
	hint?: string;
	tone?: "light" | "dark";
};

/**
 * Text/email/tel/number input with label above, helper text below, and an error
 * linked via aria-describedby (LEAGUELIFT_SALES_SITE_DESIGN.md section 11.1/11.3).
 * Forwards a ref so it composes with `register()` from react-hook-form.
 */
export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(function FormField(
	{ label, error, hint, tone = "light", id, required, className = "", ...props },
	ref,
) {
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
			<input
				id={fieldId}
				ref={ref}
				required={required}
				aria-invalid={!!error}
				aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
				className={`min-h-12 rounded-[10px] border bg-white px-3.5 py-2.5 text-navy-900 placeholder:text-slate-500 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 disabled:cursor-not-allowed disabled:bg-slate-200/40 ${
					error ? "border-error-600" : "border-slate-200"
				} ${className}`}
				{...props}
			/>
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
