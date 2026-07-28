import { TextButton } from "./buttons";
import { StatusBadge } from "./Badges";

type BentoCardProps = {
	heading: string;
	copy: string;
	actionLabel: string;
	to: string;
	badge?: string;
	large?: boolean;
};

/** Feature bento grid card — dark card on the light homepage section (section 12.4). */
export function BentoCard({ heading, copy, actionLabel, to, badge, large = false }: BentoCardProps) {
	return (
		<div
			className={`flex flex-col justify-between rounded-[22px] border border-white/[0.12] bg-navy-900 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.22)] ${large ? "sm:col-span-2" : ""}`}
		>
			<div>
				<div className="flex items-start justify-between gap-3">
					<h3 className="font-heading text-xl font-bold text-white">{heading}</h3>
					{badge && <StatusBadge tone="warning">{badge}</StatusBadge>}
				</div>
				<p className="mt-3 text-sm leading-relaxed text-slate-300">{copy}</p>
			</div>
			<TextButton to={to} icon="arrow" className="mt-6 self-start text-green-400 hover:text-green-300">
				{actionLabel}
			</TextButton>
		</div>
	);
}
