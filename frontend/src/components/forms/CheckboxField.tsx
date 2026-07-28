import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from "react";

type CheckboxFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
	label: ReactNode;
	error?: string;
};

export const CheckboxField = forwardRef<HTMLInputElement, CheckboxFieldProps>(function CheckboxField(
	{ label, error, id, className = "", ...props },
	ref,
) {
	const generatedId = useId();
	const fieldId = id ?? generatedId;
	const errorId = error ? `${fieldId}-error` : undefined;

	return (
		<div className="flex flex-col gap-1.5">
			<div className="flex items-start gap-3">
				<input
					id={fieldId}
					ref={ref}
					type="checkbox"
					aria-invalid={!!error}
					aria-describedby={errorId}
					className={`mt-0.5 size-5 shrink-0 rounded border-slate-300 text-green-500 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-green-400 ${className}`}
					{...props}
				/>
				<label htmlFor={fieldId} className="text-sm leading-relaxed text-slate-700">
					{label}
				</label>
			</div>
			{error && (
				<p id={errorId} role="alert" className="text-sm text-error-600">
					{error}
				</p>
			)}
		</div>
	);
});
