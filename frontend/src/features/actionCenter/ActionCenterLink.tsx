import { Link } from "react-router-dom";
import { ClipboardCheckIcon } from "../../dashboard/icons";
import { useActionCenter } from "./api";

export function ActionCenterLink({ compact = false }: { compact?: boolean }) {
	const actionCenter = useActionCenter();
	const count = actionCenter.data?.totalCount ?? 0;
	const highCount = actionCenter.data?.highPriorityCount ?? 0;

	return (
		<Link
			to="/app/action-center"
			aria-label={count > 0 ? `Action center, ${count} open item${count === 1 ? "" : "s"}` : "Action center"}
			className={compact
				? "relative flex size-9 items-center justify-center rounded-full text-slate-300 hover:bg-white/5 hover:text-white"
				: "inline-flex items-center gap-2 rounded-md px-2 py-1 text-sm font-medium hover:bg-pure-white/5"}
		>
			<ClipboardCheckIcon className="size-5" />
			{!compact && <span>Action Center</span>}
			{count > 0 && (
				<span className={`flex min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-bold text-white ${highCount > 0 ? "bg-error-red" : "bg-victory-green"}`}>
					{count > 9 ? "9+" : count}
				</span>
			)}
		</Link>
	);
}
