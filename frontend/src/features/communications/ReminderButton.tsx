import { useState } from "react";
import { Button } from "../../components/Button";
import { useSendReminder } from "./api";
import type { ReminderResourceType } from "./types";

export function ReminderButton({
	organizationId,
	resourceType,
	resourceId,
	label = "Send reminder",
}: {
	organizationId: string;
	resourceType: ReminderResourceType;
	resourceId: string;
	label?: string;
}) {
	const [open, setOpen] = useState(false);
	const [emailEnabled, setEmailEnabled] = useState(true);
	const [smsEnabled, setSmsEnabled] = useState(false);
	const [message, setMessage] = useState<string | null>(null);
	const sendReminder = useSendReminder();

	async function send() {
		setMessage(null);
		try {
			const result = await sendReminder.mutateAsync({
				organizationId,
				resourceType,
				resourceId,
				idempotencyKey: crypto.randomUUID(),
				emailEnabled,
				smsEnabled,
			});
			setMessage(`Published to ${result.recipientCount} recipient${result.recipientCount === 1 ? "" : "s"}.`);
			setOpen(false);
		} catch (error) {
			setMessage(error instanceof Error ? error.message : "The reminder could not be sent.");
		}
	}

	return (
		<div className="flex flex-col items-start gap-2">
			<Button type="button" variant="secondary" onClick={() => setOpen((value) => !value)}>
				{open ? "Cancel reminder" : label}
			</Button>
			{open && (
				<div className="rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-3 text-sm">
					<p className="font-medium text-navy dark:text-[#f8fafc]">Delivery channels</p>
					<label className="mt-2 flex items-center gap-2 text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={emailEnabled} onChange={(event) => setEmailEnabled(event.target.checked)} /> Email</label>
					<label className="mt-2 flex items-center gap-2 text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={smsEnabled} onChange={(event) => setSmsEnabled(event.target.checked)} /> SMS to opted-in households</label>
					<p className="mt-2 max-w-sm text-xs text-slate-gray dark:text-[#cbd5e1]">The reminder is always saved in Rally26. Email opt-outs and SMS opt-ins are enforced by the backend.</p>
					<Button type="button" className="mt-3" disabled={sendReminder.isPending} onClick={() => void send()}>
						{sendReminder.isPending ? "Publishing…" : "Publish reminder"}
					</Button>
				</div>
			)}
			{message && <p role="status" className="text-xs text-slate-gray dark:text-[#cbd5e1]">{message}</p>}
		</div>
	);
}
