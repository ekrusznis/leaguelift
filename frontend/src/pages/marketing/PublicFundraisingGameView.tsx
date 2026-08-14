import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useEnterPublicFundraisingGame, usePublicFundraisingGame } from "../../features/fundraisingGames/api";
import type { FundraisingGameType } from "../../features/fundraisingGames/types";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";

const LABELS: Record<FundraisingGameType, string> = {
	BIG_GAME_SQUARES: "Big Game Squares", BRACKET_CHALLENGE: "Bracket Challenge", PREDICTION_CHALLENGE: "Prediction Challenge",
	FREE_PRIZE_DRAWING: "Free Prize Drawing", TRIVIA_CHALLENGE: "Trivia Challenge",
};

export function PublicFundraisingGameView() {
	const { slug = "" } = useParams<{ slug: string }>();
	const game = usePublicFundraisingGame(slug);
	const enter = useEnterPublicFundraisingGame(slug);
	const [displayName, setDisplayName] = useState("");
	const [email, setEmail] = useState("");
	const [selectionKey, setSelectionKey] = useState<string | null>(null);
	const [selectionText, setSelectionText] = useState("");
	const taken = useMemo(() => new Map((game.data?.entries ?? []).filter((entry) => entry.selectionKey).map((entry) => [entry.selectionKey!, entry.displayName])), [game.data?.entries]);
	if (game.isLoading) return <div className="flex min-h-[60vh] items-center justify-center"><LoadingState label="Loading free game…" /></div>;
	if (game.isError || !game.data) return <div className="flex min-h-[60vh] items-center justify-center"><ErrorState message="This fundraiser does not have an available free game." /></div>;
	const item = game.data;
	const full = item.maxEntries != null && item.entryCount >= item.maxEntries;
	const canEnter = item.status === "OPEN" && !full;
	const needsText = item.gameType === "BRACKET_CHALLENGE" || item.gameType === "PREDICTION_CHALLENGE" || item.gameType === "TRIVIA_CHALLENGE";
	const submit = (event: React.FormEvent) => {
		event.preventDefault();
		enter.mutate({ displayName, email, selectionKey: item.gameType === "BIG_GAME_SQUARES" ? selectionKey : null, selectionText: needsText ? selectionText : null }, {
			onSuccess: () => { setSelectionKey(null); setSelectionText(""); },
		});
	};
	return <><Seo title={`${item.title} | Rally26`} description={item.freeEntryDisclosure} />
		<section className="min-h-[75vh] bg-navy-950 py-16 text-white"><PageContainer className="max-w-4xl">
			<Link to={`/campaigns/${slug}`} className="text-sm font-semibold text-green-400 hover:underline">← Back to fundraiser</Link>
			<p className="mt-6 text-xs font-bold uppercase tracking-[0.18em] text-green-400">Free fundraising game</p>
			<h1 className="mt-2 font-heading text-4xl font-extrabold">{item.title}</h1>
			<p className="mt-2 text-slate-300">{LABELS[item.gameType]} · {item.entryCount}{item.maxEntries ? ` / ${item.maxEntries}` : ""} entries</p>
			<div className="mt-5 rounded-xl border border-green-400/30 bg-green-400/10 p-4"><p className="font-semibold">Free to play</p><p className="mt-1 text-sm text-slate-200">{item.freeEntryDisclosure}</p></div>
			{item.instructions && <p className="mt-6 max-w-2xl text-lg text-slate-200">{item.instructions}</p>}
			{item.prizeDescription && <p className="mt-4 rounded-lg border border-white/10 bg-white/5 p-3"><strong>Prize / recognition:</strong> {item.prizeDescription}</p>}
			{item.winnerDisplayName && <div className="mt-6 rounded-xl bg-championship-gold/20 p-5 text-center"><p className="text-sm uppercase tracking-wide text-championship-gold">Winner</p><p className="mt-1 font-heading text-3xl font-bold">{item.winnerDisplayName}</p></div>}

			{item.gameType === "BIG_GAME_SQUARES" && item.rows && item.cols && <div className="mt-8 overflow-x-auto rounded-xl border border-white/10 bg-white/5 p-3"><div className="grid gap-1" style={{ gridTemplateColumns: `repeat(${item.cols}, minmax(58px, 1fr))`, minWidth: `${item.cols * 62}px` }}>{Array.from({ length: item.rows * item.cols }, (_, index) => { const row = Math.floor(index / item.cols!); const col = index % item.cols!; const key = `r${row}c${col}`; const owner = taken.get(key); const selected = selectionKey === key; return <button key={key} type="button" disabled={!!owner || !canEnter} onClick={() => setSelectionKey(key)} className={`min-h-14 rounded-md border p-1 text-xs ${owner ? "border-white/5 bg-white/10 text-slate-400" : selected ? "border-orange-400 bg-orange-500 text-white" : "border-white/20 bg-navy-800 hover:border-green-400"}`}><span className="block font-semibold">{row + 1}-{col + 1}</span><span className="block truncate">{owner ?? (selected ? "Your pick" : "Open")}</span></button>; })}</div></div>}

			{canEnter ? <form onSubmit={submit} className="mt-8 grid gap-3 rounded-xl border border-white/10 bg-white/5 p-5 sm:grid-cols-2">
				<div className="sm:col-span-2"><h2 className="font-heading text-2xl font-bold">Enter for free</h2><p className="mt-1 text-sm text-slate-300">Each person may use up to {item.entriesPerPerson} free entr{item.entriesPerPerson === 1 ? "y" : "ies"}. Donations are optional and separate.</p></div>
				<label className="flex flex-col gap-1 text-sm font-medium">Display name<input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={120} className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3" /><span className="text-xs font-normal text-slate-400">This name may appear on the public game board.</span></label>
				<label className="flex flex-col gap-1 text-sm font-medium">Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={254} className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3" /><span className="text-xs font-normal text-slate-400">Used to enforce free-entry limits and contact a winner; never shown publicly.</span></label>
				{needsText && <label className="flex flex-col gap-1 text-sm font-medium sm:col-span-2">{item.gameType === "TRIVIA_CHALLENGE" ? "Your answer" : "Your pick / prediction"}<textarea value={selectionText} onChange={(e) => setSelectionText(e.target.value)} required maxLength={1000} rows={3} className="rounded-md border border-white/20 bg-white/5 px-3 py-2" /></label>}
				{item.gameType === "BIG_GAME_SQUARES" && !selectionKey && <p className="sm:col-span-2 text-sm text-orange-300">Choose an open square above before entering.</p>}
				{enter.isError && <p role="alert" className="sm:col-span-2 rounded-md bg-error-red/10 p-2 text-sm text-error-red">That entry could not be saved. The selection may have just been taken, or you may have reached the free-entry limit.</p>}
				{enter.isSuccess && <p className="sm:col-span-2 rounded-md bg-green-500/10 p-2 text-sm text-green-300">Your free entry is in. Thank you for participating!</p>}
				<button type="submit" disabled={enter.isPending || (item.gameType === "BIG_GAME_SQUARES" && !selectionKey)} className="min-h-12 rounded-lg bg-orange-500 px-5 font-bold text-navy-950 disabled:opacity-50 sm:col-span-2">{enter.isPending ? "Saving entry…" : "Enter free"}</button>
			</form> : <div className="mt-8 rounded-xl border border-white/10 bg-white/5 p-5 text-center"><p className="font-semibold">{item.status === "CLOSED" ? "This game is closed." : "This game is not open yet."}</p></div>}
			<div className="mt-6 text-center"><Link to={`/campaigns/${slug}`} className="font-semibold text-green-400 hover:underline">Support the fundraiser (optional) →</Link></div>
		</PageContainer></section></>;
}
