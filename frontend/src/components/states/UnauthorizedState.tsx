export function UnauthorizedState({ message = "You don't have access to this page." }: { message?: string }) {
	return (
		<div role="alert" className="rounded-lg border border-championship-gold/40 bg-championship-gold/10 p-4">
			<p className="text-navy">{message}</p>
		</div>
	);
}
