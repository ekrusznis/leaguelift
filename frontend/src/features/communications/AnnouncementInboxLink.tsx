import { Link } from "react-router-dom";
import { MegaphoneIcon } from "../../dashboard/icons";
import { useMyAnnouncements } from "./api";

export function AnnouncementInboxLink({ compact = false }: { compact?: boolean }) {
	const inbox = useMyAnnouncements();
	const unread = inbox.data?.items.filter((item) => item.readAt === null).length ?? 0;
	return (
		<Link
			to="/app/announcements"
			aria-label={unread > 0 ? `Announcements, ${unread} unread` : "Announcements"}
			className={compact
				? "relative flex size-9 items-center justify-center rounded-full text-slate-300 hover:bg-white/5 hover:text-white"
				: "inline-flex items-center gap-2 rounded-md px-2 py-1 text-sm font-medium hover:bg-pure-white/5"}
		>
			<MegaphoneIcon className="size-5" />
			{!compact && <span>Announcements</span>}
			{unread > 0 && <span className="flex min-w-4 items-center justify-center rounded-full bg-victory-green px-1 text-[10px] font-bold text-white">{unread > 9 ? "9+" : unread}</span>}
		</Link>
	);
}
