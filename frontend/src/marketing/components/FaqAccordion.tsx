import { useId, useState } from "react";
import { track } from "../analytics";

type FaqItem = { question: string; answer: string };

export function FaqAccordion({ items, tone = "light" }: { items: FaqItem[]; tone?: "light" | "dark" }) {
	const [openIndex, setOpenIndex] = useState<number | null>(null);
	const baseId = useId();

	const borderColor = tone === "dark" ? "border-white/15" : "border-slate-200";
	const questionColor = tone === "dark" ? "text-white" : "text-navy-900";
	const answerColor = tone === "dark" ? "text-slate-300" : "text-slate-700";

	return (
		<div className={`rounded-2xl border ${borderColor}`}>
			{items.map((item, index) => {
				const isOpen = openIndex === index;
				const panelId = `${baseId}-panel-${index}`;
				const buttonId = `${baseId}-button-${index}`;

				return (
					<div key={item.question} className={`border-t first:border-t-0 ${borderColor}`}>
						<h3>
							<button
								type="button"
								id={buttonId}
								aria-expanded={isOpen}
								aria-controls={panelId}
								onClick={() => {
									const next = isOpen ? null : index;
									setOpenIndex(next);
									if (next !== null) track("faq_opened", { question: item.question });
								}}
								className={`flex w-full items-center justify-between gap-4 px-5 py-4 text-left font-heading text-base font-semibold ${questionColor}`}
							>
								{item.question}
								<svg
									className={`size-5 shrink-0 transition-transform duration-200 ${isOpen ? "rotate-180" : ""}`}
									viewBox="0 0 16 16"
									fill="none"
									aria-hidden="true"
								>
									<path d="m4 6 4 4 4-4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
								</svg>
							</button>
						</h3>
						<div
							id={panelId}
							role="region"
							aria-labelledby={buttonId}
							hidden={!isOpen}
							className={`px-5 pb-4 text-sm leading-relaxed ${answerColor}`}
						>
							{item.answer}
						</div>
					</div>
				);
			})}
		</div>
	);
}
