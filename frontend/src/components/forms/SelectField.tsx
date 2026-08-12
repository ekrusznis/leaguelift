import { forwardRef, useId, type SelectHTMLAttributes } from "react";

type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
	label: string;
	error?: string;
	options: { value: string; label: string }[];
	placeholder?: string;
};

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
	{ label, error, options, placeholder, id, required, className = "", defaultValue, ...props },
	ref,
) {
	const generatedId = useId();
	const fieldId = id ?? generatedId;
	const errorId = error ? `${fieldId}-error` : undefined;

	return (
		<div className="flex flex-col gap-1.5">
			<label htmlFor={fieldId} className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">
				{label}
				{required && (
					<span aria-hidden="true" className="text-error-600">
						{" "}
						*
					</span>
				)}
			</label>
			<select
				id={fieldId}
				ref={ref}
				required={required}
				aria-invalid={!!error}
				aria-describedby={errorId}
				defaultValue={defaultValue ?? ""}
				className={`min-h-12 rounded-[10px] border bg-white dark:bg-[#111827] px-3.5 py-2.5 text-navy-900 dark:text-[#f8fafc] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 ${
					error ? "border-error-600" : "border-slate-200 dark:border-[#334155]"
				} ${className}`}
				{...props}
			>
				{placeholder && (
					<option value="" disabled>
						{placeholder}
					</option>
				)}
				{options.map((option) => (
					<option key={option.value} value={option.value}>
						{option.label}
					</option>
				))}
			</select>
			{error && (
				<p id={errorId} role="alert" className="text-sm text-error-600">
					{error}
				</p>
			)}
		</div>
	);
});
