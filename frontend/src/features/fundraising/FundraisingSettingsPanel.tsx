import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useFundraisingSettings, useUpdateFundraisingSettings } from "./api";

export function FundraisingSettingsPanel({ organizationId, canApprove }: { organizationId: string; canApprove: boolean }) {
	const settings = useFundraisingSettings(organizationId);
	const update = useUpdateFundraisingSettings(organizationId);

	if (settings.isLoading) return <LoadingState label="Loading fundraising settings…" />;
	if (settings.isError || !settings.data) {
		return <ErrorState message="Could not load fundraising settings." onRetry={() => settings.refetch()} />;
	}

	const required = settings.data.requireOwnerApproval;
	return (
		<div className="rounded-xl border border-slate-gray/20 bg-pure-white p-4 dark:bg-[#111827]">
			<div className="flex flex-wrap items-start justify-between gap-4">
				<div className="max-w-2xl">
					<h3 className="font-heading font-semibold text-navy dark:text-[#f8fafc]">Owner approval before activation</h3>
					<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
						When enabled, a fundraiser created by a coach, parent, or administrator must be approved by the organization owner before it becomes public and can accept contributions.
					</p>
					<p className="mt-2 text-sm font-medium text-navy dark:text-[#f8fafc]">
						Current policy: {required ? "Owner approval required" : "Creators may activate without owner approval"}
					</p>
				</div>
				{canApprove && (
					<Button
						type="button"
						variant={required ? "secondary" : "primary"}
						disabled={update.isPending}
						onClick={() => update.mutate(!required)}
					>
						{update.isPending ? "Saving…" : required ? "Allow activation without approval" : "Require owner approval"}
					</Button>
				)}
			</div>
			{!canApprove && (
				<p className="mt-3 text-xs text-slate-gray dark:text-[#94a3b8]">Only the organization owner can change this policy.</p>
			)}
			{update.isError && <p role="alert" className="mt-3 text-sm text-error-red">Could not update the fundraising approval policy.</p>}
		</div>
	);
}
