export function ErrorState({
	message = "Something went wrong. Please try again.",
	onRetry,
}: {
	message?: string;
	onRetry?: () => void;
}) {
	return (
		<div role="alert" className="rounded-lg border border-error-red/30 bg-error-red/5 p-4 text-error-red">
			<p>{message}</p>
			{onRetry && (
				<button
					type="button"
					onClick={onRetry}
					className="mt-2 rounded-md border border-error-red px-3 py-1.5 text-sm font-medium hover:bg-error-red/10 focus-visible:outline-error-red"
				>
					Try again
				</button>
			)}
		</div>
	);
}
