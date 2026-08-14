import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	useCloseFundraisingGame,
	useCreateFundraisingGame,
	useDrawFundraisingGameWinner,
	useFundraisingGame,
	useFundraisingGameEntries,
	useOpenFundraisingGame,
	useUpdateFundraisingGame,
} from "./api";
import type { FundraisingGameFormValues, FundraisingGameType } from "./types";

const GAME_LABELS: Record<FundraisingGameType, string> = {
	BIG_GAME_SQUARES: "Big Game Squares",
	BRACKET_CHALLENGE: "Bracket Challenge",
	PREDICTION_CHALLENGE: "Prediction Challenge",
	FREE_PRIZE_DRAWING: "Free Prize Drawing",
	TRIVIA_CHALLENGE: "Trivia Challenge",
};
const DEFAULTS: FundraisingGameFormValues = {
	gameType: "BIG_GAME_SQUARES", title: "Big Game Squares", instructions: "Pick an open square for free and follow along with the game.", prizeDescription: "",
	maxEntries: "", entriesPerPerson: 1, rows: 10, cols: 10,
};

export function GameManagementPanel({ organizationId, campaignId, canCreateGame }: { organizationId: string; campaignId: string; canCreateGame: boolean }) {
	const game = useFundraisingGame(organizationId, campaignId);
	const entries = useFundraisingGameEntries(organizationId, campaignId, !!game.data);
	const create = useCreateFundraisingGame(organizationId, campaignId);
	const update = useUpdateFundraisingGame(organizationId, campaignId);
	const open = useOpenFundraisingGame(organizationId, campaignId);
	const close = useCloseFundraisingGame(organizationId, campaignId);
	const draw = useDrawFundraisingGameWinner(organizationId, campaignId);
	const [editing, setEditing] = useState(false);
	const [values, setValues] = useState<FundraisingGameFormValues>(DEFAULTS);
	useEffect(() => {
		if (!game.data) return;
		setValues({
			gameType: game.data.gameType, title: game.data.title, instructions: game.data.instructions ?? "", prizeDescription: game.data.prizeDescription ?? "",
			maxEntries: game.data.maxEntries == null ? "" : String(game.data.maxEntries), entriesPerPerson: game.data.entriesPerPerson,
			rows: game.data.rows ?? 10, cols: game.data.cols ?? 10,
		});
	}, [game.data]);

	if (game.isLoading) return <LoadingState label="Loading free game…" />;
	if (game.isError) return <ErrorState message="Could not load the fundraiser game." onRetry={() => game.refetch()} />;
	if (!game.data) {
		if (!canCreateGame) return <p className="text-sm text-slate-gray dark:text-[#94a3b8]">No free game is attached to this fundraiser.</p>;
		return <GameForm values={values} setValues={setValues} submitLabel={create.isPending ? "Creating…" : "Add free game"} disabled={create.isPending} error={create.isError} onSubmit={() => create.mutate(values)} />;
	}
	const item = game.data;
	return <div className="flex flex-col gap-3 rounded-lg bg-ice-white p-3 dark:bg-[#0f172a]">
		<div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-semibold text-navy dark:text-[#f8fafc]">{item.title}</p><p className="text-xs text-slate-gray dark:text-[#94a3b8]">{GAME_LABELS[item.gameType]} · {item.status} · {item.entryCount} entr{item.entryCount === 1 ? "y" : "ies"}</p></div>
			<div className="flex flex-wrap gap-2">{item.permissions.canConfigure && <Button type="button" variant="secondary" onClick={() => setEditing((v) => !v)}>{editing ? "Cancel edit" : "Edit game"}</Button>}{item.permissions.canOpen && <Button type="button" onClick={() => open.mutate()} disabled={open.isPending}>Open free game</Button>}{item.permissions.canClose && <Button type="button" variant="secondary" onClick={() => close.mutate()} disabled={close.isPending}>Close game</Button>}{item.permissions.canDrawWinner && <Button type="button" onClick={() => draw.mutate()} disabled={draw.isPending}>{draw.isPending ? "Drawing…" : "Draw winner"}</Button>}</div>
		</div>
		<p className="rounded-md bg-victory-green/10 p-2 text-xs text-navy dark:text-[#dbeafe]"><strong>Free-entry rule:</strong> no purchase or donation is necessary. Donations never add entries or improve odds.</p>
		{item.instructions && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{item.instructions}</p>}
		{item.prizeDescription && <p className="text-sm"><strong>Prize:</strong> {item.prizeDescription}</p>}
		{editing && <GameForm values={values} setValues={setValues} lockType submitLabel={update.isPending ? "Saving…" : "Save game"} disabled={update.isPending} error={update.isError} onSubmit={() => update.mutate(values, { onSuccess: () => setEditing(false) })} />}
		{entries.isLoading && <LoadingState label="Loading entries…" />}
		{entries.data && entries.data.length > 0 && <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Player</th><th className="p-2">Entry</th><th className="p-2">Email</th><th className="p-2">Result</th></tr></thead><tbody>{entries.data.map((entry) => <tr key={entry.id} className="border-t border-slate-gray/20"><td className="p-2">{entry.displayName}</td><td className="p-2">{entry.selectionKey ?? entry.selectionText ?? "Free drawing entry"}</td><td className="p-2">{entry.email}</td><td className="p-2">{entry.isWinner ? "Winner" : "—"}</td></tr>)}</tbody></table></div>}
		{draw.data && <p className="rounded-md bg-championship-gold/15 p-3 font-semibold">Winner: {draw.data.displayName}</p>}
	</div>;
}

function GameForm({ values, setValues, onSubmit, submitLabel, disabled, error, lockType = false }: {
	values: FundraisingGameFormValues; setValues: (value: FundraisingGameFormValues) => void; onSubmit: () => void; submitLabel: string; disabled: boolean; error: boolean; lockType?: boolean;
}) {
	const set = <K extends keyof FundraisingGameFormValues>(key: K, value: FundraisingGameFormValues[K]) => setValues({ ...values, [key]: value });
	return <form className="grid gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827] md:grid-cols-2" onSubmit={(event) => { event.preventDefault(); onSubmit(); }}>
		<label className="flex flex-col gap-1 text-sm font-medium">Game style<select value={values.gameType} disabled={lockType} onChange={(e) => { const type = e.target.value as FundraisingGameType; setValues({ ...values, gameType: type, title: GAME_LABELS[type] }); }} className="min-h-11 rounded-md border border-slate-gray/30 px-3"><option value="BIG_GAME_SQUARES">Big Game Squares</option><option value="BRACKET_CHALLENGE">Bracket Challenge</option><option value="PREDICTION_CHALLENGE">Prediction Challenge</option><option value="FREE_PRIZE_DRAWING">Free Prize Drawing</option><option value="TRIVIA_CHALLENGE">Trivia Challenge</option></select></label>
		<label className="flex flex-col gap-1 text-sm font-medium">Title<input value={values.title} onChange={(e) => set("title", e.target.value)} maxLength={160} required className="min-h-11 rounded-md border border-slate-gray/30 px-3" /></label>
		<label className="flex flex-col gap-1 text-sm font-medium md:col-span-2">Instructions<textarea value={values.instructions} onChange={(e) => set("instructions", e.target.value)} maxLength={3000} rows={3} className="rounded-md border border-slate-gray/30 px-3 py-2" /></label>
		<label className="flex flex-col gap-1 text-sm font-medium md:col-span-2">Prize / recognition (optional)<input value={values.prizeDescription} onChange={(e) => set("prizeDescription", e.target.value)} maxLength={1000} className="min-h-11 rounded-md border border-slate-gray/30 px-3" /><span className="text-xs font-normal text-slate-gray">Use a fixed organization/sponsor-provided prize or recognition. Do not describe a percentage of fundraiser donations as the prize.</span></label>
		<label className="flex flex-col gap-1 text-sm font-medium">Free entries per person<input type="number" min={1} max={20} value={values.entriesPerPerson} onChange={(e) => set("entriesPerPerson", Number(e.target.value))} className="min-h-11 rounded-md border border-slate-gray/30 px-3" /></label>
		{values.gameType === "BIG_GAME_SQUARES" ? <><label className="flex flex-col gap-1 text-sm font-medium">Rows<input type="number" min={1} max={26} value={values.rows} onChange={(e) => set("rows", Number(e.target.value))} className="min-h-11 rounded-md border border-slate-gray/30 px-3" /></label><label className="flex flex-col gap-1 text-sm font-medium">Columns<input type="number" min={1} max={26} value={values.cols} onChange={(e) => set("cols", Number(e.target.value))} className="min-h-11 rounded-md border border-slate-gray/30 px-3" /></label><p className="self-end pb-3 text-xs text-slate-gray">Capacity: {values.rows * values.cols} free squares</p></> : <label className="flex flex-col gap-1 text-sm font-medium">Total entries allowed<input type="number" min={1} max={100000} value={values.maxEntries} onChange={(e) => set("maxEntries", e.target.value)} placeholder="Blank = unlimited" className="min-h-11 rounded-md border border-slate-gray/30 px-3" /></label>}
		<p className="md:col-span-2 rounded-md bg-victory-green/10 p-2 text-xs">Entry is always free. Donation checkout is separate and never changes entry limits or odds.</p>
		{error && <p role="alert" className="md:col-span-2 text-sm text-error-red">Could not save the free game. Check the configuration and try again.</p>}
		<div className="flex justify-end md:col-span-2"><Button type="submit" disabled={disabled}>{submitLabel}</Button></div>
	</form>;
}
