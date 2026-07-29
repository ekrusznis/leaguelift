const STEPS_PER_ROW = 4;

function DownConnector() {
	return (
		<div className="flex justify-center py-1" aria-hidden="true">
			<svg className="size-6 text-green-400/70" viewBox="0 0 24 24" fill="none">
				<path d="M12 4v13m0 0-5-5m5 5 5-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		</div>
	);
}

/**
 * Full-width connected-step diagram for the detailed workflow (How It Works
 * section) — same numbered-circle-plus-dashed-line language as StepTimeline,
 * extended across two rows with a connector between them so an 8-step list
 * reads as one continuous flow instead of a grid of boxes.
 */
export function WorkflowFlow({ steps }: { steps: string[] }) {
	const rows: string[][] = [];
	for (let i = 0; i < steps.length; i += STEPS_PER_ROW) {
		rows.push(steps.slice(i, i + STEPS_PER_ROW));
	}

	return (
		<div className="flex flex-col">
			{rows.map((row, rowIndex) => (
				<div key={row.join("-")}>
					{rowIndex > 0 && <DownConnector />}
					<ol className="grid gap-x-4 gap-y-8 sm:grid-cols-2 lg:grid-cols-4">
						{row.map((step, stepIndex) => {
							const globalIndex = rowIndex * STEPS_PER_ROW + stepIndex;
							const isLastInRow = stepIndex === row.length - 1;
							return (
								<li key={step} className="flex flex-col gap-3">
									<div className="flex items-center gap-3">
										<span className="flex size-10 shrink-0 items-center justify-center rounded-full border border-green-400/50 bg-green-500/10 font-heading text-sm font-bold text-green-400">
											{globalIndex + 1}
										</span>
										{!isLastInRow && (
											<span className="hidden h-px flex-1 border-t border-dashed border-white/25 lg:block" aria-hidden="true" />
										)}
									</div>
									<p className="text-sm font-medium leading-snug text-white">{step}</p>
								</li>
							);
						})}
					</ol>
				</div>
			))}
		</div>
	);
}
