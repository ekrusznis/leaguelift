import { useOnboardingProgress } from "./api";

function ChecklistItem({ done, label }: { done: boolean; label: string }) {
	return (
		<li className="flex items-center gap-2">
			<span
				aria-hidden="true"
				className={`flex h-5 w-5 items-center justify-center rounded-full text-xs ${
					done ? "bg-victory-green text-pure-white" : "border border-slate-gray/40 text-slate-gray"
				}`}
			>
				{done ? "✓" : ""}
			</span>
			<span className={done ? "text-navy" : "text-slate-gray"}>{label}</span>
			<span className="sr-only">{done ? "complete" : "incomplete"}</span>
		</li>
	);
}

export function OnboardingChecklist({ organizationId }: { organizationId: string }) {
	const { data, isLoading } = useOnboardingProgress(organizationId);

	if (isLoading || !data) {
		return null;
	}

	return (
		<div className="rounded-lg border border-championship-gold/40 bg-championship-gold/10 p-4">
			<h2 className="font-heading font-semibold text-navy">Onboarding</h2>
			<ul className="mt-2 flex flex-col gap-1">
				<ChecklistItem done={data.profileComplete} label="Complete organization profile (sports + contact email)" />
				<ChecklistItem done={data.hasAdditionalAdministrator} label="Invite a second administrator" />
			</ul>
		</div>
	);
}
